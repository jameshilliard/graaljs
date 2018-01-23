/*
 * Copyright (c) 2016, 2016, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package com.oracle.truffle.regex.tregex.matchers;

import com.oracle.truffle.api.profiles.ConditionProfile;

public abstract class ProfiledCharMatcher implements CharMatcher {

    private final boolean invert;

    private final ConditionProfile profile = ConditionProfile.createBinaryProfile();

    protected ProfiledCharMatcher(boolean invert) {
        this.invert = invert;
    }

    @Override
    public boolean match(char c) {
        return profile.profile(matchChar(c) != invert);
    }

    protected abstract boolean matchChar(char c);

    protected String modifiersToString() {
        return invert ? "!" : "";
    }

    static int highByte(int i) {
        return i >> Byte.SIZE;
    }

    static int lowByte(int i) {
        return i & 0xff;
    }
}