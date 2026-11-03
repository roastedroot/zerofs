package io.roastedroot.zerofs;

import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

/**
 * Tests that {@link SystemZeroFsFileSystemProvider} can reach a {@link ZeroFsFileSystem} loaded by
 * a different class loader (google/jimfs#476).
 */
public class CrossClassLoaderAccessTest {

    @Test
    public void testCrossClassLoaderAccessToPathDoesNotThrow() throws Exception {
        try (URLClassLoader isolatedLoader = createIsolatedClassLoader()) {
            String fsName = "isolated-fs";

            Class<?> zeroFsClass = isolatedLoader.loadClass("io.roastedroot.zerofs.ZeroFs");
            Class<?> configClass = isolatedLoader.loadClass("io.roastedroot.zerofs.Configuration");
            Object unixConfig = configClass.getMethod("unix").invoke(null);
            zeroFsClass
                    .getMethod("newFileSystem", String.class, configClass)
                    .invoke(null, fsName, unixConfig);

            URI isolatedUri = new URI(ZeroFs.URI_SCHEME, fsName, "/test-path", null, null);
            Paths.get(isolatedUri);
        }
    }

    private static URLClassLoader createIsolatedClassLoader() throws Exception {
        URL zeroFsClassesUrl =
                Class.forName("io.roastedroot.zerofs.ZeroFs")
                        .getProtectionDomain()
                        .getCodeSource()
                        .getLocation();

        ClassLoader parentLoader = CrossClassLoaderAccessTest.class.getClassLoader();
        return new URLClassLoader(new URL[] {zeroFsClassesUrl}, parentLoader) {
            @Override
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                if (name.startsWith("io.roastedroot.zerofs.")) {
                    Class<?> c = findLoadedClass(name);
                    if (c != null) {
                        return c;
                    }
                    try {
                        return super.findClass(name);
                    } catch (ClassNotFoundException e) {
                        // fall back to the parent for classes outside the main jar, like tests
                    }
                }
                return super.loadClass(name);
            }
        };
    }
}
