package io.roastedroot.zerofs;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.PatternSyntaxException;
import org.junit.jupiter.api.Test;

/**
 * Tests a Windows-like file system through the public methods in {@link Files}.
 *
 * @author Colin Decker
 */
public class ZeroFsWindowsLikeFileSystemTest extends AbstractZeroFsIntegrationTest {

    @Override
    protected FileSystem createFileSystem() {
        return ZeroFs.newFileSystem(
                "win",
                Configuration.windows().toBuilder()
                        .setRoots("C:\\", "E:\\")
                        .setAttributeViews("basic", "owner", "dos", "acl", "user")
                        .build());
    }

    @Test
    public void testFileSystem() {
        assertEquals("\\", fs.getSeparator());
        Iterator<Path> rootDirsIter = fs.getRootDirectories().iterator();
        assertEquals(path("C:\\"), rootDirsIter.next());
        assertEquals(path("E:\\"), rootDirsIter.next());
        assertFalse(rootDirsIter.hasNext());
        assertTrue(fs.isOpen());
        assertFalse(fs.isReadOnly());
        assertEquals(
                Set.of("basic", "owner", "dos", "acl", "user"), fs.supportedFileAttributeViews());
        assertInstanceOf(ZeroFsFileSystemProvider.class, fs.provider());
    }

    @Test
    public void testPaths() {
        assertThatPath("C:\\")
                .isAbsolute()
                .and()
                .hasRootComponent("C:\\")
                .and()
                .hasNoNameComponents();
        assertThatPath("foo").isRelative().and().hasNameComponents("foo");
        assertThatPath("foo\\bar").isRelative().and().hasNameComponents("foo", "bar");
        assertThatPath("C:\\foo\\bar\\baz")
                .isAbsolute()
                .and()
                .hasRootComponent("C:\\")
                .and()
                .hasNameComponents("foo", "bar", "baz");
    }

    @Test
    public void testPaths_equalityIsCaseInsensitive() {
        assertEquals(path("c:\\"), path("C:\\"));
        assertEquals(path("FOO"), path("foo"));
    }

    @Test
    public void testPaths_areSortedCaseInsensitive() {
        Path p1 = path("a");
        Path p2 = path("B");
        Path p3 = path("c");
        Path p4 = path("D");

        SortedSet<Path> expected = new TreeSet();
        expected.add(p1);
        expected.add(p2);
        expected.add(p3);
        expected.add(p4);

        SortedSet<Path> provided = new TreeSet();
        provided.add(p3);
        provided.add(p4);
        provided.add(p1);
        provided.add(p2);
        assertEquals(expected, provided);

        // would be p2, p4, p1, p3 if sorting were case sensitive
    }

    @Test
    public void testPaths_withSlash() {
        assertEquals(
                path("foo\\bar"),
                assertThatPath("foo/bar")
                        .isRelative()
                        .and()
                        .hasNameComponents("foo", "bar")
                        .path());
        assertEquals(
                path("C:\\foo\\bar\\baz"),
                assertThatPath("C:/foo/bar/baz")
                        .isAbsolute()
                        .and()
                        .hasRootComponent("C:\\")
                        .and()
                        .hasNameComponents("foo", "bar", "baz")
                        .path());
        assertEquals(
                path("C:\\foo\\bar\\baz"),
                assertThatPath("C:/foo\\bar/baz")
                        .isAbsolute()
                        .and()
                        .hasRootComponent("C:\\")
                        .and()
                        .hasNameComponents("foo", "bar", "baz")
                        .path());
    }

    @Test
    public void testPaths_resolve() {
        assertThatPath(path("C:\\").resolve("foo\\bar"))
                .isAbsolute()
                .and()
                .hasRootComponent("C:\\")
                .and()
                .hasNameComponents("foo", "bar");
        assertThatPath(path("foo\\bar").resolveSibling("baz"))
                .isRelative()
                .and()
                .hasNameComponents("foo", "baz");
        assertThatPath(path("foo\\bar").resolve("C:\\one\\two"))
                .isAbsolute()
                .and()
                .hasRootComponent("C:\\")
                .and()
                .hasNameComponents("one", "two");
    }

    @Test
    public void testPaths_normalize() {
        assertThatPath(path("foo\\bar\\..").normalize())
                .isRelative()
                .and()
                .hasNameComponents("foo");
        assertThatPath(path("foo\\.\\bar\\..\\baz\\test\\.\\..\\stuff").normalize())
                .isRelative()
                .and()
                .hasNameComponents("foo", "baz", "stuff");
        assertThatPath(path("..\\..\\foo\\.\\bar").normalize())
                .isRelative()
                .and()
                .hasNameComponents("..", "..", "foo", "bar");
        assertThatPath(path("foo\\..\\..\\bar").normalize())
                .isRelative()
                .and()
                .hasNameComponents("..", "bar");
        assertThatPath(path("..\\.\\..").normalize())
                .isRelative()
                .and()
                .hasNameComponents("..", "..");
    }

    @Test
    public void testPaths_relativize() {
        assertThatPath(path("C:\\foo\\bar").relativize(path("C:\\foo\\bar\\baz")))
                .isRelative()
                .and()
                .hasNameComponents("baz");
        assertThatPath(path("C:\\foo\\bar\\baz").relativize(path("C:\\foo\\bar")))
                .isRelative()
                .and()
                .hasNameComponents("..");
        assertThatPath(path("C:\\foo\\bar\\baz").relativize(path("C:\\foo\\baz\\bar")))
                .isRelative()
                .and()
                .hasNameComponents("..", "..", "baz", "bar");
        assertThatPath(path("foo\\bar").relativize(path("foo")))
                .isRelative()
                .and()
                .hasNameComponents("..");
        assertThatPath(path("foo").relativize(path("foo\\bar")))
                .isRelative()
                .and()
                .hasNameComponents("bar");

        try {
            Path unused = path("C:\\foo\\bar").relativize(path("bar"));
            fail();
        } catch (IllegalArgumentException expected) {
        }

        try {
            Path unused = path("bar").relativize(path("C:\\foo\\bar"));
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testPaths_startsWith_endsWith() {
        assertTrue(path("C:\\foo\\bar").startsWith("C:\\"));
        assertTrue(path("C:\\foo\\bar").startsWith("C:\\foo"));
        assertTrue(path("C:\\foo\\bar").startsWith("C:\\foo\\bar"));
        assertTrue(path("C:\\foo\\bar").endsWith("bar"));
        assertTrue(path("C:\\foo\\bar").endsWith("foo\\bar"));
        assertTrue(path("C:\\foo\\bar").endsWith("C:\\foo\\bar"));
        assertFalse(path("C:\\foo\\bar").endsWith("C:\\foo"));
        assertFalse(path("C:\\foo\\bar").startsWith("foo\\bar"));
    }

    @Test
    public void testPaths_toAbsolutePath() {
        assertEquals(
                path("C:\\foo\\bar"),
                assertThatPath(path("C:\\foo\\bar").toAbsolutePath())
                        .isAbsolute()
                        .and()
                        .hasRootComponent("C:\\")
                        .and()
                        .hasNameComponents("foo", "bar")
                        .path());

        assertEquals(
                path("C:\\work\\foo\\bar"),
                assertThatPath(path("foo\\bar").toAbsolutePath())
                        .isAbsolute()
                        .and()
                        .hasRootComponent("C:\\")
                        .and()
                        .hasNameComponents("work", "foo", "bar")
                        .path());
    }

    @Test
    public void testPaths_toRealPath() throws IOException {
        Files.createDirectories(path("C:\\foo\\bar"));
        Files.createSymbolicLink(path("C:\\link"), path("C:\\"));

        assertEquals(path("C:\\foo\\bar"), path("C:\\link\\foo\\bar").toRealPath());

        assertEquals(path("C:\\work"), path("").toRealPath());
        assertEquals(path("C:\\work"), path(".").toRealPath());
        assertEquals(path("C:\\"), path("..").toRealPath());
        assertEquals(path("C:\\"), path("..\\..").toRealPath());
        assertEquals(path("C:\\"), path(".\\..\\.\\..").toRealPath());
        assertEquals(path("C:\\"), path(".\\..\\.\\..\\.").toRealPath());
    }

    @Test
    public void testPaths_toUri() {
        assertEquals(URI.create("zerofs://win/C:/"), fs.getPath("C:\\").toUri());
        assertEquals(URI.create("zerofs://win/C:/foo"), fs.getPath("C:\\foo").toUri());
        assertEquals(URI.create("zerofs://win/C:/foo/bar"), fs.getPath("C:\\foo\\bar").toUri());
        assertEquals(URI.create("zerofs://win/C:/work/foo"), fs.getPath("foo").toUri());
        assertEquals(URI.create("zerofs://win/C:/work/foo/bar"), fs.getPath("foo\\bar").toUri());
        assertEquals(URI.create("zerofs://win/C:/work/"), fs.getPath("").toUri());
        assertEquals(URI.create("zerofs://win/C:/work/./.././"), fs.getPath(".\\..\\.").toUri());
    }

    @Test
    public void testPaths_toUri_unc() {
        assertEquals(
                URI.create("zerofs://win//host/share/"), fs.getPath("\\\\host\\share\\").toUri());
        assertEquals(
                URI.create("zerofs://win//host/share/foo"),
                fs.getPath("\\\\host\\share\\foo").toUri());
        assertEquals(
                URI.create("zerofs://win//host/share/foo/bar"),
                fs.getPath("\\\\host\\share\\foo\\bar").toUri());
    }

    @Test
    public void testPaths_getFromUri() {
        assertEquals(fs.getPath("C:\\"), Paths.get(URI.create("zerofs://win/C:/")));
        assertEquals(fs.getPath("C:\\foo"), Paths.get(URI.create("zerofs://win/C:/foo")));
        assertEquals(fs.getPath("C:\\foo bar"), Paths.get(URI.create("zerofs://win/C:/foo%20bar")));
        assertEquals(
                fs.getPath("C:\\foo\\.\\bar"), Paths.get(URI.create("zerofs://win/C:/foo/./bar")));
        assertEquals(fs.getPath("C:\\foo\\bar"), Paths.get(URI.create("zerofs://win/C:/foo/bar/")));
    }

    @Test
    public void testPaths_getFromUri_unc() {
        assertEquals(
                fs.getPath("\\\\host\\share\\"),
                Paths.get(URI.create("zerofs://win//host/share/")));
        assertEquals(
                fs.getPath("\\\\host\\share\\foo"),
                Paths.get(URI.create("zerofs://win//host/share/foo")));
        assertEquals(
                fs.getPath("\\\\host\\share\\foo bar"),
                Paths.get(URI.create("zerofs://win//host/share/foo%20bar")));
        assertEquals(
                fs.getPath("\\\\host\\share\\foo\\.\\bar"),
                Paths.get(URI.create("zerofs://win//host/share/foo/./bar")));
        assertEquals(
                fs.getPath("\\\\host\\share\\foo\\bar"),
                Paths.get(URI.create("zerofs://win//host/share/foo/bar/")));
    }

    @Test
    public void testPathMatchers_glob() {
        assertThatPath("bar").matches("glob:bar");
        assertThatPath("bar").matches("glob:*");
        assertThatPath("C:\\foo").doesNotMatch("glob:*");
        assertThatPath("C:\\foo\\bar").doesNotMatch("glob:*");
        assertThatPath("C:\\foo\\bar").matches("glob:**");
        assertThatPath("C:\\foo\\bar").matches("glob:C:\\\\**");
        assertThatPath("foo\\bar").doesNotMatch("glob:C:\\\\**");
        assertThatPath("C:\\foo\\bar\\baz\\stuff").matches("glob:C:\\\\foo\\\\**");
        assertThatPath("C:\\foo\\bar\\baz\\stuff").matches("glob:C:\\\\**\\\\stuff");
        assertThatPath("C:\\foo").matches("glob:C:\\\\[a-z]*");
        assertThatPath("C:\\Foo").matches("glob:C:\\\\[a-z]*");
        assertThatPath("C:\\foo").matches("glob:C:\\\\[A-Z]*");
        assertThatPath("C:\\Foo").matches("glob:C:\\\\[A-Z]*");
        assertThatPath("C:\\foo\\bar\\baz\\Stuff.java").matches("glob:**\\\\*.java");
        assertThatPath("C:\\foo\\bar\\baz\\Stuff.java").matches("glob:**\\\\*.{java,class}");
        assertThatPath("C:\\foo\\bar\\baz\\Stuff.class").matches("glob:**\\\\*.{java,class}");
        assertThatPath("C:\\foo\\bar\\baz\\Stuff.java").matches("glob:**\\\\*.*");

        try {
            fs.getPathMatcher("glob:**\\*.{java,class");
            fail();
        } catch (PatternSyntaxException expected) {
        }
    }

    @Test
    public void testPathMatchers_glob_alternateSeparators() {
        // only need to test / in the glob pattern; tests above check that / in a path is changed to
        // \ automatically
        assertThatPath("C:\\foo").doesNotMatch("glob:*");
        assertThatPath("C:\\foo\\bar").doesNotMatch("glob:*");
        assertThatPath("C:\\foo\\bar").matches("glob:**");
        assertThatPath("C:\\foo\\bar").matches("glob:C:/**");
        assertThatPath("foo\\bar").doesNotMatch("glob:C:/**");
        assertThatPath("C:\\foo\\bar\\baz\\stuff").matches("glob:C:/foo/**");
        assertThatPath("C:\\foo\\bar\\baz\\stuff").matches("glob:C:/**/stuff");
        assertThatPath("C:\\foo").matches("glob:C:/[a-z]*");
        assertThatPath("C:\\Foo").matches("glob:C:/[a-z]*");
        assertThatPath("C:\\foo").matches("glob:C:/[A-Z]*");
        assertThatPath("C:\\Foo").matches("glob:C:/[A-Z]*");
        assertThatPath("C:\\foo\\bar\\baz\\Stuff.java").matches("glob:**/*.java");
        assertThatPath("C:\\foo\\bar\\baz\\Stuff.java").matches("glob:**/*.{java,class}");
        assertThatPath("C:\\foo\\bar\\baz\\Stuff.class").matches("glob:**/*.{java,class}");
        assertThatPath("C:\\foo\\bar\\baz\\Stuff.java").matches("glob:**/*.*");

        try {
            fs.getPathMatcher("glob:**/*.{java,class");
            fail();
        } catch (PatternSyntaxException expected) {
        }
    }

    @Test
    public void testCreateFileOrDirectory_forNonExistentRootPath_fails() throws IOException {
        try {
            Files.createDirectory(path("Z:\\"));
            fail();
        } catch (IOException expected) {
        }

        try {
            Files.createFile(path("Z:\\"));
            fail();
        } catch (IOException expected) {
        }

        try {
            Files.createSymbolicLink(path("Z:\\"), path("foo"));
            fail();
        } catch (IOException expected) {
        }
    }

    @Test
    public void testCopyFile_toNonExistentRootPath_fails() throws IOException {
        Files.createFile(path("foo"));
        Files.createDirectory(path("bar"));

        try {
            Files.copy(path("foo"), path("Z:\\"));
            fail();
        } catch (IOException expected) {
        }

        try {
            Files.copy(path("bar"), path("Z:\\"));
            fail();
        } catch (IOException expected) {
        }
    }

    @Test
    public void testMoveFile_toNonExistentRootPath_fails() throws IOException {
        Files.createFile(path("foo"));
        Files.createDirectory(path("bar"));

        try {
            Files.move(path("foo"), path("Z:\\"));
            fail();
        } catch (IOException expected) {
        }

        try {
            Files.move(path("bar"), path("Z:\\"));
            fail();
        } catch (IOException expected) {
        }
    }

    @Test
    public void testDelete_directory_cantDeleteRoot() throws IOException {
        // test with E:\ because it is empty
        try {
            Files.delete(path("E:\\"));
            fail();
        } catch (FileSystemException expected) {
            assertEquals("E:\\", expected.getFile());
            assertTrue(expected.getMessage().contains("root"));
        }
    }

    @Test
    public void testCreateFileOrDirectory_forExistingRootPath_fails() throws IOException {
        try {
            Files.createDirectory(path("E:\\"));
            fail();
        } catch (IOException expected) {
        }

        try {
            Files.createFile(path("E:\\"));
            fail();
        } catch (IOException expected) {
        }

        try {
            Files.createSymbolicLink(path("E:\\"), path("foo"));
            fail();
        } catch (IOException expected) {
        }
    }

    @Test
    public void testCopyFile_toExistingRootPath_fails() throws IOException {
        Files.createFile(path("foo"));
        Files.createDirectory(path("bar"));

        try {
            Files.copy(path("foo"), path("E:\\"), REPLACE_EXISTING);
            fail();
        } catch (IOException expected) {
        }

        try {
            Files.copy(path("bar"), path("E:\\"), REPLACE_EXISTING);
            fail();
        } catch (IOException expected) {
        }
    }

    @Test
    public void testMoveFile_toExistingRootPath_fails() throws IOException {
        Files.createFile(path("foo"));
        Files.createDirectory(path("bar"));

        try {
            Files.move(path("foo"), path("E:\\"), REPLACE_EXISTING);
            fail();
        } catch (IOException expected) {
        }

        try {
            Files.move(path("bar"), path("E:\\"), REPLACE_EXISTING);
            fail();
        } catch (IOException expected) {
        }
    }

    @Test
    public void testMove_rootDirectory_fails() throws IOException {
        try {
            Files.move(path("E:\\"), path("Z:\\"));
            fail();
        } catch (FileSystemException expected) {
        }

        try {
            Files.move(path("E:\\"), path("C:\\bar"));
            fail();
        } catch (FileSystemException expected) {
        }
    }
}
