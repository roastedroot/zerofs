package io.roastedroot.zerofs;

import java.io.IOException;
import java.nio.file.attribute.FileAttributeView;
import java.util.Objects;

/**
 * Abstract base class for {@link FileAttributeView} implementations.
 *
 * @author Colin Decker
 */
abstract class AbstractAttributeView implements FileAttributeView {

    private final FileLookup lookup;

    protected AbstractAttributeView(FileLookup lookup) {
        this.lookup = Objects.requireNonNull(lookup);
    }

    /** Looks up the file to get or set attributes on. */
    protected final File lookupFile() throws IOException {
        return lookup.lookup();
    }
}
