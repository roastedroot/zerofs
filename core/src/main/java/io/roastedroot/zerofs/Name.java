package io.roastedroot.zerofs;

import java.util.Comparator;
import java.util.Objects;

/**
 * Immutable representation of a file name. Used both for the name components of paths and as the
 * keys for directory entries.
 *
 * <p>A name has both a display string (used in the {@code toString()} form of a {@code Path} as
 * well as for {@code Path} equality and sort ordering) and a canonical string, which is used for
 * determining equality of the name during file lookup.
 *
 * <p>Note: all factory methods return a constant name instance when given the original string "."
 * or "..", ensuring that those names can be accessed statically elsewhere in the code while still
 * being equal to any names created for those values, regardless of normalization settings.
 *
 * @author Colin Decker
 */
final class Name {

    /** The empty name. */
    static final Name EMPTY = new Name("", "");

    /** The name to use for a link from a directory to itself. */
    public static final Name SELF = new Name(".", ".");

    /** The name to use for a link from a directory to its parent directory. */
    public static final Name PARENT = new Name("..", "..");

    /** Creates a new name with no normalization done on the given string. */
    public static Name simple(String name) {
        switch (name) {
            case ".":
                return SELF;
            case "..":
                return PARENT;
            default:
                return new Name(name, name);
        }
    }

    /**
     * Creates a name with the given display representation and the given canonical representation.
     */
    public static Name create(String display, String canonical) {
        return new Name(display, canonical);
    }

    private final String display;
    private final String canonical;

    private Name(String display, String canonical) {
        this.display = Objects.requireNonNull(display);
        this.canonical = Objects.requireNonNull(canonical);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Name) {
            Name other = (Name) obj;
            return canonical.equals(other.canonical);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(canonical);
    }

    @Override
    public String toString() {
        return display;
    }

    /** Returns a comparator that orders names by their display representation. */
    static Comparator<Name> displayComparator() {
        return DISPLAY_COMPARATOR;
    }

    /** Returns a comparator that orders names by their canonical representation. */
    static Comparator<Name> canonicalComparator() {
        return CANONICAL_COMPARATOR;
    }

    private static final Comparator<Name> DISPLAY_COMPARATOR =
            Comparator.comparing((Name n) -> n.display);

    private static final Comparator<Name> CANONICAL_COMPARATOR =
            Comparator.comparing((Name n) -> n.canonical);
}
