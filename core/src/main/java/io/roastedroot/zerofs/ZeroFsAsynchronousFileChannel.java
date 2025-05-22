package io.roastedroot.zerofs;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.CompletionHandler;
import java.nio.channels.FileLock;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * {@link AsynchronousFileChannel} implementation that delegates to a {@link ZeroFsFileChannel}.
 *
 * @author Colin Decker
 */
final class ZeroFsAsynchronousFileChannel extends AsynchronousFileChannel {

    private final ZeroFsFileChannel channel;
    private final CompletableListeningExecutor executor;

    public class CompletableListeningExecutor {
        private final ExecutorService delegate;

        public CompletableListeningExecutor(ExecutorService delegate) {
            this.delegate = delegate;
        }

        public <T> CompletableFuture<T> submit(Callable<T> task) {
            CompletableFuture<T> future = new CompletableFuture<>();
            delegate.submit(
                    () -> {
                        try {
                            T result = task.call();
                            future.complete(result);
                        } catch (Throwable t) {
                            future.completeExceptionally(t);
                        }
                    });
            return future;
        }

        public CompletableFuture<Void> submit(Runnable task) {
            return submit(
                    () -> {
                        task.run();
                        return null;
                    });
        }

        public void shutdown() {
            delegate.shutdown();
        }

        public ExecutorService getExecutor() {
            return delegate;
        }
    }

    public ZeroFsAsynchronousFileChannel(ZeroFsFileChannel channel, ExecutorService executor) {
        this.channel = Objects.requireNonNull(channel);
        this.executor = new CompletableListeningExecutor(executor);
    }

    @Override
    public long size() throws IOException {
        return channel.size();
    }

    private <R, A> void addCallback(
            CompletableFuture<R> future, CompletionHandler<R, ? super A> handler, A attachment) {
        future.whenCompleteAsync(
                (result, throwable) -> {
                    if (throwable != null) {
                        handler.failed(throwable, attachment);
                    } else {
                        handler.completed(result, attachment);
                    }
                },
                executor.getExecutor());
    }

    @Override
    public AsynchronousFileChannel truncate(long size) throws IOException {
        channel.truncate(size);
        return this;
    }

    @Override
    public void force(boolean metaData) throws IOException {
        channel.force(metaData);
    }

    @Override
    public <A> void lock(
            long position,
            long size,
            boolean shared,
            A attachment,
            CompletionHandler<FileLock, ? super A> handler) {
        Objects.requireNonNull(handler);
        addCallback(lock(position, size, shared), handler, attachment);
    }

    @Override
    public CompletableFuture<FileLock> lock(
            final long position, final long size, final boolean shared) {
        Util.checkNotNegative(position, "position");
        Util.checkNotNegative(size, "size");
        if (!isOpen()) {
            return closedChannelFuture();
        }
        if (shared) {
            channel.checkReadable();
        } else {
            channel.checkWritable();
        }
        return executor.submit(
                new Callable<FileLock>() {
                    @Override
                    public FileLock call() throws IOException {
                        return tryLock(position, size, shared);
                    }
                });
    }

    @Override
    public FileLock tryLock(long position, long size, boolean shared) throws IOException {
        Util.checkNotNegative(position, "position");
        Util.checkNotNegative(size, "size");
        channel.checkOpen();
        if (shared) {
            channel.checkReadable();
        } else {
            channel.checkWritable();
        }
        return new ZeroFsFileChannel.FakeFileLock(this, position, size, shared);
    }

    @Override
    public <A> void read(
            ByteBuffer dst,
            long position,
            A attachment,
            CompletionHandler<Integer, ? super A> handler) {
        addCallback(read(dst, position), handler, attachment);
    }

    @Override
    public CompletableFuture<Integer> read(final ByteBuffer dst, final long position) {
        if (dst.isReadOnly()) {
            throw new IllegalArgumentException("dst may not be read-only");
        }
        Util.checkNotNegative(position, "position");
        if (!isOpen()) {
            return closedChannelFuture();
        }
        channel.checkReadable();
        return executor.submit(
                new Callable<Integer>() {
                    @Override
                    public Integer call() throws IOException {
                        return channel.read(dst, position);
                    }
                });
    }

    @Override
    public <A> void write(
            ByteBuffer src,
            long position,
            A attachment,
            CompletionHandler<Integer, ? super A> handler) {
        addCallback(write(src, position), handler, attachment);
    }

    @Override
    public CompletableFuture<Integer> write(final ByteBuffer src, final long position) {
        Util.checkNotNegative(position, "position");
        if (!isOpen()) {
            return closedChannelFuture();
        }
        channel.checkWritable();
        return executor.submit(
                new Callable<Integer>() {
                    @Override
                    public Integer call() throws IOException {
                        return channel.write(src, position);
                    }
                });
    }

    @Override
    public boolean isOpen() {
        return channel.isOpen();
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }

    /** Immediate future indicating that the channel is closed. */
    private static <V> CompletableFuture<V> closedChannelFuture() {
        CompletableFuture<V> future = new CompletableFuture<>();
        future.completeExceptionally(new ClosedChannelException());
        return future;
    }
}
