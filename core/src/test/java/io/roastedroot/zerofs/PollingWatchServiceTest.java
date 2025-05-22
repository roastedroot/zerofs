package io.roastedroot.zerofs;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Tests for {@link PollingWatchService}.
 *
 * @author Colin Decker
 */
public class PollingWatchServiceTest {

    private ZeroFsFileSystem fs;
    private PollingWatchService watcher;

    @BeforeEach
    public void setUp() {
        fs = (ZeroFsFileSystem) ZeroFs.newFileSystem(Configuration.unix());
        watcher =
                new PollingWatchService(
                        fs.getDefaultView(),
                        fs.getPathService(),
                        new FileSystemState(new FakeFileTimeSource(), () -> {}),
                        4,
                        MILLISECONDS);
    }

    @AfterEach
    public void tearDown() throws IOException {
        watcher.close();
        fs.close();
        watcher = null;
        fs = null;
    }

    @Test
    public void testNewWatcher() {
        assertTrue(watcher.isOpen());
        assertFalse(watcher.isPolling());
    }

    @Test
    public void testRegister() throws IOException {
        AbstractWatchService.Key key = watcher.register(createDirectory(), List.of(ENTRY_CREATE));
        assertTrue(key.isValid());

        assertTrue(watcher.isPolling());
    }

    @Test
    public void testRegister_fileDoesNotExist() throws IOException {
        try {
            watcher.register(fs.getPath("/a/b/c"), List.of(ENTRY_CREATE));
            fail();
        } catch (NoSuchFileException expected) {
        }
    }

    @Test
    public void testRegister_fileIsNotDirectory() throws IOException {
        Path path = fs.getPath("/a.txt");
        Files.createFile(path);
        try {
            watcher.register(path, List.of(ENTRY_CREATE));
            fail();
        } catch (NotDirectoryException expected) {
        }
    }

    @Test
    public void testCancellingLastKeyStopsPolling() throws IOException {
        AbstractWatchService.Key key = watcher.register(createDirectory(), List.of(ENTRY_CREATE));
        key.cancel();
        assertFalse(key.isValid());

        assertFalse(watcher.isPolling());

        AbstractWatchService.Key key2 = watcher.register(createDirectory(), List.of(ENTRY_CREATE));
        AbstractWatchService.Key key3 = watcher.register(createDirectory(), List.of(ENTRY_DELETE));

        assertTrue(watcher.isPolling());

        key2.cancel();

        assertTrue(watcher.isPolling());

        key3.cancel();

        assertFalse(watcher.isPolling());
    }

    @Test
    public void testCloseCancelsAllKeysAndStopsPolling() throws IOException {
        AbstractWatchService.Key key1 = watcher.register(createDirectory(), List.of(ENTRY_CREATE));
        AbstractWatchService.Key key2 = watcher.register(createDirectory(), List.of(ENTRY_DELETE));

        assertTrue(key1.isValid());
        assertTrue(key2.isValid());
        assertTrue(watcher.isPolling());

        watcher.close();

        assertFalse(key1.isValid());
        assertFalse(key2.isValid());
        assertFalse(watcher.isPolling());
    }

    @Test
    @Timeout(2)
    public void testWatchForOneEventType() throws IOException, InterruptedException {
        ZeroFsPath path = createDirectory();
        watcher.register(path, List.of(ENTRY_CREATE));

        Files.createFile(path.resolve("foo"));

        assertWatcherHasEvents(
                new AbstractWatchService.Event<>(ENTRY_CREATE, 1, fs.getPath("foo")));

        Files.createFile(path.resolve("bar"));
        Files.createFile(path.resolve("baz"));

        assertWatcherHasEvents(
                new AbstractWatchService.Event<>(ENTRY_CREATE, 1, fs.getPath("bar")),
                new AbstractWatchService.Event<>(ENTRY_CREATE, 1, fs.getPath("baz")));
    }

    @Test
    @Timeout(2)
    public void testWatchForMultipleEventTypes() throws IOException, InterruptedException {
        ZeroFsPath path = createDirectory();
        watcher.register(path, List.of(ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY));

        Files.createDirectory(path.resolve("foo"));
        Files.createFile(path.resolve("bar"));

        assertWatcherHasEvents(
                new AbstractWatchService.Event<>(ENTRY_CREATE, 1, fs.getPath("bar")),
                new AbstractWatchService.Event<>(ENTRY_CREATE, 1, fs.getPath("foo")));

        Files.createFile(path.resolve("baz"));
        Files.delete(path.resolve("bar"));
        Files.createFile(path.resolve("foo/bar"));

        assertWatcherHasEvents(
                new AbstractWatchService.Event<>(ENTRY_CREATE, 1, fs.getPath("baz")),
                new AbstractWatchService.Event<>(ENTRY_DELETE, 1, fs.getPath("bar")),
                new AbstractWatchService.Event<>(ENTRY_MODIFY, 1, fs.getPath("foo")));

        Files.delete(path.resolve("foo/bar"));
        ensureTimeToPoll(); // watcher polls, seeing modification, then polls again, seeing delete
        Files.delete(path.resolve("foo"));

        assertWatcherHasEvents(
                new AbstractWatchService.Event<>(ENTRY_MODIFY, 1, fs.getPath("foo")),
                new AbstractWatchService.Event<>(ENTRY_DELETE, 1, fs.getPath("foo")));

        Files.createDirectories(path.resolve("foo/bar"));

        // polling here may either see just the creation of foo, or may first see the creation of
        // foo
        // and then the creation of foo/bar (modification of foo) since those don't happen
        // atomically
        assertWatcherHasEvents(
                List.<WatchEvent<?>>of(
                        new AbstractWatchService.Event<>(ENTRY_CREATE, 1, fs.getPath("foo"))),
                // or
                List.<WatchEvent<?>>of(
                        new AbstractWatchService.Event<>(ENTRY_CREATE, 1, fs.getPath("foo")),
                        new AbstractWatchService.Event<>(ENTRY_MODIFY, 1, fs.getPath("foo"))));

        Files.delete(path.resolve("foo/bar"));
        Files.delete(path.resolve("foo"));

        // polling here may either just see the deletion of foo, or may first see the deletion of
        // bar
        // (modification of foo) and then the deletion of foo
        assertWatcherHasEvents(
                List.<WatchEvent<?>>of(
                        new AbstractWatchService.Event<>(ENTRY_DELETE, 1, fs.getPath("foo"))),
                // or
                List.<WatchEvent<?>>of(
                        new AbstractWatchService.Event<>(ENTRY_MODIFY, 1, fs.getPath("foo")),
                        new AbstractWatchService.Event<>(ENTRY_DELETE, 1, fs.getPath("foo"))));
    }

    private void assertWatcherHasEvents(WatchEvent<?>... events) throws InterruptedException {
        assertWatcherHasEvents(Arrays.asList(events), List.<WatchEvent<?>>of());
    }

    private void assertWatcherHasEvents(List<WatchEvent<?>> expected, List<WatchEvent<?>> alternate)
            throws InterruptedException {
        ensureTimeToPoll(); // otherwise we could read 1 event but not all the events we're
        // expecting
        WatchKey key = watcher.take();
        List<WatchEvent<?>> keyEvents = key.pollEvents();

        if (keyEvents.size() == expected.size() || alternate.isEmpty()) {
            assertEquals(expected, keyEvents);
        } else {
            assertEquals(alternate, keyEvents);
        }
        key.reset();
    }

    private static void ensureTimeToPoll() {
        try {
            Thread.sleep(40);
        } catch (InterruptedException e) {
            // ignored
        }
    }

    private ZeroFsPath createDirectory() throws IOException {
        ZeroFsPath path = fs.getPath("/" + UUID.randomUUID().toString());
        Files.createDirectory(path);
        return path;
    }
}
