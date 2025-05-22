package io.roastedroot.zerofs;

import java.nio.file.attribute.FileTime;
import java.time.Instant;

/** Implementation of of {@link FileTimeSource} that gets the current time from the system. */
enum SystemFileTimeSource implements FileTimeSource {
    INSTANCE;

    @Override
    public FileTime now() {
        return FileTime.from(Instant.now());
    }

    @Override
    public String toString() {
        return "SystemFileTimeSource";
    }
}
