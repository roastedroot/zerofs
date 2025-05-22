package io.roastedroot.zerofs;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.AbstractList;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * ZeroFs implementation of {@link Path}. Creation of new {@code Path} objects is delegated to the
 * file system's {@link PathService}.
 *
 * @author Colin Decker
 */
@SuppressWarnings("ShouldNotSubclass") // I know what I'm doing I promise
final class ZeroFsPath implements Path {

    private final Name root;
    private final List<Name> names;
    private final PathService pathService;

    public ZeroFsPath(PathService pathService, Name root, Name... names) {
        this(pathService, root, List.of(names));
    }

    public ZeroFsPath(PathService pathService, Name root, List<Name> names) {
        this.pathService = Objects.requireNonNull(pathService);
        this.root = root;
        this.names = names;
    }

    /** Returns the root name, or null if there is no root. */
    public Name root() {
        return root;
    }

    /** Returns the list of name elements. */
    public List<Name> names() {
        return names;
    }

    /**
     * Returns the file name of this path. Unlike {@link #getFileName()}, this may return the name of
     * the root if this is a root path.
     */
    public Name name() {
        if (!names.isEmpty()) {
            return names.get(names.size() - 1);
        }
        return root;
    }

    /**
     * Returns whether or not this is the empty path, with no root and a single, empty string, name.
     */
    public boolean isEmptyPath() {
        return root == null && names.size() == 1 && names.get(0).toString().isEmpty();
    }

    @Override
    public FileSystem getFileSystem() {
        return pathService.getFileSystem();
    }

    /**
     * Equivalent to {@link #getFileSystem()} but with a return type of {@code ZeroFsFileSystem}.
     * {@code getFileSystem()}'s return type is left as {@code FileSystem} to make testing paths
     * easier (as long as methods that access the file system in some way are not called, the file
     * system can be a fake file system instance).
     */
    public ZeroFsFileSystem getZeroFsFileSystem() {
        return (ZeroFsFileSystem) pathService.getFileSystem();
    }

    @Override
    public boolean isAbsolute() {
        return root != null;
    }

    @Override
    public ZeroFsPath getRoot() {
        if (root == null) {
            return null;
        }
        return pathService.createRoot(root);
    }

    @Override
    public ZeroFsPath getFileName() {
        return names.isEmpty() ? null : getName(names.size() - 1);
    }

    @Override
    public ZeroFsPath getParent() {
        if (names.isEmpty() || (names.size() == 1 && root == null)) {
            return null;
        }

        return pathService.createPath(root, names.subList(0, names.size() - 1));
    }

    @Override
    public int getNameCount() {
        return names.size();
    }

    @Override
    public ZeroFsPath getName(int index) {
        if (!(index >= 0 && index < names.size())) {
            throw new IllegalArgumentException(
                    String.format(
                            "index (%s) must be >= 0 and < name count (%s)", index, names.size()));
        }
        return pathService.createFileName(names.get(index));
    }

    @Override
    public ZeroFsPath subpath(int beginIndex, int endIndex) {
        if (!(beginIndex >= 0 && endIndex <= names.size() && endIndex > beginIndex)) {
            throw new IllegalArgumentException(
                    String.format(
                            "beginIndex (%s) must be >= 0; endIndex (%s) must be <= name count (%s)"
                                    + " and > beginIndex",
                            beginIndex, endIndex, names.size()));
        }
        return pathService.createRelativePath(names.subList(beginIndex, endIndex));
    }

    /** Returns true if list starts with all elements of other in the same order. */
    private static boolean startsWith(List<?> list, List<?> other) {
        return list.size() >= other.size() && list.subList(0, other.size()).equals(other);
    }

    @Override
    public boolean startsWith(Path other) {
        ZeroFsPath otherPath = checkPath(other);
        return otherPath != null
                && getFileSystem().equals(otherPath.getFileSystem())
                && Objects.equals(root, otherPath.root)
                && startsWith(names, otherPath.names);
    }

    @Override
    public boolean startsWith(String other) {
        return startsWith(pathService.parsePath(other));
    }

    @Override
    public boolean endsWith(Path other) {
        ZeroFsPath otherPath = checkPath(other);
        if (otherPath == null) {
            return false;
        }

        if (otherPath.isAbsolute()) {
            return compareTo(otherPath) == 0;
        }
        List<Name> names1 = new ArrayList<>();
        names1.addAll(names);
        Collections.reverse(names1);
        List<Name> names2 = new ArrayList<>();
        names2.addAll(otherPath.names);
        Collections.reverse(names2);
        return startsWith(names1, names2);
    }

    @Override
    public boolean endsWith(String other) {
        return endsWith(pathService.parsePath(other));
    }

    @Override
    public ZeroFsPath normalize() {
        if (isNormal()) {
            return this;
        }

        Deque<Name> newNames = new ArrayDeque<>();
        for (Name name : names) {
            if (name.equals(Name.PARENT)) {
                Name lastName = newNames.peekLast();
                if (lastName != null && !lastName.equals(Name.PARENT)) {
                    newNames.removeLast();
                } else if (!isAbsolute()) {
                    // if there's a root and we have an extra ".." that would go up above the root,
                    // ignore it
                    newNames.add(name);
                }
            } else if (!name.equals(Name.SELF)) {
                newNames.add(name);
            }
        }
        // TODO: verify!
        List<Name> comparable1 = new ArrayList<>();
        comparable1.addAll(newNames);
        List<Name> comparable2 = new ArrayList<>();
        comparable2.addAll(names);

        return comparable1.equals(comparable2) ? this : pathService.createPath(root, newNames);
    }

    /**
     * Returns whether or not this path is in a normalized form. It's normal if it both contains no
     * "." names and contains no ".." names in a location other than the start of the path.
     */
    private boolean isNormal() {
        if (getNameCount() == 0 || (getNameCount() == 1 && !isAbsolute())) {
            return true;
        }

        boolean foundNonParentName =
                isAbsolute(); // if there's a root, the path doesn't start with ..
        boolean normal = true;
        for (Name name : names) {
            if (name.equals(Name.PARENT)) {
                if (foundNonParentName) {
                    normal = false;
                    break;
                }
            } else {
                if (name.equals(Name.SELF)) {
                    normal = false;
                    break;
                }

                foundNonParentName = true;
            }
        }
        return normal;
    }

    /** Resolves the given name against this path. The name is assumed not to be a root name. */
    ZeroFsPath resolve(Name name) {
        return resolve(pathService.createFileName(name));
    }

    @Override
    public ZeroFsPath resolve(Path other) {
        ZeroFsPath otherPath = checkPath(other);
        if (otherPath == null) {
            throw new ProviderMismatchException(other.toString());
        }

        if (isEmptyPath() || otherPath.isAbsolute()) {
            return otherPath;
        }
        if (otherPath.isEmptyPath()) {
            return this;
        }
        List<Name> allNames = new ArrayList<>();
        allNames.addAll(names);
        allNames.addAll(otherPath.names);
        return pathService.createPath(root, allNames);
    }

    @Override
    public ZeroFsPath resolve(String other) {
        return resolve(pathService.parsePath(other));
    }

    @Override
    public ZeroFsPath resolveSibling(Path other) {
        ZeroFsPath otherPath = checkPath(other);
        if (otherPath == null) {
            throw new ProviderMismatchException(other.toString());
        }

        if (otherPath.isAbsolute()) {
            return otherPath;
        }
        ZeroFsPath parent = getParent();
        if (parent == null) {
            return otherPath;
        }
        return parent.resolve(other);
    }

    @Override
    public ZeroFsPath resolveSibling(String other) {
        return resolveSibling(pathService.parsePath(other));
    }

    @Override
    public ZeroFsPath relativize(Path other) {
        ZeroFsPath otherPath = checkPath(other);
        if (otherPath == null) {
            throw new ProviderMismatchException(other.toString());
        }

        if (!Objects.equals(root, otherPath.root)) {
            throw new IllegalArgumentException(
                    String.format("Paths have different roots: %s, %s", this, other));
        }

        if (equals(other)) {
            return pathService.emptyPath();
        }

        if (isEmptyPath()) {
            return otherPath;
        }

        List<Name> otherNames = otherPath.names;
        int sharedSubsequenceLength = 0;
        for (int i = 0; i < Math.min(getNameCount(), otherNames.size()); i++) {
            if (names.get(i).equals(otherNames.get(i))) {
                sharedSubsequenceLength++;
            } else {
                break;
            }
        }

        int extraNamesInThis = Math.max(0, getNameCount() - sharedSubsequenceLength);

        List<Name> extraNamesInOther =
                (otherNames.size() <= sharedSubsequenceLength)
                        ? List.<Name>of()
                        : otherNames.subList(sharedSubsequenceLength, otherNames.size());

        List<Name> parts = new ArrayList<>(extraNamesInThis + extraNamesInOther.size());

        // add .. for each extra name in this path
        parts.addAll(Collections.nCopies(extraNamesInThis, Name.PARENT));
        // add each extra name in the other path
        parts.addAll(extraNamesInOther);

        return pathService.createRelativePath(parts);
    }

    @Override
    public ZeroFsPath toAbsolutePath() {
        return isAbsolute() ? this : getZeroFsFileSystem().getWorkingDirectory().resolve(this);
    }

    @Override
    public ZeroFsPath toRealPath(LinkOption... options) throws IOException {
        return getZeroFsFileSystem()
                .getDefaultView()
                .toRealPath(this, pathService, Options.getLinkOptions(options));
    }

    @Override
    public WatchKey register(
            WatchService watcher, WatchEvent.Kind<?>[] events, WatchEvent.Modifier... modifiers)
            throws IOException {
        Objects.requireNonNull(modifiers);
        return register(watcher, events);
    }

    @Override
    public WatchKey register(WatchService watcher, WatchEvent.Kind<?>... events)
            throws IOException {
        Objects.requireNonNull(watcher);
        Objects.requireNonNull(events);
        if (!(watcher instanceof AbstractWatchService)) {
            throw new IllegalArgumentException(
                    "watcher (" + watcher + ") is not associated with this file system");
        }

        AbstractWatchService service = (AbstractWatchService) watcher;
        return service.register(this, Arrays.asList(events));
    }

    @Override
    public URI toUri() {
        return getZeroFsFileSystem().toUri(this);
    }

    @Override
    public File toFile() {
        // documented as unsupported for anything but the default file system
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<Path> iterator() {
        return asList().iterator();
    }

    private List<Path> asList() {
        return new AbstractList<Path>() {
            @Override
            public Path get(int index) {
                return getName(index);
            }

            @Override
            public int size() {
                return getNameCount();
            }
        };
    }

    @Override
    public int compareTo(Path other) {
        // documented to throw CCE if other is associated with a different FileSystemProvider
        ZeroFsPath otherPath = (ZeroFsPath) other;
        Comparator<ZeroFsPath> comparator =
                Comparator.comparing((ZeroFsPath p) -> p.getZeroFsFileSystem().getUri())
                        .thenComparing(pathService);
        return comparator.compare(this, otherPath);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ZeroFsPath && compareTo((ZeroFsPath) obj) == 0;
    }

    @Override
    public int hashCode() {
        return pathService.hash(this);
    }

    @Override
    public String toString() {
        return pathService.toString(this);
    }

    private ZeroFsPath checkPath(Path other) {
        if (Objects.requireNonNull(other) instanceof ZeroFsPath
                && other.getFileSystem().equals(getFileSystem())) {
            return (ZeroFsPath) other;
        }
        return null;
    }
}
