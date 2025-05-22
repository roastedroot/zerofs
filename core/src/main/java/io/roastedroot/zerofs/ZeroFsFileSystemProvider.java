package io.roastedroot.zerofs;

import static io.roastedroot.zerofs.Feature.FILE_CHANNEL;
import static io.roastedroot.zerofs.ZeroFs.URI_SCHEME;
import static java.nio.file.StandardOpenOption.APPEND;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 * {@link FileSystemProvider} implementation for ZeroFs. This provider implements the actual file
 * system operations but does not handle creation, caching or lookup of file systems. See {@link
 * SystemZeroFsFileSystemProvider}, which is the {@code META-INF/services/} entry for ZeroFs, for
 * those operations.
 *
 * @author Colin Decker
 */
final class ZeroFsFileSystemProvider extends FileSystemProvider {

    private static final ZeroFsFileSystemProvider INSTANCE = new ZeroFsFileSystemProvider();

    static {
        // Register the URL stream handler implementation.
        try {
            Handler.register();
        } catch (Throwable e) {
            // Couldn't set the system property needed to register the handler. Nothing we can do
            // really.
        }
    }

    /** Returns the singleton instance of this provider. */
    static ZeroFsFileSystemProvider instance() {
        return INSTANCE;
    }

    @Override
    public String getScheme() {
        return URI_SCHEME;
    }

    @Override
    public FileSystem newFileSystem(URI uri, Map<String, ?> env) throws IOException {
        throw new UnsupportedOperationException(
                "This method should not be called directly;"
                        + "use an overload of ZeroFs.newFileSystem() to create a FileSystem.");
    }

    @Override
    public FileSystem newFileSystem(Path path, Map<String, ?> env) throws IOException {
        ZeroFsPath checkedPath = checkPath(path);
        Objects.requireNonNull(env);

        URI pathUri = checkedPath.toUri();
        URI jarUri = URI.create("jar:" + pathUri);

        try {
            // pass the new jar:ZeroFs://... URI to be handled by ZipFileSystemProvider
            return FileSystems.newFileSystem(jarUri, env);
        } catch (Exception e) {
            // if any exception occurred, assume the file wasn't a zip file and that we don't
            // support
            // viewing it as a file system
            throw new UnsupportedOperationException(e);
        }
    }

    @Override
    public FileSystem getFileSystem(URI uri) {
        throw new UnsupportedOperationException(
                "This method should not be called directly; "
                        + "use FileSystems.getFileSystem(URI) instead.");
    }

    /** Gets the file system for the given path. */
    private static ZeroFsFileSystem getFileSystem(Path path) {
        return (ZeroFsFileSystem) checkPath(path).getFileSystem();
    }

    @Override
    public Path getPath(URI uri) {
        throw new UnsupportedOperationException(
                "This method should not be called directly; " + "use Paths.get(URI) instead.");
    }

    private static ZeroFsPath checkPath(Path path) {
        if (path instanceof ZeroFsPath) {
            return (ZeroFsPath) path;
        }
        throw new ProviderMismatchException(
                "path " + path + " is not associated with a ZeroFs file system");
    }

    /** Returns the default file system view for the given path. */
    private static FileSystemView getDefaultView(ZeroFsPath path) {
        return getFileSystem(path).getDefaultView();
    }

    @Override
    public FileChannel newFileChannel(
            Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs)
            throws IOException {
        ZeroFsPath checkedPath = checkPath(path);
        if (!checkedPath.getZeroFsFileSystem().getFileStore().supportsFeature(FILE_CHANNEL)) {
            throw new UnsupportedOperationException();
        }
        return newZeroFsFileChannel(checkedPath, options, attrs);
    }

    private ZeroFsFileChannel newZeroFsFileChannel(
            ZeroFsPath path, Set<? extends OpenOption> options, FileAttribute<?>... attrs)
            throws IOException {
        Set<OpenOption> opts = Options.getOptionsForChannel(options);
        FileSystemView view = getDefaultView(path);
        RegularFile file = view.getOrCreateRegularFile(path, opts, attrs);
        return new ZeroFsFileChannel(file, opts, view.state());
    }

    @Override
    public SeekableByteChannel newByteChannel(
            Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs)
            throws IOException {
        ZeroFsPath checkedPath = checkPath(path);
        ZeroFsFileChannel channel = newZeroFsFileChannel(checkedPath, options, attrs);
        return checkedPath.getZeroFsFileSystem().getFileStore().supportsFeature(FILE_CHANNEL)
                ? channel
                : new DowngradedSeekableByteChannel(channel);
    }

    @Override
    public AsynchronousFileChannel newAsynchronousFileChannel(
            Path path,
            Set<? extends OpenOption> options,
            ExecutorService executor,
            FileAttribute<?>... attrs)
            throws IOException {
        // call newFileChannel and cast so that FileChannel support is checked there
        ZeroFsFileChannel channel = (ZeroFsFileChannel) newFileChannel(path, options, attrs);
        if (executor == null) {
            ZeroFsFileSystem fileSystem = (ZeroFsFileSystem) path.getFileSystem();
            executor = fileSystem.getDefaultThreadPool();
        }
        return channel.asAsynchronousFileChannel(executor);
    }

    @Override
    public InputStream newInputStream(Path path, OpenOption... options) throws IOException {
        ZeroFsPath checkedPath = checkPath(path);
        Set<OpenOption> opts = Options.getOptionsForInputStream(options);
        FileSystemView view = getDefaultView(checkedPath);
        RegularFile file = view.getOrCreateRegularFile(checkedPath, opts, NO_ATTRS);
        return new ZeroFsInputStream(file, view.state());
    }

    private static final FileAttribute<?>[] NO_ATTRS = {};

    @Override
    public OutputStream newOutputStream(Path path, OpenOption... options) throws IOException {
        ZeroFsPath checkedPath = checkPath(path);
        Set<OpenOption> opts = Options.getOptionsForOutputStream(options);
        FileSystemView view = getDefaultView(checkedPath);
        RegularFile file = view.getOrCreateRegularFile(checkedPath, opts, NO_ATTRS);
        return new ZeroFsOutputStream(file, opts.contains(APPEND), view.state());
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(
            Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
        ZeroFsPath checkedPath = checkPath(dir);
        return getDefaultView(checkedPath)
                .newDirectoryStream(checkedPath, filter, Options.FOLLOW_LINKS, checkedPath);
    }

    @Override
    public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
        ZeroFsPath checkedPath = checkPath(dir);
        FileSystemView view = getDefaultView(checkedPath);
        view.createDirectory(checkedPath, attrs);
    }

    @Override
    public void createLink(Path link, Path existing) throws IOException {
        ZeroFsPath linkPath = checkPath(link);
        ZeroFsPath existingPath = checkPath(existing);
        if (!linkPath.getFileSystem().equals(existingPath.getFileSystem())) {
            throw new IllegalArgumentException(
                    "link and existing paths must belong to the same file system instance");
        }
        FileSystemView view = getDefaultView(linkPath);
        view.link(linkPath, getDefaultView(existingPath), existingPath);
    }

    @Override
    public void createSymbolicLink(Path link, Path target, FileAttribute<?>... attrs)
            throws IOException {
        ZeroFsPath linkPath = checkPath(link);
        ZeroFsPath targetPath = checkPath(target);
        if (!linkPath.getFileSystem().equals(targetPath.getFileSystem())) {
            throw new IllegalArgumentException(
                    "link and target paths must belong to the same file system instance");
        }
        FileSystemView view = getDefaultView(linkPath);
        view.createSymbolicLink(linkPath, targetPath, attrs);
    }

    @Override
    public Path readSymbolicLink(Path link) throws IOException {
        ZeroFsPath checkedPath = checkPath(link);
        return getDefaultView(checkedPath).readSymbolicLink(checkedPath);
    }

    @Override
    public void delete(Path path) throws IOException {
        ZeroFsPath checkedPath = checkPath(path);
        FileSystemView view = getDefaultView(checkedPath);
        view.deleteFile(checkedPath, FileSystemView.DeleteMode.ANY);
    }

    @Override
    public void copy(Path source, Path target, CopyOption... options) throws IOException {
        copy(source, target, Options.getCopyOptions(options), false);
    }

    private void copy(Path source, Path target, Set<CopyOption> options, boolean move)
            throws IOException {
        ZeroFsPath sourcePath = checkPath(source);
        ZeroFsPath targetPath = checkPath(target);

        FileSystemView sourceView = getDefaultView(sourcePath);
        FileSystemView targetView = getDefaultView(targetPath);
        sourceView.copy(sourcePath, targetView, targetPath, options, move);
    }

    @Override
    public void move(Path source, Path target, CopyOption... options) throws IOException {
        copy(source, target, Options.getMoveOptions(options), true);
    }

    @Override
    public boolean isSameFile(Path path, Path path2) throws IOException {
        if (path.equals(path2)) {
            return true;
        }

        if (!(path instanceof ZeroFsPath && path2 instanceof ZeroFsPath)) {
            return false;
        }

        ZeroFsPath checkedPath = (ZeroFsPath) path;
        ZeroFsPath checkedPath2 = (ZeroFsPath) path2;

        FileSystemView view = getDefaultView(checkedPath);
        FileSystemView view2 = getDefaultView(checkedPath2);

        return view.isSameFile(checkedPath, view2, checkedPath2);
    }

    @Override
    public boolean isHidden(Path path) throws IOException {
        // TODO(cgdecker): This should probably be configurable, but this seems fine for now
        /*
         * If the DOS view is supported, use the Windows isHidden method (check the dos:hidden
         * attribute). Otherwise, use the Unix isHidden method (just check if the file name starts with
         * ".").
         */
        ZeroFsPath checkedPath = checkPath(path);
        FileSystemView view = getDefaultView(checkedPath);
        if (getFileStore(path).supportsFileAttributeView("dos")) {
            return view.readAttributes(checkedPath, DosFileAttributes.class, Options.NOFOLLOW_LINKS)
                    .isHidden();
        }
        return path.getNameCount() > 0 && path.getFileName().toString().startsWith(".");
    }

    @Override
    public FileStore getFileStore(Path path) throws IOException {
        return getFileSystem(path).getFileStore();
    }

    @Override
    public void checkAccess(Path path, AccessMode... modes) throws IOException {
        ZeroFsPath checkedPath = checkPath(path);
        getDefaultView(checkedPath).checkAccess(checkedPath);
    }

    @Override
    public <V extends FileAttributeView> V getFileAttributeView(
            Path path, Class<V> type, LinkOption... options) {
        ZeroFsPath checkedPath = checkPath(path);
        return getDefaultView(checkedPath)
                .getFileAttributeView(checkedPath, type, Options.getLinkOptions(options));
    }

    @Override
    public <A extends BasicFileAttributes> A readAttributes(
            Path path, Class<A> type, LinkOption... options) throws IOException {
        ZeroFsPath checkedPath = checkPath(path);
        return getDefaultView(checkedPath)
                .readAttributes(checkedPath, type, Options.getLinkOptions(options));
    }

    @Override
    public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options)
            throws IOException {
        ZeroFsPath checkedPath = checkPath(path);
        return getDefaultView(checkedPath)
                .readAttributes(checkedPath, attributes, Options.getLinkOptions(options));
    }

    @Override
    public void setAttribute(Path path, String attribute, Object value, LinkOption... options)
            throws IOException {
        ZeroFsPath checkedPath = checkPath(path);
        getDefaultView(checkedPath)
                .setAttribute(checkedPath, attribute, value, Options.getLinkOptions(options));
    }
}
