/*
 * Copyright (C) 2018 The GadTry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.harbby.gadtry.memory;

import com.github.harbby.gadtry.base.Lazys;
import com.github.harbby.gadtry.base.Throwables;
import sun.misc.Unsafe;
import sun.reflect.ReflectionFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

public final class UnsafeHelper
{
    private UnsafeHelper() {}

    private static final Unsafe _UNSAFE;

    public static final int BOOLEAN_ARRAY_OFFSET;

    public static final int BYTE_ARRAY_OFFSET;

    public static final int SHORT_ARRAY_OFFSET;

    public static final int INT_ARRAY_OFFSET;

    public static final int LONG_ARRAY_OFFSET;

    public static final int FLOAT_ARRAY_OFFSET;

    public static final int DOUBLE_ARRAY_OFFSET;

    public static Unsafe getUnsafe()
    {
        return _UNSAFE;
    }

    private static final Supplier<Method> classLoaderDefineClassMethod = Lazys.goLazy(() -> {
        try {
            Method defineClass = ClassLoader.class.getDeclaredMethod("defineClass", String.class, byte[].class, int.class, int.class);
            defineClass.setAccessible(true);
            return defineClass;
        }
        catch (NoSuchMethodException e) {
            throwException(e);
        }
        throw new IllegalStateException("unchecked");
    });

    private static final Supplier<Method> unsafeDefineClassMethod = Lazys.goLazy(() -> {
        try {
            Method defineClassMethod = sun.misc.Unsafe.class.getDeclaredMethod(
                    "defineClass", String.class, byte[].class, int.class, int.class,
                    ClassLoader.class, java.security.ProtectionDomain.class);
            defineClassMethod.setAccessible(true);
            return defineClassMethod;
        }
        catch (NoSuchMethodException e) {
            throwException(e);
        }
        throw new IllegalStateException("unchecked");
    });

    /**
     * only jdk8 support
     */
    @SuppressWarnings("unchecked")
    public static <T> Class<T> defineClass(byte[] classBytes, ClassLoader classLoader)
    {
        try {
            try {
                Method defineClass = unsafeDefineClassMethod.get();
                Throwables.throwsThrowable(NoSuchMethodException.class);
                return (Class<T>) defineClass.invoke(_UNSAFE, null, classBytes, 0,
                        classBytes.length, classLoader, classLoader.getClass().getProtectionDomain());
            }
            catch (NoSuchMethodException e) {
                Method defineClass = classLoaderDefineClassMethod.get();
                return (Class<T>) defineClass.invoke(classLoader, null, classBytes, 0, classBytes.length);
            }
        }
        catch (InvocationTargetException | IllegalAccessException e1) {
            throwException(e1);
        }
        throw new IllegalStateException("unchecked");
    }

    @SuppressWarnings("unchecked")
    public static <T> Class<T> defineAnonymousClass(Class<?> hostClass, byte[] classBytes, Object[] cpPatches)
    {
        return (Class<T>) _UNSAFE.defineAnonymousClass(hostClass, classBytes, cpPatches);
    }

    public static long reallocateMemory(long address, long oldSize, long newSize)
    {
        long newMemory = _UNSAFE.allocateMemory(newSize);
        copyMemory(null, address, null, newMemory, oldSize);
        _UNSAFE.freeMemory(address);
        return newMemory;
    }

    /**
     * Uses internal JDK APIs to allocate a DirectByteBuffer while ignoring the JVM's
     * MaxDirectMemorySize limit (the default limit is too low and we do not want to require users
     * to increase it).
     *
     * @param size allocate mem size
     * @return ByteBuffer
     */
    public static ByteBuffer allocateDirectBuffer(int size)
    {
        try {
            Class<?> cls = Class.forName("java.nio.DirectByteBuffer");
            Constructor<?> constructor = cls.getDeclaredConstructor(Long.TYPE, Integer.TYPE);
            constructor.setAccessible(true);
            Field cleanerField = cls.getDeclaredField("cleaner");
            cleanerField.setAccessible(true);
            long memory = _UNSAFE.allocateMemory(size);
            ByteBuffer buffer = (ByteBuffer) constructor.newInstance(memory, size);
            Method createMethod;
            try {
                createMethod = Class.forName("sun.misc.Cleaner").getDeclaredMethod("create", Object.class, Runnable.class);
            }
            catch (ClassNotFoundException e) {
                //run vm: --add-opens=java.base/jdk.internal.ref=ALL-UNNAMED
                createMethod = Class.forName("jdk.internal.ref.Cleaner").getDeclaredMethod("create", Object.class, Runnable.class);
            }
            createMethod.setAccessible(true);
            Object cleaner = createMethod.invoke(null, buffer, (Runnable) () -> _UNSAFE.freeMemory(memory));
            //Cleaner cleaner = Cleaner.create(buffer, () -> _UNSAFE.freeMemory(memory));
            cleanerField.set(buffer, cleaner);
            return buffer;
        }
        catch (Exception e) {
            throwException(e);
        }
        throw new IllegalStateException("unreachable");
    }

    @SuppressWarnings("unchecked")
    public static <T> T allocateInstance(Class<T> tClass)
            throws InstantiationException
    {
        return (T) _UNSAFE.allocateInstance(tClass);
    }

    @SuppressWarnings("unchecked")
    public static <T> T allocateInstance2(Class<T> tClass)
            throws InstantiationException, NoSuchMethodException, InvocationTargetException, IllegalAccessException
    {
        Constructor<T> superCons = (Constructor<T>) Object.class.getConstructor();
        ReflectionFactory reflFactory = ReflectionFactory.getReflectionFactory();
        Constructor<T> c = (Constructor<T>) reflFactory.newConstructorForSerialization(tClass, superCons);
        return c.newInstance();
    }

    public static void copyMemory(
            Object src, long srcOffset, Object dst, long dstOffset, long length)
    {
        // Check if dstOffset is before or after srcOffset to determine if we should copy
        // forward or backwards. This is necessary in case src and dst overlap.
        if (dstOffset < srcOffset) {
            while (length > 0) {
                long size = Math.min(length, UNSAFE_COPY_THRESHOLD);
                _UNSAFE.copyMemory(src, srcOffset, dst, dstOffset, size);
                length -= size;
                srcOffset += size;
                dstOffset += size;
            }
        }
        else {
            srcOffset += length;
            dstOffset += length;
            while (length > 0) {
                long size = Math.min(length, UNSAFE_COPY_THRESHOLD);
                srcOffset -= size;
                dstOffset -= size;
                _UNSAFE.copyMemory(src, srcOffset, dst, dstOffset, size);
                length -= size;
            }
        }
    }

    /**
     * Raises an exception bypassing compiler checks for checked exceptions.
     *
     * @param t Throwable
     */
    public static void throwException(Throwable t)
    {
        _UNSAFE.throwException(t);
    }

    /**
     * Limits the number of bytes to copy per {@link Unsafe#copyMemory(long, long, long)} to
     * allow safepoint polling during a large copy.
     */
    private static final long UNSAFE_COPY_THRESHOLD = 1024L * 1024L;

    static {
        sun.misc.Unsafe unsafe = null;
        try {
            Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            unsafe = (sun.misc.Unsafe) unsafeField.get(null);
        }
        catch (Throwable cause) {
            throwException(cause);
        }
        _UNSAFE = requireNonNull(unsafe);

        BOOLEAN_ARRAY_OFFSET = _UNSAFE.arrayBaseOffset(boolean[].class);
        BYTE_ARRAY_OFFSET = _UNSAFE.arrayBaseOffset(byte[].class);
        SHORT_ARRAY_OFFSET = _UNSAFE.arrayBaseOffset(short[].class);
        INT_ARRAY_OFFSET = _UNSAFE.arrayBaseOffset(int[].class);
        LONG_ARRAY_OFFSET = _UNSAFE.arrayBaseOffset(long[].class);
        FLOAT_ARRAY_OFFSET = _UNSAFE.arrayBaseOffset(float[].class);
        DOUBLE_ARRAY_OFFSET = _UNSAFE.arrayBaseOffset(double[].class);
    }
}
