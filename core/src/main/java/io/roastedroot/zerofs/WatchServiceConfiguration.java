package io.roastedroot.zerofs;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.nio.file.WatchService;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Configuration for the {@link WatchService} implementation used by a file system.
 *
 * @author Colin Decker
 * @since 1.1
 */
public abstract class WatchServiceConfiguration {

    /** The default configuration that's used if the user doesn't provide anything more specific. */
    static final WatchServiceConfiguration DEFAULT = polling(5, SECONDS);

    /**
     * Returns a configuration for a {@link WatchService} that polls watched directories for changes
     * every {@code interval} of the given {@code timeUnit} (e.g. every 5 {@link TimeUnit#SECONDS
     * seconds}).
     */
    @SuppressWarnings("GoodTime") // should accept a java.time.Duration
    public static WatchServiceConfiguration polling(long interval, TimeUnit timeUnit) {
        return new PollingConfig(interval, timeUnit);
    }

    WatchServiceConfiguration() {}

    /** Creates a new {@link AbstractWatchService} implementation. */
    // return type and parameters of this method subject to change if needed for any future
    // implementations
    abstract AbstractWatchService newWatchService(FileSystemView view, PathService pathService);

    /** Implementation for {@link #polling}. */
    private static final class PollingConfig extends WatchServiceConfiguration {

        private final long interval;
        private final TimeUnit timeUnit;

        private PollingConfig(long interval, TimeUnit timeUnit) {
            if (interval <= 0) {
                throw new IllegalArgumentException(
                        String.format("interval (%s) must be positive", interval));
            }
            this.interval = interval;
            this.timeUnit = Objects.requireNonNull(timeUnit);
        }

        @Override
        AbstractWatchService newWatchService(FileSystemView view, PathService pathService) {
            return new PollingWatchService(view, pathService, view.state(), interval, timeUnit);
        }

        @Override
        public String toString() {
            return "WatchServiceConfiguration.polling(" + interval + ", " + timeUnit + ")";
        }
    }
}
