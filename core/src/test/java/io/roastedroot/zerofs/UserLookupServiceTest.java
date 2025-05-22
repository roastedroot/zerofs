package io.roastedroot.zerofs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.attribute.UserPrincipalNotFoundException;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link UserLookupService}.
 *
 * @author Colin Decker
 */
public class UserLookupServiceTest {

    @Test
    public void testUserLookupService() throws IOException {
        UserPrincipalLookupService service = new UserLookupService(true);
        UserPrincipal bob1 = service.lookupPrincipalByName("bob");
        UserPrincipal bob2 = service.lookupPrincipalByName("bob");
        UserPrincipal alice = service.lookupPrincipalByName("alice");

        assertEquals(bob2, bob1);
        assertNotEquals(alice, bob1);

        GroupPrincipal group1 = service.lookupPrincipalByGroupName("group");
        GroupPrincipal group2 = service.lookupPrincipalByGroupName("group");
        GroupPrincipal foo = service.lookupPrincipalByGroupName("foo");

        assertEquals(group2, group1);
        assertNotEquals(foo, group1);
    }

    @Test
    public void testServiceNotSupportingGroups() throws IOException {
        UserPrincipalLookupService service = new UserLookupService(false);

        try {
            service.lookupPrincipalByGroupName("group");
            fail();
        } catch (UserPrincipalNotFoundException expected) {
            assertEquals("group", expected.getName());
        }
    }
}
