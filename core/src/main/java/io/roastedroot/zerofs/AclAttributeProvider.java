package io.roastedroot.zerofs;

import java.io.IOException;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileOwnerAttributeView;
import java.nio.file.attribute.UserPrincipal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Attribute provider that provides the {@link AclFileAttributeView} ("acl").
 *
 * @author Colin Decker
 */
final class AclAttributeProvider extends AttributeProvider {

    private static final Set<String> ATTRIBUTES = Set.of("acl");

    private static final Set<String> INHERITED_VIEWS = Set.of("owner");

    private static final List<AclEntry> DEFAULT_ACL = List.of();

    @Override
    public String name() {
        return "acl";
    }

    @Override
    public Set<String> inherits() {
        return INHERITED_VIEWS;
    }

    @Override
    public Set<String> fixedAttributes() {
        return ATTRIBUTES;
    }

    @Override
    public Map<String, ?> defaultValues(Map<String, ?> userProvidedDefaults) {
        Object userProvidedAcl = userProvidedDefaults.get("acl:acl");

        List<AclEntry> acl = DEFAULT_ACL;
        if (userProvidedAcl != null) {
            acl = toAcl(checkType("acl", "acl", userProvidedAcl, List.class));
        }

        return Map.of("acl:acl", acl);
    }

    @Override
    public Object get(File file, String attribute) {
        if (attribute.equals("acl")) {
            return file.getAttribute("acl", "acl");
        }

        return null;
    }

    @Override
    public void set(File file, String view, String attribute, Object value, boolean create) {
        if (attribute.equals("acl")) {
            checkNotCreate(view, attribute, create);
            file.setAttribute("acl", "acl", toAcl(checkType(view, attribute, value, List.class)));
        }
    }

    @SuppressWarnings("unchecked") // only cast after checking each element's type
    private static List<AclEntry> toAcl(List<?> list) {
        List<?> copy = List.copyOf(list);
        for (Object obj : copy) {
            if (!(obj instanceof AclEntry)) {
                throw new IllegalArgumentException(
                        "invalid element for attribute 'acl:acl': should be List<AclEntry>, "
                                + "found element of type "
                                + obj.getClass());
            }
        }

        return (List<AclEntry>) copy;
    }

    @Override
    public Class<AclFileAttributeView> viewType() {
        return AclFileAttributeView.class;
    }

    @Override
    public AclFileAttributeView view(
            FileLookup lookup, Map<String, FileAttributeView> inheritedViews) {
        return new View(lookup, (FileOwnerAttributeView) inheritedViews.get("owner"));
    }

    /** Implementation of {@link AclFileAttributeView}. */
    private static final class View extends AbstractAttributeView implements AclFileAttributeView {

        private final FileOwnerAttributeView ownerView;

        public View(FileLookup lookup, FileOwnerAttributeView ownerView) {
            super(lookup);
            this.ownerView = Objects.requireNonNull(ownerView);
        }

        @Override
        public String name() {
            return "acl";
        }

        @SuppressWarnings("unchecked")
        @Override
        public List<AclEntry> getAcl() throws IOException {
            return (List<AclEntry>) lookupFile().getAttribute("acl", "acl");
        }

        @Override
        public void setAcl(List<AclEntry> acl) throws IOException {
            Objects.requireNonNull(acl);
            lookupFile().setAttribute("acl", "acl", List.copyOf(acl));
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
}
