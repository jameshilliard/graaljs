/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.js.nodes.interop;

import com.oracle.truffle.api.TruffleLanguage.LanguageReference;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.js.lang.JavaScriptLanguage;
import com.oracle.truffle.js.nodes.JavaScriptBaseNode;
import com.oracle.truffle.js.runtime.BigInt;
import com.oracle.truffle.js.runtime.JSRuntime;
import com.oracle.truffle.js.runtime.JSTruffleOptions;
import com.oracle.truffle.js.runtime.LargeInteger;
import com.oracle.truffle.js.runtime.builtins.JSFunction;
import com.oracle.truffle.js.runtime.objects.JSLazyString;
import com.oracle.truffle.js.runtime.truffleinterop.InteropAsyncFunction;
import com.oracle.truffle.js.runtime.truffleinterop.InteropBoundFunction;

/**
 * This node prepares the export of a value via Interop. It transforms values not allowed in Truffle
 * (e.g. {@link JSLazyString}) and binds Functions. See also {@link JSRuntime#exportValue(Object)}.
 *
 * @see JSRuntime#exportValue(Object)
 * @see ExportValueNode
 */
@ImportStatic({JSTruffleOptions.class, JSFunction.class})
@GenerateUncached
public abstract class ExportMemberValueNode extends JavaScriptBaseNode {

    ExportMemberValueNode() {
    }

    public abstract Object execute(Object value, Object thiz, LanguageReference<JavaScriptLanguage> languageRef);

    @Specialization(guards = {"isJSFunction(function)", "!bindFunctions", "!InteropCompletePromises || !isAsyncFunction(function)"})
    protected static DynamicObject doFunctionNoBind(DynamicObject function, @SuppressWarnings("unused") Object thiz, @SuppressWarnings("unused") LanguageReference<JavaScriptLanguage> languageRef,
                    @Cached(value = "languageRef.get().bindMemberFunctions()", allowUncached = true) @Shared("bindFunctions") @SuppressWarnings("unused") boolean bindFunctions) {
        return function;
    }

    @Specialization(guards = {"isJSFunction(function)", "bindFunctions", "isUndefined(thiz)", "!InteropCompletePromises || !isAsyncFunction(function)"})
    protected static DynamicObject doFunctionUndefinedThis(DynamicObject function, @SuppressWarnings("unused") Object thiz,
                    @SuppressWarnings("unused") LanguageReference<JavaScriptLanguage> languageRef,
                    @Cached(value = "languageRef.get().bindMemberFunctions()", allowUncached = true) @Shared("bindFunctions") @SuppressWarnings("unused") boolean bindFunctions) {
        return function;
    }

    @Specialization(guards = {"isJSFunction(function)", "bindFunctions", "!isUndefined(thiz)", "!isBoundJSFunction(function)", "!InteropCompletePromises || !isAsyncFunction(function)"})
    protected static TruffleObject doBindUnboundFunction(DynamicObject function, Object thiz, @SuppressWarnings("unused") LanguageReference<JavaScriptLanguage> languageRef,
                    @Cached(value = "languageRef.get().bindMemberFunctions()", allowUncached = true) @Shared("bindFunctions") @SuppressWarnings("unused") boolean bindFunctions) {
        return new InteropBoundFunction(function, thiz);
    }

    @Specialization(guards = {"isJSFunction(function)", "bindFunctions", "isBoundJSFunction(function)", "!InteropCompletePromises || !isAsyncFunction(function)"})
    protected static DynamicObject doBoundFunction(DynamicObject function, @SuppressWarnings("unused") Object thiz, @SuppressWarnings("unused") LanguageReference<JavaScriptLanguage> languageRef,
                    @Cached(value = "languageRef.get().bindMemberFunctions()", allowUncached = true) @Shared("bindFunctions") @SuppressWarnings("unused") boolean bindFunctions) {
        return function;
    }

    @Specialization(guards = {"isJSFunction(function)", "InteropCompletePromises", "isAsyncFunction(function)"})
    protected static TruffleObject doAsyncFunction(DynamicObject function, @SuppressWarnings("unused") Object thiz, @SuppressWarnings("unused") LanguageReference<JavaScriptLanguage> languageRef) {
        return new InteropAsyncFunction(function);
    }

    @Specialization
    protected static double doLargeInteger(LargeInteger value, @SuppressWarnings("unused") Object thiz, @SuppressWarnings("unused") LanguageReference<JavaScriptLanguage> languageRef) {
        return value.doubleValue();
    }

    @Specialization(guards = {"!isJSFunction(value)"})
    protected static DynamicObject doObject(DynamicObject value, @SuppressWarnings("unused") Object thiz, @SuppressWarnings("unused") LanguageReference<JavaScriptLanguage> languageRef) {
        return value;
    }

    @Specialization
    protected static int doInt(int value, @SuppressWarnings("unused") Object thiz, @SuppressWarnings("unused") LanguageReference<JavaScriptLanguage> languageRef) {
        return value;
    }

    @Specialization
    protected static long doLong(long value, @SuppressWarnings("unused") Object thiz, @SuppressWarnings("unused") LanguageReference<JavaScriptLanguage> languageRef) {
        return value;
    }

    @Specialization
    protected static double doDouble(double value, @SuppressWarnings("unused") Object thiz, @SuppressWarnings("unused") LanguageReference<JavaScriptLanguage> languageRef) {
        return value;
    }

    @Specialization
    protected static boolean doBoolean(boolean value, @SuppressWarnings("unused") Object thiz, @SuppressWarnings("unused") LanguageReference<JavaScriptLanguage> languageRef) {
        return value;
    }

    @Specialization
    protected static BigInt doBigInt(BigInt value, @SuppressWarnings("unused") Object thiz, @SuppressWarnings("unused") LanguageReference<JavaScriptLanguage> languageRef) {
        return value;
    }

    @Specialization
    protected static String doString(String value, @SuppressWarnings("unused") Object thiz, @SuppressWarnings("unused") LanguageReference<JavaScriptLanguage> languageRef) {
        return value;
    }

    @Specialization(guards = {"!isJSFunction(value)"}, replaces = "doObject")
    protected static TruffleObject doTruffleObject(TruffleObject value, @SuppressWarnings("unused") Object thiz, @SuppressWarnings("unused") LanguageReference<JavaScriptLanguage> languageRef) {
        return value;
    }

    @Fallback
    protected static final Object doOther(Object value, @SuppressWarnings("unused") Object thiz, @SuppressWarnings("unused") LanguageReference<JavaScriptLanguage> languageRef) {
        return ExportValueNode.doOther(value);
    }

    public static ExportMemberValueNode create() {
        return ExportMemberValueNodeGen.create();
    }
}
