package io.roastedroot.zerofs;

import static io.roastedroot.zerofs.UserLookupService.createUserPrincipal;

import java.io.IOException;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileOwnerAttributeView;
import java.nio.file.attribute.UserPrincipal;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Attribute provider that provides the {@link FileOwnerAttributeView} ("owner").
 *
 * @author Colin Decker
 */
final class OwnerAttributeProvider extends AttributeProvider {

    private static final Set<String> ATTRIBUTES = Set.of("owner");

    private static final UserPrincipal DEFAULT_OWNER = createUserPrincipal("user");

    @Override
    public String name() {
        return "owner";
    }

    @Override
    public Set<String> fixedAttributes() {
        return ATTRIBUTES;
    }

    @Override
    public Map<String, ?> defaultValues(Map<String, ?> userProvidedDefaults) {
        Object userProvidedOwner = userProvidedDefaults.get("owner:owner");

        UserPrincipal owner = DEFAULT_OWNER;
        if (userProvidedOwner != null) {
            if (userProvidedOwner instanceof String) {
                owner = createUserPrincipal((String) userProvidedOwner);
            } else {
                throw invalidType(
                        "owner", "owner", userProvidedOwner, String.class, UserPrincipal.class);
            }
        }

        return Map.of("owner:owner", owner);
    }

    @Override
    public Object get(File file, String attribute) {
        if (attribute.equals("owner")) {
            return file.getAttribute("owner", "owner");
        }
        return null;
    }

    @Override
    public void set(File file, String view, String attribute, Object value, boolean create) {
        if (attribute.equals("owner")) {
            checkNotCreate(view, attribute, create);
            UserPrincipal user = checkType(view, attribute, value, UserPrincipal.class);
            // TODO(cgdecker): Do we really need to do this? Any reason not to allow any
            // UserPrincipal?
            if (!(user instanceof UserLookupService.ZeroFsUserPrincipal)) {
                user = createUserPrincipal(user.getName());
            }
            file.setAttribute("owner", "owner", user);
        }
    }

    @Override
    public Class<FileOwnerAttributeView> viewType() {
        return FileOwnerAttributeView.class;
    }

    @Override
    public FileOwnerAttributeView view(
            FileLookup lookup, Map<String, FileAttributeView> inheritedViews) {
        return new View(lookup);
    }

    /** Implementation of {@link FileOwnerAttributeView}. */
    private static final class View extends AbstractAttributeView
            implements FileOwnerAttributeView {

        public View(FileLookup lookup) {
            super(lookup);
        }

        @Override
        public String name() {
            return "owner";
        }

        @Override
        public UserPrincipal getOwner() throws IOException {
            return (UserPrincipal) lookupFile().getAttribute("owner", "owner");
        }

        @Override
        public void setOwner(UserPrincipal owner) throws IOException {
            lookupFile().setAttribute("owner", "owner", Objects.requireNonNull(owner));
        }
    }
}
