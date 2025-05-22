package io.roastedroot.zerofs;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link FileSystem} implementation for ZeroFs. Most behavior for the file system is implemented by
 * its {@linkplain #getDefaultView() default file system view}.
 *
 * <h3>Overview of file system design</h3>
 *
 * {@link com.google.common.ZeroFs.ZeroFsFileSystem ZeroFsFileSystem} instances are created by {@link
 * com.google.common.ZeroFs.ZeroFsFileSystems ZeroFsFileSystems} using a user-provided {@link
 * com.google.common.ZeroFs.Configuration Configuration}. The configuration is used to create the
 * various classes that implement the file system with the correct settings and to create the file
 * system root directories and working directory. The file system is then used to create the {@code
 * Path} objects that all file system operations use.
 *
 * <p>Once created, the primary entry points to the file system are {@link
 * com.google.common.ZeroFs.ZeroFsFileSystemProvider ZeroFsFileSystemProvider}, which handles calls to
 * methods in {@link java.nio.file.Files}, and {@link
 * com.google.common.ZeroFs.ZeroFsSecureDirectoryStream ZeroFsSecureDirectoryStream}, which provides
 * methods that are similar to those of the file system provider but which treat relative paths as
 * relative to the stream's directory rather than the file system's working directory.
 *
 * <p>The implementation of the methods on both of those classes is handled by the {@link
 * com.google.common.ZeroFs.FileSystemView FileSystemView} class, which acts as a view of the file
 * system with a specific working directory. The file system provider uses the file system's default
 * view, while each secure directory stream uses a view specific to that stream.
 *
 * <p>File system views make use of the file system's singleton {@link
 * com.google.common.ZeroFs.ZeroFsFileStore ZeroFsFileStore} which handles file creation, storage and
 * attributes. The file store delegates to several other classes to handle each of these:
 *
 * <ul>
 *   <li>{@link com.google.common.ZeroFs.FileFactory FileFactory} handles creation of new file
 *       objects.
 *   <li>{@link com.google.common.ZeroFs.HeapDisk HeapDisk} handles allocation of blocks to {@link
 *       RegularFile RegularFile} instances.
 *   <li>{@link com.google.common.ZeroFs.FileTree FileTree} stores the root of the file hierarchy and
 *       handles file lookup.
 *   <li>{@link com.google.common.ZeroFs.AttributeService AttributeService} handles file attributes,
 *       using a set of {@link com.google.common.ZeroFs.AttributeProvider AttributeProvider}
 *       implementations to handle each supported file attribute view.
 * </ul>
 *
 * <h3>Paths</h3>
 *
 * The implementation of {@link java.nio.file.Path} for the file system is {@link
 * com.google.common.ZeroFs.ZeroFsPath ZeroFsPath}. Paths are created by a {@link
 * com.google.common.ZeroFs.PathService PathService} with help from the file system's configured
 * {@link com.google.common.ZeroFs.PathType PathType}.
 *
 * <p>Paths are made up of {@link com.google.common.ZeroFs.Name Name} objects, which also serve as
 * the file names in directories. A name has two forms:
 *
 * <ul>
 *   <li>The <b>display form</b> is used in {@code Path} for {@code toString()}. It is also used for
 *       determining the equality and sort order of {@code Path} objects for most file systems.
 *   <li>The <b>canonical form</b> is used for equality of two {@code Name} objects. This affects
 *       the notion of name equality in the file system itself for file lookup. A file system may be
 *       configured to use the canonical form of the name for path equality (a Windows-like file
 *       system configuration does this, as the real Windows file system implementation uses
 *       case-insensitive equality for its path objects.
 * </ul>
 *
 * <p>The canonical form of a name is created by applying a series of {@linkplain PathNormalization
 * normalizations} to the original string. These normalization may be either a Unicode normalization
 * (e.g. NFD) or case folding normalization for case-insensitivity. Normalizations may also be
 * applied to the display form of a name, but this is currently only done for a Mac OS X type
 * configuration.
 *
 * <h3>Files</h3>
 *
 * All files in the file system are an instance of {@link com.google.common.ZeroFs.File File}. A file
 * object contains both the file's attributes and content.
 *
 * <p>There are three types of files:
 *
 * <ul>
 *   <li>{@link Directory Directory} - contains a table linking file names to {@linkplain
 *       com.google.common.ZeroFs.DirectoryEntry directory entries}.
 *   <li>{@link RegularFile RegularFile} - an in-memory store for raw bytes.
 *   <li>{@link com.google.common.ZeroFs.SymbolicLink SymbolicLink} - contains a path.
 * </ul>
 *
 * <p>{@link com.google.common.ZeroFs.ZeroFsFileChannel ZeroFsFileChannel}, {@link
 * com.google.common.ZeroFs.ZeroFsInputStream ZeroFsInputStream} and {@link
 * com.google.common.ZeroFs.ZeroFsOutputStream ZeroFsOutputStream} implement the standard
 * channel/stream APIs for regular files.
 *
 * <p>{@link com.google.common.ZeroFs.ZeroFsSecureDirectoryStream ZeroFsSecureDirectoryStream} handles
 * reading the entries of a directory. The secure directory stream additionally contains a {@code
 * FileSystemView} with its directory as the working directory, allowing for operations relative to
 * the actual directory file rather than just the path to the file. This allows the operations to
 * continue to work as expected even if the directory is moved.
 *
 * <p>A directory can be watched for changes using the {@link java.nio.file.WatchService}
 * implementation, {@link com.google.common.ZeroFs.PollingWatchService PollingWatchService}.
 *
 * <h3>Regular files</h3>
 *
 * {@link RegularFile RegularFile} makes use of a singleton {@link com.google.common.ZeroFs.HeapDisk
 * HeapDisk}. A disk is a resizable factory and cache for fixed size blocks of memory. These blocks
 * are allocated to files as needed and returned to the disk when a file is deleted or truncated.
 * When cached free blocks are available, those blocks are allocated to files first. If more blocks
 * are needed, they are created.
 *
 * <h3>Linking</h3>
 *
 * When a file is mapped to a file name in a directory table, it is <i>linked</i>. Each type of file
 * has different rules governing how it is linked.
 *
 * <ul>
 *   <li>Directory - A directory has two or more links to it. The first is the link from its parent
 *       directory to it. This link is the name of the directory. The second is the <i>self</i> link
 *       (".") which links the directory to itself. The directory may also have any number of
 *       additional <i>parent</i> links ("..") from child directories back to it.
 *   <li>Regular file - A regular file has one link from its parent directory by default. However,
 *       regular files are also allowed to have any number of additional user-created hard links,
 *       from the same directory with different names and/or from other directories with any names.
 *   <li>Symbolic link - A symbolic link can only have one link, from its parent directory.
 * </ul>
 *
 * <h3>Thread safety</h3>
 *
 * All file system operations should be safe in a multithreaded environment. The file hierarchy
 * itself is protected by a file system level read-write lock. This ensures safety of all
 * modifications to directory tables as well as atomicity of operations like file moves. Regular
 * files are each protected by a read-write lock which is obtained for each read or write operation.
 * File attributes are protected by synchronization on the file object itself.
 *
 * @author Colin Decker
 */
final class ZeroFsFileSystem extends FileSystem {

    private final ZeroFsFileSystemProvider provider;
    private final URI uri;

    private final ZeroFsFileStore fileStore;
    private final PathService pathService;

    private final UserPrincipalLookupService userLookupService = new UserLookupService(true);

    private final FileSystemView defaultView;

    private final WatchServiceConfiguration watchServiceConfig;

    ZeroFsFileSystem(
            ZeroFsFileSystemProvider provider,
            URI uri,
            ZeroFsFileStore fileStore,
            PathService pathService,
            FileSystemView defaultView,
            WatchServiceConfiguration watchServiceConfig) {
        this.provider = Objects.requireNonNull(provider);
        this.uri = Objects.requireNonNull(uri);
        this.fileStore = Objects.requireNonNull(fileStore);
        this.pathService = Objects.requireNonNull(pathService);
        this.defaultView = Objects.requireNonNull(defaultView);
        this.watchServiceConfig = Objects.requireNonNull(watchServiceConfig);
    }

    @Override
    public ZeroFsFileSystemProvider provider() {
        return provider;
    }

    /** Returns the URI for this file system. */
    public URI getUri() {
        return uri;
    }

    /** Returns the default view for this file system. */
    public FileSystemView getDefaultView() {
        return defaultView;
    }

    @Override
    public String getSeparator() {
        return pathService.getSeparator();
    }

    @SuppressWarnings("unchecked") // safe cast of immutable set
    @Override
    public SortedSet<Path> getRootDirectories() {
        SortedSet<ZeroFsPath> builder = new TreeSet<>(pathService);
        for (Name name : fileStore.getRootDirectoryNames()) {
            builder.add(pathService.createRoot(name));
        }
        return (SortedSet<Path>) (SortedSet<?>) builder;
    }

    /** Returns the working directory path for this file system. */
    public ZeroFsPath getWorkingDirectory() {
        return defaultView.getWorkingDirectoryPath();
    }

    /** Returns the path service for this file system. */
    PathService getPathService() {
        return pathService;
    }

    /** Returns the file store for this file system. */
    public ZeroFsFileStore getFileStore() {
        return fileStore;
    }

    @Override
    public Set<FileStore> getFileStores() {
        fileStore.state().checkOpen();
        return Set.<FileStore>of(fileStore);
    }

    @Override
    public Set<String> supportedFileAttributeViews() {
        return fileStore.supportedFileAttributeViews();
    }

    @Override
    public ZeroFsPath getPath(String first, String... more) {
        fileStore.state().checkOpen();
        return pathService.parsePath(first, more);
    }

    /** Gets the URI of the given path in this file system. */
    public URI toUri(ZeroFsPath path) {
        fileStore.state().checkOpen();
        return pathService.toUri(uri, path.toAbsolutePath());
    }

    /** Converts the given URI into a path in this file system. */
    public ZeroFsPath toPath(URI uri) {
        fileStore.state().checkOpen();
        return pathService.fromUri(uri);
    }

    @Override
    public PathMatcher getPathMatcher(String syntaxAndPattern) {
        fileStore.state().checkOpen();
        return pathService.createPathMatcher(syntaxAndPattern);
    }

    @Override
    public UserPrincipalLookupService getUserPrincipalLookupService() {
        fileStore.state().checkOpen();
        return userLookupService;
    }

    @Override
    public WatchService newWatchService() throws IOException {
        return watchServiceConfig.newWatchService(defaultView, pathService);
    }

    private ExecutorService defaultThreadPool;

    /**
     * Returns a default thread pool to use for asynchronous file channels when users do not provide
     * an executor themselves. (This is required by the spec of newAsynchronousFileChannel in
     * FileSystemProvider.)
     */
    public synchronized ExecutorService getDefaultThreadPool() {
        if (defaultThreadPool == null) {
            String host = uri.getHost();
            String nameFormat = "ZeroFsFileSystem-" + host + "-defaultThreadPool-%d";
            AtomicInteger count = new AtomicInteger(0);

            ThreadFactory threadFactory =
                    r -> {
                        Thread thread = new Thread(r);
                        thread.setDaemon(true);
                        thread.setName(String.format(nameFormat, count.getAndIncrement()));
                        return thread;
                    };

            defaultThreadPool = Executors.newCachedThreadPool(threadFactory);

            // ensure thread pool is closed when file system is closed
            fileStore
                    .state()
                    .register(
                            new Closeable() {
                                @Override
                                public void close() {
                                    defaultThreadPool.shutdown();
                                }
                            });
        }
        return defaultThreadPool;
    }

    /**
     * Returns {@code false}; currently, cannot create a read-only file system.
     *
     * @return {@code false}, always
     */
    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public boolean isOpen() {
        return fileStore.state().isOpen();
    }

    @Override
    public void close() throws IOException {
        fileStore.state().close();
    }
}
