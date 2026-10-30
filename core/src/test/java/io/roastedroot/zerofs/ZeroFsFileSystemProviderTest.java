package io.roastedroot.zerofs;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests for {@link ZeroFsFileSystemProvider} methods that the JDK only calls on newer versions. */
public class ZeroFsFileSystemProviderTest {

    private FileSystem fs;
    private ZeroFsFileSystemProvider provider;

    @BeforeEach
    public void setUp() throws IOException {
        fs = ZeroFs.newFileSystem(Configuration.unix());
        provider = (ZeroFsFileSystemProvider) fs.provider();
        Files.createDirectory(fs.getPath("/foo"));
        Files.createFile(fs.getPath("/foo/file"));
        Files.createSymbolicLink(fs.getPath("/foo/dangling"), fs.getPath("missing"));
        Files.createSymbolicLink(fs.getPath("/foo/loop"), fs.getPath("loop"));
    }

    @AfterEach
    public void tearDown() throws IOException {
        fs.close();
    }

    @Test
    public void testExists() {
        assertTrue(provider.exists(fs.getPath("/foo/file")));
        assertFalse(provider.exists(fs.getPath("/foo/missing")));
        assertFalse(provider.exists(fs.getPath("/missing/file")));
        assertFalse(provider.exists(fs.getPath("/foo/file/child")));
        assertFalse(provider.exists(fs.getPath("/foo/dangling")));
        assertTrue(provider.exists(fs.getPath("/foo/dangling"), NOFOLLOW_LINKS));
        assertFalse(provider.exists(fs.getPath("/foo/loop")));
    }

    @Test
    public void testReadAttributesIfExists() throws IOException {
        assertTrue(
                provider.readAttributesIfExists(fs.getPath("/foo"), BasicFileAttributes.class)
                        .isDirectory());
        assertNull(
                provider.readAttributesIfExists(
                        fs.getPath("/foo/missing"), BasicFileAttributes.class));
        assertNull(
                provider.readAttributesIfExists(
                        fs.getPath("/missing/file"), BasicFileAttributes.class));
        assertTrue(
                provider.readAttributesIfExists(
                                fs.getPath("/foo/dangling"),
                                BasicFileAttributes.class,
                                NOFOLLOW_LINKS)
                        .isSymbolicLink());
        assertThrows(
                IOException.class,
                () ->
                        provider.readAttributesIfExists(
                                fs.getPath("/foo/loop"), BasicFileAttributes.class));
    }
}
