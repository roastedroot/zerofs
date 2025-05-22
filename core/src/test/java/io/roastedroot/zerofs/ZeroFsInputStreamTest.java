package io.roastedroot.zerofs;

import static io.roastedroot.zerofs.TestUtils.bytes;
import static io.roastedroot.zerofs.TestUtils.regularFile;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ZeroFsInputStream}.
 *
 * @author Colin Decker
 */
public class ZeroFsInputStreamTest {

    @Test
    public void testRead_singleByte() throws IOException {
        ZeroFsInputStream in = newInputStream(2);
        assertEquals(2, in.read());
        assertEmpty(in);
    }

    @Test
    public void testRead_wholeArray() throws IOException {
        ZeroFsInputStream in = newInputStream(1, 2, 3, 4, 5, 6, 7, 8);
        byte[] bytes = new byte[8];
        assertEquals(8, in.read(bytes));
        assertArrayEquals(bytes(1, 2, 3, 4, 5, 6, 7, 8), bytes);
        assertEmpty(in);
    }

    @Test
    public void testRead_wholeArray_arrayLarger() throws IOException {
        ZeroFsInputStream in = newInputStream(1, 2, 3, 4, 5, 6, 7, 8);
        byte[] bytes = new byte[12];
        assertEquals(8, in.read(bytes));
        assertArrayEquals(bytes(1, 2, 3, 4, 5, 6, 7, 8, 0, 0, 0, 0), bytes);
        assertEmpty(in);
    }

    @Test
    public void testRead_wholeArray_arraySmaller() throws IOException {
        ZeroFsInputStream in = newInputStream(1, 2, 3, 4, 5, 6, 7, 8);
        byte[] bytes = new byte[6];
        assertEquals(6, in.read(bytes));
        assertArrayEquals(bytes(1, 2, 3, 4, 5, 6), bytes);
        bytes = new byte[6];
        assertEquals(2, in.read(bytes));
        assertArrayEquals(bytes(7, 8, 0, 0, 0, 0), bytes);
        assertEmpty(in);
    }

    @Test
    public void testRead_partialArray() throws IOException {
        ZeroFsInputStream in = newInputStream(1, 2, 3, 4, 5, 6, 7, 8);
        byte[] bytes = new byte[12];
        assertEquals(8, in.read(bytes, 0, 8));
        assertArrayEquals(bytes(1, 2, 3, 4, 5, 6, 7, 8, 0, 0, 0, 0), bytes);
        assertEmpty(in);
    }

    @Test
    public void testRead_partialArray_sliceLarger() throws IOException {
        ZeroFsInputStream in = newInputStream(1, 2, 3, 4, 5, 6, 7, 8);
        byte[] bytes = new byte[12];
        assertEquals(8, in.read(bytes, 0, 10));
        assertArrayEquals(bytes(1, 2, 3, 4, 5, 6, 7, 8, 0, 0, 0, 0), bytes);
        assertEmpty(in);
    }

    @Test
    public void testRead_partialArray_sliceSmaller() throws IOException {
        ZeroFsInputStream in = newInputStream(1, 2, 3, 4, 5, 6, 7, 8);
        byte[] bytes = new byte[12];
        assertEquals(6, in.read(bytes, 0, 6));
        assertArrayEquals(bytes(1, 2, 3, 4, 5, 6, 0, 0, 0, 0, 0, 0), bytes);
        assertEquals(2, in.read(bytes, 6, 6));
        assertArrayEquals(bytes(1, 2, 3, 4, 5, 6, 7, 8, 0, 0, 0, 0), bytes);
        assertEmpty(in);
    }

    @Test
    public void testRead_partialArray_invalidInput() throws IOException {
        ZeroFsInputStream in = newInputStream(1, 2, 3, 4, 5);

        try {
            in.read(new byte[3], -1, 1);
            fail();
        } catch (IndexOutOfBoundsException expected) {
        }

        try {
            in.read(new byte[3], 0, 4);
            fail();
        } catch (IndexOutOfBoundsException expected) {
        }

        try {
            in.read(new byte[3], 1, 3);
            fail();
        } catch (IndexOutOfBoundsException expected) {
        }
    }

    @Test
    public void testAvailable() throws IOException {
        ZeroFsInputStream in = newInputStream(1, 2, 3, 4, 5, 6, 7, 8);
        assertEquals(8, in.available());
        assertEquals(1, in.read());
        assertEquals(7, in.available());
        assertEquals(3, in.read(new byte[3]));
        assertEquals(4, in.available());
        assertEquals(2, in.read(new byte[10], 1, 2));
        assertEquals(2, in.available());
        assertEquals(2, in.read(new byte[10]));
        assertEquals(0, in.available());
    }

    @Test
    public void testSkip() throws IOException {
        ZeroFsInputStream in = newInputStream(1, 2, 3, 4, 5, 6, 7, 8);
        assertEquals(0, in.skip(0));
        assertEquals(0, in.skip(-10));
        assertEquals(2, in.skip(2));
        assertEquals(3, in.read());
        assertEquals(3, in.skip(3));
        assertEquals(7, in.read());
        assertEquals(1, in.skip(10));
        assertEmpty(in);
        assertEquals(0, in.skip(10));
        assertEmpty(in);
    }

    @SuppressWarnings("GuardedByChecker")
    @Test
    public void testFullyReadInputStream_doesNotChangeStateWhenStoreChanges() throws IOException {
        ZeroFsInputStream in = newInputStream(1, 2, 3, 4, 5);
        assertEquals(5, in.read(new byte[5]));
        assertEmpty(in);

        in.file.write(5, new byte[10], 0, 10); // append more bytes to file
        assertEmpty(in);
    }

    @Test
    public void testMark_unsupported() throws IOException {
        ZeroFsInputStream in = newInputStream(1, 2, 3);
        assertFalse(in.markSupported());

        // mark does nothing
        in.mark(1);

        try {
            // reset throws IOException when unsupported
            in.reset();
            fail();
        } catch (IOException expected) {
        }
    }

    @Test
    public void testClosedInputStream_throwsException() throws IOException {
        ZeroFsInputStream in = newInputStream(1, 2, 3);
        in.close();

        try {
            in.read();
            fail();
        } catch (IOException expected) {
        }

        try {
            in.read(new byte[3]);
            fail();
        } catch (IOException expected) {
        }

        try {
            in.read(new byte[10], 0, 2);
            fail();
        } catch (IOException expected) {
        }

        try {
            in.skip(10);
            fail();
        } catch (IOException expected) {
        }

        try {
            in.available();
            fail();
        } catch (IOException expected) {
        }

        in.close(); // does nothing
    }

    private static ZeroFsInputStream newInputStream(int... bytes) throws IOException {
        byte[] b = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            b[i] = (byte) bytes[i];
        }

        RegularFile file = regularFile(0);
        file.write(0, b, 0, b.length);
        return new ZeroFsInputStream(file, new FileSystemState(new FakeFileTimeSource(), () -> {}));
    }

    private static void assertEmpty(ZeroFsInputStream in) throws IOException {
        assertEquals(-1, in.read());
        assertEquals(-1, in.read(new byte[3]));
        assertEquals(-1, in.read(new byte[10], 1, 5));
        assertEquals(0, in.available());
    }
}
