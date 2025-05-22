package io.roastedroot.zerofs;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * {@link InputStream} for reading from a file's {@link RegularFile}.
 *
 * @author Colin Decker
 */
final class ZeroFsInputStream extends InputStream {

    // TODO: verify accesses and other GuardedBy
    // @GuardedBy("this")
    RegularFile file;

    // @GuardedBy("this")
    private long pos;

    // @GuardedBy("this")
    private boolean finished;

    private final FileSystemState fileSystemState;

    public ZeroFsInputStream(RegularFile file, FileSystemState fileSystemState) {
        this.file = Objects.requireNonNull(file);
        this.fileSystemState = fileSystemState;
        fileSystemState.register(this);
    }

    @Override
    public synchronized int read() throws IOException {
        checkNotClosed();
        if (finished) {
            return -1;
        }

        file.readLock().lock();
        try {

            int b = file.read(pos++); // it's ok for pos to go beyond size()
            if (b == -1) {
                finished = true;
            } else {
                file.setLastAccessTime(fileSystemState.now());
            }
            return b;
        } finally {
            file.readLock().unlock();
        }
    }

    @Override
    public int read(byte[] b) throws IOException {
        return readInternal(b, 0, b.length);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (off < 0 || (off + len) < off || (off + len) > b.length) {
            throw new IndexOutOfBoundsException("bad position");
        }
        return readInternal(b, off, len);
    }

    private synchronized int readInternal(byte[] b, int off, int len) throws IOException {
        checkNotClosed();
        if (finished) {
            return -1;
        }

        file.readLock().lock();
        try {
            int read = file.read(pos, b, off, len);
            if (read == -1) {
                finished = true;
            } else {
                pos += read;
            }

            file.setLastAccessTime(fileSystemState.now());
            return read;
        } finally {
            file.readLock().unlock();
        }
    }

    @Override
    public long skip(long n) throws IOException {
        if (n <= 0) {
            return 0;
        }

        synchronized (this) {
            checkNotClosed();
            if (finished) {
                return 0;
            }

            // available() must be an int, so the min must be also
            int skip = (int) Math.min(Math.max(file.size() - pos, 0), n);
            pos += skip;
            return skip;
        }
    }

    private static int saturatedCast(long value) {
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (value < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) value;
    }

    @Override
    public synchronized int available() throws IOException {
        checkNotClosed();
        if (finished) {
            return 0;
        }
        long available = Math.max(file.size() - pos, 0);
        return saturatedCast(available);
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
