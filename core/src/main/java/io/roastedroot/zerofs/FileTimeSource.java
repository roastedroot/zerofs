package io.roastedroot.zerofs;

import java.nio.file.attribute.FileTime;

/**
 * A source of the current time as a {@link FileTime}, to enable fake time sources for testing.
 *
 * @since 1.3
 */
public interface FileTimeSource {
    /** Returns the current time according to this source as a {@link FileTime}. */
    FileTime now();
}
