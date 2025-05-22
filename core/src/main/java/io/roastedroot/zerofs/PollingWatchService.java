package io.roastedroot.zerofs;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchService;
import java.nio.file.Watchable;
import java.nio.file.attribute.FileTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Implementation of {@link WatchService} that polls for changes to directories at registered paths.
 *
 * @author Colin Decker
 */
final class PollingWatchService extends AbstractWatchService {

    private static class ZeroFsThreadFactory implements ThreadFactory {
        private final String nameFormat;
        private final boolean daemon;
        private final AtomicInteger count = new AtomicInteger(0);

        public ZeroFsThreadFactory(String nameFormat, boolean daemon) {
            this.nameFormat = nameFormat;
            this.daemon = daemon;
        }

        @Override
        public Thread newThread(Runnable r) {
            String threadName = String.format(nameFormat, count.getAndIncrement());
            Thread thread = new Thread(r, threadName);
            thread.setDaemon(daemon);
            return thread;
        }
    }

    /**
     * Thread factory for polling threads, which should be daemon threads so as not to keep the VM
     * running if the user doesn't close the watch service or the file system.
     */
    private static final ThreadFactory THREAD_FACTORY =
            new ZeroFsThreadFactory("io.roastedroot.zerofs.PollingWatchService-thread-%d", true);

    private final ScheduledExecutorService pollingService =
            Executors.newSingleThreadScheduledExecutor(THREAD_FACTORY);

    /** Map of keys to the most recent directory snapshot for each key. */
    private final ConcurrentMap<Key, Snapshot> snapshots = new ConcurrentHashMap<>();

    private final FileSystemView view;
    private final PathService pathService;
    private final FileSystemState fileSystemState;

    final long interval;
    final TimeUnit timeUnit;

    private ScheduledFuture<?> pollingFuture;

    PollingWatchService(
            FileSystemView view,
            PathService pathService,
            FileSystemState fileSystemState,
            long interval,
            TimeUnit timeUnit) {
        this.view = Objects.requireNonNull(view);
        this.pathService = Objects.requireNonNull(pathService);
        this.fileSystemState = Objects.requireNonNull(fileSystemState);

        if (interval < 0) {
            throw new IllegalArgumentException(
                    String.format("interval (%s) may not be negative", interval));
        }
        this.interval = interval;
        this.timeUnit = Objects.requireNonNull(timeUnit);

        fileSystemState.register(this);
    }

    @Override
    public Key register(Watchable watchable, Iterable<? extends WatchEvent.Kind<?>> eventTypes)
            throws IOException {
        ZeroFsPath path = checkWatchable(watchable);

        Key key = super.register(path, eventTypes);

        Snapshot snapshot = takeSnapshot(path);

        synchronized (this) {
            snapshots.put(key, snapshot);
            if (pollingFuture == null) {
                startPolling();
            }
        }

        return key;
    }

    private ZeroFsPath checkWatchable(Watchable watchable) {
        if (!(watchable instanceof ZeroFsPath) || !isSameFileSystem((Path) watchable)) {
            throw new IllegalArgumentException(
                    "watchable ("
                            + watchable
                            + ") must be a Path "
                            + "associated with the same file system as this watch service");
        }

        return (ZeroFsPath) watchable;
    }

    private boolean isSameFileSystem(Path path) {
        return ((ZeroFsFileSystem) path.getFileSystem()).getDefaultView() == view;
    }

    synchronized boolean isPolling() {
        return pollingFuture != null;
    }

    @Override
    public synchronized void cancelled(Key key) {
        snapshots.remove(key);

        if (snapshots.isEmpty()) {
            stopPolling();
        }
    }

    @Override
    public void close() {
        super.close();

        synchronized (this) {
            // synchronize to ensure no new
            for (Key key : snapshots.keySet()) {
                key.cancel();
            }

            pollingService.shutdown();
            fileSystemState.unregister(this);
        }
    }

    private void startPolling() {
        pollingFuture =
                pollingService.scheduleAtFixedRate(pollingTask, interval, interval, timeUnit);
    }

    private void stopPolling() {
        pollingFuture.cancel(false);
        pollingFuture = null;
    }

    private final Runnable pollingTask =
            new Runnable() {
                @Override
                public void run() {
                    synchronized (PollingWatchService.this) {
                        for (Map.Entry<Key, Snapshot> entry : snapshots.entrySet()) {
                            Key key = entry.getKey();
                            Snapshot previousSnapshot = entry.getValue();

                            ZeroFsPath path = (ZeroFsPath) key.watchable();
                            try {
                                Snapshot newSnapshot = takeSnapshot(path);
                                boolean posted = previousSnapshot.postChanges(newSnapshot, key);
                                entry.setValue(newSnapshot);
                                if (posted) {
                                    key.signal();
                                }
                            } catch (IOException e) {
                                // snapshot failed; assume file does not exist or isn't a directory
                                // and cancel the key
                                key.cancel();
                            }
                        }
                    }
                }
            };

    private Snapshot takeSnapshot(ZeroFsPath path) throws IOException {
        return new Snapshot(view.snapshotModifiedTimes(path));
    }

    /** Snapshot of the state of a directory at a particular moment. */
    private final class Snapshot {

        /** Maps directory entry names to last modified times. */
        private final Map<Name, FileTime> modifiedTimes;

        Snapshot(Map<Name, FileTime> modifiedTimes) {
            this.modifiedTimes = Map.copyOf(modifiedTimes);
        }

        /**
         * Posts events to the given key based on the kinds of events it subscribes to and what events
         * have occurred between this state and the given new state.
         */
        boolean postChanges(Snapshot newState, Key key) {
            boolean changesPosted = false;

            if (key.subscribesTo(ENTRY_CREATE)) {
                Set<Name> created = new HashSet<>(newState.modifiedTimes.keySet());
                created.removeAll(modifiedTimes.keySet());

                for (Name name : created) {
                    key.post(new Event<>(ENTRY_CREATE, 1, pathService.createFileName(name)));
                    changesPosted = true;
                }
            }

            if (key.subscribesTo(ENTRY_DELETE)) {
                Set<Name> deleted = new HashSet<>(modifiedTimes.keySet());
                deleted.removeAll(newState.modifiedTimes.keySet());

                for (Name name : deleted) {
                    key.post(new Event<>(ENTRY_DELETE, 1, pathService.createFileName(name)));
                    changesPosted = true;
                }
            }

            if (key.subscribesTo(ENTRY_MODIFY)) {
                for (Map.Entry<Name, FileTime> entry : modifiedTimes.entrySet()) {
                    Name name = entry.getKey();
                    FileTime modifiedTime = entry.getValue();

                    FileTime newModifiedTime = newState.modifiedTimes.get(name);
                    if (newModifiedTime != null && !modifiedTime.equals(newModifiedTime)) {
                        key.post(new Event<>(ENTRY_MODIFY, 1, pathService.createFileName(name)));
                        changesPosted = true;
                    }
                }
            }

            return changesPosted;
        }
    }
}
