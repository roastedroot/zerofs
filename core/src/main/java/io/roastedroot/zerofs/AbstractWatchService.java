package io.roastedroot.zerofs;

import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.Watchable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Abstract implementation of {@link WatchService}. Provides the means for registering and managing
 * keys but does not handle actually watching. Subclasses should implement the means of watching
 * watchables, posting events to registered keys and queueing keys with the service by signalling
 * them.
 *
 * @author Colin Decker
 */
abstract class AbstractWatchService implements WatchService {

    private final BlockingQueue<WatchKey> queue = new LinkedBlockingQueue<>();
    private final WatchKey poison = new Key(this, null, Set.<WatchEvent.Kind<?>>of());

    private final AtomicBoolean open = new AtomicBoolean(true);

    /**
     * Registers the given watchable with this service, returning a new watch key for it. This
     * implementation just checks that the service is open and creates a key; subclasses may override
     * it to do other things as well.
     */
    public Key register(Watchable watchable, Iterable<? extends WatchEvent.Kind<?>> eventTypes)
            throws IOException {
        checkOpen();
        return new Key(this, watchable, eventTypes);
    }

    /** Returns whether or not this watch service is open. */
    public boolean isOpen() {
        return open.get();
    }

    /** Enqueues the given key if the watch service is open; does nothing otherwise. */
    final void enqueue(Key key) {
        if (isOpen()) {
            queue.add(key);
        }
    }

    /** Called when the given key is cancelled. Does nothing by default. */
    public void cancelled(Key key) {}

    List<WatchKey> queuedKeys() {
        return List.copyOf(queue);
    }

    @Override
    public WatchKey poll() {
        checkOpen();
        return check(queue.poll());
    }

    @Override
    public WatchKey poll(long timeout, TimeUnit unit) throws InterruptedException {
        checkOpen();
        return check(queue.poll(timeout, unit));
    }

    @Override
    public WatchKey take() throws InterruptedException {
        checkOpen();
        return check(queue.take());
    }

    /** Returns the given key, throwing an exception if it's the poison. */
    private WatchKey check(WatchKey key) {
        if (key == poison) {
            // ensure other blocking threads get the poison
            queue.offer(poison);
            throw new ClosedWatchServiceException();
        }
        return key;
    }

    /** Checks that the watch service is open, throwing {@link ClosedWatchServiceException} if not. */
    protected final void checkOpen() {
        if (!open.get()) {
            throw new ClosedWatchServiceException();
        }
    }

    @Override
    public void close() {
        if (open.compareAndSet(true, false)) {
            queue.clear();
            queue.offer(poison);
        }
    }

    /** A basic implementation of {@link WatchEvent}. */
    static final class Event<T> implements WatchEvent<T> {

        private final Kind<T> kind;
        private final int count;

        private final T context;

        public Event(Kind<T> kind, int count, T context) {
            this.kind = Objects.requireNonNull(kind);
            if (count < 0) {
                throw new IllegalArgumentException(
                        String.format("count (%s) must be non-negative", count));
            }
            this.count = count;
            this.context = context;
        }

        @Override
        public Kind<T> kind() {
            return kind;
        }

        @Override
        public int count() {
            return count;
        }

        @Override
        public T context() {
            return context;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Event) {
                Event<?> other = (Event<?>) obj;
                return kind().equals(other.kind())
                        && count() == other.count()
                        && Objects.equals(context(), other.context());
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(kind(), count(), context());
        }

        @Override
        public String toString() {
            return "Event{" + "kind=" + kind + ", count=" + count + ", context=" + context + '}';
        }
    }

    /** Implementation of {@link WatchKey} for an {@link AbstractWatchService}. */
    static final class Key implements WatchKey {

        static final int MAX_QUEUE_SIZE = 256;

        private static WatchEvent<Object> overflowEvent(int count) {
            return new Event<>(OVERFLOW, count, null);
        }

        private final AbstractWatchService watcher;
        private final Watchable watchable;
        private final Set<WatchEvent.Kind<?>> subscribedTypes;

        private final AtomicReference<State> state = new AtomicReference<>(State.READY);
        private final AtomicBoolean valid = new AtomicBoolean(true);
        private final AtomicInteger overflow = new AtomicInteger();

        private final BlockingQueue<WatchEvent<?>> events =
                new ArrayBlockingQueue<>(MAX_QUEUE_SIZE);

        public Key(
                AbstractWatchService watcher,
                Watchable watchable,
                Iterable<? extends WatchEvent.Kind<?>> subscribedTypes) {
            this.watcher = Objects.requireNonNull(watcher);
            this.watchable = watchable; // nullable for Watcher poison
            this.subscribedTypes = new HashSet<>();
            subscribedTypes.forEach(this.subscribedTypes::add);
        }

        /** Gets the current state of this key, State.READY or SIGNALLED. */
        State state() {
            return state.get();
        }

        /** Gets whether or not this key is subscribed to the given type of event. */
        public boolean subscribesTo(WatchEvent.Kind<?> eventType) {
            return subscribedTypes.contains(eventType);
        }

        /**
         * Posts the given event to this key. After posting one or more events, {@link #signal()} must
         * be called to cause the key to be enqueued with the watch service.
         */
        public void post(WatchEvent<?> event) {
            if (!events.offer(event)) {
                overflow.incrementAndGet();
            }
        }

        /**
         * Sets the state to SIGNALLED and enqueues this key with the watcher if it was previously in
         * the READY state.
         */
        public void signal() {
            if (state.getAndSet(State.SIGNALLED) == State.READY) {
                watcher.enqueue(this);
            }
        }

        @Override
        public boolean isValid() {
            return watcher.isOpen() && valid.get();
        }

        @Override
        public List<WatchEvent<?>> pollEvents() {
            // note: it's correct to be able to retrieve more events from a key without calling
            // reset()
            // reset() is ONLY for "returning" the key to the watch service to potentially be
            // retrieved by
            // another thread when you're finished with it
            List<WatchEvent<?>> result = new ArrayList<>(events.size());
            events.drainTo(result);
            int overflowCount = overflow.getAndSet(0);
            if (overflowCount != 0) {
                result.add(overflowEvent(overflowCount));
            }
            return Collections.unmodifiableList(result);
        }

        @Override
        public boolean reset() {
            // calling reset() multiple times without polling events would cause key to be placed in
            // watcher queue multiple times, but not much that can be done about that
            if (isValid() && state.compareAndSet(State.SIGNALLED, State.READY)) {
                // requeue if events are pending
                if (!events.isEmpty()) {
                    signal();
                }
            }

            return isValid();
        }

        @Override
        public void cancel() {
            valid.set(false);
            watcher.cancelled(this);
        }

        @Override
        public Watchable watchable() {
            return watchable;
        }

        enum State {
            READY,
            SIGNALLED
        }
    }
}
