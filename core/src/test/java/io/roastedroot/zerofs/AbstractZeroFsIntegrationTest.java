package io.roastedroot.zerofs;

import static io.roastedroot.zerofs.PathSubject.paths;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/** @author Colin Decker */
public abstract class AbstractZeroFsIntegrationTest {

    protected FileSystem fs;

    @BeforeEach
    public void setUp() throws IOException {
        fs = createFileSystem();
    }

    @AfterEach
    public void tearDown() throws IOException {
        fs.close();
    }

    /** Creates the file system to use in the tests. */
    protected abstract FileSystem createFileSystem();

    // helpers

    protected Path path(String first, String... more) {
        return fs.getPath(first, more);
    }

    protected Object getFileKey(String path, LinkOption... options) throws IOException {
        return Files.getAttribute(path(path), "fileKey", options);
    }

    protected PathSubject assertThatPath(String path, LinkOption... options) {
        return assertThatPath(path(path), options);
    }

    protected static PathSubject assertThatPath(Path path, LinkOption... options) {
        PathSubject subject = paths(path);
        if (options.length != 0) {
            subject = subject.noFollowLinks();
        }
        return subject;
    }

    /** Tester for testing changes in file times. */
    protected static final class FileTimeTester {

        private final Path path;

        private FileTime accessTime;
        private FileTime modifiedTime;

        FileTimeTester(Path path) throws IOException {
            this.path = path;

            BasicFileAttributes attrs = attrs();
            accessTime = attrs.lastAccessTime();
            modifiedTime = attrs.lastModifiedTime();
        }

        private BasicFileAttributes attrs() throws IOException {
            return Files.readAttributes(path, BasicFileAttributes.class);
        }

        public void assertAccessTimeChanged() throws IOException {
            FileTime t = attrs().lastAccessTime();
            assertNotEquals(accessTime, t);
            accessTime = t;
        }

        public void assertAccessTimeDidNotChange() throws IOException {
            FileTime t = attrs().lastAccessTime();
            assertEquals(accessTime, t);
        }

        public void assertModifiedTimeChanged() throws IOException {
            FileTime t = attrs().lastModifiedTime();
            assertNotEquals(modifiedTime, t);
            modifiedTime = t;
        }

        public void assertModifiedTimeDidNotChange() throws IOException {
            FileTime t = attrs().lastModifiedTime();
            assertEquals(modifiedTime, t);
        }
    }
}
