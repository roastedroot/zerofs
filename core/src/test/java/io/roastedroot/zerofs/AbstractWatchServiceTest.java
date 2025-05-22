package io.roastedroot.zerofs;

import static io.roastedroot.zerofs.AbstractWatchService.Key.State.READY;
import static io.roastedroot.zerofs.AbstractWatchService.Key.State.SIGNALLED;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.Watchable;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link AbstractWatchService}.
 *
 * @author Colin Decker
 */
public class AbstractWatchServiceTest {

    private AbstractWatchService watcher;

    @BeforeEach
    public void setUp() throws IOException {
        watcher = new AbstractWatchService() {};
    }

    @Test
    public void testNewWatcher() throws IOException {
        assertTrue(watcher.isOpen());
        assertNull(watcher.poll());
        assertTrue(watcher.queuedKeys().isEmpty());
        watcher.close();
        assertFalse(watcher.isOpen());
    }

    @Test
    public void testRegister() throws IOException {
        Watchable watchable = new StubWatchable();
        AbstractWatchService.Key key = watcher.register(watchable, Set.of(ENTRY_CREATE));
        assertTrue(key.isValid());
        assertTrue(key.pollEvents().isEmpty());
        assertTrue(key.subscribesTo(ENTRY_CREATE));
        assertFalse(key.subscribesTo(ENTRY_DELETE));
        assertEquals(watchable, key.watchable());
        assertEquals(READY, key.state());
    }

    @Test
    public void testPostEvent() throws IOException {
        AbstractWatchService.Key key = watcher.register(new StubWatchable(), Set.of(ENTRY_CREATE));

        AbstractWatchService.Event<Path> event =
                new AbstractWatchService.Event<>(ENTRY_CREATE, 1, null);
        key.post(event);
        key.signal();

        assertEquals(List.of(key), watcher.queuedKeys());

        WatchKey retrievedKey = watcher.poll();
        assertEquals(key, retrievedKey);

        List<WatchEvent<?>> events = retrievedKey.pollEvents();
        assertEquals(1, events.size());
        assertEquals(event, events.get(0));

        // polling should have removed all events
        assertTrue(retrievedKey.pollEvents().isEmpty());
    }

    @Test
    public void testKeyStates() throws IOException {
        AbstractWatchService.Key key = watcher.register(new StubWatchable(), Set.of(ENTRY_CREATE));

        AbstractWatchService.Event<Path> event =
                new AbstractWatchService.Event<>(ENTRY_CREATE, 1, null);
        assertEquals(READY, key.state());
        key.post(event);
        key.signal();
        assertEquals(SIGNALLED, key.state());

        AbstractWatchService.Event<Path> event2 =
                new AbstractWatchService.Event<>(ENTRY_CREATE, 1, null);
        key.post(event2);
        assertEquals(SIGNALLED, key.state());

        // key was not queued twice
        assertEquals(List.of(key), watcher.queuedKeys());
        assertEquals(List.of(event, event2), watcher.poll().pollEvents());

        assertNull(watcher.poll());

        key.post(event);

        // still not added to queue; already signalled
        assertNull(watcher.poll());
        assertEquals(List.of(event), key.pollEvents());

        key.reset();
        assertEquals(READY, key.state());

        key.post(event2);
        key.signal();

        // now that it's reset it can be requeued
        assertEquals(key, watcher.poll());
    }

    @Test
    public void testKeyRequeuedOnResetIfEventsArePending() throws IOException {
        AbstractWatchService.Key key = watcher.register(new StubWatchable(), Set.of(ENTRY_CREATE));
        key.post(new AbstractWatchService.Event<>(ENTRY_CREATE, 1, null));
        key.signal();

        key = (AbstractWatchService.Key) watcher.poll();
        assertTrue(watcher.queuedKeys().isEmpty());

        assertEquals(1, key.pollEvents().size());

        key.post(new AbstractWatchService.Event<>(ENTRY_CREATE, 1, null));
        assertTrue(watcher.queuedKeys().isEmpty());

        key.reset();
        assertEquals(SIGNALLED, key.state());
        assertEquals(1, watcher.queuedKeys().size());
    }

    @Test
    public void testOverflow() throws IOException {
        AbstractWatchService.Key key = watcher.register(new StubWatchable(), Set.of(ENTRY_CREATE));
        for (int i = 0; i < AbstractWatchService.Key.MAX_QUEUE_SIZE + 10; i++) {
            key.post(new AbstractWatchService.Event<>(ENTRY_CREATE, 1, null));
        }
        key.signal();

        List<WatchEvent<?>> events = key.pollEvents();

        assertEquals((AbstractWatchService.Key.MAX_QUEUE_SIZE + 1), events.size());
        for (int i = 0; i < AbstractWatchService.Key.MAX_QUEUE_SIZE; i++) {
            assertEquals(ENTRY_CREATE, events.get(i).kind());
        }

        WatchEvent<?> lastEvent = events.get(AbstractWatchService.Key.MAX_QUEUE_SIZE);
        assertEquals(OVERFLOW, lastEvent.kind());
        assertEquals(10, lastEvent.count());
    }

    @Test
    public void testResetAfterCancelReturnsFalse() throws IOException {
        AbstractWatchService.Key key = watcher.register(new StubWatchable(), Set.of(ENTRY_CREATE));
        key.signal();
        key.cancel();
        assertFalse(key.reset());
    }

    @Test
    public void testClosedWatcher() throws IOException, InterruptedException {
        AbstractWatchService.Key key1 = watcher.register(new StubWatchable(), Set.of(ENTRY_CREATE));
        AbstractWatchService.Key key2 = watcher.register(new StubWatchable(), Set.of(ENTRY_MODIFY));

        assertTrue(key1.isValid());
        assertTrue(key2.isValid());

        watcher.close();

        assertFalse(key1.isValid());
        assertFalse(key2.isValid());
        assertFalse(key1.reset());
        assertFalse(key2.reset());

        try {
            watcher.poll();
            fail();
        } catch (ClosedWatchServiceException expected) {
        }

        try {
            watcher.poll(10, SECONDS);
            fail();
        } catch (ClosedWatchServiceException expected) {
        }

        try {
            watcher.take();
            fail();
        } catch (ClosedWatchServiceException expected) {
        }

        try {
            watcher.register(new StubWatchable(), List.<WatchEvent.Kind<?>>of());
            fail();
        } catch (ClosedWatchServiceException expected) {
        }
    }

    // TODO(cgdecker): Test concurrent use of Watcher

    /** A fake {@link Watchable} for testing. */
    private static final class StubWatchable implements Watchable {

        @Override
        public WatchKey register(
                WatchService watcher, WatchEvent.Kind<?>[] events, WatchEvent.Modifier... modifiers)
                throws IOException {
            return register(watcher, events);
        }

        @Override
        public WatchKey register(WatchService watcher, WatchEvent.Kind<?>... events)
                throws IOException {
            return ((AbstractWatchService) watcher).register(this, Arrays.asList(events));
        }
    }
}
