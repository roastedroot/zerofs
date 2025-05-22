package io.roastedroot.zerofs;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import java.nio.file.CopyOption;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Utility methods for normalizing user-provided options arrays and sets to canonical immutable sets
 * of options.
 *
 * @author Colin Decker
 */
final class Options {

    private Options() {}

    /** Set containing LinkOption.NOFOLLOW_LINKS. */
    public static final Set<LinkOption> NOFOLLOW_LINKS = Set.of(LinkOption.NOFOLLOW_LINKS);

    /** Empty LinkOption set. */
    public static final Set<LinkOption> FOLLOW_LINKS = Set.of();

    private static final Set<OpenOption> DEFAULT_READ = Set.<OpenOption>of(READ);

    private static final Set<OpenOption> DEFAULT_READ_NOFOLLOW_LINKS =
            Set.<OpenOption>of(READ, LinkOption.NOFOLLOW_LINKS);

    private static final Set<OpenOption> DEFAULT_WRITE =
            Set.<OpenOption>of(WRITE, CREATE, TRUNCATE_EXISTING);

    /** Returns an immutable set of link options. */
    public static Set<LinkOption> getLinkOptions(LinkOption... options) {
        return options.length == 0 ? FOLLOW_LINKS : NOFOLLOW_LINKS;
    }

    /** Returns an immutable set of open options for opening a new file channel. */
    public static Set<OpenOption> getOptionsForChannel(Set<? extends OpenOption> options) {
        if (options.isEmpty()) {
            return DEFAULT_READ;
        }

        boolean append = options.contains(APPEND);
        boolean write = append || options.contains(WRITE);
        boolean read = !write || options.contains(READ);

        if (read) {
            if (append) {
                throw new UnsupportedOperationException("'READ' + 'APPEND' not allowed");
            }

            if (!write) {
                // ignore all write related options
                return options.stream()
                                .anyMatch(o -> o.hashCode() == LinkOption.NOFOLLOW_LINKS.hashCode())
                        // throws ClassCastException
                        // return options.contains(LinkOption.NOFOLLOW_LINKS)
                        ? DEFAULT_READ_NOFOLLOW_LINKS
                        : DEFAULT_READ;
            }
        }

        // options contains write or append and may also contain read
        // it does not contain both read and append
        return addWrite(options);
    }

    /** Returns an immutable set of open options for opening a new input stream. */
    @SuppressWarnings("unchecked") // safe covariant cast
    public static Set<OpenOption> getOptionsForInputStream(OpenOption... options) {
        boolean nofollowLinks = false;
        for (OpenOption option : options) {
            if (Objects.requireNonNull(option) != READ) {
                if (option == LinkOption.NOFOLLOW_LINKS) {
                    nofollowLinks = true;
                } else {
                    throw new UnsupportedOperationException("'" + option + "' not allowed");
                }
            }
        }

        // just return the link options for finding the file, nothing else is needed
        return (Set<OpenOption>) (Set<?>) (nofollowLinks ? NOFOLLOW_LINKS : FOLLOW_LINKS);
    }

    /** Returns an immutable set of open options for opening a new output stream. */
    public static Set<OpenOption> getOptionsForOutputStream(OpenOption... options) {
        if (options.length == 0) {
            return DEFAULT_WRITE;
        }

        Set<OpenOption> result = addWrite(Arrays.asList(options));
        if (result.contains(READ)) {
            throw new UnsupportedOperationException("'READ' not allowed");
        }
        return result;
    }

    /**
     * Returns an {@link Set} copy of the given {@code options}, adding {@link
     * StandardOpenOption#WRITE} if it isn't already present.
     */
    private static Set<OpenOption> addWrite(Collection<? extends OpenOption> options) {
        if (options.contains(WRITE)) {
            return Set.copyOf(options);
        } else {
            Set<OpenOption> opts = new HashSet<>();
            opts.add(WRITE);
            opts.addAll(options);
            return opts;
        }
    }

    /** Returns an immutable set of the given options for a move. */
    public static Set<CopyOption> getMoveOptions(CopyOption... options) {
        List<CopyOption> opts = new ArrayList<>();
        opts.add(LinkOption.NOFOLLOW_LINKS);
        opts.addAll(List.of(options));
        return Set.copyOf(opts);
    }

    /** Returns an immutable set of the given options for a copy. */
    public static Set<CopyOption> getCopyOptions(CopyOption... options) {
        Set<CopyOption> result = Set.copyOf(List.of(options));
        if (result.contains(ATOMIC_MOVE)) {
            throw new UnsupportedOperationException("'ATOMIC_MOVE' not allowed");
        }
        return result;
    }
}
