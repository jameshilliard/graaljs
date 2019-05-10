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

package com.oracle.truffle.js.runtime.array.dyn;

import static com.oracle.truffle.js.runtime.builtins.JSAbstractArray.arrayGetArray;
import static com.oracle.truffle.js.runtime.builtins.JSAbstractArray.arrayGetLength;

import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.js.runtime.JSContext;
import com.oracle.truffle.js.runtime.JSTruffleOptions;
import com.oracle.truffle.js.runtime.array.DynamicArray;
import com.oracle.truffle.js.runtime.array.ScriptArray;
import com.oracle.truffle.js.runtime.builtins.JSArray;

/**
 * LazyArray is a backing class for a JSArray that allows array elements to be supplied on demand
 * (i.e., lazily) via a generator object. The generator object needs to implement the
 * {@link LazyArrayGenerator} interface, which is tied to the LazyArray during creation via
 * {@link JSArray#createLazyArray(JSContext, LazyArrayGenerator)}.
 *
 * Whenever a lazy array is written to, the entire lazy array is enumerated and copied and loses its
 * lazy lookup property.
 */
public class LazyArray extends AbstractConstantArray {

    private static final LazyArray LAZY_ARRAY = new LazyArray(INTEGRITY_LEVEL_NONE, createCache());

    protected LazyArray(int integrityLevel, DynamicArrayCache cache) {
        super(integrityLevel, cache);
    }

    public static LazyArray createLazyArray() {
        return LAZY_ARRAY;
    }

    @Override
    protected DynamicArray withIntegrityLevel(int newIntegrityLevel) {
        return new LazyArray(newIntegrityLevel, cache);
    }

    static LazyArrayGenerator arrayGetLazyGenerator(DynamicObject object, boolean condition) {
        return (LazyArrayGenerator) arrayGetArray(object, condition);
    }

    @Override
    public Object getElementInBounds(DynamicObject object, int index, boolean condition) {
        return arrayGetLazyGenerator(object, condition).getElement(index);
    }

    @Override
    public boolean hasElement(DynamicObject object, long index, boolean condition) {
        return index >= 0 && index < lengthInt(object, condition);
    }

    @Override
    public int lengthInt(DynamicObject object, boolean condition) {
        return (int) arrayGetLength(object, condition);
    }

    @Override
    public AbstractWritableArray createWriteableObject(DynamicObject object, long index, Object value, boolean condition, ProfileHolder profile) {
        // enumerate the whole array
        int len = lengthInt(object, condition);
        Object[] array = new Object[len];
        for (int i = 0; i < len; i++) {
            array[i] = getElementInBounds(object, i, condition);
        }
        AbstractObjectArray newArray;
        newArray = ZeroBasedObjectArray.makeZeroBasedObjectArray(object, array.length, array.length, array, integrityLevel);
        if (JSTruffleOptions.TraceArrayTransitions) {
            traceArrayTransition(this, newArray, index, value);
        }
        return newArray;
    }

    @Override
    public AbstractWritableArray createWriteableDouble(DynamicObject object, long index, double value, boolean condition, ProfileHolder profile) {
        return createWriteableObject(object, index, value, condition, profile);
    }

    @Override
    public AbstractWritableArray createWriteableInt(DynamicObject object, long index, int value, boolean condition, ProfileHolder profile) {
        return createWriteableObject(object, index, value, condition, profile);
    }

    @Override
    public AbstractWritableArray createWriteableJSObject(DynamicObject object, long index, DynamicObject value, boolean condition, ProfileHolder profile) {
        return createWriteableObject(object, index, value, condition, profile);
    }

    @Override
    public ScriptArray deleteElementImpl(DynamicObject object, long index, boolean strict, boolean condition) {
        return createWriteableObject(object, index, null, condition, ProfileHolder.empty()).deleteElementImpl(object, index, strict, condition);
    }

    @Override
    public ScriptArray setLengthImpl(DynamicObject object, long len, boolean condition, ProfileHolder profile) {
        return createWriteableObject(object, len - 1, null, condition, ProfileHolder.empty()).setLengthImpl(object, len, condition, profile);
    }

    @Override
    public ScriptArray removeRangeImpl(DynamicObject object, long start, long end) {
        return createWriteableObject(object, start, null, JSArray.isJSArray(object), ProfileHolder.empty()).removeRangeImpl(object, start, end);
    }

    @Override
    public ScriptArray addRangeImpl(DynamicObject object, long offset, int size) {
        return createWriteableObject(object, offset, null, JSArray.isJSArray(object), ProfileHolder.empty()).addRangeImpl(object, offset, size);
    }

    @Override
    public Object[] toArray(DynamicObject object) {
        // enumerate the whole array
        int len = lengthInt(object);
        Object[] array = new Object[len];
        for (int i = 0; i < len; i++) {
            array[i] = getElementInBounds(object, i, arrayCondition());
        }
        return array;
    }
}
