package io.roastedroot.zerofs;

/**
 * Optional file system features that may be supported or unsupported by a ZeroFs file system
 * instance.
 *
 * @author Colin Decker
 */
public enum Feature {

    /**
     * Feature controlling support for hard links to regular files.
     *
     * <p>Affected method:
     *
     * <ul>
     *   <li>{@link Files#createLink(Path, Path)}
     * </ul>
     *
     * <p>If this feature is not enabled, this method will throw {@link
     * UnsupportedOperationException}.
     */
    LINKS,

    /**
     * Feature controlling support for symbolic links.
     *
     * <p>Affected methods:
     *
     * <ul>
     *   <li>{@link Files#createSymbolicLink(Path, Path, FileAttribute...)}
     *   <li>{@link Files#readSymbolicLink(Path)}
     * </ul>
     *
     * <p>If this feature is not enabled, these methods will throw {@link
     * UnsupportedOperationException}.
     */
    SYMBOLIC_LINKS,

    /**
     * Feature controlling support for {@link SecureDirectoryStream}.
     *
     * <p>Affected methods:
     *
     * <ul>
     *   <li>{@link Files#newDirectoryStream(Path)}
     *   <li>{@link Files#newDirectoryStream(Path, DirectoryStream.Filter)}
     *   <li>{@link Files#newDirectoryStream(Path, String)}
     * </ul>
     *
     * <p>If this feature is enabled, the {@link DirectoryStream} instances returned by these methods
     * will also implement {@link SecureDirectoryStream}.
     */
    SECURE_DIRECTORY_STREAM,

    /**
     * Feature controlling support for {@link FileChannel}.
     *
     * <p>Affected methods:
     *
     * <ul>
     *   <li>{@link Files#newByteChannel(Path, OpenOption...)}
     *   <li>{@link Files#newByteChannel(Path, Set, FileAttribute...)}
     *   <li>{@link FileChannel#open(Path, OpenOption...)}
     *   <li>{@link FileChannel#open(Path, Set, FileAttribute...)}
     *   <li>{@link AsynchronousFileChannel#open(Path, OpenOption...)}
     *   <li>{@link AsynchronousFileChannel#open(Path, Set, ExecutorService, FileAttribute...)}
     * </ul>
     *
     * <p>If this feature is not enabled, the {@link SeekableByteChannel} instances returned by the
     * {@code Files} methods will not be {@code FileChannel} instances and the {@code
     * FileChannel.open} and {@code AsynchronousFileChannel.open} methods will throw {@link
     * UnsupportedOperationException}.
     */
    // TODO(cgdecker): Should support for AsynchronousFileChannel be a separate feature?
    FILE_CHANNEL
}
