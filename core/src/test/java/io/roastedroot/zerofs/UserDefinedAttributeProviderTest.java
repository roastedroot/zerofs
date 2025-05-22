package io.roastedroot.zerofs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link UserDefinedAttributeProvider}.
 *
 * @author Colin Decker
 */
public class UserDefinedAttributeProviderTest
        extends AbstractAttributeProviderTest<UserDefinedAttributeProvider> {

    @Override
    protected UserDefinedAttributeProvider createProvider() {
        return new UserDefinedAttributeProvider();
    }

    @Override
    protected Set<? extends AttributeProvider> createInheritedProviders() {
        return Set.of();
    }

    @Test
    public void testInitialAttributes() {
        // no initial attributes
        assertTrue(List.copyOf(file.getAttributeKeys()).isEmpty());
        assertTrue(provider.attributes(file).isEmpty());
    }

    @Test
    public void testGettingAndSetting() {
        byte[] bytes = {0, 1, 2, 3};
        provider.set(file, "user", "one", bytes, false);
        provider.set(file, "user", "two", ByteBuffer.wrap(bytes), false);

        byte[] one = (byte[]) provider.get(file, "one");
        byte[] two = (byte[]) provider.get(file, "two");
        assertTrue(Arrays.equals(one, bytes));
        assertTrue(Arrays.equals(two, bytes));

        assertSetFails("foo", "hello");

        assertEquals(Set.of("one", "two"), provider.attributes(file));
    }

    @Test
    public void testSetOnCreate() {
        assertSetFailsOnCreate("anything", new byte[0]);
    }

    @Test
    public void testView() throws IOException {
        UserDefinedFileAttributeView view = provider.view(fileLookup(), NO_INHERITED_VIEWS);
        assertNotNull(view);

        assertEquals("user", view.name());
        assertTrue(view.list().isEmpty());

        byte[] b1 = {0, 1, 2};
        byte[] b2 = {0, 1, 2, 3, 4};

        view.write("b1", ByteBuffer.wrap(b1));
        view.write("b2", ByteBuffer.wrap(b2));

        assertTrue(view.list().contains("b1"));
        assertTrue(view.list().contains("b2"));
        assertEquals(Set.of("user:b1", "user:b2"), file.getAttributeKeys());

        assertEquals(3, view.size("b1"));
        assertEquals(5, view.size("b2"));

        ByteBuffer buf1 = ByteBuffer.allocate(view.size("b1"));
        ByteBuffer buf2 = ByteBuffer.allocate(view.size("b2"));

        view.read("b1", buf1);
        view.read("b2", buf2);

        assertTrue(Arrays.equals(b1, buf1.array()));
        assertTrue(Arrays.equals(b2, buf2.array()));

        view.delete("b2");

        assertEquals(List.of("b1"), view.list());
        assertEquals(Set.of("user:b1"), file.getAttributeKeys());

        try {
            view.size("b2");
            fail();
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("not set"));
        }

        try {
            view.read("b2", ByteBuffer.allocate(10));
            fail();
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("not set"));
        }

        view.write("b1", ByteBuffer.wrap(b2));
        assertEquals(5, view.size("b1"));

        view.delete("b2"); // succeeds
    }
}
