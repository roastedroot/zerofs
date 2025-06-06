package io.roastedroot.zerofs;

import static io.roastedroot.zerofs.Util.toLowerCase;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Normalizations that can be applied to names in paths. Includes Unicode normalizations and
 * normalizations for case insensitive paths. These normalizations can be set in {@code
 * Configuration.Builder} when creating a ZeroFs file system instance and are automatically applied
 * to paths in the file system.
 *
 * @author Colin Decker
 */
public enum PathNormalization implements Function<String, String> {

    /** No normalization. */
    NONE(0) {
        @Override
        public String apply(String string) {
            return string;
        }
    },

    /** Unicode composed normalization (form {@linkplain java.text.Normalizer.Form#NFC NFC}). */
    NFC(Pattern.CANON_EQ) {
        @Override
        public String apply(String string) {
            return Normalizer.normalize(string, Normalizer.Form.NFC);
        }
    },

    /** Unicode decomposed normalization (form {@linkplain java.text.Normalizer.Form#NFD NFD}). */
    NFD(Pattern.CANON_EQ) {
        @Override
        public String apply(String string) {
            return Normalizer.normalize(string, Normalizer.Form.NFD);
        }
    },

    /*
     * Some notes on case folding/case insensitivity of file systems:
     *
     * In general (I don't have any counterexamples) case-insensitive file systems handle
     * their case insensitivity in a locale-independent way. NTFS, for example, writes a
     * special case mapping file ($UpCase) to the file system when it's first initialized,
     * and this is not affected by the locale of either the user or the copy of Windows
     * being used. This means that it will NOT handle i/I-variants in filenames as you'd
     * expect for Turkic languages, even for a Turkish user who has installed a Turkish
     * copy of Windows.
     */

    /** Unicode case folding for case insensitive paths. Requires ICU4J on the classpath. */
    @Deprecated(since = "broken")
    CASE_FOLD_UNICODE(Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE) {
        @Override
        public String apply(String string) {
            // TODO: is this a viable implementation?
            return string.toLowerCase(Locale.ROOT);
        }
    },

    /** ASCII case folding for simple case insensitive paths. */
    CASE_FOLD_ASCII(Pattern.CASE_INSENSITIVE) {
        @Override
        public String apply(String string) {
            return toLowerCase(string);
        }
    };

    private final int patternFlags;

    private PathNormalization(int patternFlags) {
        this.patternFlags = patternFlags;
    }

    /** Applies this normalization to the given string, returning the normalized result. */
    @Override
    public abstract String apply(String string);

    /**
     * Returns the flags that should be used when creating a regex {@link Pattern} in order to
     * approximate this normalization.
     */
    public int patternFlags() {
        return patternFlags;
    }

    /**
     * Applies the given normalizations to the given string in order, returning the normalized result.
     */
    public static String normalize(String string, Iterable<PathNormalization> normalizations) {
        String result = string;
        for (PathNormalization normalization : normalizations) {
            result = normalization.apply(result);
        }
        return result;
    }

    /** Compiles a regex pattern using flags based on the given normalizations. */
    public static Pattern compilePattern(String regex, Iterable<PathNormalization> normalizations) {
        int flags = 0;
        for (PathNormalization normalization : normalizations) {
            flags |= normalization.patternFlags();
        }
        return Pattern.compile(regex, flags);
    }
}
