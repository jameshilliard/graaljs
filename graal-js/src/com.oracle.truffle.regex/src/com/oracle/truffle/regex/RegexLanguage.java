/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package com.oracle.truffle.regex;

import java.util.Collections;
import java.util.Map;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.regex.joni.JoniRegexEngine;
import com.oracle.truffle.regex.nodes.RegexGetCompiledRegexRootNode;
import com.oracle.truffle.regex.result.RegexResult;
import com.oracle.truffle.regex.runtime.RegexCompiledRegex;
import com.oracle.truffle.regex.runtime.RegexResultObject;
import com.oracle.truffle.regex.tregex.TRegexEngine;
import com.oracle.truffle.regex.tregex.TRegexOptions;
import com.oracle.truffle.regex.tregex.parser.RegexParser;
import com.oracle.truffle.regex.util.LRUCache;

/**
 * Truffle Regular Expression Language
 * <p>
 * This language represents classic regular expressions, currently in JavaScript flavor only.
 * Accepted sources are single-line regular expressions in the form "/regex/flags". If the provided
 * source is a valid expression, it will return a {@link RegexCompiledRegex}, which is executable.
 * <p>
 * {@link RegexCompiledRegex} accepts two parameters:
 * <ol>
 * <li>{@link Object} {@code input}: the character sequence to search in. This may either be a
 * {@link String} or a {@link TruffleObject} that responds to {@link Message#GET_SIZE} and returns
 * {@link Character}s on indexed {@link Message#READ} requests.</li>
 * <li>{@link Number} {@code fromIndex}: the position to start searching from. This argument will be
 * cast to {@code int}, since a {@link String} can not be longer than {@link Integer#MAX_VALUE}. If
 * {@code fromIndex} is greater than {@link Integer#MAX_VALUE}, this method will immediately return
 * NO_MATCH.</li>
 * <li>The return value is a {@link RegexResultObject}, which has the following properties:
 * <ol>
 * <li>{@code Object input}: The input sequence this result was calculated from. If the result is no
 * match, this property is {@code null}.</li>
 * <li>{@code boolean isMatch}: {@code true} if a match was found, {@code false} otherwise.</li>
 * <li>{@code int groupCount}: number of capture groups present in the regular expression, including
 * group 0. If the result is no match, this property is {@code 0}.</li>
 * <li>{@link TruffleObject}{@code start}: array of positions where the beginning of the capture
 * group with the given number was found. If the result is no match, this property is an empty
 * array. Capture group number {@code 0} denotes the boundaries of the entire expression. If no
 * match was found for a particular capture group, the returned value at its respective index is
 * {@code -1}.</li>
 * <li>{@link TruffleObject}{@code end}: array of positions where the end of the capture group with
 * the given number was found. If the result is no match, this property is an empty array. Capture
 * group number {@code 0} denotes the boundaries of the entire expression. If no match was found for
 * a particular capture group, the returned value at its respective index is {@code -1}.</li>
 * </ol>
 * </li>
 * </ol>
 * <p>
 * Usage example in JavaScript: {@code
 * var pattern = Interop.eval("application/js-regex", "/(a|(b))c/i");
 * var result = pattern.exec("xacy", 0);
 * print(result.isMatch); // true
 * print(result.input); // "xacy"
 * print(result.groupCount); // 3
 * print(result.start[0] + ", " + result.end[0]); // "1, 3"
 * print(result.start[1] + ", " + result.end[1]); // "1, 2"
 * print(result.start[2] + ", " + result.end[2]); // "-1, -1"
 * var result2 = pattern.exec("xxx", 0);
 * print(result.isMatch); // false
 * print(result.input); // null
 * print(result.groupCount); // 0
 * print(result.start[0] + ", " + result.end[0]); // throws IndexOutOfBoundsException
 * }
 */

@TruffleLanguage.Registration(name = RegexLanguage.NAME, id = RegexLanguage.ID, mimeType = RegexLanguage.MIME_TYPE, version = "0.1", internal = true)
public final class RegexLanguage extends TruffleLanguage<Void> {

    public static final String NAME = "REGEX";
    public static final String ID = "regex";
    public static final String MIME_TYPE = "application/js-regex";

    private final TRegexEngine tRegexEngine = new TRegexEngine();
    private final JoniRegexEngine fallbackEngine = new JoniRegexEngine();
    static final String NO_MATCH_RESULT_IDENTIFIER = "T_REGEX_NO_MATCH_RESULT";
    public static final RegexResultObject EXPORT_NO_MATCH_RESULT = new RegexResultObject(RegexResult.NO_MATCH);
    private static final Iterable<Scope> NO_MATCH_RESULT_SCOPE = Collections.singleton(Scope.newBuilder("global", new NoMatchResultObject()).build());

    /**
     * Trying to parse and compile a regular expression using can produce one of two results. This
     * class encodes the sum of these two possibilities.
     * 
     * <ul>
     * <li>the regular expression is successfully compiled: getCompiledRegex is not null,
     * syntaxException is null</li>
     * <li>there is a syntax error in the regular expression: getCompiledRegex is null,
     * syntaxException is not null</li>
     * </ul>
     */
    private static final class ParsingResult {

        private final CallTarget getCompiledRegex;
        private final RegexSyntaxException syntaxException;

        private ParsingResult(CallTarget getCompiledRegex, RegexSyntaxException syntaxException) {
            this.getCompiledRegex = getCompiledRegex;
            this.syntaxException = syntaxException;
        }
    }

    private final Map<RegexSource, ParsingResult> cache = Collections.synchronizedMap(new LRUCache<>(TRegexOptions.RegexMaxCacheSize));

    private CallTarget compileWithCache(RegexSource source) throws RegexSyntaxException {
        ParsingResult result = cache.get(source);
        if (result == null) {
            result = compileCallTarget(source);
            cache.put(source, result);
        }
        if (result.getCompiledRegex == null) {
            assert result.syntaxException != null;
            throw result.syntaxException;
        } else {
            assert result.syntaxException == null;
            return result.getCompiledRegex;
        }
    }

    private ParsingResult compileCallTarget(RegexSource regexSource) {
        try {
            CompiledRegex regex = compileRegex(regexSource);
            RegexGetCompiledRegexRootNode rootNode = new RegexGetCompiledRegexRootNode(this, null, new RegexCompiledRegex(regex));
            return new ParsingResult(Truffle.getRuntime().createCallTarget(rootNode), null);
        } catch (RegexSyntaxException e) {
            return new ParsingResult(null, e);
        }
    }

    public static void tRegexValidate(Source source) throws RegexSyntaxException {
        RegexParser.validate(parseRegexSource(source));
    }

    private static RegexSource parseRegexSource(Source source) throws RegexSyntaxException {
        String code = source.getCharacters().toString();
        if (code.length() < 2) {
            throw new RegexSyntaxException(code, "length must be at least 2 (//)!");
        }
        int firstSlash = code.indexOf('/');
        if (firstSlash < 0) {
            throw new RegexSyntaxException(code, "pattern must start with a slash!");
        }
        int lastSlash = code.lastIndexOf('/');
        if (lastSlash == firstSlash) {
            throw new RegexSyntaxException(code, "pattern must end with a slash!");
        }
        final String optionsString = code.substring(0, firstSlash);
        final String patternString = code.substring(firstSlash + 1, lastSlash);
        final String flagsString = code.substring(lastSlash + 1);
        return new RegexSource(patternString, RegexFlags.parseFlags(flagsString), RegexOptions.parse(optionsString));
    }

    private CompiledRegex compileRegex(RegexSource regexSource) throws RegexSyntaxException {
        CompiledRegex regex = null;
        if (!regexSource.getOptions().useJoniEngine()) {
            regex = tRegexEngine.compile(regexSource);
        }
        if (regex == null) {
            regex = fallbackEngine.compile(regexSource);
        }
        return regex;
    }

    @Override
    protected Void createContext(Env env) {
        env.exportSymbol(NO_MATCH_RESULT_IDENTIFIER, EXPORT_NO_MATCH_RESULT);
        return null;
    }

    @Override
    protected CallTarget parse(ParsingRequest request) throws Exception {
        return compileWithCache(parseRegexSource(request.getSource()));
    }

    @Override
    protected Object findExportedSymbol(Void context, String globalName, boolean onlyExplicit) {
        if (globalName.equals(NO_MATCH_RESULT_IDENTIFIER)) {
            return EXPORT_NO_MATCH_RESULT;
        }
        return null;
    }

    @Override
    protected Iterable<Scope> findTopScopes(Void context) {
        return NO_MATCH_RESULT_SCOPE;
    }

    @Override
    protected Object getLanguageGlobal(Void context) {
        return null;
    }

    @Override
    protected boolean isObjectOfLanguage(Object object) {
        return object instanceof RegexLanguageObject;
    }
}