package io.roastedroot.zerofs;

import static io.roastedroot.zerofs.FileFactoryTest.fakePath;
import static io.roastedroot.zerofs.TestUtils.regularFile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link File}.
 *
 * @author Colin Decker
 */
public class FileTest {

    private final FakeFileTimeSource fileTimeSource = new FakeFileTimeSource();

    @Test
    public void testAttributes() {
        // these methods are basically just thin wrappers around a map, so no need to test too
        // thoroughly

        File file = RegularFile.create(0, fileTimeSource.now(), new HeapDisk(10, 10, 10));

        assertTrue(file.getAttributeKeys().isEmpty());
        assertNull(file.getAttribute("foo", "foo"));

        file.deleteAttribute("foo", "foo"); // doesn't throw

        file.setAttribute("foo", "foo", "foo");

        assertTrue(file.getAttributeKeys().contains("foo:foo"));
        assertEquals("foo", file.getAttribute("foo", "foo"));

        file.deleteAttribute("foo", "foo");

        assertTrue(file.getAttributeKeys().isEmpty());
        assertNull(file.getAttribute("foo", "foo"));
    }

    @Test
    public void testFileBasics() {
        File file = regularFile(0);

        assertEquals(0, file.id());
        assertEquals(0, file.links());
    }

    @Test
    public void testDirectory() {
        File file = Directory.create(0, fileTimeSource.now());
        assertTrue(file.isDirectory());
        assertFalse(file.isRegularFile());
        assertFalse(file.isSymbolicLink());
    }

    @Test
    public void testRegularFile() {
        File file = regularFile(10);
        assertFalse(file.isDirectory());
        assertTrue(file.isRegularFile());
        assertFalse(file.isSymbolicLink());
    }

    @Test
    public void testSymbolicLink() {
        File file = SymbolicLink.create(0, fileTimeSource.now(), fakePath());
        assertFalse(file.isDirectory());
        assertFalse(file.isRegularFile());
        assertTrue(file.isSymbolicLink());
    }

    @Test
    public void testRootDirectory() {
        Directory file = Directory.createRoot(0, fileTimeSource.now(), Name.simple("/"));
        assertTrue(file.isRootDirectory());

        Directory otherFile = Directory.createRoot(1, fileTimeSource.now(), Name.simple("$"));
        assertTrue(otherFile.isRootDirectory());
    }

    @Test
    public void testLinkAndUnlink() {
        File file = regularFile(0);
        assertEquals(0, file.links());

        file.incrementLinkCount();
        assertEquals(1, file.links());

        file.incrementLinkCount();
        assertEquals(2, file.links());

        file.decrementLinkCount();
        assertEquals(1, file.links());

        file.decrementLinkCount();
        assertEquals(0, file.links());
    }
}
