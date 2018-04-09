/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.parser.ast.visitors;

import com.oracle.truffle.regex.tregex.parser.ast.BackReference;
import com.oracle.truffle.regex.tregex.parser.ast.CharacterClass;
import com.oracle.truffle.regex.tregex.parser.ast.Group;
import com.oracle.truffle.regex.tregex.parser.ast.LookAheadAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.LookBehindAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.MatchFound;
import com.oracle.truffle.regex.tregex.parser.ast.PositionAssertion;
import com.oracle.truffle.regex.tregex.parser.ast.RegexAST;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTNode;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTSubtreeRootNode;
import com.oracle.truffle.regex.tregex.parser.ast.Sequence;

public final class InitIDVisitor extends DepthFirstTraversalRegexASTVisitor {

    /**
     * ID of the parent node of AST nodes that are not part of a lookaround assertion.
     */
    public static final int REGEX_AST_ROOT_PARENT_ID = 0;

    private final RegexASTNode[] index;
    private int nextID;

    private InitIDVisitor(RegexASTNode[] index, int nextID) {
        this.index = index;
        this.nextID = nextID;
    }

    public static void init(RegexAST ast) {
        // additional reserved slots:
        // - 1 slot for REGEX_AST_ROOT_PARENT_ID
        // - prefix length + 1 anchored initial NFA states
        // - prefix length + 1 unanchored initial NFA states
        // - 1 slot at the end for NFA loopBack matcher
        int initialID = 3 + (ast.getWrappedPrefixLength() * 2);
        InitIDVisitor visitor = new InitIDVisitor(new RegexASTNode[initialID + ast.getNumberOfNodes() + 1], initialID);
        assert ast.getWrappedRoot().getSubTreeParent().getId() == REGEX_AST_ROOT_PARENT_ID;
        visitor.index[REGEX_AST_ROOT_PARENT_ID] = ast.getWrappedRoot().getSubTreeParent();
        visitor.run(ast.getWrappedRoot());
        ast.setIndex(visitor.index);
    }

    private void initID(RegexASTNode node) {
        node.setId(nextID++);
        index[node.getId()] = node;
    }

    @Override
    protected void visit(BackReference backReference) {
        initID(backReference);
    }

    @Override
    protected void visit(Group group) {
        initID(group);
    }

    @Override
    protected void leave(Group group) {
        if (group.getParent() instanceof RegexASTSubtreeRootNode) {
            final MatchFound matchFound = group.getSubTreeParent().getMatchFound();
            if (!matchFound.idInitialized()) {
                initID(matchFound);
            }
        }
    }

    @Override
    protected void visit(Sequence sequence) {
        initID(sequence);
    }

    @Override
    protected void visit(PositionAssertion assertion) {
        initID(assertion);
    }

    @Override
    protected void visit(LookBehindAssertion assertion) {
        initID(assertion);
    }

    @Override
    protected void visit(LookAheadAssertion assertion) {
        initID(assertion);
    }

    @Override
    protected void visit(CharacterClass characterClass) {
        initID(characterClass);
    }

    @Override
    protected void visit(MatchFound matchFound) {
        initID(matchFound);
    }
}
