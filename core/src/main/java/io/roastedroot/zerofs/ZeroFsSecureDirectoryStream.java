package io.roastedroot.zerofs;

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.ClosedDirectoryStreamException;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

/**
 * Secure directory stream implementation that uses a {@link FileSystemView} with the stream's
 * directory as its working directory.
 *
 * @author Colin Decker
 */
final class ZeroFsSecureDirectoryStream implements SecureDirectoryStream<Path> {

    private final FileSystemView view;
    private final Filter<? super Path> filter;
    private final FileSystemState fileSystemState;

    private boolean open = true;
    private Iterator<Path> iterator = new DirectoryIterator();

    public ZeroFsSecureDirectoryStream(
            FileSystemView view, Filter<? super Path> filter, FileSystemState fileSystemState) {
        this.view = Objects.requireNonNull(view);
        this.filter = Objects.requireNonNull(filter);
        this.fileSystemState = fileSystemState;
        fileSystemState.register(this);
    }

    private ZeroFsPath path() {
        return view.getWorkingDirectoryPath();
    }

    @Override
    public synchronized Iterator<Path> iterator() {
        checkOpen();
        Iterator<Path> result = iterator;
        // checkState(result != null, "iterator() has already been called once");
        if (result == null) {
            throw new IllegalStateException("iterator() has already been called once");
        }
        iterator = null;
        return result;
    }

    @Override
    public synchronized void close() {
        open = false;
        fileSystemState.unregister(this);
    }

    protected synchronized void checkOpen() {
        if (!open) {
            throw new ClosedDirectoryStreamException();
        }
    }

    public final class DirectoryIterator implements Iterator<Path> {

        private Iterator<Name> fileNames;
        private Path nextPath;
        private boolean nextPathReady = false;

        private void prepareNext() {
            if (nextPathReady) return;

            checkOpen();

            try {
                if (fileNames == null) {
                    fileNames = view.snapshotWorkingDirectoryEntries().iterator();
                }

                while (fileNames.hasNext()) {
                    Name name = fileNames.next();
                    Path path = view.getWorkingDirectoryPath().resolve(name);

                    if (filter.accept(path)) {
                        nextPath = path;
                        nextPathReady = true;
                        return;
                    }
                }

                nextPath = null;
                nextPathReady = false;
            } catch (IOException e) {
                throw new DirectoryIteratorException(e);
            }
        }

        @Override
        public synchronized boolean hasNext() {
            prepareNext();
            return nextPathReady;
        }

        @Override
        public synchronized Path next() {
            prepareNext();
            if (!nextPathReady) {
                throw new NoSuchElementException();
            }

            Path result = nextPath;
            nextPath = null;
            nextPathReady = false;
            return result;
        }
    }

    /** A stream filter that always returns true. */
    public static final Filter<Object> ALWAYS_TRUE_FILTER =
            new Filter<Object>() {
                @Override
                public boolean accept(Object entry) throws IOException {
                    return true;
                }
            };

    @Override
    public SecureDirectoryStream<Path> newDirectoryStream(Path path, LinkOption... options)
            throws IOException {
        checkOpen();
        ZeroFsPath checkedPath = checkPath(path);

        // safe cast because a file system that supports SecureDirectoryStream always creates
        // SecureDirectoryStreams
        return (SecureDirectoryStream<Path>)
                view.newDirectoryStream(
                        checkedPath,
                        ALWAYS_TRUE_FILTER,
                        Options.getLinkOptions(options),
                        path().resolve(checkedPath));
    }

    @Override
    public SeekableByteChannel newByteChannel(
            Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs)
            throws IOException {
        checkOpen();
        ZeroFsPath checkedPath = checkPath(path);
        Set<OpenOption> opts = Options.getOptionsForChannel(options);
        return new ZeroFsFileChannel(
                view.getOrCreateRegularFile(checkedPath, opts), opts, fileSystemState);
    }

    @Override
    public void deleteFile(Path path) throws IOException {
        checkOpen();
        ZeroFsPath checkedPath = checkPath(path);
        view.deleteFile(checkedPath, FileSystemView.DeleteMode.NON_DIRECTORY_ONLY);
    }

    @Override
    public void deleteDirectory(Path path) throws IOException {
        checkOpen();
        ZeroFsPath checkedPath = checkPath(path);
        view.deleteFile(checkedPath, FileSystemView.DeleteMode.DIRECTORY_ONLY);
    }

    @Override
    public void move(Path srcPath, SecureDirectoryStream<Path> targetDir, Path targetPath)
            throws IOException {
        checkOpen();
        ZeroFsPath checkedSrcPath = checkPath(srcPath);
        ZeroFsPath checkedTargetPath = checkPath(targetPath);

        if (!(targetDir instanceof ZeroFsSecureDirectoryStream)) {
            throw new ProviderMismatchException(
                    "targetDir isn't a secure directory stream associated with this file system");
        }

        ZeroFsSecureDirectoryStream checkedTargetDir = (ZeroFsSecureDirectoryStream) targetDir;

        view.copy(
                checkedSrcPath,
                checkedTargetDir.view,
                checkedTargetPath,
                Set.<CopyOption>of(),
                true);
    }

    @Override
    public <V extends FileAttributeView> V getFileAttributeView(Class<V> type) {
        return getFileAttributeView(path().getFileSystem().getPath("."), type);
    }

    @Override
    public <V extends FileAttributeView> V getFileAttributeView(
            Path path, Class<V> type, LinkOption... options) {
        checkOpen();
        final ZeroFsPath checkedPath = checkPath(path);
        final Set<LinkOption> optionsSet = Options.getLinkOptions(options);
        return view.getFileAttributeView(
                new FileLookup() {
                    @Override
                    public File lookup() throws IOException {
                        checkOpen(); // per the spec, must check that the stream is open for each
                        // view operation
                        return view.lookUpWithLock(checkedPath, optionsSet)
                                .requireExists(checkedPath)
                                .file();
                    }
                },
                type);
    }

    private static ZeroFsPath checkPath(Path path) {
        if (path instanceof ZeroFsPath) {
            return (ZeroFsPath) path;
        }
        throw new ProviderMismatchException(
                "path " + path + " is not associated with a ZeroFs file system");
    }
}
