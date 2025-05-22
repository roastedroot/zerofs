package io.roastedroot.zerofs;

import static io.roastedroot.zerofs.PathType.windows;
import static io.roastedroot.zerofs.PathTypeTest.assertParseResult;
import static io.roastedroot.zerofs.PathTypeTest.assertUriRoundTripsCorrectly;
import static io.roastedroot.zerofs.PathTypeTest.fileSystemUri;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.net.URI;
import java.nio.file.InvalidPathException;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link WindowsPathType}.
 *
 * @author Colin Decker
 */
public class WindowsPathTypeTest {

    @Test
    public void testWindows() {
        PathType windows = windows();
        assertEquals("\\", windows.getSeparator());
        assertEquals("/", windows.getOtherSeparators());

        // "C:\\foo\bar" results from "C:\", "foo", "bar" passed to getPath
        PathType.ParseResult path = windows.parsePath("C:\\\\foo\\bar");
        assertParseResult(path, "C:\\", "foo", "bar");
        assertEquals("C:\\foo\\bar", windows.toString(path.root(), path.names()));

        PathType.ParseResult path2 = windows.parsePath("foo/bar/");
        assertParseResult(path2, null, "foo", "bar");
        assertEquals("foo\\bar", windows.toString(path2.root(), path2.names()));

        PathType.ParseResult path3 = windows.parsePath("hello world/foo/bar");
        assertParseResult(path3, null, "hello world", "foo", "bar");
        assertEquals("hello world\\foo\\bar", windows.toString(null, path3.names()));
    }

    @Test
    public void testWindows_relativePathsWithDriveRoot_unsupported() {
        try {
            windows().parsePath("C:");
            fail();
        } catch (InvalidPathException expected) {
        }

        try {
            windows().parsePath("C:foo\\bar");
            fail();
        } catch (InvalidPathException expected) {
        }
    }

    @Test
    public void testWindows_absolutePathOnCurrentDrive_unsupported() {
        try {
            windows().parsePath("\\foo\\bar");
            fail();
        } catch (InvalidPathException expected) {
        }

        try {
            windows().parsePath("\\");
            fail();
        } catch (InvalidPathException expected) {
        }
    }

    @Test
    public void testWindows_uncPaths() {
        PathType windows = windows();
        PathType.ParseResult path = windows.parsePath("\\\\host\\share");
        assertParseResult(path, "\\\\host\\share\\");

        path = windows.parsePath("\\\\HOST\\share\\foo\\bar");
        assertParseResult(path, "\\\\HOST\\share\\", "foo", "bar");

        try {
            windows.parsePath("\\\\");
            fail();
        } catch (InvalidPathException expected) {
            assertEquals("\\\\", expected.getInput());
            assertEquals("UNC path is missing hostname", expected.getReason());
        }

        try {
            windows.parsePath("\\\\host");
            fail();
        } catch (InvalidPathException expected) {
            assertEquals("\\\\host", expected.getInput());
            assertEquals("UNC path is missing sharename", expected.getReason());
        }

        try {
            windows.parsePath("\\\\host\\");
            fail();
        } catch (InvalidPathException expected) {
            assertEquals("\\\\host\\", expected.getInput());
            assertEquals("UNC path is missing sharename", expected.getReason());
        }

        try {
            windows.parsePath("//host");
            fail();
        } catch (InvalidPathException expected) {
            assertEquals("//host", expected.getInput());
            assertEquals("UNC path is missing sharename", expected.getReason());
        }
    }

    @Test
    public void testWindows_illegalNames() {
        try {
            windows().parsePath("foo<bar");
            fail();
        } catch (InvalidPathException expected) {
        }

        try {
            windows().parsePath("foo?");
            fail();
        } catch (InvalidPathException expected) {
        }

        try {
            windows().parsePath("foo ");
            fail();
        } catch (InvalidPathException expected) {
        }

        try {
            windows().parsePath("foo \\bar");
            fail();
        } catch (InvalidPathException expected) {
        }
    }

    @Test
    public void testWindows_toUri_normal() {
        URI fileUri = windows().toUri(fileSystemUri, "C:\\", List.of("foo", "bar"), false);
        assertEquals("zerofs://foo/C:/foo/bar", fileUri.toString());
        assertEquals("/C:/foo/bar", fileUri.getPath());

        URI directoryUri = windows().toUri(fileSystemUri, "C:\\", List.of("foo", "bar"), true);
        assertEquals("zerofs://foo/C:/foo/bar/", directoryUri.toString());
        assertEquals("/C:/foo/bar/", directoryUri.getPath());

        URI rootUri = windows().toUri(fileSystemUri, "C:\\", List.<String>of(), true);
        assertEquals("zerofs://foo/C:/", rootUri.toString());
        assertEquals("/C:/", rootUri.getPath());
    }

    @Test
    public void testWindows_toUri_unc() {
        URI fileUri =
                windows().toUri(fileSystemUri, "\\\\host\\share\\", List.of("foo", "bar"), false);
        assertEquals("zerofs://foo//host/share/foo/bar", fileUri.toString());
        assertEquals("//host/share/foo/bar", fileUri.getPath());

        URI rootUri = windows().toUri(fileSystemUri, "\\\\host\\share\\", List.<String>of(), true);
        assertEquals("zerofs://foo//host/share/", rootUri.toString());
        assertEquals("//host/share/", rootUri.getPath());
    }

    @Test
    public void testWindows_toUri_escaping() {
        URI uri =
                windows()
                        .toUri(
                                fileSystemUri,
                                "C:\\",
                                List.of("Users", "foo", "My Documents"),
                                true);
        assertEquals("zerofs://foo/C:/Users/foo/My%20Documents/", uri.toString());
        assertEquals("/C:/Users/foo/My%20Documents/", uri.getRawPath());
        assertEquals("/C:/Users/foo/My Documents/", uri.getPath());
    }

    @Test
    public void testWindows_uriRoundTrips_normal() {
        assertUriRoundTripsCorrectly(windows(), "C:\\");
        assertUriRoundTripsCorrectly(windows(), "C:\\foo");
        assertUriRoundTripsCorrectly(windows(), "C:\\foo\\bar\\baz");
        assertUriRoundTripsCorrectly(windows(), "C:\\Users\\foo\\My Documents\\");
        assertUriRoundTripsCorrectly(windows(), "C:\\foo bar");
        assertUriRoundTripsCorrectly(windows(), "C:\\foo bar\\baz");
    }

    @Test
    public void testWindows_uriRoundTrips_unc() {
        assertUriRoundTripsCorrectly(windows(), "\\\\host\\share");
        assertUriRoundTripsCorrectly(windows(), "\\\\host\\share\\");
        assertUriRoundTripsCorrectly(windows(), "\\\\host\\share\\foo");
        assertUriRoundTripsCorrectly(windows(), "\\\\host\\share\\foo\\bar\\baz");
        assertUriRoundTripsCorrectly(windows(), "\\\\host\\share\\Users\\foo\\My Documents\\");
        assertUriRoundTripsCorrectly(windows(), "\\\\host\\share\\foo bar");
        assertUriRoundTripsCorrectly(windows(), "\\\\host\\share\\foo bar\\baz");
    }
}
