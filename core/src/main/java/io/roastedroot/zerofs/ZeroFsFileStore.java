package io.roastedroot.zerofs;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileStoreAttributeView;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/**
 * {@link FileStore} implementation which provides methods for file creation, lookup and attribute
 * handling.
 *
 * <p>Most of these methods are actually implemented in another class: {@link FileTree} for lookup,
 * {@link FileFactory} for creating and copying files and {@link AttributeService} for attribute
 * handling. This class merely provides a single API through which to access the functionality of
 * those classes.
 *
 * @author Colin Decker
 */
final class ZeroFsFileStore extends FileStore {

    private final FileTree tree;
    private final HeapDisk disk;
    private final AttributeService attributes;
    private final FileFactory factory;
    private final Set<Feature> supportedFeatures;
    private final FileSystemState state;

    private final Lock readLock;
    private final Lock writeLock;

    public ZeroFsFileStore(
            FileTree tree,
            FileFactory factory,
            HeapDisk disk,
            AttributeService attributes,
            Set<Feature> supportedFeatures,
            FileSystemState state) {
        this.tree = Objects.requireNonNull(tree);
        this.factory = Objects.requireNonNull(factory);
        this.disk = Objects.requireNonNull(disk);
        this.attributes = Objects.requireNonNull(attributes);
        this.supportedFeatures = Objects.requireNonNull(supportedFeatures);
        this.state = Objects.requireNonNull(state);

        ReadWriteLock lock = new ReentrantReadWriteLock();
        this.readLock = lock.readLock();
        this.writeLock = lock.writeLock();
    }

    // internal use methods

    /** Returns the file system state object. */
    FileSystemState state() {
        return state;
    }

    /** Returns the read lock for this store. */
    Lock readLock() {
        return readLock;
    }

    /** Returns the write lock for this store. */
    Lock writeLock() {
        return writeLock;
    }

    /** Returns the names of the root directories in this store. */
    SortedSet<Name> getRootDirectoryNames() {
        state.checkOpen();
        return tree.getRootDirectoryNames();
    }

    /** Returns the root directory with the given name or {@code null} if no such directory exists. */
    Directory getRoot(Name name) {
        DirectoryEntry entry = tree.getRoot(name);
        return entry == null ? null : (Directory) entry.file();
    }

    /** Returns whether or not the given feature is supported by this file store. */
    boolean supportsFeature(Feature feature) {
        return supportedFeatures.contains(feature);
    }

    /**
     * Looks up the file at the given path using the given link options. If the path is relative, the
     * lookup is relative to the given working directory.
     *
     * @throws NoSuchFileException if an element of the path other than the final element does not
     *     resolve to a directory or symbolic link (e.g. it doesn't exist or is a regular file)
     * @throws IOException if a symbolic link cycle is detected or the depth of symbolic link
     *     recursion otherwise exceeds a threshold
     */
    DirectoryEntry lookUp(File workingDirectory, ZeroFsPath path, Set<? super LinkOption> options)
            throws IOException {
        state.checkOpen();
        return tree.lookUp(workingDirectory, path, options);
    }

    /** Returns a supplier that creates a new regular file. */
    Supplier<RegularFile> regularFileCreator() {
        state.checkOpen();
        return factory.regularFileCreator();
    }

    /** Returns a supplier that creates a new directory. */
    Supplier<Directory> directoryCreator() {
        state.checkOpen();
        return factory.directoryCreator();
    }

    /** Returns a supplier that creates a new symbolic link with the given target. */
    Supplier<SymbolicLink> symbolicLinkCreator(ZeroFsPath target) {
        state.checkOpen();
        return factory.symbolicLinkCreator(target);
    }

    /**
     * Creates a copy of the given file, copying its attributes as well according to the given {@code
     * attributeCopyOption}.
     */
    File copyWithoutContent(File file, AttributeCopyOption attributeCopyOption) throws IOException {
        File copy = factory.copyWithoutContent(file);
        setInitialAttributes(copy);
        attributes.copyAttributes(file, copy, attributeCopyOption);
        return copy;
    }

    /**
     * Sets initial attributes on the given file. Sets default attributes first, then attempts to set
     * the given user-provided attributes.
     */
    void setInitialAttributes(File file, FileAttribute<?>... attrs) {
        state.checkOpen();
        attributes.setInitialAttributes(file, attrs);
    }

    /**
     * Returns an attribute view of the given type for the given file lookup callback, or {@code null}
     * if the view type is not supported.
     */
    <V extends FileAttributeView> V getFileAttributeView(FileLookup lookup, Class<V> type) {
        state.checkOpen();
        return attributes.getFileAttributeView(lookup, type);
    }

    /**
     * Returns a map containing the attributes described by the given string mapped to their values.
     */
    Map<String, Object> readAttributes(File file, String attributes) {
        state.checkOpen();
        return this.attributes.readAttributes(file, attributes);
    }

    /**
     * Returns attributes of the given file as an object of the given type.
     *
     * @throws UnsupportedOperationException if the given attributes type is not supported
     */
    <A extends BasicFileAttributes> A readAttributes(File file, Class<A> type) {
        state.checkOpen();
        return attributes.readAttributes(file, type);
    }

    /** Sets the given attribute to the given value for the given file. */
    void setAttribute(File file, String attribute, Object value) {
        state.checkOpen();
        // TODO(cgdecker): Change attribute stuff to avoid the sad boolean parameter
        attributes.setAttribute(file, attribute, value, false);
    }

    /** Returns the file attribute views supported by this store. */
    Set<String> supportedFileAttributeViews() {
        state.checkOpen();
        return attributes.supportedFileAttributeViews();
    }

    // methods implementing the FileStore API

    @Override
    public String name() {
        return "zerofs";
    }

    @Override
    public String type() {
        return "zerofs";
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public long getTotalSpace() throws IOException {
        state.checkOpen();
        return disk.getTotalSpace();
    }

    @Override
    public long getUsableSpace() throws IOException {
        state.checkOpen();
        return getUnallocatedSpace();
    }

    @Override
    public long getUnallocatedSpace() throws IOException {
        state.checkOpen();
        return disk.getUnallocatedSpace();
    }

    @Override
    public boolean supportsFileAttributeView(Class<? extends FileAttributeView> type) {
        state.checkOpen();
        return attributes.supportsFileAttributeView(type);
    }

    @Override
    public boolean supportsFileAttributeView(String name) {
        state.checkOpen();
        return attributes.supportedFileAttributeViews().contains(name);
    }

    @Override
    public <V extends FileStoreAttributeView> V getFileStoreAttributeView(Class<V> type) {
        state.checkOpen();
        return null; // no supported views
    }

    @Override
    public Object getAttribute(String attribute) throws IOException {
        throw new UnsupportedOperationException();
    }
}
