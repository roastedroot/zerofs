package io.roastedroot.zerofs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.attribute.FileAttributeView;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;

/**
 * Abstract base class for tests of individual {@link AttributeProvider} implementations.
 *
 * @author Colin Decker
 */
public abstract class AbstractAttributeProviderTest<P extends AttributeProvider> {

    protected static final Map<String, FileAttributeView> NO_INHERITED_VIEWS = Map.of();

    protected final FakeFileTimeSource fileTimeSource = new FakeFileTimeSource();

    protected P provider;
    protected File file;

    /** Create the provider being tested. */
    protected abstract P createProvider();

    /** Creates the set of providers the provider being tested depends on. */
    protected abstract Set<? extends AttributeProvider> createInheritedProviders();

    protected FileLookup fileLookup() {
        return new FileLookup() {
            @Override
            public File lookup() throws IOException {
                return file;
            }
        };
    }

    @BeforeEach
    public void setUp() {
        this.provider = createProvider();
        this.file = Directory.create(0, fileTimeSource.now());

        Map<String, ?> defaultValues = createDefaultValues();
        setDefaultValues(file, provider, defaultValues);

        Set<? extends AttributeProvider> inheritedProviders = createInheritedProviders();
        for (AttributeProvider inherited : inheritedProviders) {
            setDefaultValues(file, inherited, defaultValues);
        }
    }

    private static void setDefaultValues(
            File file, AttributeProvider provider, Map<String, ?> defaultValues) {
        Map<String, ?> defaults = provider.defaultValues(defaultValues);
        for (Map.Entry<String, ?> entry : defaults.entrySet()) {
            int separatorIndex = entry.getKey().indexOf(':');
            String view = entry.getKey().substring(0, separatorIndex);
            String attr = entry.getKey().substring(separatorIndex + 1);
            file.setAttribute(view, attr, entry.getValue());
        }
    }

    protected Map<String, ?> createDefaultValues() {
        return Map.of();
    }

    // assertions

    protected void assertSupportsAll(String... attributes) {
        for (String attribute : attributes) {
            assertTrue(provider.supports(attribute));
        }
    }

    protected void assertContainsAll(File file, Map<String, Object> expectedAttributes) {
        for (Map.Entry<String, Object> entry : expectedAttributes.entrySet()) {
            String attribute = entry.getKey();
            Object value = entry.getValue();

            assertEquals(value, provider.get(file, attribute));
        }
    }

    protected void assertSetAndGetSucceeds(String attribute, Object value) {
        assertSetAndGetSucceeds(attribute, value, false);
    }

    protected void assertSetAndGetSucceeds(String attribute, Object value, boolean create) {
        provider.set(file, provider.name(), attribute, value, create);
        assertEquals(value, provider.get(file, attribute));
    }

    protected void assertSetAndGetSucceedsOnCreate(String attribute, Object value) {
        assertSetAndGetSucceeds(attribute, value, true);
    }

    @SuppressWarnings("EmptyCatchBlock")
    protected void assertSetFails(String attribute, Object value) {
        try {
            provider.set(file, provider.name(), attribute, value, false);
            throw new AssertionError();
        } catch (IllegalArgumentException expected) {
        }
    }

    @SuppressWarnings("EmptyCatchBlock")
    protected void assertSetFailsOnCreate(String attribute, Object value) {
        try {
            provider.set(file, provider.name(), attribute, value, true);
            throw new AssertionError();
        } catch (UnsupportedOperationException expected) {
        }
    }
}
