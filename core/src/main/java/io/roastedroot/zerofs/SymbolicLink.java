package io.roastedroot.zerofs;

import java.nio.file.attribute.FileTime;
import java.util.Objects;

/**
 * A symbolic link file, containing a {@linkplain ZeroFsPath path}.
 *
 * @author Colin Decker
 */
final class SymbolicLink extends File {

    private final ZeroFsPath target;

    /** Creates a new symbolic link with the given ID and target. */
    public static SymbolicLink create(int id, FileTime creationTime, ZeroFsPath target) {
        return new SymbolicLink(id, creationTime, target);
    }

    private SymbolicLink(int id, FileTime creationTime, ZeroFsPath target) {
        super(id, creationTime);
        this.target = Objects.requireNonNull(target);
    }

    /** Returns the target path of this symbolic link. */
    ZeroFsPath target() {
        return target;
    }

    @Override
    File copyWithoutContent(int id, FileTime creationTime) {
        return SymbolicLink.create(id, creationTime, target);
    }
}
