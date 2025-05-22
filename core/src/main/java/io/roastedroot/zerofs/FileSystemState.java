package io.roastedroot.zerofs;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.ClosedFileSystemException;
import java.nio.file.attribute.FileTime;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Object that manages the open/closed state of a file system, ensuring that all open resources are
 * closed when the file system is closed and that file system methods throw an exception when the
 * file system has been closed.
 *
 * @author Colin Decker
 */
final class FileSystemState implements Closeable {

    private final Set<Closeable> resources =
            Collections.newSetFromMap(new ConcurrentHashMap<Closeable, Boolean>());
    private final FileTimeSource fileTimeSource;
    private final Runnable onClose;

    private final AtomicBoolean open = new AtomicBoolean(true);

    /** Count of resources currently in the process of being registered. */
    private final AtomicInteger registering = new AtomicInteger();

    FileSystemState(FileTimeSource fileTimeSource, Runnable onClose) {
        this.fileTimeSource = Objects.requireNonNull(fileTimeSource);
        this.onClose = Objects.requireNonNull(onClose);
    }

    /** Returns whether or not the file system is open. */
    public boolean isOpen() {
        return open.get();
    }

    /**
     * Checks that the file system is open, throwing {@link ClosedFileSystemException} if it is not.
     */
    public void checkOpen() {
        if (!open.get()) {
            throw new ClosedFileSystemException();
        }
    }

    /**
     * Registers the given resource to be closed when the file system is closed. Should be called when
     * the resource is opened.
     */
    public <C extends Closeable> C register(C resource) {
        // Initial open check to avoid incrementing registering if we already know it's closed.
        // This is to prevent any possibility of a weird pathalogical situation where the do/while
        // loop in close() keeps looping as register() is called repeatedly from multiple threads.
        checkOpen();

        registering.incrementAndGet();
        try {
            // Need to check again after marking registration in progress to avoid a potential race.
            // (close() could have run completely between the first checkOpen() and
            // registering.incrementAndGet().)
            checkOpen();
            resources.add(resource);
            return resource;
        } finally {
            registering.decrementAndGet();
        }
    }

    /** Unregisters the given resource. Should be called when the resource is closed. */
    public void unregister(Closeable resource) {
        resources.remove(resource);
    }

    /** Returns the current {@link FileTime}. */
    public FileTime now() {
        return fileTimeSource.now();
    }

    /**
     * Closes the file system, runs the {@code onClose} callback and closes all registered resources.
     */
    @Override
    public void close() throws IOException {
        if (open.compareAndSet(true, false)) {
            onClose.run();

            Throwable thrown = null;
            do {
                for (Closeable resource : resources) {
                    try {
                        resource.close();
                    } catch (Throwable e) {
                        if (thrown == null) {
                            thrown = e;
                        } else {
                            thrown.addSuppressed(e);
                        }
                    } finally {
                        // ensure the resource is removed even if it doesn't remove itself when
                        // closed
                        resources.remove(resource);
                    }
                }

                // It's possible for a thread registering a resource to register that resource after
                // open
                // has been set to false and even after we've looped through and closed all the
                // resources.
                // Since registering must be incremented *before* checking the state of open,
                // however,
                // when we reach this point in that situation either the register call is still in
                // progress
                // (registering > 0) or the new resource has been successfully added (resources not
                // empty).
                // In either case, we just need to repeat the loop until there are no more register
                // calls
                // in progress (no new calls can start and no resources left to close.
            } while (registering.get() > 0 || !resources.isEmpty());
            if (thrown != null) {
                if (thrown instanceof IOException) {
                    throw (IOException) thrown;
                }
                if (thrown instanceof RuntimeException) {
                    throw (RuntimeException) thrown;
                }
            }
        }
    }
}
