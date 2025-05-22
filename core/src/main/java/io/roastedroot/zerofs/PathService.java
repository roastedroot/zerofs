package io.roastedroot.zerofs;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.util.Comparator.nullsLast;

import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * Service for creating {@link ZeroFsPath} instances and handling other path-related operations.
 *
 * @author Colin Decker
 */
final class PathService implements Comparator<ZeroFsPath> {

    private static final Comparator<Name> DISPLAY_ROOT_COMPARATOR =
            nullsLast(Name.displayComparator());
    private static final Comparator<Iterable<Name>> DISPLAY_NAMES_COMPARATOR =
            new LexicographicalOrdering(Name.displayComparator());

    private static final Comparator<Name> CANONICAL_ROOT_COMPARATOR =
            nullsLast(Name.canonicalComparator());
    private static final Comparator<Iterable<Name>> CANONICAL_NAMES_COMPARATOR =
            new LexicographicalOrdering(Name.canonicalComparator());

    private final PathType type;

    private final Set<PathNormalization> displayNormalizations;
    private final Set<PathNormalization> canonicalNormalizations;
    private final boolean equalityUsesCanonicalForm;

    private final Comparator<Name> rootComparator;
    private final Comparator<Iterable<Name>> namesComparator;

    private volatile FileSystem fileSystem;
    private volatile ZeroFsPath emptyPath;

    PathService(Configuration config) {
        this(
                config.pathType,
                config.nameDisplayNormalization,
                config.nameCanonicalNormalization,
                config.pathEqualityUsesCanonicalForm);
    }

    PathService(
            PathType type,
            Iterable<PathNormalization> displayNormalizations,
            Iterable<PathNormalization> canonicalNormalizations,
            boolean equalityUsesCanonicalForm) {
        this.type = Objects.requireNonNull(type);
        this.displayNormalizations = new TreeSet<>();
        Iterator<PathNormalization> displayNormalizationsIter = displayNormalizations.iterator();
        while (displayNormalizationsIter.hasNext()) {
            this.displayNormalizations.add(displayNormalizationsIter.next());
        }
        this.canonicalNormalizations = new TreeSet();
        Iterator<PathNormalization> canonicalNormalizationsIter =
                canonicalNormalizations.iterator();
        while (canonicalNormalizationsIter.hasNext()) {
            this.canonicalNormalizations.add(canonicalNormalizationsIter.next());
        }
        this.equalityUsesCanonicalForm = equalityUsesCanonicalForm;

        this.rootComparator =
                equalityUsesCanonicalForm ? CANONICAL_ROOT_COMPARATOR : DISPLAY_ROOT_COMPARATOR;
        this.namesComparator =
                equalityUsesCanonicalForm ? CANONICAL_NAMES_COMPARATOR : DISPLAY_NAMES_COMPARATOR;
    }

    /** Sets the file system to use for created paths. */
    public void setFileSystem(FileSystem fileSystem) {
        // allowed to not be ZeroFsFileSystem for testing purposes only
        if (this.fileSystem != null) {
            new IllegalStateException("may not set fileSystem twice");
        }
        this.fileSystem = Objects.requireNonNull(fileSystem);
    }

    /** Returns the file system this service is for. */
    public FileSystem getFileSystem() {
        return fileSystem;
    }

    /** Returns the default path separator. */
    public String getSeparator() {
        return type.getSeparator();
    }

    /** Returns an empty path which has a single name, the empty string. */
    public ZeroFsPath emptyPath() {
        ZeroFsPath result = emptyPath;
        if (result == null) {
            // use createPathInternal to avoid recursive call from createPath()
            result = createPathInternal(null, List.of(Name.EMPTY));
            emptyPath = result;
            return result;
        }
        return result;
    }

    /** Returns the {@link Name} form of the given string. */
    public Name name(String name) {
        switch (name) {
            case "":
                return Name.EMPTY;
            case ".":
                return Name.SELF;
            case "..":
                return Name.PARENT;
            default:
                String display = PathNormalization.normalize(name, displayNormalizations);
                String canonical = PathNormalization.normalize(name, canonicalNormalizations);
                return Name.create(display, canonical);
        }
    }

    /** Returns the {@link Name} forms of the given strings. */
    List<Name> names(String[] names) {
        List<Name> result = new ArrayList<>();
        for (String name : names) {
            result.add(name(name));
        }
        return result;
    }

    /** Returns a root path with the given name. */
    public ZeroFsPath createRoot(Name root) {
        return createPath(Objects.requireNonNull(root), List.<Name>of());
    }

    /** Returns a single filename path with the given name. */
    public ZeroFsPath createFileName(Name name) {
        return createPath(null, List.of(name));
    }

    /** Returns a relative path with the given names. */
    public ZeroFsPath createRelativePath(Iterable<Name> names) {
        List<Name> allNames = new ArrayList<>();
        Iterator<Name> namesIter = names.iterator();
        while (namesIter.hasNext()) {
            allNames.add(namesIter.next());
        }
        return createPath(null, allNames);
    }

    /** Returns a path with the given root (or no root, if null) and the given names. */
    public ZeroFsPath createPath(Name root, Iterable<Name> names) {
        List<Name> nameList = new ArrayList();
        Iterator<Name> namesIter = names.iterator();
        while (namesIter.hasNext()) {
            Name current = namesIter.next();
            if (NOT_EMPTY.test(current)) {
                nameList.add(current);
            }
        }
        if (root == null && nameList.isEmpty()) {
            // ensure the canonical empty path (one empty string name) is used rather than a path
            // with
            // no root and no names
            return emptyPath();
        }
        return createPathInternal(root, nameList);
    }

    /** Returns a path with the given root (or no root, if null) and the given names. */
    protected final ZeroFsPath createPathInternal(Name root, Iterable<Name> names) {
        List<Name> nameList = new ArrayList();
        Iterator<Name> namesIter = names.iterator();
        while (namesIter.hasNext()) {
            nameList.add(namesIter.next());
        }
        return new ZeroFsPath(this, root, nameList.toArray(Name[]::new));
    }

    /** Parses the given strings as a path. */
    public ZeroFsPath parsePath(String first, String... more) {
        List<String> args = new ArrayList<>();
        if (NOT_EMPTY.test(first)) {
            args.add(first);
        }
        for (String e : more) {
            if (NOT_EMPTY.test(e)) {
                args.add(e);
            }
        }
        String joined = type.join(args.toArray(String[]::new));
        return toPath(type.parsePath(joined));
    }

    private ZeroFsPath toPath(PathType.ParseResult parsed) {
        Name root = parsed.root() == null ? null : name(parsed.root());
        Iterable<Name> names = names(parsed.names());
        return createPath(root, names);
    }

    /** Returns the string form of the given path. */
    public String toString(ZeroFsPath path) {
        Name root = path.root();
        String rootString = root == null ? null : root.toString();
        String[] names = Util.toArray(path.names());
        return type.toString(rootString, names);
    }

    /** Creates a hash code for the given path. */
    public int hash(ZeroFsPath path) {
        // Note: ZeroFsPath.equals() is implemented using the compare() method below;
        // equalityUsesCanonicalForm is taken into account there via the namesComparator, which is
        // set
        // at construction time.
        int hash = 31;
        hash = 31 * hash + getFileSystem().hashCode();

        final Name root = path.root();
        final List<Name> names = path.names();

        if (equalityUsesCanonicalForm) {
            // use hash codes of names themselves, which are based on the canonical form
            hash = 31 * hash + (root == null ? 0 : root.hashCode());
            for (Name name : names) {
                hash = 31 * hash + name.hashCode();
            }
        } else {
            // use hash codes from toString() form of names
            hash = 31 * hash + (root == null ? 0 : root.toString().hashCode());
            for (Name name : names) {
                hash = 31 * hash + name.toString().hashCode();
            }
        }
        return hash;
    }

    @Override
    public int compare(ZeroFsPath a, ZeroFsPath b) {
        Comparator<ZeroFsPath> comparator =
                Comparator.comparing(ZeroFsPath::root, rootComparator)
                        .thenComparing(ZeroFsPath::names, namesComparator);
        return comparator.compare(a, b);
    }

    /**
     * Returns the URI for the given path. The given file system URI is the base against which the
     * path is resolved to create the returned URI.
     */
    public URI toUri(URI fileSystemUri, ZeroFsPath path) {
        if (!path.isAbsolute()) {
            throw new IllegalArgumentException(String.format("path (%s) must be absolute", path));
        }
        String root = String.valueOf(path.root());

        String[] names = Util.toArray(path.names());
        return type.toUri(fileSystemUri, root, names, Files.isDirectory(path, NOFOLLOW_LINKS));
    }

    /** Converts the path of the given URI into a path for this file system. */
    public ZeroFsPath fromUri(URI uri) {
        return toPath(type.fromUri(uri));
    }

    /**
     * Returns a {@link PathMatcher} for the given syntax and pattern as specified by {@link
     * FileSystem#getPathMatcher(String)}.
     */
    public PathMatcher createPathMatcher(String syntaxAndPattern) {
        return PathMatchers.getPathMatcher(
                syntaxAndPattern,
                type.getSeparator() + type.getOtherSeparators(),
                equalityUsesCanonicalForm ? canonicalNormalizations : displayNormalizations);
    }

    private static final Predicate<Object> NOT_EMPTY = input -> !input.toString().isEmpty();
}
