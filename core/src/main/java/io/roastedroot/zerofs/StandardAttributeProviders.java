package io.roastedroot.zerofs;

import java.util.Map;

/**
 * Static registry of {@link AttributeProvider} implementations for the standard set of file
 * attribute views ZeroFs supports.
 *
 * @author Colin Decker
 */
final class StandardAttributeProviders {

    private StandardAttributeProviders() {}

    private static final Map<String, AttributeProvider> PROVIDERS =
            Map.of(
                    "basic",
                    new BasicAttributeProvider(),
                    "owner",
                    new OwnerAttributeProvider(),
                    "posix",
                    new PosixAttributeProvider(),
                    "dos",
                    new DosAttributeProvider(),
                    "acl",
                    new AclAttributeProvider(),
                    "user",
                    new UserDefinedAttributeProvider());

    /**
     * Returns the attribute provider for the given view, or {@code null} if the given view is not one
     * of the attribute views this supports.
     */
    public static AttributeProvider get(String view) {
        AttributeProvider provider = PROVIDERS.get(view);

        if (provider == null && view.equals("unix")) {
            // create a new UnixAttributeProvider per file system, as it does some caching that
            // should be
            // cleaned up when the file system is garbage collected
            return new UnixAttributeProvider();
        }

        return provider;
    }
}
