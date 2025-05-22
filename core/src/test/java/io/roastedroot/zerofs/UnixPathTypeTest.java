package io.roastedroot.zerofs;

import static io.roastedroot.zerofs.PathTypeTest.assertParseResult;
import static io.roastedroot.zerofs.PathTypeTest.assertUriRoundTripsCorrectly;
import static io.roastedroot.zerofs.PathTypeTest.fileSystemUri;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.net.URI;
import java.nio.file.InvalidPathException;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link UnixPathType}.
 *
 * @author Colin Decker
 */
public class UnixPathTypeTest {

    @Test
    public void testUnix() {
        PathType unix = PathType.unix();
        assertEquals("/", unix.getSeparator());
        assertEquals("", unix.getOtherSeparators());

        // "//foo/bar" is what will be passed to parsePath if "/", "foo", "bar" is passed to getPath
        PathType.ParseResult path = unix.parsePath("//foo/bar");
        assertParseResult(path, "/", "foo", "bar");
        assertEquals("/foo/bar", unix.toString(path.root(), path.names()));

        PathType.ParseResult path2 = unix.parsePath("foo/bar/");
        assertParseResult(path2, null, "foo", "bar");
        assertEquals("foo/bar", unix.toString(path2.root(), path2.names()));
    }

    @Test
    public void testUnix_toUri() {
        URI fileUri = PathType.unix().toUri(fileSystemUri, "/", new String[] {"foo", "bar"}, false);
        assertEquals("zerofs://foo/foo/bar", fileUri.toString());
        assertEquals("/foo/bar", fileUri.getPath());

        URI directoryUri =
                PathType.unix().toUri(fileSystemUri, "/", new String[] {"foo", "bar"}, true);
        assertEquals("zerofs://foo/foo/bar/", directoryUri.toString());
        assertEquals("/foo/bar/", directoryUri.getPath());

        URI rootUri = PathType.unix().toUri(fileSystemUri, "/", new String[0], true);
        assertEquals("zerofs://foo/", rootUri.toString());
        assertEquals("/", rootUri.getPath());
    }

    @Test
    public void testUnix_toUri_escaping() {
        URI uri = PathType.unix().toUri(fileSystemUri, "/", new String[] {"foo bar"}, false);
        assertEquals("zerofs://foo/foo%20bar", uri.toString());
        assertEquals("/foo%20bar", uri.getRawPath());
        assertEquals("/foo bar", uri.getPath());
    }

    @Test
    public void testUnix_uriRoundTrips() {
        assertUriRoundTripsCorrectly(PathType.unix(), "/");
        assertUriRoundTripsCorrectly(PathType.unix(), "/foo");
        assertUriRoundTripsCorrectly(PathType.unix(), "/foo/bar/baz");
        assertUriRoundTripsCorrectly(PathType.unix(), "/foo/bar baz/one/two");
        assertUriRoundTripsCorrectly(PathType.unix(), "/foo bar");
        assertUriRoundTripsCorrectly(PathType.unix(), "/foo bar/");
        assertUriRoundTripsCorrectly(PathType.unix(), "/foo bar/baz/one");
    }

    @Test
    public void testUnix_illegalCharacters() {
        try {
            PathType.unix().parsePath("/foo/bar\0");
            fail();
        } catch (InvalidPathException expected) {
            assertEquals(8, expected.getIndex());
        }

        try {
            PathType.unix().parsePath("/\u00001/foo");
            fail();
        } catch (InvalidPathException expected) {
            assertEquals(1, expected.getIndex());
        }
    }
}
