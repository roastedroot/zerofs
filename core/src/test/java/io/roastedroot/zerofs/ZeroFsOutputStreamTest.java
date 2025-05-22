package io.roastedroot.zerofs;

import static io.roastedroot.zerofs.TestUtils.bytes;
import static io.roastedroot.zerofs.TestUtils.regularFile;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ZeroFsOutputStream}.
 *
 * @author Colin Decker
 */
public class ZeroFsOutputStreamTest {

    @Test
    public void testWrite_singleByte() throws IOException {
        ZeroFsOutputStream out = newOutputStream(false);
        out.write(1);
        out.write(2);
        out.write(3);
        assertStoreContains(out, 1, 2, 3);
    }

    @Test
    public void testWrite_wholeArray() throws IOException {
        ZeroFsOutputStream out = newOutputStream(false);
        out.write(new byte[] {1, 2, 3, 4});
        assertStoreContains(out, 1, 2, 3, 4);
    }

    @Test
    public void testWrite_partialArray() throws IOException {
        ZeroFsOutputStream out = newOutputStream(false);
        out.write(new byte[] {1, 2, 3, 4, 5, 6}, 1, 3);
        assertStoreContains(out, 2, 3, 4);
    }

    @Test
    public void testWrite_partialArray_invalidInput() throws IOException {
        ZeroFsOutputStream out = newOutputStream(false);

        try {
            out.write(new byte[3], -1, 1);
            fail();
        } catch (IndexOutOfBoundsException expected) {
        }

        try {
            out.write(new byte[3], 0, 4);
            fail();
        } catch (IndexOutOfBoundsException expected) {
        }

        try {
            out.write(new byte[3], 1, 3);
            fail();
        } catch (IndexOutOfBoundsException expected) {
        }
    }

    @Test
    public void testWrite_singleByte_appendMode() throws IOException {
        ZeroFsOutputStream out = newOutputStream(true);
        addBytesToStore(out, 9, 8, 7);
        out.write(1);
        out.write(2);
        out.write(3);
        assertStoreContains(out, 9, 8, 7, 1, 2, 3);
    }

    @Test
    public void testWrite_wholeArray_appendMode() throws IOException {
        ZeroFsOutputStream out = newOutputStream(true);
        addBytesToStore(out, 9, 8, 7);
        out.write(new byte[] {1, 2, 3, 4});
        assertStoreContains(out, 9, 8, 7, 1, 2, 3, 4);
    }

    @Test
    public void testWrite_partialArray_appendMode() throws IOException {
        ZeroFsOutputStream out = newOutputStream(true);
        addBytesToStore(out, 9, 8, 7);
        out.write(new byte[] {1, 2, 3, 4, 5, 6}, 1, 3);
        assertStoreContains(out, 9, 8, 7, 2, 3, 4);
    }

    @Test
    public void testWrite_singleByte_overwriting() throws IOException {
        ZeroFsOutputStream out = newOutputStream(false);
        addBytesToStore(out, 9, 8, 7, 6, 5, 4, 3);
        out.write(1);
        out.write(2);
        out.write(3);
        assertStoreContains(out, 1, 2, 3, 6, 5, 4, 3);
    }

    @Test
    public void testWrite_wholeArray_overwriting() throws IOException {
        ZeroFsOutputStream out = newOutputStream(false);
        addBytesToStore(out, 9, 8, 7, 6, 5, 4, 3);
        out.write(new byte[] {1, 2, 3, 4});
        assertStoreContains(out, 1, 2, 3, 4, 5, 4, 3);
    }

    @Test
    public void testWrite_partialArray_overwriting() throws IOException {
        ZeroFsOutputStream out = newOutputStream(false);
        addBytesToStore(out, 9, 8, 7, 6, 5, 4, 3);
        out.write(new byte[] {1, 2, 3, 4, 5, 6}, 1, 3);
        assertStoreContains(out, 2, 3, 4, 6, 5, 4, 3);
    }

    @Test
    public void testClosedOutputStream_throwsException() throws IOException {
        ZeroFsOutputStream out = newOutputStream(false);
        out.close();

        try {
            out.write(1);
            fail();
        } catch (IOException expected) {
        }

        try {
            out.write(new byte[3]);
            fail();
        } catch (IOException expected) {
        }

        try {
            out.write(new byte[10], 1, 3);
            fail();
        } catch (IOException expected) {
        }

        out.close(); // does nothing
    }

    @Test
    public void testClosedOutputStream_doesNotThrowOnFlush() throws IOException {
        ZeroFsOutputStream out = newOutputStream(false);
        out.close();
        out.flush(); // does nothing

        try (ZeroFsOutputStream out2 = newOutputStream(false);
                BufferedOutputStream bout = new BufferedOutputStream(out2);
                OutputStreamWriter writer = new OutputStreamWriter(bout, UTF_8)) {
            /*
             * This specific scenario is why flush() shouldn't throw when the stream is already closed.
             * Nesting try-with-resources like this will cause close() to be called on the
             * BufferedOutputStream multiple times. Each time, BufferedOutputStream will first call
             * out2.flush(), then call out2.close(). If out2.flush() throws when the stream is already
             * closed, the second flush() will throw an exception. Prior to JDK8, this exception would be
             * swallowed and ignored completely; in JDK8, the exception is thrown from close().
             */
        }
    }

    private static ZeroFsOutputStream newOutputStream(boolean append) {
        RegularFile file = regularFile(0);
        return new ZeroFsOutputStream(
                file, append, new FileSystemState(new FakeFileTimeSource(), () -> {}));
    }

    @SuppressWarnings("GuardedByChecker")
    private static void addBytesToStore(ZeroFsOutputStream out, int... bytes) throws IOException {
        RegularFile file = out.file;
        long pos = file.sizeWithoutLocking();
        for (int b : bytes) {
            file.write(pos++, (byte) b);
        }
    }

    @SuppressWarnings("GuardedByChecker")
    private static void assertStoreContains(ZeroFsOutputStream out, int... bytes) {
        byte[] actualBytes = new byte[bytes.length];
        int unused = out.file.read(0, actualBytes, 0, actualBytes.length);
        assertArrayEquals(bytes(bytes), actualBytes);
    }
}
