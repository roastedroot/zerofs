package io.roastedroot.zerofs;

import static io.roastedroot.zerofs.TestUtils.bytesAsList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for the lower-level operations dealing with the blocks of a {@link RegularFile}.
 *
 * @author Colin Decker
 */
public class RegularFileBlocksTest {

    private static final int BLOCK_SIZE = 2;

    private final FakeFileTimeSource fileTimeSource = new FakeFileTimeSource();

    private RegularFile file;

    @BeforeEach
    public void setUp() {
        file = createFile();
    }

    private RegularFile createFile() {
        return RegularFile.create(-1, fileTimeSource.now(), new HeapDisk(BLOCK_SIZE, 2, 2));
    }

    @Test
    public void testInitialState() {
        assertEquals(0, file.blockCount());

        // no bounds checking, but there should never be a block at an index >= size
        assertNull(file.getBlock(0));
    }

    @Test
    public void testAddAndGet() {
        file.addBlock(new byte[] {1});

        assertEquals(1, file.blockCount());
        assertEquals(bytesAsList(new byte[] {1}), bytesAsList(file.getBlock(0)));
        assertNull(file.getBlock(1));

        file.addBlock(new byte[] {1, 2});

        assertEquals(2, file.blockCount());
        assertEquals(bytesAsList(new byte[] {1, 2}), bytesAsList(file.getBlock(1)));
        assertNull(file.getBlock(2));
    }

    @Test
    public void testTruncate() {
        file.addBlock(new byte[0]);
        file.addBlock(new byte[0]);
        file.addBlock(new byte[0]);
        file.addBlock(new byte[0]);

        assertEquals(4, file.blockCount());

        file.truncateBlocks(2);

        assertEquals(2, file.blockCount());
        assertNull(file.getBlock(2));
        assertNull(file.getBlock(3));
        assertNotNull(file.getBlock(0));

        file.truncateBlocks(0);
        assertEquals(0, file.blockCount());
        assertNull(file.getBlock(0));
    }

    @Test
    public void testCopyTo() {
        file.addBlock(new byte[] {1});
        file.addBlock(new byte[] {1, 2});
        RegularFile other = createFile();

        assertEquals(0, other.blockCount());

        file.copyBlocksTo(other, 2);

        assertEquals(2, other.blockCount());
        assertEquals(file.getBlock(0), other.getBlock(0));
        assertEquals(file.getBlock(1), other.getBlock(1));

        file.copyBlocksTo(other, 1); // should copy the last block

        assertEquals(3, other.blockCount());
        assertEquals(file.getBlock(1), other.getBlock(2));

        other.copyBlocksTo(file, 3);

        assertEquals(5, file.blockCount());
        assertEquals(other.getBlock(0), file.getBlock(2));
        assertEquals(other.getBlock(1), file.getBlock(3));
        assertEquals(other.getBlock(2), file.getBlock(4));
    }

    @Test
    public void testTransferTo() {
        file.addBlock(new byte[] {1});
        file.addBlock(new byte[] {1, 2});
        file.addBlock(new byte[] {1, 2, 3});
        RegularFile other = createFile();

        assertEquals(3, file.blockCount());
        assertEquals(0, other.blockCount());

        file.transferBlocksTo(other, 3);

        assertEquals(0, file.blockCount());
        assertEquals(3, other.blockCount());

        assertNull(file.getBlock(0));
        assertEquals(bytesAsList(new byte[] {1}), bytesAsList(other.getBlock(0)));
        assertEquals(bytesAsList(new byte[] {1, 2}), bytesAsList(other.getBlock(1)));
        assertEquals(bytesAsList(new byte[] {1, 2, 3}), bytesAsList(other.getBlock(2)));

        other.transferBlocksTo(file, 1);

        assertEquals(1, file.blockCount());
        assertEquals(2, other.blockCount());
        assertNull(other.getBlock(2));
        assertEquals(bytesAsList(new byte[] {1, 2, 3}), bytesAsList(file.getBlock(0)));
        assertNull(file.getBlock(1));
    }

    @Test
    public void testTransferFrom() throws IOException {
        // Test that when a transferFrom ends on a block boundary because the input has no further
        // bytes
        // and not because count bytes have been transferred, we don't leave an extra empty block
        // allocated on the end of the file.
        // https://github.com/google/jimfs/issues/163
        byte[] bytes = new byte[BLOCK_SIZE];
        RegularFile file = createFile();

        long transferred =
                file.transferFrom(
                        Channels.newChannel(new ByteArrayInputStream(bytes)), 0, Long.MAX_VALUE);
        assertEquals(bytes.length, transferred);
        assertEquals(1, file.blockCount());
    }

    @Test
    public void testTransferFrom_noBytesNoAllocation() throws IOException {
        // Similar to the previous test but ensures that if no bytes are transferred at all, no new
        // blocks remain allocated.
        // https://github.com/google/jimfs/issues/163
        byte[] bytes = new byte[0];
        RegularFile file = createFile();

        long transferred =
                file.transferFrom(
                        Channels.newChannel(new ByteArrayInputStream(bytes)), 0, Long.MAX_VALUE);
        assertEquals(0, transferred);
        assertEquals(0, file.blockCount());
    }
}
