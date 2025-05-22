package io.roastedroot.zerofs;

import static io.roastedroot.zerofs.UserLookupService.createUserPrincipal;
import static java.nio.file.attribute.AclEntryFlag.DIRECTORY_INHERIT;
import static java.nio.file.attribute.AclEntryPermission.APPEND_DATA;
import static java.nio.file.attribute.AclEntryPermission.DELETE;
import static java.nio.file.attribute.AclEntryType.ALLOW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.UserPrincipal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link AclAttributeProvider}.
 *
 * @author Colin Decker
 */
public class AclAttributeProviderTest extends AbstractAttributeProviderTest<AclAttributeProvider> {

    private static final UserPrincipal USER = createUserPrincipal("user");
    private static final UserPrincipal FOO = createUserPrincipal("foo");

    private static final List<AclEntry> defaultAcl =
            List.of(
                    AclEntry.newBuilder()
                            .setType(ALLOW)
                            .setFlags(DIRECTORY_INHERIT)
                            .setPermissions(DELETE, APPEND_DATA)
                            .setPrincipal(USER)
                            .build(),
                    AclEntry.newBuilder()
                            .setType(ALLOW)
                            .setFlags(DIRECTORY_INHERIT)
                            .setPermissions(DELETE, APPEND_DATA)
                            .setPrincipal(FOO)
                            .build());

    @Override
    protected AclAttributeProvider createProvider() {
        return new AclAttributeProvider();
    }

    @Override
    protected Set<? extends AttributeProvider> createInheritedProviders() {
        return Set.of(new BasicAttributeProvider(), new OwnerAttributeProvider());
    }

    @Override
    protected Map<String, ?> createDefaultValues() {
        return Map.of("acl:acl", defaultAcl);
    }

    @Test
    public void testInitialAttributes() {
        assertEquals(defaultAcl, provider.get(file, "acl"));
    }

    @Test
    public void testSet() {
        assertSetAndGetSucceeds("acl", List.of());
        assertSetFailsOnCreate("acl", List.of());
        assertSetFails("acl", Set.of());
        assertSetFails("acl", List.of("hello"));
    }

    @Test
    public void testView() throws IOException {
        AclFileAttributeView view =
                provider.view(
                        fileLookup(),
                        Map.<String, FileAttributeView>of(
                                "owner",
                                new OwnerAttributeProvider()
                                        .view(fileLookup(), NO_INHERITED_VIEWS)));
        assertNotNull(view);

        assertEquals("acl", view.name());

        assertEquals(defaultAcl, view.getAcl());

        view.setAcl(List.<AclEntry>of());
        view.setOwner(FOO);

        assertEquals(List.of(), view.getAcl());
        assertEquals(FOO, view.getOwner());

        assertEquals(List.of(), file.getAttribute("acl", "acl"));
    }
}
