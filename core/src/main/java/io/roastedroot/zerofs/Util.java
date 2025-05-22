package io.roastedroot.zerofs;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Miscellaneous static utility methods.
 *
 * @author Colin Decker
 * @author Austin Appleby
 */
final class Util {

    private Util() {}

    /** Returns the next power of 2 >= n. */
    public static int nextPowerOf2(int n) {
        if (n == 0) {
            return 1;
        }
        int b = Integer.highestOneBit(n);
        return b == n ? n : b << 1;
    }

    /**
     * Checks that the given number is not negative, throwing IAE if it is. The given description
     * describes the number in the exception message.
     */
    static void checkNotNegative(long n, String description) {
        if (n < 0) {
            throw new IllegalArgumentException(
                    String.format("%s must not be negative: %s", description, n));
        }
    }

    /** Checks that no element in the given iterable is null, throwing NPE if any is. */
    static void checkNoneNull(Iterable<?> objects) {
        if (!(objects instanceof Collection)) {
            for (Object o : objects) {
                Objects.requireNonNull(o);
            }
        }
    }

    private static final int C1 = 0xcc9e2d51;
    private static final int C2 = 0x1b873593;

    /*
     * This method was rewritten in Java from an intermediate step of the Murmur hash function in
     * http://code.google.com/p/smhasher/source/browse/trunk/MurmurHash3.cpp, which contained the
     * following header:
     *
     * MurmurHash3 was written by Austin Appleby, and is placed in the public domain. The author
     * hereby disclaims copyright to this source code.
     */
    static int smearHash(int hashCode) {
        return C2 * Integer.rotateLeft(hashCode * C1, 15);
    }

    private static final int ARRAY_LEN = 8192;
    private static final byte[] ZERO_ARRAY = new byte[ARRAY_LEN];
    private static final byte[][] NULL_ARRAY = new byte[ARRAY_LEN][];

    /** Zeroes all bytes between off (inclusive) and off + len (exclusive) in the given array. */
    static void zero(byte[] bytes, int off, int len) {
        // this is significantly faster than looping or Arrays.fill (which loops), particularly when
        // the length of the slice to be zeroed is <= to ARRAY_LEN (in that case, it's faster by a
        // factor of 2)
        int remaining = len;
        while (remaining > ARRAY_LEN) {
            System.arraycopy(ZERO_ARRAY, 0, bytes, off, ARRAY_LEN);
            off += ARRAY_LEN;
            remaining -= ARRAY_LEN;
        }

        System.arraycopy(ZERO_ARRAY, 0, bytes, off, remaining);
    }

    /**
     * Clears (sets to null) all blocks between off (inclusive) and off + len (exclusive) in the given
     * array.
     */
    static void clear(byte[][] blocks, int off, int len) {
        // this is significantly faster than looping or Arrays.fill (which loops), particularly when
        // the length of the slice to be cleared is <= to ARRAY_LEN (in that case, it's faster by a
        // factor of 2)
        int remaining = len;
        while (remaining > ARRAY_LEN) {
            System.arraycopy(NULL_ARRAY, 0, blocks, off, ARRAY_LEN);
            off += ARRAY_LEN;
            remaining -= ARRAY_LEN;
        }

        System.arraycopy(NULL_ARRAY, 0, blocks, off, remaining);
    }

    // from com.google.common.base.Ascii
    private static final char CASE_MASK = 0x20;

    public static boolean isUpperCase(char c) {
        return (c >= 'A') && (c <= 'Z');
    }

    public static String toLowerCase(String string) {
        int length = string.length();
        for (int i = 0; i < length; i++) {
            if (isUpperCase(string.charAt(i))) {
                char[] chars = string.toCharArray();
                for (; i < length; i++) {
                    char c = chars[i];
                    if (isUpperCase(c)) {
                        chars[i] = (char) (c ^ CASE_MASK);
                    }
                }
                return String.valueOf(chars);
            }
        }
        return string;
    }

    public static String[] toArray(List<Name> names) {
        String[] res = new String[names.size()];
        for (int i = 0; i < names.size(); i++) {
            res[i] = names.get(i).toString();
        }
        return res;
    }
}
