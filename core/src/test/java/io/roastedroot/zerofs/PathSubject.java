package io.roastedroot.zerofs;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * Subject for doing assertions on file system paths.
 *
 * @author Colin Decker
 */
public final class PathSubject {

    /** Returns the subject factory for doing assertions on paths. */
    public static PathSubject paths(Path actual) {
        return new PathSubjectFactory().createSubject(actual);
    }

    private static final LinkOption[] FOLLOW_LINKS = new LinkOption[0];
    private static final LinkOption[] NOFOLLOW_LINKS = {LinkOption.NOFOLLOW_LINKS};

    private final Path actual;
    protected LinkOption[] linkOptions = FOLLOW_LINKS;
    private Charset charset = UTF_8;

    private PathSubject(Path subject) {
        this.actual = subject;
    }

    private Path toPath(String path) {
        return actual.getFileSystem().getPath(path);
    }

    public Path path() {
        return this.actual;
    }

    /** Returns this, for readability of chained assertions. */
    public PathSubject and() {
        return this;
    }

    /** Do not follow links when looking up the path. */
    public PathSubject noFollowLinks() {
        this.linkOptions = NOFOLLOW_LINKS;
        return this;
    }

    /**
     * Set the given charset to be used when reading the file at this path as text. Default charset if
     * not set is UTF-8.
     */
    public PathSubject withCharset(Charset charset) {
        this.charset = Objects.requireNonNull(charset);
        return this;
    }

    /** Asserts that the path is absolute (it has a root component). */
    public PathSubject isAbsolute() {
        if (!actual.isAbsolute()) {
            throw new AssertionError(String.format("expected to be absolute %s", actual));
        }
        return this;
    }

    /** Asserts that the path is relative (it has no root component). */
    public PathSubject isRelative() {
        if (actual.isAbsolute()) {
            throw new AssertionError(String.format("expected to be relative %s", actual));
        }
        return this;
    }

    /** Asserts that the path has the given root component. */
    public PathSubject hasRootComponent(String root) {
        Path rootComponent = actual.getRoot();
        if (root == null && rootComponent != null) {
            throw new AssertionError(String.format("expected to have root component %s", root));
        } else if (root != null && !root.equals(rootComponent.toString())) {
            throw new AssertionError(String.format("expected to have root component %s", root));
        }
        return this;
    }

    /** Asserts that the path has no name components. */
    public PathSubject hasNoNameComponents() {
        assertEquals(0, actual.getNameCount());
        return this;
    }

    /** Asserts that the path has the given name components. */
    public PathSubject hasNameComponents(String... names) {
        List<String> builder = new ArrayList();
        for (Path name : actual) {
            builder.add(name.toString());
        }

        if (!List.copyOf(builder).equals(List.of(names))) {
            throw new AssertionError(String.format("expected components %s", asList(names)));
        }
        return this;
    }

    /** Asserts that the path matches the given syntax and pattern. */
    public PathSubject matches(String syntaxAndPattern) {
        PathMatcher matcher = actual.getFileSystem().getPathMatcher(syntaxAndPattern);
        if (!matcher.matches(actual)) {
            throw new AssertionError(String.format("expected to match %s", syntaxAndPattern));
        }
        return this;
    }

    /** Asserts that the path does not match the given syntax and pattern. */
    public PathSubject doesNotMatch(String syntaxAndPattern) {
        PathMatcher matcher = actual.getFileSystem().getPathMatcher(syntaxAndPattern);
        if (matcher.matches(actual)) {
            throw new AssertionError(String.format("expected not to match %s", syntaxAndPattern));
        }
        return this;
    }

    /** Asserts that the path exists. */
    public PathSubject exists() {
        if (!Files.exists(actual, linkOptions)) {
            throw new AssertionError(String.format("expected to exist %s", actual));
        }
        if (Files.notExists(actual, linkOptions)) {
            throw new AssertionError(String.format("expected to exist"));
        }
        return this;
    }

    /** Asserts that the path does not exist. */
    public PathSubject doesNotExist() {
        if (!Files.notExists(actual, linkOptions)) {
            throw new AssertionError(String.format("expected not to exist %s", actual));
        }
        if (Files.exists(actual, linkOptions)) {
            throw new AssertionError(String.format("expected not to exist %s", actual));
        }
        return this;
    }

    /** Asserts that the path is a directory. */
    public PathSubject isDirectory() {
        exists(); // check for directoryness should imply check for existence

        if (!Files.isDirectory(actual, linkOptions)) {
            throw new AssertionError(String.format("expected to be director %sy, absolute"));
        }
        return this;
    }

    /** Asserts that the path is a regular file. */
    public PathSubject isRegularFile() {
        exists(); // check for regular fileness should imply check for existence

        if (!Files.isRegularFile(actual, linkOptions)) {
            throw new AssertionError(String.format("expected to be regular  %sf, absoluteile"));
        }
        return this;
    }

    /** Asserts that the path is a symbolic link. */
    public PathSubject isSymbolicLink() {
        exists(); // check for symbolic linkness should imply check for existence

        if (!Files.isSymbolicLink(actual)) {
            throw new AssertionError(String.format("expected to be symbolic %s , absolutelink"));
        }
        return this;
    }

    /** Asserts that the path, which is a symbolic link, has the given path as a target. */
    public PathSubject withTarget(String targetPath) throws IOException {
        Path actualTarget = Files.readSymbolicLink(actual);
        if (!actualTarget.equals(toPath(targetPath))) {
            throw new AssertionError(
                    String.format(
                            "expected link target %s but target was %s for path %s",
                            targetPath, actualTarget, actual));
        }
        return this;
    }

    /**
     * Asserts that the file the path points to exists and has the given number of links to it. Fails
     * on a file system that does not support the "unix" view.
     */
    public PathSubject hasLinkCount(int count) throws IOException {
        exists();

        int linkCount = (int) Files.getAttribute(actual, "unix:nlink", linkOptions);
        if (linkCount != count) {
            throw new AssertionError(String.format("expected to have link count %s", count));
        }
        return this;
    }

    /** Asserts that the path resolves to the same file as the given path. */
    public PathSubject isSameFileAs(String path) throws IOException {
        return isSameFileAs(toPath(path));
    }

    /** Asserts that the path resolves to the same file as the given path. */
    public PathSubject isSameFileAs(Path path) throws IOException {
        if (!Files.isSameFile(actual, path)) {
            throw new AssertionError(String.format("expected to be same file %s as", path));
        }
        return this;
    }

    /** Asserts that the path does not resolve to the same file as the given path. */
    public PathSubject isNotSameFileAs(String path) throws IOException {
        if (Files.isSameFile(actual, toPath(path))) {
            throw new AssertionError(String.format("expected not to be same %s file as", path));
        }
        return this;
    }

    /** Asserts that the directory has no children. */
    public PathSubject hasNoChildren() throws IOException {
        isDirectory();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(actual)) {
            if (stream.iterator().hasNext()) {
                throw new AssertionError(
                        String.format("expected to have no chi %sl, absolutedren"));
            }
        }
        return this;
    }

    /** Asserts that the directory has children with the given names, in the given order. */
    public PathSubject hasChildren(String... children) throws IOException {
        isDirectory();

        List<Path> expectedNames = new ArrayList<>();
        for (String child : children) {
            expectedNames.add(actual.getFileSystem().getPath(child));
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(actual)) {
            List<Path> actualNames = new ArrayList<>();
            for (Path path : stream) {
                actualNames.add(path.getFileName());
            }

            if (!actualNames.equals(expectedNames)) {
                throw new AssertionError(
                        String.format(
                                "expected to have children %s but had children %s for path %s",
                                expectedNames, actualNames, actual));
            }
        }
        return this;
    }

    /** Asserts that the file has the given size. */
    public PathSubject hasSize(long size) throws IOException {
        if (Files.size(actual) != size) {
            throw new AssertionError(String.format("expected to have size %s", size));
        }
        return this;
    }

    /** Asserts that the file is a regular file containing no bytes. */
    public PathSubject containsNoBytes() throws IOException {
        return containsBytes(new byte[0]);
    }

    /**
     * Asserts that the file is a regular file containing exactly the byte values of the given ints.
     */
    public PathSubject containsBytes(int... bytes) throws IOException {
        byte[] realBytes = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            realBytes[i] = (byte) bytes[i];
        }
        return containsBytes(realBytes);
    }

    /** Asserts that the file is a regular file containing exactly the given bytes. */
    public PathSubject containsBytes(byte[] bytes) throws IOException {
        isRegularFile();
        hasSize(bytes.length);

        byte[] actual = Files.readAllBytes(this.actual);
        if (!Arrays.equals(bytes, actual)) {
            System.out.println(Base64.getEncoder().encode(actual));
            System.out.println(Base64.getEncoder().encode(bytes));
            throw new AssertionError(
                    String.format(
                            "expected to contain bytes %s", Base64.getEncoder().encode(bytes)));
        }
        return this;
    }

    /**
     * Asserts that the file is a regular file containing the same bytes as the regular file at the
     * given path.
     */
    public PathSubject containsSameBytesAs(String path) throws IOException {
        isRegularFile();

        byte[] expectedBytes = Files.readAllBytes(toPath(path));
        if (!Arrays.equals(expectedBytes, Files.readAllBytes(actual))) {
            throw new AssertionError(String.format("expected to contain same bytes %s as", path));
        }
        return this;
    }

    /**
     * Asserts that the file is a regular file containing the given lines of text. By default, the
     * bytes are decoded as UTF-8; for a different charset, use {@link #withCharset(Charset)}.
     */
    public PathSubject containsLines(String... lines) throws IOException {
        return containsLines(Arrays.asList(lines));
    }

    /**
     * Asserts that the file is a regular file containing the given lines of text. By default, the
     * bytes are decoded as UTF-8; for a different charset, use {@link #withCharset(Charset)}.
     */
    public PathSubject containsLines(Iterable<String> lines) throws IOException {
        isRegularFile();

        List<String> expected = new ArrayList<>();
        Iterator<String> linesIter = lines.iterator();
        while (linesIter.hasNext()) {
            expected.add(linesIter.next());
        }
        List<String> actual = Files.readAllLines(this.actual, charset);

        assertEquals(expected, actual);
        return this;
    }

    /** Returns an object for making assertions about the given attribute. */
    public Attribute attribute(final String attribute) {
        return new Attribute() {
            @Override
            public Attribute is(Object value) throws IOException {
                Object actualValue = Files.getAttribute(actual, attribute, linkOptions);
                assertEquals(value, actualValue);
                return this;
            }

            @Override
            public Attribute isNot(Object value) throws IOException {
                Object actualValue = Files.getAttribute(actual, attribute, linkOptions);
                assertNotEquals(value, actualValue);
                return this;
            }

            @Override
            public PathSubject and() {
                return PathSubject.this;
            }
        };
    }

    private static class PathSubjectFactory {

        public PathSubject createSubject(Path that) {
            return new PathSubject(that);
        }
    }

    /** Interface for assertions about a file attribute. */
    public interface Attribute {

        /** Asserts that the value of this attribute is equal to the given value. */
        Attribute is(Object value) throws IOException;

        /** Asserts that the value of this attribute is not equal to the given value. */
        Attribute isNot(Object value) throws IOException;

        /** Returns the path subject for further chaining. */
        PathSubject and();
    }
}
