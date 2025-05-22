package io.roastedroot.zerofs;

import static io.roastedroot.zerofs.UserLookupService.createGroupPrincipal;

import java.io.IOException;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileOwnerAttributeView;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Attribute provider that provides the {@link PosixFileAttributeView} ("posix") and allows reading
 * of {@link PosixFileAttributes}.
 *
 * @author Colin Decker
 */
final class PosixAttributeProvider extends AttributeProvider {

    private static final Set<String> ATTRIBUTES = Set.of("group", "permissions");

    private static final Set<String> INHERITED_VIEWS = Set.of("basic", "owner");

    private static final GroupPrincipal DEFAULT_GROUP = createGroupPrincipal("group");
    private static final Set<PosixFilePermission> DEFAULT_PERMISSIONS =
            Set.copyOf(PosixFilePermissions.fromString("rw-r--r--"));

    @Override
    public String name() {
        return "posix";
    }

    @Override
    public Set<String> inherits() {
        return INHERITED_VIEWS;
    }

    @Override
    public Set<String> fixedAttributes() {
        return ATTRIBUTES;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<String, ?> defaultValues(Map<String, ?> userProvidedDefaults) {
        Object userProvidedGroup = userProvidedDefaults.get("posix:group");

        UserPrincipal group = DEFAULT_GROUP;
        if (userProvidedGroup != null) {
            if (userProvidedGroup instanceof String) {
                group = createGroupPrincipal((String) userProvidedGroup);
            } else {
                throw new IllegalArgumentException(
                        "invalid type "
                                + userProvidedGroup.getClass().getName()
                                + " for attribute 'posix:group': should be one of "
                                + String.class
                                + " or "
                                + GroupPrincipal.class);
            }
        }

        Object userProvidedPermissions = userProvidedDefaults.get("posix:permissions");

        Set<PosixFilePermission> permissions = DEFAULT_PERMISSIONS;
        if (userProvidedPermissions != null) {
            if (userProvidedPermissions instanceof String) {
                permissions =
                        Set.copyOf(
                                PosixFilePermissions.fromString((String) userProvidedPermissions));
            } else if (userProvidedPermissions instanceof Set) {
                permissions = toPermissions((Set<?>) userProvidedPermissions);
            } else {
                throw new IllegalArgumentException(
                        "invalid type "
                                + userProvidedPermissions.getClass().getName()
                                + " for attribute 'posix:permissions': should be one of "
                                + String.class
                                + " or "
                                + Set.class);
            }
        }

        return Map.of(
                "posix:group", group,
                "posix:permissions", permissions);
    }

    @Override
    public Object get(File file, String attribute) {
        switch (attribute) {
            case "group":
                return file.getAttribute("posix", "group");
            case "permissions":
                return file.getAttribute("posix", "permissions");
            default:
                return null;
        }
    }

    @Override
    public void set(File file, String view, String attribute, Object value, boolean create) {
        switch (attribute) {
            case "group":
                checkNotCreate(view, attribute, create);

                GroupPrincipal group = checkType(view, attribute, value, GroupPrincipal.class);
                if (!(group instanceof UserLookupService.ZeroFsGroupPrincipal)) {
                    group = createGroupPrincipal(group.getName());
                }
                file.setAttribute("posix", "group", group);
                break;
            case "permissions":
                file.setAttribute(
                        "posix",
                        "permissions",
                        toPermissions(checkType(view, attribute, value, Set.class)));
                break;
            default:
        }
    }

    @SuppressWarnings("unchecked") // only cast after checking each element's type
    private static Set<PosixFilePermission> toPermissions(Set<?> set) {
        Set<?> copy = Set.copyOf(set);
        for (Object obj : copy) {
            if (!(obj instanceof PosixFilePermission)) {
                throw new IllegalArgumentException(
                        "invalid element for attribute 'posix:permissions': "
                                + "should be Set<PosixFilePermission>, found element of type "
                                + obj.getClass());
            }
        }

        return Set.copyOf((Set<PosixFilePermission>) copy);
    }

    @Override
    public Class<PosixFileAttributeView> viewType() {
        return PosixFileAttributeView.class;
    }

    @Override
    public PosixFileAttributeView view(
            FileLookup lookup, Map<String, FileAttributeView> inheritedViews) {
        return new View(
                lookup,
                (BasicFileAttributeView) inheritedViews.get("basic"),
                (FileOwnerAttributeView) inheritedViews.get("owner"));
    }

    @Override
    public Class<PosixFileAttributes> attributesType() {
        return PosixFileAttributes.class;
    }

    @Override
    public PosixFileAttributes readAttributes(File file) {
        return new Attributes(file);
    }

    /** Implementation of {@link PosixFileAttributeView}. */
    private static class View extends AbstractAttributeView implements PosixFileAttributeView {

        private final BasicFileAttributeView basicView;
        private final FileOwnerAttributeView ownerView;

        protected View(
                FileLookup lookup,
                BasicFileAttributeView basicView,
                FileOwnerAttributeView ownerView) {
            super(lookup);
            this.basicView = Objects.requireNonNull(basicView);
            this.ownerView = Objects.requireNonNull(ownerView);
        }

        @Override
        public String name() {
            return "posix";
        }

        @Override
        public PosixFileAttributes readAttributes() throws IOException {
            return new Attributes(lookupFile());
        }

        @Override
        public void setTimes(
                FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime)
                throws IOException {
            basicView.setTimes(lastModifiedTime, lastAccessTime, createTime);
        }

        @Override
        public void setPermissions(Set<PosixFilePermission> perms) throws IOException {
            lookupFile().setAttribute("posix", "permissions", Set.copyOf(perms));
        }

        @Override
        public void setGroup(GroupPrincipal group) throws IOException {
            lookupFile().setAttribute("posix", "group", Objects.requireNonNull(group));
        }

        @Override
        public UserPrincipal getOwner() throws IOException {
            return ownerView.getOwner();
        }

        @Override
        public void setOwner(UserPrincipal owner) throws IOException {
            ownerView.setOwner(owner);
        }
    }

    /** Implementation of {@link PosixFileAttributes}. */
    static class Attributes extends BasicAttributeProvider.Attributes
            implements PosixFileAttributes {

        private final UserPrincipal owner;
        private final GroupPrincipal group;
        private final Set<PosixFilePermission> permissions;

        @SuppressWarnings("unchecked")
        protected Attributes(File file) {
            super(file);
            this.owner = (UserPrincipal) file.getAttribute("owner", "owner");
            this.group = (GroupPrincipal) file.getAttribute("posix", "group");
            this.permissions = (Set<PosixFilePermission>) file.getAttribute("posix", "permissions");
        }

        @Override
        public UserPrincipal owner() {
            return owner;
        }

        @Override
        public GroupPrincipal group() {
            return group;
        }

        @Override
        public Set<PosixFilePermission> permissions() {
            return permissions;
        }
    }
}
