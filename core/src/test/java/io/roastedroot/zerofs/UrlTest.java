package io.roastedroot.zerofs;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests that {@link URL} instances can be created and used from jimfs URIs.
 *
 * @author Colin Decker
 */
public class UrlTest {

    private final FileSystem fs = ZeroFs.newFileSystem(Configuration.unix());
    private Path path = fs.getPath("foo");

    @Test
    public void creatUrl() throws MalformedURLException {
        URL url = path.toUri().toURL();
        assertNotNull(url);
    }

    @Test
    public void readFromUrl() throws IOException {
        Files.write(path, List.of("Hello World"), UTF_8);

        URL url = path.toUri().toURL();
        String result;
        try (InputStream in = url.openStream()) {
            byte[] buffer = in.readAllBytes();
            result = new String(buffer, UTF_8);
        }

        assertEquals("Hello World" + System.getProperty("line.separator"), result);
    }

    @Test
    public void readDirectoryContents() throws IOException {
        Files.createDirectory(path);
        Files.createFile(path.resolve("a.txt"));
        Files.createFile(path.resolve("b.txt"));
        Files.createDirectory(path.resolve("c"));

        URL url = path.toUri().toURL();
        String result;
        try (InputStream in = url.openStream()) {
            byte[] buffer = in.readAllBytes();
            result = new String(buffer, UTF_8);
        }

        assertEquals("a.txt\nb.txt\nc\n", result);
    }

    @Test
    public void headers() throws IOException {
        byte[] bytes = {1, 2, 3};
        Files.write(path, bytes);
        FileTime lastModified = Files.getLastModifiedTime(path);

        URL url = path.toUri().toURL();
        URLConnection conn = url.openConnection();

        // read header fields directly
        assertTrue(conn.getHeaderFields().containsKey("content-length"));
        assertEquals(List.of("3"), conn.getHeaderFields().get("content-length"));
        assertTrue(conn.getHeaderFields().containsKey("content-type"));
        assertEquals(
                List.of("application/octet-stream"), conn.getHeaderFields().get("content-type"));

        if (lastModified != null) {
            assertTrue(conn.getHeaderFields().containsKey("last-modified"));
            assertEquals(3, conn.getHeaderFields().size());
        } else {
            assertEquals(2, conn.getHeaderFields().size());
        }

        // use the specific methods for reading the expected headers
        assertEquals(Files.size(path), conn.getContentLengthLong());
        assertEquals("application/octet-stream", conn.getContentType());

        if (lastModified != null) {
            // The HTTP date format does not include milliseconds, which means that the last
            // modified time
            // returned from the connection may not be exactly the same as that of the file system
            // itself.
            // The difference should less than 1000ms though, and should never be greater.
            long difference = lastModified.toMillis() - conn.getLastModified();
            assertTrue(difference >= 0L && difference < 1000L);
        } else {
            assertEquals(0L, conn.getLastModified());
        }
    }

    @Test
    public void contentType() throws IOException {
        path = fs.getPath("foo.txt");
        Files.write(path, List.of("Hello World"), UTF_8);

        URL url = path.toUri().toURL();
        URLConnection conn = url.openConnection();

        // Should be text/plain, but this is entirely dependent on the installed FileTypeDetectors
        String detectedContentType = Files.probeContentType(path);
        if (detectedContentType == null) {
            assertEquals("application/octet-stream", conn.getContentType());
        } else {
            assertEquals(detectedContentType, conn.getContentType());
        }
    }
}
