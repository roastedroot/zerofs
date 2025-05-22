package io.roastedroot.zerofs;

import java.nio.file.InvalidPathException;

/**
 * Unix-style path type.
 *
 * @author Colin Decker
 */
final class UnixPathType extends PathType {

    /** Unix path type. */
    static final PathType INSTANCE = new UnixPathType();

    private UnixPathType() {
        super(false, '/');
    }

    @Override
    public ParseResult parsePath(String path) {
        if (path.isEmpty()) {
            return emptyPath();
        }

        checkValid(path);

        String root = path.startsWith("/") ? "/" : null;
        return new ParseResult(root, split(path));
    }

    private static void checkValid(String path) {
        int nulIndex = path.indexOf('\0');
        if (nulIndex != -1) {
            throw new InvalidPathException(path, "nul character not allowed", nulIndex);
        }
    }

    @Override
    public String toString(String root, String[] names) {
        StringBuilder builder = new StringBuilder();
        if (root != null) {
            builder.append(root);
        }
        builder.append(join(names));
        return builder.toString();
    }

    @Override
    public String toUriPath(String root, String[] names, boolean directory) {
        StringBuilder builder = new StringBuilder();
        for (String name : names) {
            builder.append('/').append(name);
        }

        if (directory || builder.length() == 0) {
            builder.append('/');
        }
        return builder.toString();
    }

    @Override
    public ParseResult parseUriPath(String uriPath) {
        if (!uriPath.startsWith("/")) {
            throw new IllegalArgumentException(
                    String.format("uriPath (%s) must start with /", uriPath));
        }
        return parsePath(uriPath);
    }
}
