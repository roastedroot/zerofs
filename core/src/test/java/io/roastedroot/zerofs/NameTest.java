package io.roastedroot.zerofs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

public class NameTest {

    @Test
    public void testNames() {
        assertEquals(Name.create("foo", "foo"), Name.create("foo", "foo"));
        assertEquals(Name.create("FOO", "foo"), Name.create("foo", "foo"));
        assertNotEquals(Name.create("FOO", "foo"), Name.create("FOO", "FOO"));

        assertEquals(Name.create("a", "b").toString(), "a");
    }
}
