package io.roastedroot.zerofs;

import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * {@link PathMatcher} factory for any file system.
 *
 * @author Colin Decker
 */
final class PathMatchers {

    private PathMatchers() {}

    /**
     * Gets a {@link PathMatcher} for the given syntax and pattern as specified by {@link
     * FileSystem#getPathMatcher}. The {@code separators} string contains the path name element
     * separators (one character each) recognized by the file system. For a glob-syntax path matcher,
     * any of the given separators will be recognized as a separator in the pattern, and any of them
     * will be matched as a separator when checking a path.
     */
    // TODO(cgdecker): Should I be just canonicalizing separators rather than matching any
    // separator?
    // Perhaps so, assuming Path always canonicalizes its separators
    public static PathMatcher getPathMatcher(
            String syntaxAndPattern, String separators, Set<PathNormalization> normalizations) {
        int syntaxSeparator = syntaxAndPattern.indexOf(':');
        if (syntaxSeparator <= 0) {
            throw new IllegalArgumentException(
                    String.format("Must be of the form 'syntax:pattern': %s", syntaxAndPattern));
        }

        String syntax = Util.toLowerCase(syntaxAndPattern.substring(0, syntaxSeparator));
        String pattern = syntaxAndPattern.substring(syntaxSeparator + 1);

        switch (syntax) {
            case "glob":
                pattern = GlobToRegex.toRegex(pattern, separators);
                // fall through
            case "regex":
                return fromRegex(pattern, normalizations);
            default:
                throw new UnsupportedOperationException("Invalid syntax: " + syntaxAndPattern);
        }
    }

    private static PathMatcher fromRegex(String regex, Iterable<PathNormalization> normalizations) {
        return new RegexPathMatcher(PathNormalization.compilePattern(regex, normalizations));
    }

    /**
     * {@code PathMatcher} that matches the {@code toString()} form of a {@code Path} against a regex
     * {@code Pattern}.
     */
    static final class RegexPathMatcher implements PathMatcher {

        private final Pattern pattern;

        private RegexPathMatcher(Pattern pattern) {
            this.pattern = Objects.requireNonNull(pattern);
        }

        @Override
        public boolean matches(Path path) {
            return pattern.matcher(path.toString()).matches();
        }

        @Override
        public String toString() {
            return "RegexPathMatcher{" + "pattern=" + pattern + '}';
        }
    }
}
