package io.roastedroot.zerofs;

import java.io.IOException;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.UserPrincipal;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.attribute.UserPrincipalNotFoundException;
import java.util.Objects;

/**
 * {@link UserPrincipalLookupService} implementation.
 *
 * @author Colin Decker
 */
final class UserLookupService extends UserPrincipalLookupService {

    private final boolean supportsGroups;

    public UserLookupService(boolean supportsGroups) {
        this.supportsGroups = supportsGroups;
    }

    @Override
    public UserPrincipal lookupPrincipalByName(String name) {
        return createUserPrincipal(name);
    }

    @Override
    public GroupPrincipal lookupPrincipalByGroupName(String group) throws IOException {
        if (!supportsGroups) {
            throw new UserPrincipalNotFoundException(group); // required by spec
        }
        return createGroupPrincipal(group);
    }

    /** Creates a {@link UserPrincipal} for the given user name. */
    static UserPrincipal createUserPrincipal(String name) {
        return new ZeroFsUserPrincipal(name);
    }

    /** Creates a {@link GroupPrincipal} for the given group name. */
    static GroupPrincipal createGroupPrincipal(String name) {
        return new ZeroFsGroupPrincipal(name);
    }

    /** Base class for {@link UserPrincipal} and {@link GroupPrincipal} implementations. */
    private abstract static class NamedPrincipal implements UserPrincipal {

        protected final String name;

        private NamedPrincipal(String name) {
            this.name = Objects.requireNonNull(name);
        }

        @Override
        public final String getName() {
            return name;
        }

        @Override
        public final int hashCode() {
            return name.hashCode();
        }

        @Override
        public final String toString() {
            return name;
        }
    }

    /** {@link UserPrincipal} implementation. */
    static final class ZeroFsUserPrincipal extends NamedPrincipal {

        private ZeroFsUserPrincipal(String name) {
            super(name);
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof ZeroFsUserPrincipal
                    && getName().equals(((ZeroFsUserPrincipal) obj).getName());
        }
    }

    /** {@link GroupPrincipal} implementation. */
    static final class ZeroFsGroupPrincipal extends NamedPrincipal implements GroupPrincipal {

        private ZeroFsGroupPrincipal(String name) {
            super(name);
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof ZeroFsGroupPrincipal
                    && ((ZeroFsGroupPrincipal) obj).name.equals(name);
        }
    }
}
