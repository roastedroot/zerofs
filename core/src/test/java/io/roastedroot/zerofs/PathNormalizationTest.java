package io.roastedroot.zerofs;

import static io.roastedroot.zerofs.PathNormalization.CASE_FOLD_ASCII;
import static io.roastedroot.zerofs.PathNormalization.CASE_FOLD_UNICODE;
import static io.roastedroot.zerofs.PathNormalization.NFC;
import static io.roastedroot.zerofs.PathNormalization.NFD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link PathNormalization}.
 *
 * @author Colin Decker
 */
public class PathNormalizationTest {

    @Test
    public void testNone() {
        Set<PathNormalization> normalizations = Set.of();

        assertNormalizedEqual("foo", "foo", normalizations);
        assertNormalizedUnequal("Foo", "foo", normalizations);
        assertNormalizedUnequal("\u00c5", "\u212b", normalizations);
        assertNormalizedUnequal("Am\u00e9lie", "Ame\u0301lie", normalizations);
    }

    private static final String[][] CASE_FOLD_TEST_DATA = {
        {"foo", "fOo", "foO", "Foo", "FOO"},
        {"eﬃcient", "efficient", "eﬃcient", "Eﬃcient", "EFFICIENT"},
        {"ﬂour", "flour", "ﬂour", "Flour", "FLOUR"},
        {"poſt", "post", "poſt", "Poſt", "POST"},
        {"poﬅ", "post", "poﬅ", "Poﬅ", "POST"},
        {"ﬅop", "stop", "ﬅop", "Stop", "STOP"},
        {"tschüß", "tschüss", "tschüß", "Tschüß", "TSCHÜSS"},
        {"weiß", "weiss", "weiß", "Weiß", "WEISS"},
        {"WEIẞ", "weiss", "weiß", "Weiß", "WEIẞ"},
        {"στιγμας", "στιγμασ", "στιγμας", "Στιγμας", "ΣΤΙΓΜΑΣ"},
        {"ᾲ στο διάολο", "ὰι στο διάολο", "ᾲ στο διάολο", "Ὰͅ Στο Διάολο", "ᾺΙ ΣΤΟ ΔΙΆΟΛΟ"},
        {"Henry Ⅷ", "henry ⅷ", "henry ⅷ", "Henry Ⅷ", "HENRY Ⅷ"},
        {"I Work At Ⓚ", "i work at ⓚ", "i work at ⓚ", "I Work At Ⓚ", "I WORK AT Ⓚ"},
        {"ʀᴀʀᴇ", "ʀᴀʀᴇ", "ʀᴀʀᴇ", "Ʀᴀʀᴇ", "ƦᴀƦᴇ"},
        {"Ὰͅ", "ὰι", "ᾲ", "Ὰͅ", "ᾺΙ"}
    };

    // TODO: skip to avoid an heavy library dependency
    // let see if we should make it optional somehow
    //  @Test
    //  public void testCaseFold() {
    //    normalizations = Set.of(PathNormalization.CASE_FOLD_UNICODE);
    //
    //    for (String[] row : CASE_FOLD_TEST_DATA) {
    //      for (int i = 0; i < row.length; i++) {
    //        for (int j = i; j < row.length; j++) {
    //          assertNormalizedEqual(row[i], row[j], normalizations);
    //        }
    //      }
    //    }
    //  }

    @Test
    public void testCaseInsensitiveAscii() {
        Set<PathNormalization> normalizations = Set.of(CASE_FOLD_ASCII);

        String[] row = {"foo", "FOO", "fOo", "Foo"};
        for (int i = 0; i < row.length; i++) {
            for (int j = i; j < row.length; j++) {
                assertNormalizedEqual(row[i], row[j], normalizations);
            }
        }

        assertNormalizedUnequal("weiß", "weiss", normalizations);
    }

    private static final String[][] NORMALIZE_TEST_DATA = {
        {"\u00c5", "\u212b"}, // two forms of Å (one code point each)
        {"Am\u00e9lie", "Ame\u0301lie"} // two forms of Amélie (one composed, one decomposed)
    };

    @Test
    public void testNormalizeNfc() {
        Set<PathNormalization> normalizations = Set.of(NFC);

        for (String[] row : NORMALIZE_TEST_DATA) {
            for (int i = 0; i < row.length; i++) {
                for (int j = i; j < row.length; j++) {
                    assertNormalizedEqual(row[i], row[j], normalizations);
                }
            }
        }
    }

    @Test
    public void testNormalizeNfd() {
        Set<PathNormalization> normalizations = Set.of(NFD);

        for (String[] row : NORMALIZE_TEST_DATA) {
            for (int i = 0; i < row.length; i++) {
                for (int j = i; j < row.length; j++) {
                    assertNormalizedEqual(row[i], row[j], normalizations);
                }
            }
        }
    }

    private static final String[][] NORMALIZE_CASE_FOLD_TEST_DATA = {
        {"\u00c5", "\u00e5", "\u212b"},
        {"Am\u00e9lie", "Am\u00c9lie", "Ame\u0301lie", "AME\u0301LIE"}
    };

    @Test
    public void testNormalizeNfcCaseFold() {
        Set<PathNormalization> normalizations = Set.of(NFC, CASE_FOLD_UNICODE);

        for (String[] row : NORMALIZE_CASE_FOLD_TEST_DATA) {
            for (int i = 0; i < row.length; i++) {
                for (int j = i; j < row.length; j++) {
                    assertNormalizedEqual(row[i], row[j], normalizations);
                }
            }
        }
    }

    @Test
    public void testNormalizeNfdCaseFold() {
        Set<PathNormalization> normalizations = Set.of(NFD, CASE_FOLD_UNICODE);

        for (String[] row : NORMALIZE_CASE_FOLD_TEST_DATA) {
            for (int i = 0; i < row.length; i++) {
                for (int j = i; j < row.length; j++) {
                    assertNormalizedEqual(row[i], row[j], normalizations);
                }
            }
        }
    }

    private static final String[][] NORMALIZED_CASE_INSENSITIVE_ASCII_TEST_DATA = {
        {"\u00e5", "\u212b"},
        {"Am\u00e9lie", "AME\u0301LIE"}
    };

    @Test
    public void testNormalizeNfcCaseFoldAscii() {
        Set<PathNormalization> normalizations = new TreeSet();
        normalizations.add(NFC);
        normalizations.add(CASE_FOLD_ASCII);

        for (String[] row : NORMALIZED_CASE_INSENSITIVE_ASCII_TEST_DATA) {
            for (int i = 0; i < row.length; i++) {
                for (int j = i + 1; j < row.length; j++) {
                    assertNormalizedUnequal(row[i], row[j], normalizations);
                }
            }
        }
    }

    @Test
    public void testNormalizeNfdCaseFoldAscii() {
        Set<PathNormalization> normalizations = new TreeSet();
        normalizations.add(NFD);
        normalizations.add(CASE_FOLD_ASCII);

        for (String[] row : NORMALIZED_CASE_INSENSITIVE_ASCII_TEST_DATA) {
            for (int i = 0; i < row.length; i++) {
                for (int j = i + 1; j < row.length; j++) {
                    // since decomposition happens before case folding, the strings are equal when
                    // the
                    // decomposed ASCII letter is folded
                    assertNormalizedEqual(row[i], row[j], normalizations);
                }
            }
        }
    }

    // regex patterns offer loosely similar matching, but that's all

    @Test
    public void testNone_pattern() {
        Set<PathNormalization> normalizations = Set.of();
        assertNormalizedPatternMatches("foo", "foo", normalizations);
        assertNormalizedPatternDoesNotMatch("foo", "FOO", normalizations);
        assertNormalizedPatternDoesNotMatch("FOO", "foo", normalizations);
    }

    @Test
    public void testCaseFold_pattern() {
        Set<PathNormalization> normalizations = Set.of(CASE_FOLD_UNICODE);
        assertNormalizedPatternMatches("foo", "foo", normalizations);
        assertNormalizedPatternMatches("foo", "FOO", normalizations);
        assertNormalizedPatternMatches("FOO", "foo", normalizations);
        assertNormalizedPatternMatches("Am\u00e9lie", "AM\u00c9LIE", normalizations);
        assertNormalizedPatternMatches("Ame\u0301lie", "AME\u0301LIE", normalizations);
        assertNormalizedPatternDoesNotMatch("Am\u00e9lie", "Ame\u0301lie", normalizations);
        assertNormalizedPatternDoesNotMatch("AM\u00c9LIE", "AME\u0301LIE", normalizations);
        assertNormalizedPatternDoesNotMatch("Am\u00e9lie", "AME\u0301LIE", normalizations);
    }

    @Test
    public void testCaseFoldAscii_pattern() {
        Set<PathNormalization> normalizations = Set.of(CASE_FOLD_ASCII);
        assertNormalizedPatternMatches("foo", "foo", normalizations);
        assertNormalizedPatternMatches("foo", "FOO", normalizations);
        assertNormalizedPatternMatches("FOO", "foo", normalizations);
        assertNormalizedPatternMatches("Ame\u0301lie", "AME\u0301LIE", normalizations);
        assertNormalizedPatternDoesNotMatch("Am\u00e9lie", "AM\u00c9LIE", normalizations);
        assertNormalizedPatternDoesNotMatch("Am\u00e9lie", "Ame\u0301lie", normalizations);
        assertNormalizedPatternDoesNotMatch("AM\u00c9LIE", "AME\u0301LIE", normalizations);
        assertNormalizedPatternDoesNotMatch("Am\u00e9lie", "AME\u0301LIE", normalizations);
    }

    @Test
    public void testNormalizeNfc_pattern() {
        Set<PathNormalization> normalizations = Set.of(NFC);
        assertNormalizedPatternMatches("foo", "foo", normalizations);
        assertNormalizedPatternDoesNotMatch("foo", "FOO", normalizations);
        assertNormalizedPatternDoesNotMatch("FOO", "foo", normalizations);
        assertNormalizedPatternMatches("Am\u00e9lie", "Ame\u0301lie", normalizations);
        assertNormalizedPatternDoesNotMatch("Am\u00e9lie", "AME\u0301LIE", normalizations);
    }

    @Test
    public void testNormalizeNfd_pattern() {
        Set<PathNormalization> normalizations = Set.of(NFD);
        assertNormalizedPatternMatches("foo", "foo", normalizations);
        assertNormalizedPatternDoesNotMatch("foo", "FOO", normalizations);
        assertNormalizedPatternDoesNotMatch("FOO", "foo", normalizations);
        assertNormalizedPatternMatches("Am\u00e9lie", "Ame\u0301lie", normalizations);
        assertNormalizedPatternDoesNotMatch("Am\u00e9lie", "AME\u0301LIE", normalizations);
    }

    @Test
    public void testNormalizeNfcCaseFold_pattern() {
        Set<PathNormalization> normalizations = Set.of(NFC, CASE_FOLD_UNICODE);
        assertNormalizedPatternMatches("foo", "foo", normalizations);
        assertNormalizedPatternMatches("foo", "FOO", normalizations);
        assertNormalizedPatternMatches("FOO", "foo", normalizations);
        assertNormalizedPatternMatches("Am\u00e9lie", "AM\u00c9LIE", normalizations);
        assertNormalizedPatternMatches("Ame\u0301lie", "AME\u0301LIE", normalizations);
        assertNormalizedPatternMatches("Am\u00e9lie", "Ame\u0301lie", normalizations);
        assertNormalizedPatternMatches("AM\u00c9LIE", "AME\u0301LIE", normalizations);
        assertNormalizedPatternMatches("Am\u00e9lie", "AME\u0301LIE", normalizations);
    }

    @Test
    public void testNormalizeNfdCaseFold_pattern() {
        Set<PathNormalization> normalizations = Set.of(NFD, CASE_FOLD_ASCII, CASE_FOLD_UNICODE);
        assertNormalizedPatternMatches("foo", "foo", normalizations);
        assertNormalizedPatternMatches("foo", "FOO", normalizations);
        assertNormalizedPatternMatches("FOO", "foo", normalizations);
        assertNormalizedPatternMatches("Am\u00e9lie", "AM\u00c9LIE", normalizations);
        assertNormalizedPatternMatches("Ame\u0301lie", "AME\u0301LIE", normalizations);
        assertNormalizedPatternMatches("Am\u00e9lie", "Ame\u0301lie", normalizations);
        assertNormalizedPatternMatches("AM\u00c9LIE", "AME\u0301LIE", normalizations);
        assertNormalizedPatternMatches("Am\u00e9lie", "AME\u0301LIE", normalizations);
    }

    @Test
    public void testNormalizeNfcCaseFoldAscii_pattern() {
        Set<PathNormalization> normalizations = Set.of(NFC, CASE_FOLD_ASCII);
        assertNormalizedPatternMatches("foo", "foo", normalizations);
        assertNormalizedPatternMatches("foo", "FOO", normalizations);
        assertNormalizedPatternMatches("FOO", "foo", normalizations);

        // these are all a bit fuzzy as when CASE_INSENSITIVE is present but not UNICODE_CASE, ASCII
        // only strings are expected
        assertNormalizedPatternMatches("Ame\u0301lie", "AME\u0301LIE", normalizations);
        assertNormalizedPatternDoesNotMatch("Am\u00e9lie", "AM\u00c9LIE", normalizations);
        assertNormalizedPatternMatches("Am\u00e9lie", "Ame\u0301lie", normalizations);
        assertNormalizedPatternMatches("AM\u00c9LIE", "AME\u0301LIE", normalizations);
    }

    @Test
    public void testNormalizeNfdCaseFoldAscii_pattern() {
        Set<PathNormalization> normalizations = Set.of(NFD, CASE_FOLD_ASCII);
        assertNormalizedPatternMatches("foo", "foo", normalizations);
        assertNormalizedPatternMatches("foo", "FOO", normalizations);
        assertNormalizedPatternMatches("FOO", "foo", normalizations);

        // these are all a bit fuzzy as when CASE_INSENSITIVE is present but not UNICODE_CASE, ASCII
        // only strings are expected
        assertNormalizedPatternMatches("Ame\u0301lie", "AME\u0301LIE", normalizations);
        assertNormalizedPatternDoesNotMatch("Am\u00e9lie", "AM\u00c9LIE", normalizations);
        assertNormalizedPatternMatches("Am\u00e9lie", "Ame\u0301lie", normalizations);
        assertNormalizedPatternMatches("AM\u00c9LIE", "AME\u0301LIE", normalizations);
    }

    /** Asserts that the given strings normalize to the same string using the current normalizer. */
    private void assertNormalizedEqual(
            String first, String second, Set<PathNormalization> normalizations) {
        assertEquals(
                PathNormalization.normalize(first, normalizations),
                PathNormalization.normalize(second, normalizations));
    }

    /** Asserts that the given strings normalize to different strings using the current normalizer. */
    private void assertNormalizedUnequal(
            String first, String second, Set<PathNormalization> normalizations) {
        assertNotEquals(
                PathNormalization.normalize(first, normalizations),
                PathNormalization.normalize(second, normalizations));
    }

    /**
     * Asserts that the given strings match when one is compiled as a regex pattern using the current
     * normalizer and matched against the other.
     */
    private void assertNormalizedPatternMatches(
            String first, String second, Set<PathNormalization> normalizations) {
        Pattern pattern = PathNormalization.compilePattern(first, normalizations);
        assertTrue(
                pattern.matcher(second).matches(),
                "pattern '" + pattern + "' does not match '" + second + "'");

        pattern = PathNormalization.compilePattern(second, normalizations);
        assertTrue(
                pattern.matcher(first).matches(),
                "pattern '" + pattern + "' does not match '" + first + "'");
    }

    /**
     * Asserts that the given strings do not match when one is compiled as a regex pattern using the
     * current normalizer and matched against the other.
     */
    private void assertNormalizedPatternDoesNotMatch(
            String first, String second, Set<PathNormalization> normalizations) {
        Pattern pattern = PathNormalization.compilePattern(first, normalizations);
        assertFalse(
                pattern.matcher(second).matches(),
                "pattern '" + pattern + "' should not match '" + second + "'");

        pattern = PathNormalization.compilePattern(second, normalizations);
        assertFalse(
                pattern.matcher(first).matches(),
                "pattern '" + pattern + "' should not match '" + first + "'");
    }
}
