package io.roastedroot.zerofs;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.spi.FileSystemProvider;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests behavior when user code loads ZeroFs in a separate class loader from the system class loader
 * (which is what {@link FileSystemProvider#installedProviders()} uses to load {@link
 * FileSystemProvider}s as services from the classpath).
 *
 * @author Colin Decker
 */
public class ClassLoaderTest {

    @Test
    public void separateClassLoader() throws Exception {
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader systemLoader = ClassLoader.getSystemClassLoader();

        List<URL> classpathUrls = new ArrayList<>();
        if (contextLoader != null && contextLoader instanceof URLClassLoader) {
            for (URL url : ((URLClassLoader) contextLoader).getURLs()) {
                classpathUrls.add(url);
            }
        } else if (systemLoader != null && systemLoader instanceof URLClassLoader) {
            for (URL url : ((URLClassLoader) systemLoader).getURLs()) {
                classpathUrls.add(url);
            }
        } else {
            // Try to extract classpath URLs in Java 9+ using the system property
            String classpath = System.getProperty("java.class.path");
            String[] paths = classpath.split(System.getProperty("path.separator"));

            for (String path : paths) {
                try {
                    classpathUrls.add(new java.io.File(path).toURI().toURL());
                } catch (MalformedURLException e) {
                    fail();
                }
            }
        }

        ClassLoader separateLoader =
                new URLClassLoader(
                        classpathUrls.toArray(URL[]::new),
                        systemLoader.getParent()); // either null or the boostrap loader

        Thread.currentThread().setContextClassLoader(separateLoader);
        try {
            Class<?> thisClass = separateLoader.loadClass(getClass().getName());
            Method createFileSystem = thisClass.getDeclaredMethod("createFileSystem");

            // First, the call to ZeroFs.newFileSystem in createFileSystem needs to succeed
            Object fs = createFileSystem.invoke(null);

            // Next, some sanity checks:

            // The file system is a ZeroFsFileSystem
            assertEquals("io.roastedroot.zerofs.ZeroFsFileSystem", fs.getClass().getName());

            // But it is not seen as an instance of ZeroFsFileSystem here because it was loaded
            // by a
            // different ClassLoader
            assertFalse(fs instanceof ZeroFsFileSystem);

            // But it should be an instance of FileSystem regardless, which is the important
            // thing.
            assertInstanceOf(FileSystem.class, fs);

            // And normal file operations should work on it despite its provenance from a
            // different
            // ClassLoader
            writeAndRead((FileSystem) fs, "bar.txt", "blah blah");

            // And for the heck of it, test the contents of the file that was created in
            // createFileSystem too
            assertEquals(
                    "blah", Files.readAllLines(((FileSystem) fs).getPath("foo.txt"), UTF_8).get(0));
        } finally {
            Thread.currentThread().setContextClassLoader(contextLoader);
        }
    }

    /**
     * This method is really just testing that {@code ZeroFs.newFileSystem()} succeeds. Without special
     * handling, when the system class loader loads our {@code FileSystemProvider} implementation as a
     * service and this code (the user code) is loaded in a separate class loader, the system-loaded
     * provider won't see the instance of {@code Configuration} we give it as being an instance of the
     * {@code Configuration} it's expecting (they're completely separate classes) and creation of the
     * file system will fail.
     */
    public static FileSystem createFileSystem() throws IOException {
        FileSystem fs = ZeroFs.newFileSystem(Configuration.unix());

        // Just some random operations to verify that basic things work on the created file system.
        writeAndRead(fs, "foo.txt", "blah");

        return fs;
    }

    private static void writeAndRead(FileSystem fs, String path, String text) throws IOException {
        Path p = fs.getPath(path);
        Files.write(p, List.of(text), UTF_8);
        List<String> lines = Files.readAllLines(p, UTF_8);
        assertEquals(text, lines.get(0));
    }
}
