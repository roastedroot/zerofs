package io.roastedroot.zerofs;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.io.IOException;
import java.nio.file.WatchService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link WatchServiceConfiguration}.
 *
 * @author Colin Decker
 */
public class WatchServiceConfigurationTest {

    private ZeroFsFileSystem fs;

    @BeforeEach
    public void setUp() {
        // kind of putting the cart before the horse maybe, but it's the easiest way to get valid
        // instances of both a FileSystemView and a PathService
        fs = (ZeroFsFileSystem) ZeroFs.newFileSystem();
    }

    @AfterEach
    public void tearDown() throws IOException {
        fs.close();
        fs = null;
    }

    @Test
    public void testPollingConfig() {
        WatchServiceConfiguration polling = WatchServiceConfiguration.polling(50, MILLISECONDS);
        WatchService watchService =
                polling.newWatchService(fs.getDefaultView(), fs.getPathService());
        assertInstanceOf(PollingWatchService.class, watchService);

        PollingWatchService pollingWatchService = (PollingWatchService) watchService;
        assertEquals(50, pollingWatchService.interval);
        assertEquals(MILLISECONDS, pollingWatchService.timeUnit);
    }

    @Test
    public void testDefaultConfig() {
        WatchService watchService =
                WatchServiceConfiguration.DEFAULT.newWatchService(
                        fs.getDefaultView(), fs.getPathService());
        assertInstanceOf(PollingWatchService.class, watchService);

        PollingWatchService pollingWatchService = (PollingWatchService) watchService;
        assertEquals(5, pollingWatchService.interval);
        assertEquals(SECONDS, pollingWatchService.timeUnit);
    }
}
