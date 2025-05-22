package io.roastedroot.zerofs;

import java.io.IOException;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.DosFileAttributes;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileTime;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Attribute provider that provides the {@link DosFileAttributeView} ("dos") and allows the reading
 * of {@link DosFileAttributes}.
 *
 * @author Colin Decker
 */
final class DosAttributeProvider extends AttributeProvider {

    private static final Set<String> ATTRIBUTES = Set.of("readonly", "hidden", "archive", "system");

    private static final Set<String> INHERITED_VIEWS = Set.of("basic", "owner");

    @Override
    public String name() {
        return "dos";
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
        return Map.of(
                "dos:readonly", getDefaultValue("dos:readonly", userProvidedDefaults),
                "dos:hidden", getDefaultValue("dos:hidden", userProvidedDefaults),
                "dos:archive", getDefaultValue("dos:archive", userProvidedDefaults),
                "dos:system", getDefaultValue("dos:system", userProvidedDefaults));
    }

    private static Boolean getDefaultValue(String attribute, Map<String, ?> userProvidedDefaults) {
        Object userProvidedValue = userProvidedDefaults.get(attribute);
        if (userProvidedValue != null) {
            return checkType("dos", attribute, userProvidedValue, Boolean.class);
        }

        return false;
    }

    @Override
    public Object get(File file, String attribute) {
        if (ATTRIBUTES.contains(attribute)) {
            return file.getAttribute("dos", attribute);
        }

        return null;
    }

    @Override
    public void set(File file, String view, String attribute, Object value, boolean create) {
        if (supports(attribute)) {
            checkNotCreate(view, attribute, create);
            file.setAttribute("dos", attribute, checkType(view, attribute, value, Boolean.class));
        }
    }

    @Override
    public Class<DosFileAttributeView> viewType() {
        return DosFileAttributeView.class;
    }

    @Override
    public DosFileAttributeView view(
            FileLookup lookup, Map<String, FileAttributeView> inheritedViews) {
        return new View(lookup, (BasicFileAttributeView) inheritedViews.get("basic"));
    }

    @Override
    public Class<DosFileAttributes> attributesType() {
        return DosFileAttributes.class;
    }

    @Override
    public DosFileAttributes readAttributes(File file) {
        return new Attributes(file);
    }

    /** Implementation of {@link DosFileAttributeView}. */
    private static final class View extends AbstractAttributeView implements DosFileAttributeView {

        private final BasicFileAttributeView basicView;

        public View(FileLookup lookup, BasicFileAttributeView basicView) {
            super(lookup);
            this.basicView = Objects.requireNonNull(basicView);
        }

        @Override
        public String name() {
            return "dos";
        }

        @Override
        public DosFileAttributes readAttributes() throws IOException {
            return new Attributes(lookupFile());
        }

        @Override
        public void setTimes(
                FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime)
                throws IOException {
            basicView.setTimes(lastModifiedTime, lastAccessTime, createTime);
        }

        @Override
        public void setReadOnly(boolean value) throws IOException {
            lookupFile().setAttribute("dos", "readonly", value);
        }

        @Override
        public void setHidden(boolean value) throws IOException {
            lookupFile().setAttribute("dos", "hidden", value);
        }

        @Override
        public void setSystem(boolean value) throws IOException {
            lookupFile().setAttribute("dos", "system", value);
        }

        @Override
        public void setArchive(boolean value) throws IOException {
            lookupFile().setAttribute("dos", "archive", value);
        }
    }

    /** Implementation of {@link DosFileAttributes}. */
    static class Attributes extends BasicAttributeProvider.Attributes implements DosFileAttributes {

        private final boolean readOnly;
        private final boolean hidden;
        private final boolean archive;
        private final boolean system;

        protected Attributes(File file) {
            super(file);
            this.readOnly = (boolean) file.getAttribute("dos", "readonly");
            this.hidden = (boolean) file.getAttribute("dos", "hidden");
            this.archive = (boolean) file.getAttribute("dos", "archive");
            this.system = (boolean) file.getAttribute("dos", "system");
        }

        @Override
        public boolean isReadOnly() {
            return readOnly;
        }

        @Override
        public boolean isHidden() {
            return hidden;
        }

        @Override
        public boolean isArchive() {
            return archive;
        }

        @Override
        public boolean isSystem() {
            return system;
        }
    }
}
