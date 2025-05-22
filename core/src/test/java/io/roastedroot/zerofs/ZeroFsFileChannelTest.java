package io.roastedroot.zerofs;

import static io.roastedroot.zerofs.TestUtils.assertNotEquals;
import static io.roastedroot.zerofs.TestUtils.buffer;
import static io.roastedroot.zerofs.TestUtils.bytes;
import static io.roastedroot.zerofs.TestUtils.regularFile;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.FileLockInterruptionException;
import java.nio.channels.NonReadableChannelException;
import java.nio.channels.NonWritableChannelException;
import java.nio.file.OpenOption;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Most of the behavior of {@link ZeroFsFileChannel} is handled by the {@link RegularFile}
 * implementations, so the thorough tests of that are in {@link RegularFileTest}. This mostly tests
 * interactions with the file and channel positions.
 *
 * @author Colin Decker
 */
public class ZeroFsFileChannelTest {

    private static FileChannel channel(RegularFile file, OpenOption... options) throws IOException {
        Set<OpenOption> opts = new TreeSet<>();
        for (OpenOption oo : options) {
            opts.add(oo);
        }
        return new ZeroFsFileChannel(
                file,
                Options.getOptionsForChannel(opts),
                new FileSystemState(new FakeFileTimeSource(), () -> {}));
    }

    @Test
    public void testPosition() throws IOException {
        FileChannel channel = channel(regularFile(10), READ);
        assertEquals(0, channel.position());
        assertSame(channel, channel.position(100));
        assertEquals(100, channel.position());
    }

    @Test
    public void testSize() throws IOException {
        RegularFile file = regularFile(10);
        FileChannel channel = channel(file, READ);

        assertEquals(10, channel.size());

        file.write(10, new byte[90], 0, 90);
        assertEquals(100, channel.size());
    }

    @Test
    public void testRead() throws IOException {
        RegularFile file = regularFile(20);
        FileChannel channel = channel(file, READ);
        assertEquals(0, channel.position());

        ByteBuffer buf = buffer("1234567890");
        ByteBuffer buf2 = buffer("123457890");
        assertEquals(10, channel.read(buf));
        assertEquals(10, channel.position());

        buf.flip();
        assertEquals(10, channel.read(new ByteBuffer[] {buf, buf2}));
        assertEquals(20, channel.position());

        buf.flip();
        buf2.flip();
        file.write(20, new byte[10], 0, 10);
        assertEquals(10, channel.read(new ByteBuffer[] {buf, buf2}, 0, 2));
        assertEquals(30, channel.position());

        buf.flip();
        assertEquals(10, channel.read(buf, 5));
        assertEquals(30, channel.position());

        buf.flip();
        assertEquals(-1, channel.read(buf));
        assertEquals(30, channel.position());
    }

    @Test
    public void testWrite() throws IOException {
        RegularFile file = regularFile(0);
        FileChannel channel = channel(file, WRITE);
        assertEquals(0, channel.position());

        ByteBuffer buf = buffer("1234567890");
        ByteBuffer buf2 = buffer("1234567890");
        assertEquals(10, channel.write(buf));
        assertEquals(10, channel.position());

        buf.flip();
        assertEquals(20, channel.write(new ByteBuffer[] {buf, buf2}));
        assertEquals(30, channel.position());

        buf.flip();
        buf2.flip();
        assertEquals(20, channel.write(new ByteBuffer[] {buf, buf2}, 0, 2));
        assertEquals(50, channel.position());

        buf.flip();
        assertEquals(10, channel.write(buf, 5));
        assertEquals(50, channel.position());
    }

    @Test
    public void testAppend() throws IOException {
        RegularFile file = regularFile(0);
        FileChannel channel = channel(file, WRITE, APPEND);
        assertEquals(0, channel.position());

        ByteBuffer buf = buffer("1234567890");
        ByteBuffer buf2 = buffer("1234567890");

        assertEquals(10, channel.write(buf));
        assertEquals(10, channel.position());

        buf.flip();
        channel.position(0);
        assertEquals(20, channel.write(new ByteBuffer[] {buf, buf2}));
        assertEquals(30, channel.position());

        buf.flip();
        buf2.flip();
        channel.position(0);
        assertEquals(20, channel.write(new ByteBuffer[] {buf, buf2}, 0, 2));
        assertEquals(50, channel.position());

        buf.flip();
        channel.position(0);
        assertEquals(10, channel.write(buf, 5));
        assertEquals(60, channel.position());

        buf.flip();
        channel.position(0);
        assertEquals(10, channel.transferFrom(new ByteBufferChannel(buf), 0, 10));
        assertEquals(70, channel.position());
    }

    @Test
    public void testTransferTo() throws IOException {
        RegularFile file = regularFile(10);
        FileChannel channel = channel(file, READ);

        ByteBufferChannel writeChannel = new ByteBufferChannel(buffer("1234567890"));
        assertEquals(10, channel.transferTo(0, 100, writeChannel));
        assertEquals(0, channel.position());
    }

    @Test
    public void testTransferFrom() throws IOException {
        RegularFile file = regularFile(0);
        FileChannel channel = channel(file, WRITE);

        ByteBufferChannel readChannel = new ByteBufferChannel(buffer("1234567890"));
        assertEquals(10, channel.transferFrom(readChannel, 0, 100));
        assertEquals(0, channel.position());
    }

    @Test
    public void testTruncate() throws IOException {
        RegularFile file = regularFile(10);
        FileChannel channel = channel(file, WRITE);

        channel.truncate(10); // no resize, >= size
        assertEquals(10, file.size());
        channel.truncate(11); // no resize, > size
        assertEquals(10, file.size());
        channel.truncate(5); // resize down to 5
        assertEquals(5, file.size());

        channel.position(20);
        channel.truncate(10);
        assertEquals(10, channel.position());
        channel.truncate(2);
        assertEquals(2, channel.position());
    }

    @Test
    public void testFileTimeUpdates() throws IOException {
        RegularFile file = regularFile(10);
        FakeFileTimeSource fileTimeSource = new FakeFileTimeSource();
        FileChannel channel =
                new ZeroFsFileChannel(
                        file,
                        Set.<OpenOption>of(READ, WRITE),
                        new FileSystemState(fileTimeSource, () -> {}));

        // accessedTime
        FileTime accessTime = file.getLastAccessTime();
        fileTimeSource.advance(Duration.ofMillis(2));

        channel.read(ByteBuffer.allocate(10));
        assertNotEquals(accessTime, file.getLastAccessTime());

        accessTime = file.getLastAccessTime();
        fileTimeSource.advance(Duration.ofMillis(2));

        channel.read(ByteBuffer.allocate(10), 0);
        assertNotEquals(accessTime, file.getLastAccessTime());

        accessTime = file.getLastAccessTime();
        fileTimeSource.advance(Duration.ofMillis(2));

        channel.read(new ByteBuffer[] {ByteBuffer.allocate(10)});
        assertNotEquals(accessTime, file.getLastAccessTime());

        accessTime = file.getLastAccessTime();
        fileTimeSource.advance(Duration.ofMillis(2));

        channel.read(new ByteBuffer[] {ByteBuffer.allocate(10)}, 0, 1);
        assertNotEquals(accessTime, file.getLastAccessTime());

        accessTime = file.getLastAccessTime();
        fileTimeSource.advance(Duration.ofMillis(2));

        channel.transferTo(0, 10, new ByteBufferChannel(10));
        assertNotEquals(accessTime, file.getLastAccessTime());

        // modified
        FileTime modifiedTime = file.getLastModifiedTime();
        fileTimeSource.advance(Duration.ofMillis(2));

        channel.write(ByteBuffer.allocate(10));
        assertNotEquals(modifiedTime, file.getLastModifiedTime());

        modifiedTime = file.getLastModifiedTime();
        fileTimeSource.advance(Duration.ofMillis(2));

        channel.write(ByteBuffer.allocate(10), 0);
        assertNotEquals(modifiedTime, file.getLastModifiedTime());

        modifiedTime = file.getLastModifiedTime();
        fileTimeSource.advance(Duration.ofMillis(2));

        channel.write(new ByteBuffer[] {ByteBuffer.allocate(10)});
        assertNotEquals(modifiedTime, file.getLastModifiedTime());

        modifiedTime = file.getLastModifiedTime();
        fileTimeSource.advance(Duration.ofMillis(2));

        channel.write(new ByteBuffer[] {ByteBuffer.allocate(10)}, 0, 1);
        assertNotEquals(modifiedTime, file.getLastModifiedTime());

        modifiedTime = file.getLastModifiedTime();
        fileTimeSource.advance(Duration.ofMillis(2));

        channel.truncate(0);
        assertNotEquals(modifiedTime, file.getLastModifiedTime());

        modifiedTime = file.getLastModifiedTime();
        fileTimeSource.advance(Duration.ofMillis(2));

        channel.transferFrom(new ByteBufferChannel(10), 0, 10);
        assertNotEquals(modifiedTime, file.getLastModifiedTime());
    }

    @Test
    public void testClose() throws IOException {
        FileChannel channel = channel(regularFile(0), READ, WRITE);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        assertTrue(channel.isOpen());
        channel.close();
        assertFalse(channel.isOpen());

        try {
            channel.position();
            fail();
        } catch (ClosedChannelException expected) {
        }

        try {
            channel.position(0);
            fail();
        } catch (ClosedChannelException expected) {
        }

        try {
            channel.lock();
            fail();
        } catch (ClosedChannelException expected) {
        }

        try {
            channel.lock(0, 10, true);
            fail();
        } catch (ClosedChannelException expected) {
        }

        try {
            channel.tryLock();
            fail();
        } catch (ClosedChannelException expected) {
        }

        try {
            channel.tryLock(0, 10, true);
            fail();
        } catch (ClosedChannelException expected) {
        }

        try {
            channel.force(true);
            fail();
        } catch (ClosedChannelException expected) {
        }

        try {
            channel.write(buffer("111"));
            fail();
        } catch (ClosedChannelException expected) {
        }

        try {
            channel.write(buffer("111"), 10);
            fail();
        } catch (ClosedChannelException expected) {
        }

        try {
            channel.write(new ByteBuffer[] {buffer("111"), buffer("111")});
            fail();
        } catch (ClosedChannelException expected) {
        }

        try {
            channel.write(new ByteBuffer[] {buffer("111"), buffer("111")}, 0, 2);
            fail();
        } catch (ClosedChannelException expected) {
        }

        try {
            channel.transferFrom(new ByteBufferChannel(bytes("1111")), 0, 4);
            fail();
        } catch (ClosedChannelException expected) {
        }

        try {
            channel.truncate(0);
            fail();
        } catch (ClosedChannelException expected) {
        }

        try {
            channel.read(buffer("111"));
            fail();
        } catch (ClosedChannelException expected) {
        }

        try {
            channel.read(buffer("111"), 10);
            fail();
        } catch (ClosedChannelException expected) {
        }

        try {
            channel.read(new ByteBuffer[] {buffer("111"), buffer("111")});
            fail();
        } catch (ClosedChannelException expected) {
        }

        try {
            channel.read(new ByteBuffer[] {buffer("111"), buffer("111")}, 0, 2);
            fail();
        } catch (ClosedChannelException expected) {
        }

        try {
            channel.transferTo(0, 10, new ByteBufferChannel(buffer("111")));
            fail();
        } catch (ClosedChannelException expected) {
        }

        executor.shutdown();
    }

    @Test
    public void testWritesInReadOnlyMode() throws IOException {
        FileChannel channel = channel(regularFile(0), READ);

        try {
            channel.write(buffer("111"));
            fail();
        } catch (NonWritableChannelException expected) {
        }

        try {
            channel.write(buffer("111"), 10);
            fail();
        } catch (NonWritableChannelException expected) {
        }

        try {
            channel.write(new ByteBuffer[] {buffer("111"), buffer("111")});
            fail();
        } catch (NonWritableChannelException expected) {
        }

        try {
            channel.write(new ByteBuffer[] {buffer("111"), buffer("111")}, 0, 2);
            fail();
        } catch (NonWritableChannelException expected) {
        }

        try {
            channel.transferFrom(new ByteBufferChannel(bytes("1111")), 0, 4);
            fail();
        } catch (NonWritableChannelException expected) {
        }

        try {
            channel.truncate(0);
            fail();
        } catch (NonWritableChannelException expected) {
        }

        try {
            channel.lock(0, 10, false);
            fail();
        } catch (NonWritableChannelException expected) {
        }
    }

    @Test
    public void testReadsInWriteOnlyMode() throws IOException {
        FileChannel channel = channel(regularFile(0), WRITE);

        try {
            channel.read(buffer("111"));
            fail();
        } catch (NonReadableChannelException expected) {
        }

        try {
            channel.read(buffer("111"), 10);
            fail();
        } catch (NonReadableChannelException expected) {
        }

        try {
            channel.read(new ByteBuffer[] {buffer("111"), buffer("111")});
            fail();
        } catch (NonReadableChannelException expected) {
        }

        try {
            channel.read(new ByteBuffer[] {buffer("111"), buffer("111")}, 0, 2);
            fail();
        } catch (NonReadableChannelException expected) {
        }

        try {
            channel.transferTo(0, 10, new ByteBufferChannel(buffer("111")));
            fail();
        } catch (NonReadableChannelException expected) {
        }

        try {
            channel.lock(0, 10, true);
            fail();
        } catch (NonReadableChannelException expected) {
        }
    }

    @Test
    public void testPositionNegative() throws IOException {
        FileChannel channel = channel(regularFile(0), READ, WRITE);

        try {
            channel.position(-1);
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testTruncateNegative() throws IOException {
        FileChannel channel = channel(regularFile(0), READ, WRITE);

        try {
            channel.truncate(-1);
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testWriteNegative() throws IOException {
        FileChannel channel = channel(regularFile(0), READ, WRITE);

        try {
            channel.write(buffer("111"), -1);
            fail();
        } catch (IllegalArgumentException expected) {
        }

        ByteBuffer[] bufs = {buffer("111"), buffer("111")};
        try {
            channel.write(bufs, -1, 10);
            fail();
        } catch (IndexOutOfBoundsException expected) {
        }

        try {
            channel.write(bufs, 0, -1);
            fail();
        } catch (IndexOutOfBoundsException expected) {
        }
    }

    @Test
    public void testReadNegative() throws IOException {
        FileChannel channel = channel(regularFile(0), READ, WRITE);

        try {
            channel.read(buffer("111"), -1);
            fail();
        } catch (IllegalArgumentException expected) {
        }

        ByteBuffer[] bufs = {buffer("111"), buffer("111")};
        try {
            channel.read(bufs, -1, 10);
            fail();
        } catch (IndexOutOfBoundsException expected) {
        }

        try {
            channel.read(bufs, 0, -1);
            fail();
        } catch (IndexOutOfBoundsException expected) {
        }
    }

    @Test
    public void testTransferToNegative() throws IOException {
        FileChannel channel = channel(regularFile(0), READ, WRITE);

        try {
            channel.transferTo(-1, 0, new ByteBufferChannel(10));
            fail();
        } catch (IllegalArgumentException expected) {
        }

        try {
            channel.transferTo(0, -1, new ByteBufferChannel(10));
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testTransferFromNegative() throws IOException {
        FileChannel channel = channel(regularFile(0), READ, WRITE);

        try {
            channel.transferFrom(new ByteBufferChannel(10), -1, 0);
            fail();
        } catch (IllegalArgumentException expected) {
        }

        try {
            channel.transferFrom(new ByteBufferChannel(10), 0, -1);
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testLockNegative() throws IOException {
        FileChannel channel = channel(regularFile(0), READ, WRITE);

        try {
            channel.lock(-1, 10, true);
            fail();
        } catch (IllegalArgumentException expected) {
        }

        try {
            channel.lock(0, -1, true);
            fail();
        } catch (IllegalArgumentException expected) {
        }

        try {
            channel.tryLock(-1, 10, true);
            fail();
        } catch (IllegalArgumentException expected) {
        }

        try {
            channel.tryLock(0, -1, true);
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }

    //  TODO: skip only null checks
    //  @Test
    //  public void testNullPointerExceptions() throws IOException {
    //    FileChannel channel = channel(regularFile(100), READ, WRITE);
    //
    ////    NullPointerTester tester = new NullPointerTester();
    ////    tester.testAllPublicInstanceMethods(channel);
    //  }
    // attempted to be ported
    //  @Test
    //  void testAllPublicInstanceMethodsForNulls() throws IOException {
    //    FileChannel instance = channel(regularFile(100), READ, WRITE);
    //
    //    Class<?> clazz = instance.getClass();
    //
    //    for (Method method : clazz.getMethods()) {
    //      if (method.getDeclaringClass() == Object.class) continue; // skip Object methods
    //      if (Modifier.isStatic(method.getModifiers())) continue;
    //
    //      int paramCount = method.getParameterCount();
    //      Object[] nullArgs = new Object[paramCount];
    //      var illegalArgument = assertThrows(IllegalArgumentException.class, () ->
    // method.invoke(instance, nullArgs), "method: " + method);
    //      assertInstanceOf(NullPointerException.class, illegalArgument.getCause());
    //    }
    //  }

    @Test
    public void testLock() throws IOException {
        FileChannel channel = channel(regularFile(10), READ, WRITE);

        assertNotNull(channel.lock());
        assertNotNull(channel.lock(0, 10, false));
        assertNotNull(channel.lock(0, 10, true));

        assertNotNull(channel.tryLock());
        assertNotNull(channel.tryLock(0, 10, false));
        assertNotNull(channel.tryLock(0, 10, true));

        FileLock lock = channel.lock();
        assertTrue(lock.isValid());
        lock.release();
        assertFalse(lock.isValid());
    }

    @Test
    public void testAsynchronousClose() throws Exception {
        RegularFile file = regularFile(10);
        final FileChannel channel = channel(file, READ, WRITE);

        file.writeLock().lock(); // ensure all operations on the channel will block

        ExecutorService executor = Executors.newCachedThreadPool();

        CountDownLatch latch = new CountDownLatch(BLOCKING_OP_COUNT);
        List<Future<?>> futures = queueAllBlockingOperations(channel, executor, latch);

        // wait for all the threads to have started running
        latch.await();
        // then ensure time for operations to start blocking
        Thread.sleep(20);
        // Uninterruptibles.sleepUninterruptibly(20, MILLISECONDS);

        // close channel on this thread
        channel.close();

        // the blocking operations are running on different threads, so they all get
        // AsynchronousCloseException
        for (Future<?> future : futures) {
            try {
                future.get();
                fail();
            } catch (ExecutionException expected) {
                assertInstanceOf(
                        AsynchronousCloseException.class,
                        expected.getCause(),
                        "blocking thread exception");
                //        assertWithMessage("blocking thread exception")
                //            .that(expected.getCause())
                //            .isInstanceOf(AsynchronousCloseException.class);
            }
        }
    }

    @Test
    public void testCloseByInterrupt() throws Exception {
        RegularFile file = regularFile(10);
        final FileChannel channel = channel(file, READ, WRITE);

        file.writeLock().lock(); // ensure all operations on the channel will block

        ExecutorService executor = Executors.newCachedThreadPool();

        final CountDownLatch threadStartLatch = new CountDownLatch(1);
        final AtomicReference<Throwable> interruptException = new AtomicReference<>();

        // This thread, being the first to run, will be blocking on the interruptible lock (the byte
        // file's write lock) and as such will be interrupted properly... the other threads will be
        // blocked on the lock that guards the position field and the specification that only one
        // method
        // on the channel will be in progress at a time. That lock is not interruptible, so we must
        // interrupt this thread.
        Thread thread =
                new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                threadStartLatch.countDown();
                                try {
                                    channel.write(ByteBuffer.allocate(20));
                                    interruptException.set(null);
                                } catch (Throwable e) {
                                    interruptException.set(e);
                                }
                            }
                        });
        thread.start();

        // let the thread start running
        threadStartLatch.await();
        // then ensure time for thread to start blocking on the write lock
        Thread.sleep(10);
        // Uninterruptibles.sleepUninterruptibly(10, MILLISECONDS);

        CountDownLatch blockingStartLatch = new CountDownLatch(BLOCKING_OP_COUNT);
        List<Future<?>> futures = queueAllBlockingOperations(channel, executor, blockingStartLatch);

        // wait for all blocking threads to start
        blockingStartLatch.await();
        // then ensure time for the operations to start blocking
        Thread.sleep(20);
        // Uninterruptibles.sleepUninterruptibly(20, MILLISECONDS);

        // interrupting this blocking thread closes the channel and makes all the other threads
        // throw AsynchronousCloseException... the operation on this thread should throw
        // ClosedByInterruptException
        thread.interrupt();

        // get the exception that caused the interrupted operation to terminate
        Thread.sleep(200);
        assertInstanceOf(
                ClosedByInterruptException.class,
                interruptException.get(),
                "interrupted thread exception");
        //    assertWithMessage("interrupted thread exception")
        //        .that(interruptException.get(200, MILLISECONDS))
        //        .isInstanceOf(ClosedByInterruptException.class);

        // check that each other thread got AsynchronousCloseException (since the interrupt, on a
        // different thread, closed the channel)
        for (Future<?> future : futures) {
            try {
                future.get();
                fail();
            } catch (ExecutionException expected) {
                assertInstanceOf(
                        AsynchronousCloseException.class,
                        expected.getCause(),
                        "blocking thread exception");
                //        assertWithMessage("blocking thread exception")
                //            .that(expected.getCause())
                //            .isInstanceOf(AsynchronousCloseException.class);
            }
        }
    }

    private static final int BLOCKING_OP_COUNT = 10;

    /**
     * Queues blocking operations on the channel in separate threads using the given executor. The
     * given latch should have a count of BLOCKING_OP_COUNT to allow the caller wants to wait for all
     * threads to start executing.
     */
    private List<Future<?>> queueAllBlockingOperations(
            final FileChannel channel, ExecutorService executor, final CountDownLatch startLatch) {
        List<Future<?>> futures = new ArrayList<>();

        final ByteBuffer buffer = ByteBuffer.allocate(10);
        futures.add(
                executor.submit(
                        new Callable<Object>() {
                            @Override
                            public Object call() throws Exception {
                                startLatch.countDown();
                                channel.write(buffer);
                                return null;
                            }
                        }));

        futures.add(
                executor.submit(
                        new Callable<Object>() {
                            @Override
                            public Object call() throws Exception {
                                startLatch.countDown();
                                channel.write(buffer, 0);
                                return null;
                            }
                        }));

        futures.add(
                executor.submit(
                        new Callable<Object>() {
                            @Override
                            public Object call() throws Exception {
                                startLatch.countDown();
                                channel.write(new ByteBuffer[] {buffer, buffer});
                                return null;
                            }
                        }));

        futures.add(
                executor.submit(
                        new Callable<Object>() {
                            @Override
                            public Object call() throws Exception {
                                startLatch.countDown();
                                channel.write(new ByteBuffer[] {buffer, buffer, buffer}, 0, 2);
                                return null;
                            }
                        }));

        futures.add(
                executor.submit(
                        new Callable<Object>() {
                            @Override
                            public Object call() throws Exception {
                                startLatch.countDown();
                                channel.read(buffer);
                                return null;
                            }
                        }));

        futures.add(
                executor.submit(
                        new Callable<Object>() {
                            @Override
                            public Object call() throws Exception {
                                startLatch.countDown();
                                channel.read(buffer, 0);
                                return null;
                            }
                        }));

        futures.add(
                executor.submit(
                        new Callable<Object>() {
                            @Override
                            public Object call() throws Exception {
                                startLatch.countDown();
                                channel.read(new ByteBuffer[] {buffer, buffer});
                                return null;
                            }
                        }));

        futures.add(
                executor.submit(
                        new Callable<Object>() {
                            @Override
                            public Object call() throws Exception {
                                startLatch.countDown();
                                channel.read(new ByteBuffer[] {buffer, buffer, buffer}, 0, 2);
                                return null;
                            }
                        }));

        futures.add(
                executor.submit(
                        new Callable<Object>() {
                            @Override
                            public Object call() throws Exception {
                                startLatch.countDown();
                                channel.transferTo(0, 10, new ByteBufferChannel(buffer));
                                return null;
                            }
                        }));

        futures.add(
                executor.submit(
                        new Callable<Object>() {
                            @Override
                            public Object call() throws Exception {
                                startLatch.countDown();
                                channel.transferFrom(new ByteBufferChannel(buffer), 0, 10);
                                return null;
                            }
                        }));

        return futures;
    }

    /**
     * Tests that the methods on the default FileChannel that support InterruptibleChannel behavior
     * also support it on ZeroFsFileChannel, by just interrupting the thread before calling the method.
     */
    @Test
    public void testInterruptedThreads() throws IOException {
        final ByteBuffer buf = ByteBuffer.allocate(10);
        final ByteBuffer[] bufArray = {buf};

        assertClosedByInterrupt(
                new FileChannelMethod() {
                    @Override
                    public void call(FileChannel channel) throws IOException {
                        channel.size();
                    }
                });

        assertClosedByInterrupt(
                new FileChannelMethod() {
                    @Override
                    public void call(FileChannel channel) throws IOException {
                        channel.position();
                    }
                });

        assertClosedByInterrupt(
                new FileChannelMethod() {
                    @Override
                    public void call(FileChannel channel) throws IOException {
                        channel.position(0);
                    }
                });

        assertClosedByInterrupt(
                new FileChannelMethod() {
                    @Override
                    public void call(FileChannel channel) throws IOException {
                        channel.write(buf);
                    }
                });

        assertClosedByInterrupt(
                new FileChannelMethod() {
                    @Override
                    public void call(FileChannel channel) throws IOException {
                        channel.write(bufArray, 0, 1);
                    }
                });

        assertClosedByInterrupt(
                new FileChannelMethod() {
                    @Override
                    public void call(FileChannel channel) throws IOException {
                        channel.read(buf);
                    }
                });

        assertClosedByInterrupt(
                new FileChannelMethod() {
                    @Override
                    public void call(FileChannel channel) throws IOException {
                        channel.read(bufArray, 0, 1);
                    }
                });

        assertClosedByInterrupt(
                new FileChannelMethod() {
                    @Override
                    public void call(FileChannel channel) throws IOException {
                        channel.write(buf, 0);
                    }
                });

        assertClosedByInterrupt(
                new FileChannelMethod() {
                    @Override
                    public void call(FileChannel channel) throws IOException {
                        channel.read(buf, 0);
                    }
                });

        assertClosedByInterrupt(
                new FileChannelMethod() {
                    @Override
                    public void call(FileChannel channel) throws IOException {
                        channel.transferTo(0, 1, channel(regularFile(10), READ, WRITE));
                    }
                });

        assertClosedByInterrupt(
                new FileChannelMethod() {
                    @Override
                    public void call(FileChannel channel) throws IOException {
                        channel.transferFrom(channel(regularFile(10), READ, WRITE), 0, 1);
                    }
                });

        assertClosedByInterrupt(
                new FileChannelMethod() {
                    @Override
                    public void call(FileChannel channel) throws IOException {
                        channel.force(true);
                    }
                });

        assertClosedByInterrupt(
                new FileChannelMethod() {
                    @Override
                    public void call(FileChannel channel) throws IOException {
                        channel.truncate(0);
                    }
                });

        assertClosedByInterrupt(
                new FileChannelMethod() {
                    @Override
                    public void call(FileChannel channel) throws IOException {
                        channel.lock(0, 1, true);
                    }
                });

        // tryLock() does not handle interruption
        // map() always throws UOE; it doesn't make sense for it to try to handle interruption
    }

    private interface FileChannelMethod {
        void call(FileChannel channel) throws IOException;
    }

    /**
     * Asserts that when the given operation is run on an interrupted thread, {@code
     * ClosedByInterruptException} is thrown, the channel is closed and the thread is no longer
     * interrupted.
     */
    private static void assertClosedByInterrupt(FileChannelMethod method) throws IOException {
        FileChannel channel = channel(regularFile(10), READ, WRITE);
        Thread.currentThread().interrupt();
        try {
            method.call(channel);
            fail(
                    "expected the method to throw ClosedByInterruptException or "
                            + "FileLockInterruptionException");
        } catch (ClosedByInterruptException | FileLockInterruptionException expected) {
            assertFalse(channel.isOpen(), "expected the channel to be closed");
            assertTrue(Thread.interrupted(), "expected the thread to still be interrupted");
        } finally {
            Thread.interrupted(); // ensure the thread isn't interrupted when this method returns
        }
    }
}
