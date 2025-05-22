package io.roastedroot.zerofs;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

/**
 * {@link OutputStream} for writing to a {@link RegularFile}.
 *
 * @author Colin Decker
 */
final class ZeroFsOutputStream extends OutputStream {

    // @GuardedBy("this")
    RegularFile file;

    // @GuardedBy("this")
    private long pos;

    private final boolean append;
    private final FileSystemState fileSystemState;

    ZeroFsOutputStream(RegularFile file, boolean append, FileSystemState fileSystemState) {
        this.file = Objects.requireNonNull(file);
        this.append = append;
        this.fileSystemState = fileSystemState;
        fileSystemState.register(this);
    }

    @Override
    public synchronized void write(int b) throws IOException {
        checkNotClosed();

        file.writeLock().lock();
        try {
            if (append) {
                pos = file.sizeWithoutLocking();
            }
            file.write(pos++, (byte) b);

            file.setLastModifiedTime(fileSystemState.now());
        } finally {
            file.writeLock().unlock();
        }
    }

    @Override
    public void write(byte[] b) throws IOException {
        writeInternal(b, 0, b.length);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (off < 0 || (off + len) < off || (off + len) > b.length) {
            throw new IndexOutOfBoundsException("bad position");
        }
        writeInternal(b, off, len);
    }

    private synchronized void writeInternal(byte[] b, int off, int len) throws IOException {
        checkNotClosed();

        file.writeLock().lock();
        try {
            if (append) {
                pos = file.sizeWithoutLocking();
            }
            pos += file.write(pos, b, off, len);

            file.setLastModifiedTime(fileSystemState.now());
        } finally {
            file.writeLock().unlock();
        }
    }

    // @GuardedBy("this")
    private void checkNotClosed() throws IOException {
        synchronized (this) {
            if (file == null) {
                throw new IOException("stream is closed");
            }
        }
    }

    @Override
    public synchronized void close() throws IOException {
        if (isOpen()) {
            fileSystemState.unregister(this);
            file.closed();

            // file is set to null here and only here
            file = null;
        }
    }

    // @GuardedBy("this")
    private boolean isOpen() {
        synchronized (this) {
            return file != null;
        }
    }
}
