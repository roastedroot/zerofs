package io.roastedroot.zerofs;

import java.io.IOException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The tree of directories and files for the file system. Contains the file system root directories
 * and provides the ability to look up files by path. One piece of the file store implementation.
 *
 * @author Colin Decker
 */
final class FileTree {

    /**
     * Doesn't much matter, but this number comes from MIN_ELOOP_THRESHOLD <a
     * href="https://sourceware.org/git/gitweb.cgi?p=glibc.git;a=blob_plain;f=sysdeps/generic/eloop-threshold.h;hb=HEAD">
     * here</a>
     */
    private static final int MAX_SYMBOLIC_LINK_DEPTH = 40;

    private static final List<Name> EMPTY_PATH_NAMES = List.of(Name.SELF);

    /** Map of root names to root directories. */
    private final SortedMap<Name, Directory> roots;

    /** Creates a new file tree with the given root directories. */
    FileTree(Map<Name, Directory> roots) {
        this.roots = new TreeMap(Name.canonicalComparator());
        this.roots.putAll(roots);
    }

    /** Returns the names of the root directories in this tree. */
    public SortedSet<Name> getRootDirectoryNames() {
        SortedSet result = new TreeSet(Name.canonicalComparator());
        result.addAll(roots.keySet());
        return result;
    }

    /**
     * Gets the directory entry for the root with the given name or {@code null} if no such root
     * exists.
     */
    public DirectoryEntry getRoot(Name name) {
        Directory dir = roots.get(name);
        return dir == null ? null : dir.entryInParent();
    }

    /** Returns the result of the file lookup for the given path. */
    public DirectoryEntry lookUp(
            File workingDirectory, ZeroFsPath path, Set<? super LinkOption> options)
            throws IOException {
        Objects.requireNonNull(path);
        Objects.requireNonNull(options);

        DirectoryEntry result = lookUp(workingDirectory, path, options, 0);
        if (result == null) {
            // an intermediate file in the path did not exist or was not a directory
            throw new NoSuchFileException(path.toString());
        }
        return result;
    }

    private DirectoryEntry lookUp(
            File dir, ZeroFsPath path, Set<? super LinkOption> options, int linkDepth)
            throws IOException {
        List<Name> names = path.names();

        if (path.isAbsolute()) {
            // look up the root directory
            DirectoryEntry entry = getRoot(path.root());
            if (entry == null) {
                // root not found; always return null as no real parent directory exists
                // this prevents new roots from being created in file systems supporting multiple
                // roots
                return null;
            } else if (names.isEmpty()) {
                // root found, no more names to look up
                return entry;
            } else {
                // root found, more names to look up; set dir to the root directory for the path
                dir = entry.file();
            }
        } else if (isEmpty(names)) {
            // set names to the canonical list of names for an empty path (singleton list of ".")
            names = EMPTY_PATH_NAMES;
        }

        return lookUp(dir, names, options, linkDepth);
    }

    /**
     * Looks up the given names against the given base file. If the file is not a directory, the
     * lookup fails.
     */
    private DirectoryEntry lookUp(
            File dir, Iterable<Name> names, Set<? super LinkOption> options, int linkDepth)
            throws IOException {
        Iterator<Name> nameIterator = names.iterator();
        Name name = nameIterator.next();
        while (nameIterator.hasNext()) {
            Directory directory = toDirectory(dir);
            if (directory == null) {
                return null;
            }

            DirectoryEntry entry = directory.get(name);
            if (entry == null) {
                return null;
            }

            File file = entry.file();
            if (file.isSymbolicLink()) {
                DirectoryEntry linkResult = followSymbolicLink(dir, (SymbolicLink) file, linkDepth);

                if (linkResult == null) {
                    return null;
                }

                dir = linkResult.fileOrNull();
            } else {
                dir = file;
            }

            name = nameIterator.next();
        }

        return lookUpLast(dir, name, options, linkDepth);
    }

    /** Looks up the last element of a path. */
    private DirectoryEntry lookUpLast(
            File dir, Name name, Set<? super LinkOption> options, int linkDepth)
            throws IOException {
        Directory directory = toDirectory(dir);
        if (directory == null) {
            return null;
        }

        DirectoryEntry entry = directory.get(name);
        if (entry == null) {
            return new DirectoryEntry(directory, name, null);
        }

        File file = entry.file();
        if (!options.contains(LinkOption.NOFOLLOW_LINKS) && file.isSymbolicLink()) {
            return followSymbolicLink(dir, (SymbolicLink) file, linkDepth);
        }

        return getRealEntry(entry);
    }

    /**
     * Returns the directory entry located by the target path of the given symbolic link, resolved
     * relative to the given directory.
     */
    private DirectoryEntry followSymbolicLink(File dir, SymbolicLink link, int linkDepth)
            throws IOException {
        if (linkDepth >= MAX_SYMBOLIC_LINK_DEPTH) {
            throw new IOException("too many levels of symbolic links");
        }

        return lookUp(dir, link.target(), Options.FOLLOW_LINKS, linkDepth + 1);
    }

    /**
     * Returns the entry for the file in its parent directory. This will be the given entry unless the
     * name for the entry is "." or "..", in which the directory linking to the file is not the file's
     * parent directory. In that case, we know the file must be a directory ("." and ".." can only
     * link to directories), so we can just get the entry in the directory's parent directory that
     * links to it. So, for example, if we have a directory "foo" that contains a directory "bar" and
     * we find an entry [bar -> "." -> bar], we instead return the entry for bar in its parent, [foo
     * -> "bar" -> bar].
     */
    private DirectoryEntry getRealEntry(DirectoryEntry entry) {
        Name name = entry.name();

        if (name.equals(Name.SELF) || name.equals(Name.PARENT)) {
            Directory dir = toDirectory(entry.file());
            assert dir != null;
            return dir.entryInParent();
        } else {
            return entry;
        }
    }

    private Directory toDirectory(File file) {
        return file == null || !file.isDirectory() ? null : (Directory) file;
    }

    private static boolean isEmpty(List<Name> names) {
        // the empty path (created by FileSystem.getPath("")), has no root and a single name, ""
        return names.isEmpty() || (names.size() == 1 && names.get(0).toString().isEmpty());
    }
}
