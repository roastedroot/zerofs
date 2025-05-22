package io.roastedroot.zerofs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link FileFactory}.
 *
 * @author Colin Decker
 */
public class FileFactoryTest {

    private final FakeFileTimeSource fileTimeSource = new FakeFileTimeSource();

    private FileFactory factory;

    @BeforeEach
    public void setUp() {
        factory = new FileFactory(new HeapDisk(2, 2, 0), fileTimeSource);
    }

    @Test
    public void testCreateFiles_basic() {
        File file = factory.createDirectory();
        assertEquals(0L, file.id());
        assertTrue(file.isDirectory());
        assertEquals(fileTimeSource.now(), file.getCreationTime());

        fileTimeSource.randomize();
        file = factory.createRegularFile();
        assertEquals(1L, file.id());
        assertTrue(file.isRegularFile());
        assertEquals(fileTimeSource.now(), file.getCreationTime());

        fileTimeSource.randomize();
        file = factory.createSymbolicLink(fakePath());
        assertEquals(2L, file.id());
        assertTrue(file.isSymbolicLink());
        assertEquals(fileTimeSource.now(), file.getCreationTime());
    }

    @Test
    public void testCreateFiles_withSupplier() {
        File file = factory.directoryCreator().get();
        assertEquals(0L, file.id());
        assertTrue(file.isDirectory());
        assertEquals(fileTimeSource.now(), file.getCreationTime());

        fileTimeSource.randomize();
        file = factory.regularFileCreator().get();
        assertEquals(1L, file.id());
        assertTrue(file.isRegularFile());
        assertEquals(fileTimeSource.now(), file.getCreationTime());

        fileTimeSource.randomize();
        file = factory.symbolicLinkCreator(fakePath()).get();
        assertEquals(2L, file.id());
        assertTrue(file.isSymbolicLink());
        assertEquals(fileTimeSource.now(), file.getCreationTime());
    }

    static ZeroFsPath fakePath() {
        return PathServiceTest.fakeUnixPathService().emptyPath();
    }
}
