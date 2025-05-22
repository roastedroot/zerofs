package io.roastedroot.zerofs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link HeapDisk}.
 *
 * @author Colin Decker
 */
public class HeapDiskTest {

    private final FakeFileTimeSource fileTimeSource = new FakeFileTimeSource();

    private RegularFile blocks;

    @BeforeEach
    public void setUp() {
        // the HeapDisk of this file is unused; it's passed to other HeapDisks to test operations
        blocks = RegularFile.create(-1, fileTimeSource.now(), new HeapDisk(2, 2, 2));
    }

    @Test
    public void testInitialSettings_basic() {
        HeapDisk disk = new HeapDisk(8192, 100, 100);

        assertEquals(8192, disk.blockSize());
        assertEquals(819200, disk.getTotalSpace());
        assertEquals(819200, disk.getUnallocatedSpace());
        assertEquals(0, disk.blockCache.blockCount());
    }

    @Test
    public void testInitialSettings_fromConfiguration() {
        Configuration config =
                Configuration.unix().toBuilder()
                        .setBlockSize(4)
                        .setMaxSize(99) // not a multiple of 4
                        .setMaxCacheSize(25)
                        .build();

        HeapDisk disk = new HeapDisk(config);

        assertEquals(4, disk.blockSize());
        assertEquals(96, disk.getTotalSpace());
        assertEquals(96, disk.getUnallocatedSpace());
        assertEquals(0, disk.blockCache.blockCount());
    }

    @Test
    public void testAllocate() throws IOException {
        HeapDisk disk = new HeapDisk(4, 10, 0);

        disk.allocate(blocks, 1);

        assertEquals(1, blocks.blockCount());
        assertEquals(4, blocks.getBlock(0).length);
        assertEquals(36, disk.getUnallocatedSpace());

        disk.allocate(blocks, 5);

        assertEquals(6, blocks.blockCount());
        for (int i = 0; i < blocks.blockCount(); i++) {
            assertEquals(4, blocks.getBlock(i).length);
        }
        assertEquals(16, disk.getUnallocatedSpace());
        assertEquals(0, disk.blockCache.blockCount());
    }

    @Test
    public void testFree_noCaching() throws IOException {
        HeapDisk disk = new HeapDisk(4, 10, 0);
        disk.allocate(blocks, 6);

        disk.free(blocks, 2);
        assertEquals(4, blocks.blockCount());
        assertEquals(24, disk.getUnallocatedSpace());
        assertEquals(0, disk.blockCache.blockCount());

        disk.free(blocks);

        assertEquals(0, blocks.blockCount());
        assertEquals(40, disk.getUnallocatedSpace());
        assertEquals(0, disk.blockCache.blockCount());
    }

    @Test
    public void testFree_fullCaching() throws IOException {
        HeapDisk disk = new HeapDisk(4, 10, 10);
        disk.allocate(blocks, 6);

        disk.free(blocks, 2);

        assertEquals(4, blocks.blockCount());
        assertEquals(24, disk.getUnallocatedSpace());
        assertEquals(2, disk.blockCache.blockCount());

        disk.free(blocks);

        assertEquals(0, blocks.blockCount());
        assertEquals(40, disk.getUnallocatedSpace());
        assertEquals(6, disk.blockCache.blockCount());
    }

    @Test
    public void testFree_partialCaching() throws IOException {
        HeapDisk disk = new HeapDisk(4, 10, 4);
        disk.allocate(blocks, 6);

        disk.free(blocks, 2);

        assertEquals(4, blocks.blockCount());
        assertEquals(24, disk.getUnallocatedSpace());
        assertEquals(2, disk.blockCache.blockCount());

        disk.free(blocks);

        assertEquals(0, blocks.blockCount());
        assertEquals(40, disk.getUnallocatedSpace());
        assertEquals(4, disk.blockCache.blockCount());
    }

    @Test
    public void testAllocateFromCache_fullAllocationFromCache() throws IOException {
        HeapDisk disk = new HeapDisk(4, 10, 10);
        disk.allocate(blocks, 10);

        assertEquals(0, disk.getUnallocatedSpace());

        disk.free(blocks);

        assertEquals(0, blocks.blockCount());
        assertEquals(10, disk.blockCache.blockCount());

        List<byte[]> cachedBlocks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            cachedBlocks.add(disk.blockCache.getBlock(i));
        }

        disk.allocate(blocks, 6);

        assertEquals(6, blocks.blockCount());
        assertEquals(4, disk.blockCache.blockCount());

        // the 6 arrays in blocks are the last 6 arrays that were cached
        for (int i = 0; i < 6; i++) {
            assertEquals(cachedBlocks.get(i + 4), blocks.getBlock(i));
        }
    }

    @Test
    public void testAllocateFromCache_partialAllocationFromCache() throws IOException {
        HeapDisk disk = new HeapDisk(4, 10, 4);
        disk.allocate(blocks, 10);

        assertEquals(0, disk.getUnallocatedSpace());

        disk.free(blocks);

        assertEquals(0, blocks.blockCount());
        assertEquals(4, disk.blockCache.blockCount());

        List<byte[]> cachedBlocks = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            cachedBlocks.add(disk.blockCache.getBlock(i));
        }

        disk.allocate(blocks, 6);

        assertEquals(6, blocks.blockCount());
        assertEquals(0, disk.blockCache.blockCount());

        // the last 4 arrays in blocks are the 4 arrays that were cached
        for (int i = 2; i < 6; i++) {
            assertEquals(cachedBlocks.get(i - 2), blocks.getBlock(i));
        }
    }

    @Test
    public void testFullDisk() throws IOException {
        HeapDisk disk = new HeapDisk(4, 10, 4);
        disk.allocate(blocks, 10);

        try {
            disk.allocate(blocks, 1);
            fail();
        } catch (IOException expected) {
        }
    }

    @Test
    public void testFullDisk_doesNotAllocatePartiallyWhenTooManyBlocksRequested()
            throws IOException {
        HeapDisk disk = new HeapDisk(4, 10, 4);
        disk.allocate(blocks, 6);

        RegularFile blocks2 = RegularFile.create(-2, fileTimeSource.now(), disk);

        try {
            disk.allocate(blocks2, 5);
            fail();
        } catch (IOException expected) {
        }

        assertEquals(0, blocks2.blockCount());
    }
}
