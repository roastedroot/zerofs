package io.roastedroot.zerofs;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Attribute provider that provides the {@link UserDefinedFileAttributeView} ("user"). Unlike most
 * other attribute providers, this one has no pre-defined set of attributes. Rather, it allows
 * arbitrary user defined attributes to be set (as {@code ByteBuffer} or {@code byte[]}) and read
 * (as {@code byte[]}).
 *
 * @author Colin Decker
 */
final class UserDefinedAttributeProvider extends AttributeProvider {

    UserDefinedAttributeProvider() {}

    @Override
    public String name() {
        return "user";
    }

    @Override
    public Set<String> fixedAttributes() {
        // no fixed set of attributes for this view
        return Set.of();
    }

    @Override
    public boolean supports(String attribute) {
        // any attribute name is supported
        return true;
    }

    @Override
    public Set<String> attributes(File file) {
        return userDefinedAttributes(file);
    }

    private static Set<String> userDefinedAttributes(File file) {
        Set<String> builder = new TreeSet<>();
        for (String attribute : file.getAttributeNames("user")) {
            builder.add(attribute);
        }
        return Set.copyOf(builder);
    }

    @Override
    public Object get(File file, String attribute) {
        Object value = file.getAttribute("user", attribute);
        if (value instanceof byte[]) {
            byte[] bytes = (byte[]) value;
            return bytes.clone();
        }
        return null;
    }

    @Override
    public void set(File file, String view, String attribute, Object value, boolean create) {
        Objects.requireNonNull(value);
        checkNotCreate(view, attribute, create);

        byte[] bytes;
        if (value instanceof byte[]) {
            bytes = ((byte[]) value).clone();
        } else if (value instanceof ByteBuffer) {
            // value instanceof ByteBuffer
            ByteBuffer buffer = (ByteBuffer) value;
            bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
        } else {
            throw invalidType(view, attribute, value, byte[].class, ByteBuffer.class);
        }

        file.setAttribute("user", attribute, bytes);
    }

    @Override
    public Class<UserDefinedFileAttributeView> viewType() {
        return UserDefinedFileAttributeView.class;
    }

    @Override
    public UserDefinedFileAttributeView view(
            FileLookup lookup, Map<String, FileAttributeView> inheritedViews) {
        return new View(lookup);
    }

    /** Implementation of {@link UserDefinedFileAttributeView}. */
    private static class View extends AbstractAttributeView
            implements UserDefinedFileAttributeView {

        public View(FileLookup lookup) {
            super(lookup);
        }

        @Override
        public String name() {
            return "user";
        }

        @Override
        public List<String> list() throws IOException {
            return List.copyOf(userDefinedAttributes(lookupFile()));
        }

        private byte[] getStoredBytes(String name) throws IOException {
            byte[] bytes = (byte[]) lookupFile().getAttribute(name(), name);
            if (bytes == null) {
                throw new IllegalArgumentException(
                        "attribute '" + name() + ":" + name + "' is not set");
            }
            return bytes;
        }

        @Override
        public int size(String name) throws IOException {
            return getStoredBytes(name).length;
        }

        @Override
        public int read(String name, ByteBuffer dst) throws IOException {
            byte[] bytes = getStoredBytes(name);
            dst.put(bytes);
            return bytes.length;
        }

        @Override
        public int write(String name, ByteBuffer src) throws IOException {
            byte[] bytes = new byte[src.remaining()];
            src.get(bytes);
            lookupFile().setAttribute(name(), name, bytes);
            return bytes.length;
        }

        @Override
        public void delete(String name) throws IOException {
            lookupFile().deleteAttribute(name(), name);
        }
    }
}
