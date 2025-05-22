package io.roastedroot.zerofs;

import static io.roastedroot.zerofs.PathNormalization.CASE_FOLD_ASCII;
import static io.roastedroot.zerofs.PathNormalization.CASE_FOLD_UNICODE;
import static io.roastedroot.zerofs.PathNormalization.NFC;
import static io.roastedroot.zerofs.PathNormalization.NFD;
import static io.roastedroot.zerofs.WatchServiceConfiguration.polling;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchService;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Iterator;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link Configuration}, {@link Configuration.Builder} and file systems created from
 * them.
 *
 * @author Colin Decker
 */
public class ConfigurationTest {

    @Test
    public void testDefaultUnixConfiguration() {
        Configuration config = Configuration.unix();

        assertEquals(PathType.unix(), config.pathType);
        Iterator<String> rootsIter = config.roots.iterator();
        assertEquals("/", rootsIter.next());
        assertFalse(rootsIter.hasNext());
        assertEquals("/work", config.workingDirectory);
        assertTrue(config.nameCanonicalNormalization.isEmpty());
        assertTrue(config.nameDisplayNormalization.isEmpty());
        assertFalse(config.pathEqualityUsesCanonicalForm);
        assertEquals(8192, config.blockSize);
        assertEquals(4L * 1024 * 1024 * 1024, config.maxSize);
        assertEquals(-1, config.maxCacheSize);
        Iterator<String> attributeViewsIter = config.attributeViews.iterator();
        assertEquals("basic", attributeViewsIter.next());
        assertFalse(attributeViewsIter.hasNext());
        assertTrue(config.attributeProviders.isEmpty());
        assertTrue(config.defaultAttributeValues.isEmpty());
        assertEquals(SystemFileTimeSource.INSTANCE, config.fileTimeSource);
    }

    @Test
    public void testFileSystemForDefaultUnixConfiguration() throws IOException {
        FileSystem fs = ZeroFs.newFileSystem(Configuration.unix());

        Iterator<Path> rootDirsIter = fs.getRootDirectories().iterator();
        assertEquals(fs.getPath("/"), rootDirsIter.next());
        assertFalse(rootDirsIter.hasNext());
        assertEquals(fs.getPath("/work"), fs.getPath("").toRealPath());
        Iterator<FileStore> fsIter = fs.getFileStores().iterator();
        assertEquals(4L * 1024 * 1024 * 1024, fsIter.next().getTotalSpace());
        assertFalse(fsIter.hasNext());

        Iterator<String> attributeViewsIter = fs.supportedFileAttributeViews().iterator();
        assertEquals("basic", attributeViewsIter.next());
        assertFalse(attributeViewsIter.hasNext());

        Files.createFile(fs.getPath("/foo"));
        Files.createFile(fs.getPath("/FOO"));
    }

    @Test
    public void testDefaultOsXConfiguration() {
        Configuration config = Configuration.osX();

        assertEquals(PathType.unix(), config.pathType);
        Iterator<String> rootsIter = config.roots.iterator();
        assertEquals("/", rootsIter.next());
        assertFalse(rootsIter.hasNext());
        assertEquals("/work", config.workingDirectory);
        Iterator<PathNormalization> canonicalPathNormsIter =
                config.nameCanonicalNormalization.iterator();
        assertEquals(Set.of(NFD, CASE_FOLD_ASCII), config.nameCanonicalNormalization);
        Iterator<PathNormalization> nameDisplayNormsIter =
                config.nameDisplayNormalization.iterator();
        assertEquals(NFC, nameDisplayNormsIter.next());
        assertFalse(nameDisplayNormsIter.hasNext());
        assertFalse(config.pathEqualityUsesCanonicalForm);
        assertEquals(8192, config.blockSize);
        assertEquals(4L * 1024 * 1024 * 1024, config.maxSize);
        assertEquals(-1, config.maxCacheSize);
        Iterator<String> attributeViewsIter = config.attributeViews.iterator();
        assertEquals("basic", attributeViewsIter.next());
        assertFalse(attributeViewsIter.hasNext());
        assertTrue(config.attributeProviders.isEmpty());
        assertTrue(config.defaultAttributeValues.isEmpty());
        assertEquals(SystemFileTimeSource.INSTANCE, config.fileTimeSource);
    }

    @Test
    public void testFileSystemForDefaultOsXConfiguration() throws IOException {
        FileSystem fs = ZeroFs.newFileSystem(Configuration.osX());

        Iterator<Path> rootsIter = fs.getRootDirectories().iterator();
        assertEquals(fs.getPath("/"), rootsIter.next());
        assertFalse(rootsIter.hasNext());
        assertEquals(fs.getPath("/work"), fs.getPath("").toRealPath());
        Iterator<FileStore> fsIter = fs.getFileStores().iterator();
        assertEquals(4L * 1024 * 1024 * 1024, fsIter.next().getTotalSpace());
        assertFalse(fsIter.hasNext());
        Iterator<String> attributeViewsIter = fs.supportedFileAttributeViews().iterator();
        assertEquals("basic", attributeViewsIter.next());
        assertFalse(attributeViewsIter.hasNext());

        Files.createFile(fs.getPath("/foo"));

        try {
            Files.createFile(fs.getPath("/FOO"));
            fail();
        } catch (FileAlreadyExistsException expected) {
        }
    }

    @Test
    public void testDefaultWindowsConfiguration() {
        Configuration config = Configuration.windows();

        assertEquals(PathType.windows(), config.pathType);
        Iterator<String> rootsIter = config.roots.iterator();
        assertEquals("C:\\", rootsIter.next());
        assertFalse(rootsIter.hasNext());
        assertEquals("C:\\work", config.workingDirectory);
        Iterator<PathNormalization> canonicalPathNormsIter =
                config.nameCanonicalNormalization.iterator();
        assertEquals(CASE_FOLD_ASCII, canonicalPathNormsIter.next());
        assertFalse(canonicalPathNormsIter.hasNext());
        assertTrue(config.nameDisplayNormalization.isEmpty());
        assertTrue(config.pathEqualityUsesCanonicalForm);
        assertEquals(8192, config.blockSize);
        assertEquals(4L * 1024 * 1024 * 1024, config.maxSize);
        assertEquals(-1, config.maxCacheSize);
        Iterator<String> attributeViewsIter = config.attributeViews.iterator();
        assertEquals("basic", attributeViewsIter.next());
        assertFalse(attributeViewsIter.hasNext());
        assertTrue(config.attributeProviders.isEmpty());
        assertTrue(config.defaultAttributeValues.isEmpty());
        assertEquals(SystemFileTimeSource.INSTANCE, config.fileTimeSource);
    }

    @Test
    public void testFileSystemForDefaultWindowsConfiguration() throws IOException {
        FileSystem fs = ZeroFs.newFileSystem(Configuration.windows());

        Iterator<Path> rootsIter = fs.getRootDirectories().iterator();
        assertEquals(fs.getPath("C:\\"), rootsIter.next());
        assertFalse(rootsIter.hasNext());

        assertEquals(fs.getPath("C:\\work"), fs.getPath("").toRealPath());
        Iterator<FileStore> fsIter = fs.getFileStores().iterator();
        assertEquals(4L * 1024 * 1024 * 1024, fsIter.next().getTotalSpace());
        assertFalse(fsIter.hasNext());
        Iterator<String> attributeViewsIter = fs.supportedFileAttributeViews().iterator();
        assertEquals("basic", attributeViewsIter.next());
        assertFalse(attributeViewsIter.hasNext());

        Files.createFile(fs.getPath("C:\\foo"));

        try {
            Files.createFile(fs.getPath("C:\\FOO"));
            fail();
        } catch (FileAlreadyExistsException expected) {
        }
    }

    @Test
    public void testBuilder() {
        AttributeProvider unixProvider = StandardAttributeProviders.get("unix");

        FileTimeSource fileTimeSource = new FakeFileTimeSource();
        Configuration config =
                Configuration.builder(PathType.unix())
                        .setRoots("/")
                        .setWorkingDirectory("/hello/world")
                        .setNameCanonicalNormalization(NFD, CASE_FOLD_UNICODE)
                        .setNameDisplayNormalization(NFC)
                        .setPathEqualityUsesCanonicalForm(true)
                        .setBlockSize(10)
                        .setMaxSize(100)
                        .setMaxCacheSize(50)
                        .setAttributeViews("basic", "posix")
                        .addAttributeProvider(unixProvider)
                        .setDefaultAttributeValue(
                                "posix:permissions", PosixFilePermissions.fromString("---------"))
                        .setFileTimeSource(fileTimeSource)
                        .build();

        assertEquals(PathType.unix(), config.pathType);
        assertEquals(Set.of("/"), config.roots);
        assertEquals("/hello/world", config.workingDirectory);
        assertEquals(Set.of(NFD, CASE_FOLD_UNICODE), config.nameCanonicalNormalization);
        assertEquals(Set.of(NFC), config.nameDisplayNormalization);
        assertTrue(config.pathEqualityUsesCanonicalForm);
        assertEquals(10, config.blockSize);
        assertEquals(100, config.maxSize);
        assertEquals(50, config.maxCacheSize);
        assertEquals(Set.of("basic", "posix"), config.attributeViews);
        assertEquals(Set.of(unixProvider), config.attributeProviders);
        assertTrue(config.defaultAttributeValues.containsKey("posix:permissions"));
        assertEquals(
                PosixFilePermissions.fromString("---------"),
                config.defaultAttributeValues.get("posix:permissions"));
        assertEquals(fileTimeSource, config.fileTimeSource);
    }

    @Test
    public void testFileSystemForCustomConfiguration() throws IOException {
        FileTimeSource fileTimeSource = new FakeFileTimeSource();
        Configuration config =
                Configuration.builder(PathType.unix())
                        .setRoots("/")
                        .setWorkingDirectory("/hello/world")
                        .setNameCanonicalNormalization(NFD, CASE_FOLD_UNICODE)
                        .setNameDisplayNormalization(NFC)
                        .setPathEqualityUsesCanonicalForm(true)
                        .setBlockSize(10)
                        .setMaxSize(100)
                        .setMaxCacheSize(50)
                        .setAttributeViews("unix")
                        .setDefaultAttributeValue(
                                "posix:permissions", PosixFilePermissions.fromString("---------"))
                        .setFileTimeSource(fileTimeSource)
                        .build();

        FileSystem fs = ZeroFs.newFileSystem(config);

        Iterator<Path> rootsIter = fs.getRootDirectories().iterator();
        assertEquals(fs.getPath("/"), rootsIter.next());
        assertFalse(rootsIter.hasNext());
        assertEquals(fs.getPath("/hello/world"), fs.getPath("").toRealPath());
        Iterator<FileStore> fsIter = fs.getFileStores().iterator();
        assertEquals(100, fsIter.next().getTotalSpace());
        assertFalse(fsIter.hasNext());
        assertEquals(Set.of("basic", "owner", "posix", "unix"), fs.supportedFileAttributeViews());

        Files.createFile(fs.getPath("/foo"));
        assertEquals(
                PosixFilePermissions.fromString("---------"),
                Files.getAttribute(fs.getPath("/foo"), "posix:permissions"));
        assertEquals(fileTimeSource.now(), Files.getAttribute(fs.getPath("/foo"), "creationTime"));

        try {
            Files.createFile(fs.getPath("/FOO"));
            fail();
        } catch (FileAlreadyExistsException expected) {
        }
    }

    @Test
    public void testToBuilder() {
        Configuration config =
                Configuration.unix().toBuilder()
                        .setWorkingDirectory("/hello/world")
                        .setAttributeViews("basic", "posix")
                        .build();

        assertEquals(PathType.unix(), config.pathType);
        assertEquals(Set.of("/"), config.roots);
        assertEquals("/hello/world", config.workingDirectory);
        assertTrue(config.nameCanonicalNormalization.isEmpty());
        assertTrue(config.nameDisplayNormalization.isEmpty());
        assertFalse(config.pathEqualityUsesCanonicalForm);
        assertEquals(8192, config.blockSize);
        assertEquals(4L * 1024 * 1024 * 1024, config.maxSize);
        assertEquals(-1, config.maxCacheSize);
        assertEquals(Set.of("basic", "posix"), config.attributeViews);
        assertTrue(config.attributeProviders.isEmpty());
        assertTrue(config.defaultAttributeValues.isEmpty());
    }

    @Test
    public void testSettingRootsUnsupportedByPathType() {
        assertIllegalRoots(PathType.unix(), "\\");
        assertIllegalRoots(PathType.unix(), "/", "\\");
        assertIllegalRoots(PathType.windows(), "/");
        assertIllegalRoots(PathType.windows(), "C:"); // must have a \ (or a /)
    }

    private static void assertIllegalRoots(PathType type, String first, String... more) {
        try {
            Configuration.builder(type).setRoots(first, more); // wrong root
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testSettingWorkingDirectoryWithRelativePath() {
        try {
            Configuration.unix().toBuilder().setWorkingDirectory("foo/bar");
            fail();
        } catch (IllegalArgumentException expected) {
        }

        try {
            Configuration.windows().toBuilder().setWorkingDirectory("foo\\bar");
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testSettingNormalizationWhenNormalizationAlreadySet() {
        assertIllegalNormalizations(NFC, NFC);
        assertIllegalNormalizations(NFC, NFD);
        assertIllegalNormalizations(CASE_FOLD_ASCII, CASE_FOLD_ASCII);
        assertIllegalNormalizations(CASE_FOLD_ASCII, CASE_FOLD_UNICODE);
    }

    private static void assertIllegalNormalizations(
            PathNormalization first, PathNormalization... more) {
        try {
            Configuration.builder(PathType.unix()).setNameCanonicalNormalization(first, more);
            fail();
        } catch (IllegalArgumentException expected) {
        }

        try {
            Configuration.builder(PathType.unix()).setNameDisplayNormalization(first, more);
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testSetDefaultAttributeValue_illegalAttributeFormat() {
        try {
            Configuration.unix().toBuilder().setDefaultAttributeValue("foo", 1);
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test // how's that for a name?
    public void testCreateFileSystemFromConfigurationWithWorkingDirectoryNotUnderConfiguredRoot() {
        try {
            ZeroFs.newFileSystem(
                    Configuration.windows().toBuilder()
                            .setRoots("C:\\", "D:\\")
                            .setWorkingDirectory("E:\\foo")
                            .build());
            fail();
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testFileSystemWithDefaultWatchService() throws IOException {
        FileSystem fs = ZeroFs.newFileSystem(Configuration.unix());

        WatchService watchService = fs.newWatchService();
        assertInstanceOf(PollingWatchService.class, watchService);

        PollingWatchService pollingWatchService = (PollingWatchService) watchService;
        assertEquals(5, pollingWatchService.interval);
        assertEquals(SECONDS, pollingWatchService.timeUnit);
    }

    @Test
    public void testFileSystemWithCustomWatchServicePollingInterval() throws IOException {
        FileSystem fs =
                ZeroFs.newFileSystem(
                        Configuration.unix().toBuilder()
                                .setWatchServiceConfiguration(polling(10, MILLISECONDS))
                                .build());

        WatchService watchService = fs.newWatchService();
        assertInstanceOf(PollingWatchService.class, watchService);

        PollingWatchService pollingWatchService = (PollingWatchService) watchService;
        assertEquals(10, pollingWatchService.interval);
        assertEquals(MILLISECONDS, pollingWatchService.timeUnit);
    }
}
