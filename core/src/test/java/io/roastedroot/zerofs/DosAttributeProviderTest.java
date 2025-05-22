package io.roastedroot.zerofs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.DosFileAttributes;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link DosAttributeProvider}.
 *
 * @author Colin Decker
 */
public class DosAttributeProviderTest extends AbstractAttributeProviderTest<DosAttributeProvider> {

    private static final List<String> DOS_ATTRIBUTES =
            List.of("hidden", "archive", "readonly", "system");

    @Override
    protected DosAttributeProvider createProvider() {
        return new DosAttributeProvider();
    }

    @Override
    protected Set<? extends AttributeProvider> createInheritedProviders() {
        return Set.of(new BasicAttributeProvider(), new OwnerAttributeProvider());
    }

    @Test
    public void testInitialAttributes() {
        for (String attribute : DOS_ATTRIBUTES) {
            assertEquals(false, provider.get(file, attribute));
        }
    }

    @Test
    public void testSet() {
        for (String attribute : DOS_ATTRIBUTES) {
            assertSetAndGetSucceeds(attribute, true);
            assertSetFailsOnCreate(attribute, true);
        }
    }

    @Test
    public void testView() throws IOException {
        DosFileAttributeView view =
                provider.view(
                        fileLookup(),
                        Map.<String, FileAttributeView>of(
                                "basic",
                                new BasicAttributeProvider()
                                        .view(fileLookup(), NO_INHERITED_VIEWS)));
        assertNotNull(view);

        assertEquals("dos", view.name());

        DosFileAttributes attrs = view.readAttributes();
        assertFalse(attrs.isHidden());
        assertFalse(attrs.isArchive());
        assertFalse(attrs.isReadOnly());
        assertFalse(attrs.isSystem());

        view.setArchive(true);
        view.setReadOnly(true);
        view.setHidden(true);
        view.setSystem(false);

        assertFalse(attrs.isHidden());
        assertFalse(attrs.isArchive());
        assertFalse(attrs.isReadOnly());

        attrs = view.readAttributes();
        assertTrue(attrs.isHidden());
        assertTrue(attrs.isArchive());
        assertTrue(attrs.isReadOnly());
        assertFalse(attrs.isSystem());

        view.setTimes(FileTime.fromMillis(0L), null, null);
        assertEquals(FileTime.fromMillis(0L), view.readAttributes().lastModifiedTime());
    }

    @Test
    public void testAttributes() {
        DosFileAttributes attrs = provider.readAttributes(file);
        assertFalse(attrs.isHidden());
        assertFalse(attrs.isArchive());
        assertFalse(attrs.isReadOnly());
        assertFalse(attrs.isSystem());

        file.setAttribute("dos", "hidden", true);

        attrs = provider.readAttributes(file);
        assertTrue(attrs.isHidden());
        assertFalse(attrs.isArchive());
        assertFalse(attrs.isReadOnly());
        assertFalse(attrs.isSystem());
    }
}
