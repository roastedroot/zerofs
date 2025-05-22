package io.roastedroot.zerofs;

import static io.roastedroot.zerofs.UserLookupService.createGroupPrincipal;
import static io.roastedroot.zerofs.UserLookupService.createUserPrincipal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link UnixAttributeProvider}.
 *
 * @author Colin Decker
 */
@SuppressWarnings("OctalInteger")
public class UnixAttributeProviderTest
        extends AbstractAttributeProviderTest<UnixAttributeProvider> {

    @Override
    protected UnixAttributeProvider createProvider() {
        return new UnixAttributeProvider();
    }

    @Override
    protected Set<? extends AttributeProvider> createInheritedProviders() {
        return Set.of(
                new BasicAttributeProvider(),
                new OwnerAttributeProvider(),
                new PosixAttributeProvider());
    }

    @Test
    public void testInitialAttributes() {
        // unix provider relies on other providers to set their initial attributes
        file.setAttribute("owner", "owner", createUserPrincipal("foo"));
        file.setAttribute("posix", "group", createGroupPrincipal("bar"));
        file.setAttribute(
                "posix", "permissions", Set.copyOf(PosixFilePermissions.fromString("rw-r--r--")));

        // these are pretty much meaningless here since they aren't properties this
        // file system actually has, so don't really care about the exact value of these
        assertInstanceOf(Integer.class, provider.get(file, "uid"));
        assertInstanceOf(Integer.class, provider.get(file, "gid"));
        assertEquals(0L, provider.get(file, "rdev"));
        assertEquals(1L, provider.get(file, "dev"));
        assertInstanceOf(Integer.class, provider.get(file, "ino"));

        // these have logical origins in attributes from other views
        assertEquals(0644, provider.get(file, "mode")); // rw-r--r--
        assertEquals(file.getCreationTime(), provider.get(file, "ctime"));

        // this is based on a property this file system does actually have
        assertEquals(1, provider.get(file, "nlink"));

        file.incrementLinkCount();
        assertEquals(2, provider.get(file, "nlink"));
        file.decrementLinkCount();
        assertEquals(1, provider.get(file, "nlink"));
    }

    @Test
    public void testSet() {
        assertSetFails("unix:uid", 1);
        assertSetFails("unix:gid", 1);
        assertSetFails("unix:rdev", 1L);
        assertSetFails("unix:dev", 1L);
        assertSetFails("unix:ino", 1);
        assertSetFails("unix:mode", 1);
        assertSetFails("unix:ctime", 1L);
        assertSetFails("unix:nlink", 1);
    }
}
