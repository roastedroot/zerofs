package io.roastedroot.zerofs;

import static java.nio.file.StandardOpenOption.READ;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Tests for {@link Options}. */
public class OptionsTest {

    @Test
    public void testGetOptionsForChannel_noFollowLinks() {
        OpenOption lookalike =
                new OpenOption() {
                    @Override
                    public int hashCode() {
                        return LinkOption.NOFOLLOW_LINKS.hashCode();
                    }
                };

        assertEquals(Set.of(READ), Options.getOptionsForChannel(Set.of(READ, lookalike)));
        assertEquals(
                Set.of(READ, LinkOption.NOFOLLOW_LINKS),
                Options.getOptionsForChannel(Set.of(READ, LinkOption.NOFOLLOW_LINKS)));
    }
}
