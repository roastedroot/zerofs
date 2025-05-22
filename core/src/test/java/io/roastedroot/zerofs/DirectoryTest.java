package io.roastedroot.zerofs;

import static io.roastedroot.zerofs.Name.PARENT;
import static io.roastedroot.zerofs.Name.SELF;
import static io.roastedroot.zerofs.TestUtils.iteratorToList;
import static io.roastedroot.zerofs.TestUtils.iteratorToSet;
import static io.roastedroot.zerofs.TestUtils.regularFile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link Directory}.
 *
 * @author Colin Decker
 */
public class DirectoryTest {

    private final FakeFileTimeSource fileTimeSource = new FakeFileTimeSource();

    private Directory root;
    private Directory dir;

    @BeforeEach
    public void setUp() {
        root = Directory.createRoot(0, fileTimeSource.now(), Name.simple("/"));

        dir = createDirectory(1);
        root.link(Name.simple("foo"), dir);
    }

    private Directory createDirectory(int id) {
        return Directory.create(id, fileTimeSource.now());
    }

    @Test
    public void testRootDirectory() {
        assertEquals(3, root.entryCount()); // two for parent/self, one for dir
        assertFalse(root.isEmpty());
        assertEquals(entry(root, "/", root), root.entryInParent());
        assertEquals(Name.simple("/"), root.entryInParent().name());

        assertParentAndSelf(root, root, root);
    }

    @Test
    public void testEmptyDirectory() {
        assertEquals(2, dir.entryCount());
        assertTrue(dir.isEmpty());

        assertParentAndSelf(dir, root, dir);
    }

    @Test
    public void testGet() {
        assertEquals(entry(root, "foo", dir), root.get(Name.simple("foo")));
        assertNull(dir.get(Name.simple("foo")));
        assertNull(root.get(Name.simple("Foo")));
    }

    @Test
    public void testLink() {
        assertNull(dir.get(Name.simple("bar")));

        File bar = createDirectory(2);
        dir.link(Name.simple("bar"), bar);

        assertEquals(entry(dir, "bar", bar), dir.get(Name.simple("bar")));
    }

    @Test
    public void testLink_existingNameFails() {
        try {
            root.link(Name.simple("foo"), createDirectory(2));
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testLink_parentAndSelfNameFails() {
        try {
            dir.link(Name.simple("."), createDirectory(2));
            fail();
        } catch (IllegalArgumentException expected) {
        }

        try {
            dir.link(Name.simple(".."), createDirectory(2));
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testGet_normalizingCaseInsensitive() {
        File bar = createDirectory(2);
        Name barName = caseInsensitive("bar");

        dir.link(barName, bar);

        DirectoryEntry expected = new DirectoryEntry(dir, barName, bar);
        assertEquals(expected, dir.get(caseInsensitive("bar")));
        assertEquals(expected, dir.get(caseInsensitive("BAR")));
        assertEquals(expected, dir.get(caseInsensitive("Bar")));
        assertEquals(expected, dir.get(caseInsensitive("baR")));
    }

    @Test
    public void testUnlink() {
        assertNotNull(root.get(Name.simple("foo")));

        root.unlink(Name.simple("foo"));

        assertNull(root.get(Name.simple("foo")));
    }

    @Test
    public void testUnlink_nonExistentNameFails() {
        try {
            dir.unlink(Name.simple("bar"));
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testUnlink_parentAndSelfNameFails() {
        try {
            dir.unlink(Name.simple("."));
            fail();
        } catch (IllegalArgumentException expected) {
        }

        try {
            dir.unlink(Name.simple(".."));
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testUnlink_normalizingCaseInsensitive() {
        dir.link(caseInsensitive("bar"), createDirectory(2));

        assertNotNull(dir.get(caseInsensitive("bar")));

        dir.unlink(caseInsensitive("BAR"));

        assertNull(dir.get(caseInsensitive("bar")));
    }

    @Test
    public void testLinkDirectory() {
        Directory newDir = createDirectory(10);

        assertNull(newDir.entryInParent());
        assertEquals(newDir, newDir.get(Name.SELF).file());
        assertNull(newDir.get(Name.PARENT));
        assertEquals(1, newDir.links());

        dir.link(Name.simple("foo"), newDir);

        assertEquals(entry(dir, "foo", newDir), newDir.entryInParent());
        assertEquals(dir, newDir.parent());
        assertEquals(Name.simple("foo"), newDir.entryInParent().name());
        assertEquals(entry(newDir, ".", newDir), newDir.get(Name.SELF));
        assertEquals(entry(newDir, "..", dir), newDir.get(Name.PARENT));
        assertEquals(2, newDir.links());
    }

    @Test
    public void testUnlinkDirectory() {
        Directory newDir = createDirectory(10);

        dir.link(Name.simple("foo"), newDir);

        assertEquals(3, dir.links());

        assertEquals(entry(dir, "foo", newDir), newDir.entryInParent());
        assertEquals(2, newDir.links());

        dir.unlink(Name.simple("foo"));

        assertEquals(2, dir.links());

        assertEquals(entry(dir, "foo", newDir), newDir.entryInParent());
        assertEquals(newDir, newDir.get(Name.SELF).file());
        assertEquals(entry(newDir, "..", dir), newDir.get(Name.PARENT));
        assertEquals(1, newDir.links());
    }

    @Test
    public void testSnapshot() {
        root.link(Name.simple("bar"), regularFile(10));
        root.link(Name.simple("abc"), regularFile(10));

        // does not include . or .. and is sorted by the name
        Iterator<Name> snapshot = root.snapshot().iterator();

        assertEquals(Name.simple("abc"), snapshot.next());
        assertEquals(Name.simple("bar"), snapshot.next());
        assertEquals(Name.simple("foo"), snapshot.next());
        assertFalse(snapshot.hasNext());
    }

    @Test
    public void testSnapshot_sortsUsingStringAndNotCanonicalValueOfNames() {
        dir.link(caseInsensitive("FOO"), regularFile(10));
        dir.link(caseInsensitive("bar"), regularFile(10));

        Iterator<Name> snapshotIter = dir.snapshot().iterator();

        // "FOO" comes before "bar"
        // if the order were based on the normalized, canonical form of the names ("foo" and "bar"),
        // "bar" would come first
        assertEquals("FOO", snapshotIter.next().toString());
        assertEquals("bar", snapshotIter.next().toString());
        assertFalse(snapshotIter.hasNext());
    }

    // Tests for internal hash table implementation
    private final Directory a = createDirectory(0);

    @Test
    public void testInitialState() {
        assertEquals(2, dir.entryCount());
        Iterator<DirectoryEntry> iter = dir.iterator();
        assertEquals(new DirectoryEntry(dir, Name.PARENT, root), iter.next());
        assertEquals(new DirectoryEntry(dir, Name.SELF, dir), iter.next());
        assertFalse(iter.hasNext());
        assertNull(dir.get(Name.simple("foo")));
    }

    @Test
    public void testPutAndGet() {
        dir.put(entry("foo"));

        assertEquals(3, dir.entryCount());
        assertTrue(iteratorToList(dir.iterator()).contains(entry("foo")));
        assertEquals(entry("foo"), dir.get(Name.simple("foo")));

        dir.put(entry("bar"));

        assertEquals(4, dir.entryCount());
        assertTrue(iteratorToList(dir.iterator()).contains(entry("foo")));
        assertTrue(iteratorToList(dir.iterator()).contains(entry("bar")));
        assertEquals(entry("foo"), dir.get(Name.simple("foo")));
        assertEquals(entry("bar"), dir.get(Name.simple("bar")));
    }

    @Test
    public void testPutEntryForExistingNameIsIllegal() {
        dir.put(entry("foo"));

        try {
            dir.put(entry("foo"));
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testRemove() {
        dir.put(entry("foo"));
        dir.put(entry("bar"));

        dir.remove(Name.simple("foo"));

        assertEquals(3, dir.entryCount());
        List<DirectoryEntry> dirs = iteratorToList(dir.iterator());
        assertTrue(dirs.contains(entry("bar")));
        assertTrue(dirs.contains(new DirectoryEntry(dir, Name.SELF, dir)));
        assertTrue(dirs.contains(new DirectoryEntry(dir, Name.PARENT, root)));
        assertEquals(3, dirs.size());
        assertNull(dir.get(Name.simple("foo")));
        assertEquals(entry("bar"), dir.get(Name.simple("bar")));

        dir.remove(Name.simple("bar"));

        assertEquals(2, dir.entryCount());

        dir.put(entry("bar"));
        dir.put(entry("foo")); // these should just succeeded
    }

    @Test
    public void testManyPutsAndRemoves() {
        // test resizing/rehashing

        Set<DirectoryEntry> entriesInDir = new HashSet<>();
        entriesInDir.add(new DirectoryEntry(dir, Name.SELF, dir));
        entriesInDir.add(new DirectoryEntry(dir, Name.PARENT, root));

        // add 1000 entries
        for (int i = 0; i < 1000; i++) {
            DirectoryEntry entry = entry(String.valueOf(i));
            dir.put(entry);
            entriesInDir.add(entry);

            assertEquals(entriesInDir, iteratorToSet(dir.iterator()));

            for (DirectoryEntry expected : entriesInDir) {
                assertEquals(expected, dir.get(expected.name()));
            }
        }

        // remove 1000 entries
        for (int i = 0; i < 1000; i++) {
            dir.remove(Name.simple(String.valueOf(i)));
            entriesInDir.remove(entry(String.valueOf(i)));

            assertEquals(entriesInDir, iteratorToSet(dir.iterator()));

            for (DirectoryEntry expected : entriesInDir) {
                assertEquals(expected, dir.get(expected.name()));
            }
        }

        // mixed adds and removes
        for (int i = 0; i < 10000; i++) {
            DirectoryEntry entry = entry(String.valueOf(i));
            dir.put(entry);
            entriesInDir.add(entry);

            if (i > 0 && i % 20 == 0) {
                String nameToRemove = String.valueOf(i / 2);
                dir.remove(Name.simple(nameToRemove));
                entriesInDir.remove(entry(nameToRemove));
            }
        }

        // for this one, only test that the end result is correct
        // takes too long to test at each iteration
        assertEquals(entriesInDir, iteratorToSet(dir.iterator()));

        for (DirectoryEntry expected : entriesInDir) {
            assertEquals(expected, dir.get(expected.name()));
        }
    }

    private DirectoryEntry entry(String name) {
        return new DirectoryEntry(a, Name.simple(name), a);
    }

    private DirectoryEntry entry(Directory dir, String name, File file) {
        return new DirectoryEntry(dir, Name.simple(name), file);
    }

    private void assertParentAndSelf(Directory dir, File parent, File self) {
        assertEquals(self, dir);
        assertEquals(parent, dir.parent());

        assertEquals(entry((Directory) self, "..", parent), dir.get(PARENT));
        assertEquals(entry((Directory) self, ".", self), dir.get(SELF));
    }

    private static Name caseInsensitive(String name) {
        // was UNICODE is ASCII now
        return Name.create(name, PathNormalization.CASE_FOLD_ASCII.apply(name));
    }
}
