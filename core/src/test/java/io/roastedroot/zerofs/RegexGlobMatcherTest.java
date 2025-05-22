package io.roastedroot.zerofs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link PathMatcher} instances created by {@link GlobToRegex}.
 *
 * @author Colin Decker
 */
public class RegexGlobMatcherTest extends AbstractGlobMatcherTest {

    @Override
    protected PathMatcher matcher(String pattern) {
        return PathMatchers.getPathMatcher("glob:" + pattern, "/", Set.<PathNormalization>of());
    }

    @Override
    protected PathMatcher realMatcher(String pattern) {
        FileSystem defaultFileSystem = FileSystems.getDefault();
        if ("/".equals(defaultFileSystem.getSeparator())) {
            return defaultFileSystem.getPathMatcher("glob:" + pattern);
        }
        return null;
    }

    @Test
    public void testRegexTranslation() {
        assertGlobRegexIs("foo", "foo");
        assertGlobRegexIs("/", "/");
        assertGlobRegexIs("?", "[^/]");
        assertGlobRegexIs("*", "[^/]*");
        assertGlobRegexIs("**", ".*");
        assertGlobRegexIs("/foo", "/foo");
        assertGlobRegexIs("?oo", "[^/]oo");
        assertGlobRegexIs("*oo", "[^/]*oo");
        assertGlobRegexIs("**/*.java", ".*/[^/]*\\.java");
        assertGlobRegexIs("[a-z]", "[[^/]&&[a-z]]");
        assertGlobRegexIs("[!a-z]", "[[^/]&&[^a-z]]");
        assertGlobRegexIs("[-a-z]", "[[^/]&&[-a-z]]");
        assertGlobRegexIs("[!-a-z]", "[[^/]&&[^-a-z]]");
        assertGlobRegexIs("{a,b,c}", "(a|b|c)");
        assertGlobRegexIs("{?oo,[A-Z]*,foo/**}", "([^/]oo|[[^/]&&[A-Z]][^/]*|foo/.*)");
    }

    @Test
    public void testRegexEscaping() {
        assertGlobRegexIs("(", "\\(");
        assertGlobRegexIs(".", "\\.");
        assertGlobRegexIs("^", "\\^");
        assertGlobRegexIs("$", "\\$");
        assertGlobRegexIs("+", "\\+");
        assertGlobRegexIs("\\\\", "\\\\");
        assertGlobRegexIs("]", "\\]");
        assertGlobRegexIs(")", "\\)");
        assertGlobRegexIs("}", "\\}");
    }

    @Test
    public void testRegexTranslationWithMultipleSeparators() {
        assertGlobRegexIs("?", "[^\\\\/]", "\\/");
        assertGlobRegexIs("*", "[^\\\\/]*", "\\/");
        assertGlobRegexIs("/", "[\\\\/]", "\\/");
        assertGlobRegexIs("\\\\", "[\\\\/]", "\\/");
    }

    private static void assertGlobRegexIs(String glob, String regex) {
        assertGlobRegexIs(glob, regex, "/");
    }

    private static void assertGlobRegexIs(String glob, String regex, String separators) {
        assertEquals(GlobToRegex.toRegex(glob, separators), regex);
        Pattern.compile(regex); // ensure the regex syntax is valid
    }
}
