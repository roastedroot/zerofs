package io.roastedroot.zerofs;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.CompletionHandler;
import java.nio.channels.FileLock;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

/**
 * {@link AsynchronousFileChannel} implementation that delegates to a {@link ZeroFsFileChannel}.
 *
 * @author Colin Decker
 */
final class ZeroFsAsynchronousFileChannel extends AsynchronousFileChannel {

    private final ZeroFsFileChannel channel;
    private final ExecutorService executor;

    public ZeroFsAsynchronousFileChannel(ZeroFsFileChannel channel, ExecutorService executor) {
        this.channel = Objects.requireNonNull(channel);
        this.executor = Objects.requireNonNull(executor);
    }

    @Override
    public long size() throws IOException {
        return channel.size();
    }

    private <R, A> void addCallback(
            ListenableTask<R> future, CompletionHandler<R, ? super A> handler, A attachment) {
        future.addListener(
                () ->
                        executor.execute(
                                new CompletionHandlerCallback<>(future, handler, attachment)));
    }

    private <R> ListenableTask<R> submit(Callable<R> task) {
        ListenableTask<R> future = new ListenableTask<>(task);
        executor.execute(future);
        return future;
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
    public ListenableTask<FileLock> lock(
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
        return submit(
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
    public ListenableTask<Integer> read(final ByteBuffer dst, final long position) {
        if (dst.isReadOnly()) {
            throw new IllegalArgumentException("dst may not be read-only");
        }
        Util.checkNotNegative(position, "position");
        if (!isOpen()) {
            return closedChannelFuture();
        }
        channel.checkReadable();
        return submit(
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
    public ListenableTask<Integer> write(final ByteBuffer src, final long position) {
        Util.checkNotNegative(position, "position");
        if (!isOpen()) {
            return closedChannelFuture();
        }
        channel.checkWritable();
        return submit(
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
    private static <V> ListenableTask<V> closedChannelFuture() {
        ListenableTask<V> future =
                new ListenableTask<>(
                        () -> {
                            throw new ClosedChannelException();
                        });
        future.run();
        return future;
    }

    /** {@link FutureTask} that runs a single listener once it is done, including on cancel. */
    static final class ListenableTask<V> extends FutureTask<V> {

        private Runnable listener;
        private boolean done;

        ListenableTask(Callable<V> callable) {
            super(callable);
        }

        void addListener(Runnable listener) {
            synchronized (this) {
                if (!done) {
                    this.listener = listener;
                    return;
                }
            }
            listener.run();
        }

        @Override
        protected void done() {
            Runnable toRun;
            synchronized (this) {
                done = true;
                toRun = listener;
            }
            if (toRun != null) {
                toRun.run();
            }
        }
    }

    /** Runnable callback that wraps a {@link CompletionHandler} and an attachment. */
    private static final class CompletionHandlerCallback<R, A> implements Runnable {

        private final Future<R> future;
        private final CompletionHandler<R, ? super A> completionHandler;
        private final A attachment;

        private CompletionHandlerCallback(
                Future<R> future, CompletionHandler<R, ? super A> completionHandler, A attachment) {
            this.future = Objects.requireNonNull(future);
            this.completionHandler = Objects.requireNonNull(completionHandler);
            this.attachment = attachment;
        }

        @Override
        public void run() {
            R result;
            try {
                result = future.get();
            } catch (ExecutionException e) {
                completionHandler.failed(e.getCause(), attachment);
                return;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                completionHandler.failed(e, attachment);
                return;
            } catch (RuntimeException | Error e) {
                completionHandler.failed(e, attachment);
                return;
            }
            completionHandler.completed(result, attachment);
        }
    }
}
