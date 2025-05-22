package io.roastedroot.zerofs;

import static io.roastedroot.zerofs.UserLookupService.createUserPrincipal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.nio.file.attribute.FileOwnerAttributeView;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link OwnerAttributeProvider}.
 *
 * @author Colin Decker
 */
public class OwnerAttributeProviderTest
        extends AbstractAttributeProviderTest<OwnerAttributeProvider> {

    @Override
    protected OwnerAttributeProvider createProvider() {
        return new OwnerAttributeProvider();
    }

    @Override
    protected Set<? extends AttributeProvider> createInheritedProviders() {
        return Set.of();
    }

    @Test
    public void testInitialAttributes() {
        assertEquals(createUserPrincipal("user"), provider.get(file, "owner"));
    }

    @Test
    public void testSet() {
        assertSetAndGetSucceeds("owner", createUserPrincipal("user"));
        assertSetFailsOnCreate("owner", createUserPrincipal("user"));

        // invalid type
        assertSetFails("owner", "root");
    }

    @Test
    public void testView() throws IOException {
        FileOwnerAttributeView view = provider.view(fileLookup(), NO_INHERITED_VIEWS);
        assertNotNull(view);

        assertEquals("owner", view.name());
        assertEquals(createUserPrincipal("user"), view.getOwner());

        view.setOwner(createUserPrincipal("root"));
        assertEquals(createUserPrincipal("root"), view.getOwner());
        assertEquals(createUserPrincipal("root"), file.getAttribute("owner", "owner"));
    }
}
