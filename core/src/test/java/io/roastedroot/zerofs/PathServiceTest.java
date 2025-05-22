package io.roastedroot.zerofs;

import static io.roastedroot.zerofs.PathNormalization.CASE_FOLD_ASCII;
import static io.roastedroot.zerofs.PathSubject.paths;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.PathMatcher;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link PathService}.
 *
 * @author Colin Decker
 */
public class PathServiceTest {

    private static final Set<PathNormalization> NO_NORMALIZATIONS = Set.of();

    private final PathService service = fakeUnixPathService();

    @Test
    public void testBasicProperties() {
        assertEquals("/", service.getSeparator());
        assertEquals("\\", fakeWindowsPathService().getSeparator());
    }

    @Test
    public void testPathCreation() {
        paths(service.emptyPath()).hasRootComponent(null).and().hasNameComponents("");

        paths(service.createRoot(service.name("/")))
                .isAbsolute()
                .and()
                .hasRootComponent("/")
                .and()
                .hasNoNameComponents();

        paths(service.createFileName(service.name("foo")))
                .hasRootComponent(null)
                .and()
                .hasNameComponents("foo");

        ZeroFsPath relative =
                service.createRelativePath(service.names(new String[] {"foo", "bar"}));
        paths(relative).hasRootComponent(null).and().hasNameComponents("foo", "bar");

        ZeroFsPath absolute =
                service.createPath(service.name("/"), service.names(new String[] {"foo", "bar"}));
        paths(absolute)
                .isAbsolute()
                .and()
                .hasRootComponent("/")
                .and()
                .hasNameComponents("foo", "bar");
    }

    @Test
    public void testPathCreation_emptyPath() {
        // normalized to empty path with single empty string name
        paths(service.createPath(null, List.<Name>of()))
                .hasRootComponent(null)
                .and()
                .hasNameComponents("");
    }

    @Test
    public void testPathCreation_parseIgnoresEmptyString() {
        // if the empty string wasn't ignored, the resulting path would be "/foo" since the empty
        // string would be joined with foo
        paths(service.parsePath("", "foo")).hasRootComponent(null).and().hasNameComponents("foo");
    }

    @Test
    public void testToString() {
        // not much to test for this since it just delegates to PathType anyway
        ZeroFsPath path = new ZeroFsPath(service, null, Name.simple("foo"), Name.simple("bar"));
        assertEquals("foo/bar", service.toString(path));

        path = new ZeroFsPath(service, Name.simple("/"), Name.simple("foo"));
        assertEquals("/foo", service.toString(path));
    }

    @Test
    public void testHash_usingDisplayForm() {
        PathService pathService = fakePathService(PathType.unix(), false);

        ZeroFsPath path1 = new ZeroFsPath(pathService, null, (Name.create("FOO", "foo")));
        ZeroFsPath path2 = new ZeroFsPath(pathService, null, Name.create("FOO", "FOO"));
        ZeroFsPath path3 =
                new ZeroFsPath(pathService, null, Name.create("FOO", "9874238974897189741"));

        assertEquals(pathService.hash(path2), pathService.hash(path1));
        assertEquals(pathService.hash(path3), pathService.hash(path2));
    }

    @Test
    public void testHash_usingCanonicalForm() {
        PathService pathService = fakePathService(PathType.unix(), true);

        ZeroFsPath path1 = new ZeroFsPath(pathService, null, Name.create("foo", "foo"));
        ZeroFsPath path2 = new ZeroFsPath(pathService, null, Name.create("FOO", "foo"));
        ZeroFsPath path3 =
                new ZeroFsPath(
                        pathService, null, List.of(Name.create("28937497189478912374897", "foo")));

        assertEquals(pathService.hash(path2), pathService.hash(path1));
        assertEquals(pathService.hash(path3), pathService.hash(path2));
    }

    @Test
    public void testCompareTo_usingDisplayForm() {
        PathService pathService = fakePathService(PathType.unix(), false);

        ZeroFsPath path1 = new ZeroFsPath(pathService, null, List.of(Name.create("a", "z")));
        ZeroFsPath path2 = new ZeroFsPath(pathService, null, List.of(Name.create("b", "y")));
        ZeroFsPath path3 = new ZeroFsPath(pathService, null, List.of(Name.create("c", "x")));

        assertEquals(-1, pathService.compare(path1, path2));
        assertEquals(-1, pathService.compare(path2, path3));
    }

    @Test
    public void testCompareTo_usingCanonicalForm() {
        PathService pathService = fakePathService(PathType.unix(), true);

        ZeroFsPath path1 = new ZeroFsPath(pathService, null, List.of(Name.create("a", "z")));
        ZeroFsPath path2 = new ZeroFsPath(pathService, null, List.of(Name.create("b", "y")));
        ZeroFsPath path3 = new ZeroFsPath(pathService, null, List.of(Name.create("c", "x")));

        assertEquals(1, pathService.compare(path1, path2));
        assertEquals(1, pathService.compare(path2, path3));
    }

    @Test
    public void testPathMatcher() {
        assertInstanceOf(
                PathMatchers.RegexPathMatcher.class, service.createPathMatcher("regex:foo"));
        assertInstanceOf(
                PathMatchers.RegexPathMatcher.class, service.createPathMatcher("glob:foo"));
    }

    @Test
    public void testPathMatcher_usingCanonicalForm_usesCanonicalNormalizations() {
        // https://github.com/google/ZeroFs/issues/91
        // This matches the behavior of Windows (the only built-in configuration that uses canonical
        // form for equality). There, PathMatchers should do case-insensitive matching despite
        // Windows
        // not normalizing case for display.
        assertCaseInsensitiveMatches(
                new PathService(PathType.unix(), NO_NORMALIZATIONS, Set.of(CASE_FOLD_ASCII), true));
        assertCaseSensitiveMatches(
                new PathService(PathType.unix(), Set.of(CASE_FOLD_ASCII), NO_NORMALIZATIONS, true));
    }

    @Test
    public void testPathMatcher_usingDisplayForm_usesDisplayNormalizations() {
        assertCaseInsensitiveMatches(
                new PathService(
                        PathType.unix(), Set.of(CASE_FOLD_ASCII), NO_NORMALIZATIONS, false));
        assertCaseSensitiveMatches(
                new PathService(
                        PathType.unix(), NO_NORMALIZATIONS, Set.of(CASE_FOLD_ASCII), false));
    }

    private static void assertCaseInsensitiveMatches(PathService service) {
        List<PathMatcher> matchers =
                List.of(
                        service.createPathMatcher("glob:foo"),
                        service.createPathMatcher("glob:FOO"));

        ZeroFsPath lowerCasePath = singleNamePath(service, "foo");
        ZeroFsPath upperCasePath = singleNamePath(service, "FOO");
        ZeroFsPath nonMatchingPath = singleNamePath(service, "bar");

        for (PathMatcher matcher : matchers) {
            assertTrue(matcher.matches(lowerCasePath));
            assertTrue(matcher.matches(upperCasePath));
            assertFalse(matcher.matches(nonMatchingPath));
        }
    }

    private static void assertCaseSensitiveMatches(PathService service) {
        PathMatcher matcher = service.createPathMatcher("glob:foo");

        ZeroFsPath lowerCasePath = singleNamePath(service, "foo");
        ZeroFsPath upperCasePath = singleNamePath(service, "FOO");

        assertTrue(matcher.matches(lowerCasePath));
        assertFalse(matcher.matches(upperCasePath));
    }

    public static PathService fakeUnixPathService() {
        return fakePathService(PathType.unix(), false);
    }

    public static PathService fakeWindowsPathService() {
        return fakePathService(PathType.windows(), false);
    }

    public static PathService fakePathService(PathType type, boolean equalityUsesCanonicalForm) {
        PathService service =
                new PathService(
                        type, NO_NORMALIZATIONS, NO_NORMALIZATIONS, equalityUsesCanonicalForm);
        service.setFileSystem(FILE_SYSTEM);
        return service;
    }

    private static ZeroFsPath singleNamePath(PathService service, String name) {
        return new ZeroFsPath(service, null, new Name[] {Name.create(name, name)});
    }

    private static final FileSystem FILE_SYSTEM;

    static {
        try {
            FILE_SYSTEM =
                    ZeroFsFileSystems.newFileSystem(
                            new ZeroFsFileSystemProvider(),
                            URI.create("zerofs://foo"),
                            Configuration.unix());
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }
}
