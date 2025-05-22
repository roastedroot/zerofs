package io.roastedroot.zerofs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.ClosedFileSystemException;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link FileSystemState}.
 *
 * @author Colin Decker
 */
public class FileSystemStateTest {

    private final TestRunnable onClose = new TestRunnable();
    private final FakeFileTimeSource fileTimeSource = new FakeFileTimeSource();
    private final FileSystemState state = new FileSystemState(fileTimeSource, onClose);

    @Test
    public void testIsOpen() throws IOException {
        assertTrue(state.isOpen());
        state.close();
        assertFalse(state.isOpen());
    }

    @Test
    public void testCheckOpen() throws IOException {
        state.checkOpen(); // does not throw
        state.close();
        try {
            state.checkOpen();
            fail();
        } catch (ClosedFileSystemException expected) {
        }
    }

    @Test
    public void testNow() {
        assertEquals(fileTimeSource.now(), state.now());
        fileTimeSource.advance(Duration.ofSeconds(1));
        assertEquals(fileTimeSource.now(), state.now());
    }

    @Test
    public void testClose_callsOnCloseRunnable() throws IOException {
        assertEquals(0, onClose.runCount);
        state.close();
        assertEquals(1, onClose.runCount);
    }

    @Test
    public void testClose_multipleTimesDoNothing() throws IOException {
        state.close();
        assertEquals(1, onClose.runCount);
        state.close();
        state.close();
        assertEquals(1, onClose.runCount);
    }

    @Test
    public void testClose_registeredResourceIsClosed() throws IOException {
        TestCloseable resource = new TestCloseable();
        state.register(resource);
        assertFalse(resource.closed);
        state.close();
        assertTrue(resource.closed);
    }

    @Test
    public void testClose_unregisteredResourceIsNotClosed() throws IOException {
        TestCloseable resource = new TestCloseable();
        state.register(resource);
        assertFalse(resource.closed);
        state.unregister(resource);
        state.close();
        assertFalse(resource.closed);
    }

    @Test
    public void testClose_multipleRegisteredResourcesAreClosed() throws IOException {
        List<TestCloseable> resources =
                List.of(new TestCloseable(), new TestCloseable(), new TestCloseable());
        for (TestCloseable resource : resources) {
            state.register(resource);
            assertFalse(resource.closed);
        }
        state.close();
        for (TestCloseable resource : resources) {
            assertTrue(resource.closed);
        }
    }

    @Test
    public void testClose_resourcesThatThrowOnClose() {
        List<TestCloseable> resources =
                List.of(
                        new TestCloseable(),
                        new ThrowsOnClose("a"),
                        new TestCloseable(),
                        new ThrowsOnClose("b"),
                        new ThrowsOnClose("c"),
                        new TestCloseable(),
                        new TestCloseable());
        for (TestCloseable resource : resources) {
            state.register(resource);
            assertFalse(resource.closed);
        }

        try {
            state.close();
            fail();
        } catch (IOException expected) {
            Throwable[] suppressed = expected.getSuppressed();
            assertEquals(2, suppressed.length);
            Set<String> messages =
                    Set.of(
                            expected.getMessage(),
                            suppressed[0].getMessage(),
                            suppressed[1].getMessage());
            assertEquals(Set.of("a", "b", "c"), messages);
        }

        for (TestCloseable resource : resources) {
            assertTrue(resource.closed);
        }
    }

    private static class TestCloseable implements Closeable {

        boolean closed = false;

        @Override
        public void close() throws IOException {
            closed = true;
        }
    }

    private static final class TestRunnable implements Runnable {
        int runCount = 0;

        @Override
        public void run() {
            runCount++;
        }
    }

    private static class ThrowsOnClose extends TestCloseable {

        private final String string;

        private ThrowsOnClose(String string) {
            this.string = string;
        }

        @Override
        public void close() throws IOException {
            super.close();
            throw new IOException(string);
        }
    }
}
