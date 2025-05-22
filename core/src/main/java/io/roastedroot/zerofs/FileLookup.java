package io.roastedroot.zerofs;

import java.io.IOException;

/**
 * Callback for looking up a file.
 *
 * @author Colin Decker
 */
public interface FileLookup {

    /**
     * Looks up the file.
     *
     * @throws IOException if the lookup fails for any reason, such as the file not existing
     */
    File lookup() throws IOException;
}
