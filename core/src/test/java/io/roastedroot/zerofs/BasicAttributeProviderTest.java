package io.roastedroot.zerofs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link BasicAttributeProvider}.
 *
 * @author Colin Decker
 */
public class BasicAttributeProviderTest
        extends AbstractAttributeProviderTest<BasicAttributeProvider> {

    @Override
    protected BasicAttributeProvider createProvider() {
        return new BasicAttributeProvider();
    }

    @Override
    protected Set<? extends AttributeProvider> createInheritedProviders() {
        return Set.of();
    }

    @Test
    public void testSupportedAttributes() {
        assertSupportsAll(
                "fileKey",
                "size",
                "isDirectory",
                "isRegularFile",
                "isSymbolicLink",
                "isOther",
                "creationTime",
                "lastModifiedTime",
                "lastAccessTime");
    }

    @Test
    public void testInitialAttributes() {
        FileTime expected = fileTimeSource.now();
        assertEquals(expected, file.getCreationTime());
        assertEquals(expected, file.getLastAccessTime());
        assertEquals(expected, file.getLastModifiedTime());

        assertContainsAll(
                file,
                Map.of(
                        "fileKey", 0,
                        "size", 0L,
                        "isDirectory", true,
                        "isRegularFile", false,
                        "isSymbolicLink", false,
                        "isOther", false));
    }

    @Test
    public void testSet() {
        FileTime time = FileTime.fromMillis(0L);

        // settable
        assertSetAndGetSucceeds("creationTime", time);
        assertSetAndGetSucceeds("lastModifiedTime", time);
        assertSetAndGetSucceeds("lastAccessTime", time);

        // unsettable
        assertSetFails("fileKey", 3L);
        assertSetFails("size", 1L);
        assertSetFails("isRegularFile", true);
        assertSetFails("isDirectory", true);
        assertSetFails("isSymbolicLink", true);
        assertSetFails("isOther", true);

        // invalid type
        assertSetFails("creationTime", "foo");
    }

    @Test
    public void testSetOnCreate() {
        FileTime time = FileTime.fromMillis(0L);

        assertSetFailsOnCreate("creationTime", time);
        assertSetFailsOnCreate("lastModifiedTime", time);
        assertSetFailsOnCreate("lastAccessTime", time);
    }

    @Test
    public void testView() throws IOException {
        BasicFileAttributeView view = provider.view(fileLookup(), NO_INHERITED_VIEWS);

        assertNotNull(view);
        assertEquals("basic", view.name());

        BasicFileAttributes attrs = view.readAttributes();
        assertEquals(0, attrs.fileKey());

        FileTime initial = fileTimeSource.now();
        assertEquals(initial, attrs.creationTime());
        assertEquals(initial, attrs.lastAccessTime());
        assertEquals(initial, attrs.lastModifiedTime());

        view.setTimes(null, null, null);

        assertEquals(initial, attrs.creationTime());
        assertEquals(initial, attrs.lastAccessTime());
        assertEquals(initial, attrs.lastModifiedTime());

        view.setTimes(FileTime.fromMillis(0L), null, null);

        attrs = view.readAttributes();
        assertEquals(initial, attrs.creationTime());
        assertEquals(initial, attrs.lastAccessTime());
        assertEquals(FileTime.fromMillis(0L), attrs.lastModifiedTime());
    }

    @Test
    public void testAttributes() {
        BasicFileAttributes attrs = provider.readAttributes(file);
        assertEquals(0, attrs.fileKey());
        assertTrue(attrs.isDirectory());
        assertFalse(attrs.isRegularFile());
        assertNotNull(attrs.creationTime());
    }
}
