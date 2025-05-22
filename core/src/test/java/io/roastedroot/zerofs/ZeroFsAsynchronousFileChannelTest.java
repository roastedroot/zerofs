package io.roastedroot.zerofs;

import static io.roastedroot.zerofs.TestUtils.buffer;
import static io.roastedroot.zerofs.TestUtils.regularFile;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.CompletionHandler;
import java.nio.channels.FileLock;
import java.nio.file.OpenOption;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ZeroFsAsynchronousFileChannel}.
 *
 * @author Colin Decker
 */
public class ZeroFsAsynchronousFileChannelTest {

    private static ZeroFsAsynchronousFileChannel channel(
            RegularFile file, ExecutorService executor, OpenOption... options) throws IOException {
        ZeroFsFileChannel channel =
                new ZeroFsFileChannel(
                        file,
                        Options.getOptionsForChannel(Set.of(options)),
                        new FileSystemState(new FakeFileTimeSource(), () -> {}));
        return new ZeroFsAsynchronousFileChannel(channel, executor);
    }

    /**
     * Just tests the main read/write methods... the methods all delegate to the non-async channel
     * anyway.
     */
    @Test
    public void testAsyncChannel() throws Throwable {
        RegularFile file = regularFile(15);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        ZeroFsAsynchronousFileChannel channel = channel(file, executor, READ, WRITE);

        try {
            assertEquals(15, channel.size());

            assertSame(channel, channel.truncate(5));
            assertEquals(5, channel.size());

            file.write(5, new byte[5], 0, 5);
            checkAsyncRead(channel);
            checkAsyncWrite(channel);
            checkAsyncLock(channel);

            channel.close();
            assertFalse(channel.isOpen());
        } finally {
            executor.shutdown();
        }
    }

    @Test
    public void testClosedChannel() throws Throwable {
        RegularFile file = regularFile(15);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            ZeroFsAsynchronousFileChannel channel = channel(file, executor, READ, WRITE);
            channel.close();

            assertClosed(channel.read(ByteBuffer.allocate(10), 0));
            assertClosed(channel.write(ByteBuffer.allocate(10), 15));
            assertClosed(channel.lock());
            assertClosed(channel.lock(0, 10, true));
        } finally {
            executor.shutdown();
        }
    }

    @Test
    public void testAsyncClose_write() throws Throwable {
        RegularFile file = regularFile(15);
        ExecutorService executor = Executors.newFixedThreadPool(4);

        try {
            ZeroFsAsynchronousFileChannel channel = channel(file, executor, READ, WRITE);

            file.writeLock().lock(); // cause another thread trying to write to block

            // future-returning write
            Future<Integer> future = channel.write(ByteBuffer.allocate(10), 0);

            // completion handler write
            CompletableFuture<Integer> completionHandlerFuture = new CompletableFuture<>();
            channel.write(ByteBuffer.allocate(10), 0, null, setFuture(completionHandlerFuture));

            // Despite this 10ms sleep to allow plenty of time, it's possible, though very rare, for
            // a
            // race to cause the channel to be closed before the asynchronous calls get to the
            // initial
            // check that the channel is open, causing ClosedChannelException to be thrown rather
            // than
            // AsynchronousCloseException. This is not a problem in practice, just a quirk of how
            // these
            // tests work and that we don't have a way of waiting for the operations to get past
            // that
            // check.
            Thread.sleep(10);

            channel.close();

            assertAsynchronousClose(future);
            assertAsynchronousClose(completionHandlerFuture);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    public void testAsyncClose_read() throws Throwable {
        RegularFile file = regularFile(15);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            ZeroFsAsynchronousFileChannel channel = channel(file, executor, READ, WRITE);

            file.writeLock().lock(); // cause another thread trying to read to block

            // future-returning read
            Future<Integer> future = channel.read(ByteBuffer.allocate(10), 0);

            // completion handler read
            CompletableFuture<Integer> completionHandlerFuture = new CompletableFuture();
            channel.read(ByteBuffer.allocate(10), 0, null, setFuture(completionHandlerFuture));

            // Despite this 10ms sleep to allow plenty of time, it's possible, though very rare, for
            // a
            // race to cause the channel to be closed before the asynchronous calls get to the
            // initial
            // check that the channel is open, causing ClosedChannelException to be thrown rather
            // than
            // AsynchronousCloseException. This is not a problem in practice, just a quirk of how
            // these
            // tests work and that we don't have a way of waiting for the operations to get past
            // that
            // check.
            Thread.sleep(10);

            channel.close();

            assertAsynchronousClose(future);
            assertAsynchronousClose(completionHandlerFuture);
        } finally {
            executor.shutdown();
        }
    }

    private static void checkAsyncRead(AsynchronousFileChannel channel) throws Throwable {
        ByteBuffer buf = buffer("1234567890");
        assertEquals(10, (int) channel.read(buf, 0).get());

        buf.flip();

        CompletableFuture<Integer> future = new CompletableFuture<>();
        channel.read(buf, 0, null, setFuture(future));

        assertEquals(10, future.get(10, SECONDS));
    }

    private static void checkAsyncWrite(AsynchronousFileChannel asyncChannel) throws Throwable {
        ByteBuffer buf = buffer("1234567890");
        assertEquals(10, (int) asyncChannel.write(buf, 0).get());

        buf.flip();
        CompletableFuture<Integer> future = new CompletableFuture();
        asyncChannel.write(buf, 0, null, setFuture(future));

        assertEquals(10, future.get(10, SECONDS));
    }

    private static void checkAsyncLock(AsynchronousFileChannel channel) throws Throwable {
        assertNotNull(channel.lock().get());
        assertNotNull(channel.lock(0, 10, true).get());

        CompletableFuture<FileLock> future = new CompletableFuture<>();
        channel.lock(0, 10, true, null, setFuture(future));

        assertNotNull(future.get(10, SECONDS));
    }

    /**
     * Returns a {@code CompletionHandler} that sets the appropriate result or exception on the given
     * {@code future} on completion.
     */
    private static <T> CompletionHandler<T, Object> setFuture(final CompletableFuture<T> future) {
        return new CompletionHandler<T, Object>() {
            @Override
            public void completed(T result, Object attachment) {
                future.complete(result);
            }

            @Override
            public void failed(Throwable exc, Object attachment) {
                future.completeExceptionally(exc);
            }
        };
    }

    /** Assert that the future fails, with the failure caused by {@code ClosedChannelException}. */
    private static void assertClosed(Future<?> future) throws Throwable {
        try {
            future.get(10, SECONDS);
            fail("ChannelClosedException was not thrown");
        } catch (ExecutionException expected) {
            assertInstanceOf(ClosedChannelException.class, expected.getCause());
        }
    }

    /**
     * Assert that the future fails, with the failure caused by either {@code
     * AsynchronousCloseException} or (rarely) {@code ClosedChannelException}.
     */
    private static void assertAsynchronousClose(Future<?> future) throws Throwable {
        try {
            future.get(10, SECONDS);
            fail("no exception was thrown");
        } catch (ExecutionException expected) {
            Throwable t = expected.getCause();
            if (!(t instanceof AsynchronousCloseException || t instanceof ClosedChannelException)) {
                fail(
                        "expected AsynchronousCloseException (or in rare cases"
                                + " ClosedChannelException); got "
                                + t);
            }
        }
    }
}
