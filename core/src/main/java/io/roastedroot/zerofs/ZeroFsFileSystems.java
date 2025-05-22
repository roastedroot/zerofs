package io.roastedroot.zerofs;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Initializes and configures new file system instances.
 *
 * @author Colin Decker
 */
final class ZeroFsFileSystems {

    private ZeroFsFileSystems() {}

    private static final Runnable DO_NOTHING =
            new Runnable() {
                @Override
                public void run() {}
            };

    /**
     * Returns a {@code Runnable} that will remove the file system with the given {@code URI} from the
     * system provider's cache when called.
     */
    private static Runnable removeFileSystemRunnable(URI uri) {
        if (ZeroFs.systemProvider == null) {
            // TODO(cgdecker): Use Runnables.doNothing() when it's out of @Beta
            return DO_NOTHING;
        }

        // We have to invoke the SystemZeroFsFileSystemProvider.removeFileSystemRunnable(URI)
        // method reflectively since the system-loaded instance of it may be a different class
        // than the one we'd get if we tried to cast it and call it like normal here.
        try {
            Method method =
                    ZeroFs.systemProvider
                            .getClass()
                            .getDeclaredMethod("removeFileSystemRunnable", URI.class);
            return (Runnable) method.invoke(null, uri);
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            throw new RuntimeException(
                    "Unable to get Runnable for removing the FileSystem from the cache when it is"
                            + " closed",
                    e);
        }
    }

    /**
     * Initialize and configure a new file system with the given provider and URI, using the given
     * configuration.
     */
    public static ZeroFsFileSystem newFileSystem(
            ZeroFsFileSystemProvider provider, URI uri, Configuration config) throws IOException {
        PathService pathService = new PathService(config);
        FileSystemState state =
                new FileSystemState(config.fileTimeSource, removeFileSystemRunnable(uri));

        ZeroFsFileStore fileStore = createFileStore(config, pathService, state);
        FileSystemView defaultView = createDefaultView(config, fileStore, pathService);
        WatchServiceConfiguration watchServiceConfig = config.watchServiceConfig;

        ZeroFsFileSystem fileSystem =
                new ZeroFsFileSystem(
                        provider, uri, fileStore, pathService, defaultView, watchServiceConfig);

        pathService.setFileSystem(fileSystem);
        return fileSystem;
    }

    /** Creates the file store for the file system. */
    private static ZeroFsFileStore createFileStore(
            Configuration config, PathService pathService, FileSystemState state) {
        AttributeService attributeService = new AttributeService(config);

        HeapDisk disk = new HeapDisk(config);
        FileFactory fileFactory = new FileFactory(disk, config.fileTimeSource);

        Map<Name, Directory> roots = new HashMap<>();

        // create roots
        for (String root : config.roots) {
            ZeroFsPath path = pathService.parsePath(root);
            if (!path.isAbsolute() && path.getNameCount() == 0) {
                throw new IllegalArgumentException("Invalid root path: " + root);
            }

            Name rootName = path.root();

            Directory rootDir = fileFactory.createRootDirectory(rootName);
            attributeService.setInitialAttributes(rootDir);
            roots.put(rootName, rootDir);
        }

        return new ZeroFsFileStore(
                new FileTree(roots),
                fileFactory,
                disk,
                attributeService,
                config.supportedFeatures,
                state);
    }

    /** Creates the default view of the file system using the given working directory. */
    private static FileSystemView createDefaultView(
            Configuration config, ZeroFsFileStore fileStore, PathService pathService)
            throws IOException {
        ZeroFsPath workingDirPath = pathService.parsePath(config.workingDirectory);

        Directory dir = fileStore.getRoot(workingDirPath.root());
        if (dir == null) {
            throw new IllegalArgumentException("Invalid working dir path: " + workingDirPath);
        }

        for (Name name : workingDirPath.names()) {
            Directory newDir = fileStore.directoryCreator().get();
            fileStore.setInitialAttributes(newDir);
            dir.link(name, newDir);

            dir = newDir;
        }

        return new FileSystemView(fileStore, dir, workingDirPath);
    }
}
