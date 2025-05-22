package io.roastedroot.zerofs;

import static io.roastedroot.zerofs.UserLookupService.createGroupPrincipal;
import static io.roastedroot.zerofs.UserLookupService.createUserPrincipal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link PosixAttributeProvider}.
 *
 * @author Colin Decker
 */
public class PosixAttributeProviderTest
        extends AbstractAttributeProviderTest<PosixAttributeProvider> {

    @Override
    protected PosixAttributeProvider createProvider() {
        return new PosixAttributeProvider();
    }

    @Override
    protected Set<? extends AttributeProvider> createInheritedProviders() {
        return Set.of(new BasicAttributeProvider(), new OwnerAttributeProvider());
    }

    @Test
    public void testInitialAttributes() {
        assertContainsAll(
                file,
                Map.of(
                        "group", createGroupPrincipal("group"),
                        "permissions", PosixFilePermissions.fromString("rw-r--r--")));
    }

    @Test
    public void testSet() {
        assertSetAndGetSucceeds("group", createGroupPrincipal("foo"));
        assertSetAndGetSucceeds("permissions", PosixFilePermissions.fromString("rwxrwxrwx"));

        // invalid types
        assertSetFails("permissions", List.of(PosixFilePermission.GROUP_EXECUTE));
        assertSetFails("permissions", Set.of("foo"));
    }

    @Test
    public void testSetOnCreate() {
        assertSetAndGetSucceedsOnCreate(
                "permissions", PosixFilePermissions.fromString("rwxrwxrwx"));
        assertSetFailsOnCreate("group", createGroupPrincipal("foo"));
    }

    @Test
    public void testView() throws IOException {
        file.setAttribute("owner", "owner", createUserPrincipal("user"));

        PosixFileAttributeView view =
                provider.view(
                        fileLookup(),
                        Map.of(
                                "basic",
                                        new BasicAttributeProvider()
                                                .view(fileLookup(), NO_INHERITED_VIEWS),
                                "owner",
                                        new OwnerAttributeProvider()
                                                .view(fileLookup(), NO_INHERITED_VIEWS)));
        assertNotNull(view);

        assertEquals("posix", view.name());
        assertEquals(createUserPrincipal("user"), view.getOwner());

        PosixFileAttributes attrs = view.readAttributes();
        assertEquals(0, attrs.fileKey());
        assertEquals(createUserPrincipal("user"), attrs.owner());
        assertEquals(createGroupPrincipal("group"), attrs.group());
        assertEquals(PosixFilePermissions.fromString("rw-r--r--"), attrs.permissions());

        view.setOwner(createUserPrincipal("root"));
        assertEquals(createUserPrincipal("root"), view.getOwner());
        assertEquals(createUserPrincipal("root"), file.getAttribute("owner", "owner"));

        view.setGroup(createGroupPrincipal("root"));
        assertEquals(createGroupPrincipal("root"), view.readAttributes().group());
        assertEquals(createGroupPrincipal("root"), file.getAttribute("posix", "group"));

        view.setPermissions(PosixFilePermissions.fromString("rwx------"));
        assertEquals(
                PosixFilePermissions.fromString("rwx------"), view.readAttributes().permissions());
        assertEquals(
                PosixFilePermissions.fromString("rwx------"),
                file.getAttribute("posix", "permissions"));
    }

    @Test
    public void testAttributes() {
        PosixFileAttributes attrs = provider.readAttributes(file);
        assertEquals(PosixFilePermissions.fromString("rw-r--r--"), attrs.permissions());
        assertEquals(createGroupPrincipal("group"), attrs.group());
        assertEquals(0, attrs.fileKey());
    }
}
