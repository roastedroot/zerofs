package io.roastedroot.zerofs;

import static io.roastedroot.zerofs.SystemZeroFsFileSystemProvider.FILE_SYSTEM_KEY;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.ProviderNotFoundException;
import java.nio.file.spi.FileSystemProvider;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ZeroFs {

    /** The URI scheme for the ZeroFs file system ("zerofs"). */
    public static final String URI_SCHEME = "zerofs";

    private static final Logger LOGGER = Logger.getLogger(ZeroFs.class.getName());

    private ZeroFs() {}

    /**
     * Creates a new in-memory file system with a {@linkplain Configuration#forCurrentPlatform()
     * default configuration} appropriate to the current operating system.
     *
     * <p>More specifically, if the operating system is Windows, {@link Configuration#windows()} is
     * used; if the operating system is Mac OS X, {@link Configuration#osX()} is used; otherwise,
     * {@link Configuration#unix()} is used.
     */
    public static FileSystem newFileSystem() {
        return newFileSystem(newRandomFileSystemName());
    }

    /**
     * Creates a new in-memory file system with a {@linkplain Configuration#forCurrentPlatform()
     * default configuration} appropriate to the current operating system.
     *
     * <p>More specifically, if the operating system is Windows, {@link Configuration#windows()} is
     * used; if the operating system is Mac OS X, {@link Configuration#osX()} is used; otherwise,
     * {@link Configuration#unix()} is used.
     *
     * <p>The returned file system uses the given name as the host part of its URI and the URIs of
     * paths in the file system. For example, given the name {@code my-file-system}, the file system's
     * URI will be {@code zerofs://my-file-system} and the URI of the path {@code /foo/bar} will be
     * {@code zerofs://my-file-system/foo/bar}.
     */
    public static FileSystem newFileSystem(String name) {
        return newFileSystem(name, Configuration.forCurrentPlatform());
    }

    /** Creates a new in-memory file system with the given configuration. */
    public static FileSystem newFileSystem(Configuration configuration) {
        return newFileSystem(newRandomFileSystemName(), configuration);
    }

    /**
     * Creates a new in-memory file system with the given configuration.
     *
     * <p>The returned file system uses the given name as the host part of its URI and the URIs of
     * paths in the file system. For example, given the name {@code my-file-system}, the file system's
     * URI will be {@code zerofs://my-file-system} and the URI of the path {@code /foo/bar} will be
     * {@code zerofs://my-file-system/foo/bar}.
     */
    public static FileSystem newFileSystem(String name, Configuration configuration) {
        try {
            URI uri = new URI(URI_SCHEME, name, null, null);
            return newFileSystem(uri, configuration);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    static FileSystem newFileSystem(URI uri, Configuration config) {
        if (!URI_SCHEME.equals(uri.getScheme())) {
            throw new IllegalArgumentException(
                    String.format("uri (%s) must have scheme %s", uri, URI_SCHEME));
        }

        try {
            // Create the FileSystem. It uses ZeroFsFileSystemProvider as its provider, as that is
            // the provider that actually implements the operations needed for Files methods to
            // work.
            ZeroFsFileSystem fileSystem =
                    ZeroFsFileSystems.newFileSystem(
                            ZeroFsFileSystemProvider.instance(), uri, config);

            /*
             * Now, call FileSystems.newFileSystem, passing it the FileSystem we just created. This
             * allows the system-loaded SystemZeroFsFileSystemProvider instance to cache the FileSystem
             * so that methods like Paths.get(URI) work.
             * We do it in this awkward way to avoid issues when the classes in the API (this class
             * and Configuration, for example) are loaded by a different classloader than the one that
             * loads SystemZeroFsFileSystemProvider using ServiceLoader. See
             * https://github.com/google/jimfs/issues/18 for gory details.
             */
            try {
                Map<String, ?> env = Map.of(FILE_SYSTEM_KEY, fileSystem);
                FileSystems.newFileSystem(
                        uri, env, SystemZeroFsFileSystemProvider.class.getClassLoader());
            } catch (ProviderNotFoundException | ServiceConfigurationError ignore) {
                // See the similar catch block below for why we ignore this.
                // We log there rather than here so that there's only typically one such message per
                // VM.
            }

            return fileSystem;
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * The system-loaded instance of {@code SystemZeroFsFileSystemProvider}, or {@code null} if it
     * could not be found or loaded.
     */
    static final FileSystemProvider systemProvider = getSystemZeroFsProvider();

    /**
     * Returns the system-loaded instance of {@code SystemZeroFsFileSystemProvider} or {@code null} if
     * it could not be found or loaded.
     *
     * <p>Like {@link FileSystems#newFileSystem(URI, Map, ClassLoader)}, this method first looks in
     * the list of {@linkplain FileSystemProvider#installedProviders() installed providers} and if not
     * found there, attempts to load it from the {@code ClassLoader} with {@link ServiceLoader}.
     *
     * <p>The idea is that this method should return an instance of the same class (i.e. loaded by the
     * same class loader) as the class whose static cache a {@code ZeroFsFileSystem} instance will be
     * placed in when {@code FileSystems.newFileSystem} is called in {@code ZeroFs.newFileSystem}.
     */
    private static FileSystemProvider getSystemZeroFsProvider() {
        try {
            for (FileSystemProvider provider : FileSystemProvider.installedProviders()) {
                if (provider.getScheme().equals(URI_SCHEME)) {
                    return provider;
                }
            }

            /*
             * ZeroFs.newFileSystem passes SystemZeroFsFileSystemProvider.class.getClassLoader() to
             * FileSystems.newFileSystem so that it will fall back to loading from that classloader if
             * the provider isn't found in the installed providers. So do the same fallback here to ensure
             * that we can remove file systems from the static cache on SystemZeroFsFileSystemProvider if
             * it gets loaded that way.
             */
            ServiceLoader<FileSystemProvider> loader =
                    ServiceLoader.load(
                            FileSystemProvider.class,
                            SystemZeroFsFileSystemProvider.class.getClassLoader());
            for (FileSystemProvider provider : loader) {
                if (provider.getScheme().equals(URI_SCHEME)) {
                    return provider;
                }
            }
        } catch (ProviderNotFoundException | ServiceConfigurationError e) {
            /*
             * This can apparently (https://github.com/google/jimfs/issues/31) occur in an environment
             * where services are not loaded from META-INF/services, such as JBoss/Wildfly. In this
             * case, FileSystems.newFileSystem will most likely fail in the same way when called from
             * ZeroFs.newFileSystem above, and there will be no way to make URI-based methods like
             * Paths.get(URI) work. Rather than making the user completly unable to use ZeroFs, just
             * log this exception and continue.
             *
             * Note: Catching both ProviderNotFoundException, which would occur if no provider matching
             * the "zerofs" URI scheme is found, and ServiceConfigurationError, which can occur if the
             * ServiceLoader finds the META-INF/services entry for ZeroFs (or some other
             * FileSystemProvider!) but is then unable to load that class.
             */
            LOGGER.log(
                    Level.INFO,
                    "An exception occurred when attempting to find the system-loaded"
                        + " FileSystemProvider for ZeroFs. This likely means that your environment"
                        + " does not support loading services via ServiceLoader or is not"
                        + " configured correctly. This does not prevent using ZeroFs, but it will"
                        + " mean that methods that look up via URI such as Paths.get(URI) cannot"
                        + " work.",
                    e);
        }

        return null;
    }

    private static String newRandomFileSystemName() {
        return UUID.randomUUID().toString();
    }
}
