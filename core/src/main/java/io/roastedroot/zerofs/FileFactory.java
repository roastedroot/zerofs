package io.roastedroot.zerofs;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Factory for creating new files and copying files. One piece of the file store implementation.
 *
 * @author Colin Decker
 */
final class FileFactory {

    private final AtomicInteger idGenerator = new AtomicInteger();

    private final HeapDisk disk;
    private final FileTimeSource fileTimeSource;

    /**
     * Creates a new file factory using the given disk for regular files and the given time source.
     */
    public FileFactory(HeapDisk disk, FileTimeSource fileTimeSource) {
        this.disk = Objects.requireNonNull(disk);
        this.fileTimeSource = Objects.requireNonNull(fileTimeSource);
    }

    private int nextFileId() {
        return idGenerator.getAndIncrement();
    }

    /** Creates a new directory. */
    public Directory createDirectory() {
        return Directory.create(nextFileId(), fileTimeSource.now());
    }

    /** Creates a new root directory with the given name. */
    public Directory createRootDirectory(Name name) {
        return Directory.createRoot(nextFileId(), fileTimeSource.now(), name);
    }

    /** Creates a new regular file. */
    RegularFile createRegularFile() {
        return RegularFile.create(nextFileId(), fileTimeSource.now(), disk);
    }

    /** Creates a new symbolic link referencing the given target path. */
    SymbolicLink createSymbolicLink(ZeroFsPath target) {
        return SymbolicLink.create(nextFileId(), fileTimeSource.now(), target);
    }

    /** Creates and returns a copy of the given file. */
    public File copyWithoutContent(File file) throws IOException {
        return file.copyWithoutContent(nextFileId(), fileTimeSource.now());
    }

    // suppliers to act as file creation callbacks

    private final Supplier<Directory> directorySupplier = new DirectorySupplier();

    private final Supplier<RegularFile> regularFileSupplier = new RegularFileSupplier();

    /** Returns a supplier that creates directories. */
    public Supplier<Directory> directoryCreator() {
        return directorySupplier;
    }

    /** Returns a supplier that creates regular files. */
    public Supplier<RegularFile> regularFileCreator() {
        return regularFileSupplier;
    }

    /** Returns a supplier that creates a symbolic links to the given path. */
    public Supplier<SymbolicLink> symbolicLinkCreator(ZeroFsPath target) {
        return new SymbolicLinkSupplier(target);
    }

    private final class DirectorySupplier implements Supplier<Directory> {
        @Override
        public Directory get() {
            return createDirectory();
        }
    }

    private final class RegularFileSupplier implements Supplier<RegularFile> {
        @Override
        public RegularFile get() {
            return createRegularFile();
        }
    }

    private final class SymbolicLinkSupplier implements Supplier<SymbolicLink> {

        private final ZeroFsPath target;

        protected SymbolicLinkSupplier(ZeroFsPath target) {
            this.target = Objects.requireNonNull(target);
        }

        @Override
        public SymbolicLink get() {
            return createSymbolicLink(target);
        }
    }
}
