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
package com.oracle.truffle.regex.tregex.matchers;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.regex.tregex.util.DebugUtil;
import com.oracle.truffle.regex.util.CompilationFinalBitSet;

/**
 * Matcher that matches multiple characters with a common high byte using a bit set.<br>
 * Example: characters {@code \u1010, \u1020, \u1030} have a common high byte {@code 0x10}, so they
 * are matched by this high byte and a bit set that matches {@code 0x10}, {@code 0x20} and
 * {@code 0x30}.
 */
public final class BitSetMatcher extends ProfiledCharMatcher {

    private final int highByte;
    private final CompilationFinalBitSet bitSet;

    private BitSetMatcher(boolean invert, int highByte, CompilationFinalBitSet bitSet) {
        super(invert);
        this.highByte = highByte;
        this.bitSet = bitSet;
    }

    /**
     * Constructs a new bit-set-based character matcher.
     * 
     * @param invert see {@link ProfiledCharMatcher}.
     * @param highByte the high byte common to all characters to match.
     * @param bitSet the bit set to match the low byte of the characters to match.
     * @return a new {@link BitSetMatcher} or a {@link NullHighByteBitSetMatcher}.
     */
    public static ProfiledCharMatcher create(boolean invert, int highByte, CompilationFinalBitSet bitSet) {
        if (highByte == 0) {
            return new NullHighByteBitSetMatcher(invert, bitSet);
        }
        return new BitSetMatcher(invert, highByte, bitSet);
    }

    @Override
    public boolean matchChar(char c) {
        return highByte(c) == highByte && bitSet.get(lowByte(c));
    }

    @Override
    @CompilerDirectives.TruffleBoundary
    public String toString() {
        return modifiersToString() + "{hi " + DebugUtil.charToString(highByte) + " lo " + bitSet + "}";
    }

    public CompilationFinalBitSet getBitSet() {
        return bitSet;
    }
}
