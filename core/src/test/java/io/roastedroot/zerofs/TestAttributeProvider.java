package io.roastedroot.zerofs;

import java.io.IOException;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** @author Colin Decker */
public final class TestAttributeProvider extends AttributeProvider {

    private static final Set<String> ATTRIBUTES = Set.of("foo", "bar", "baz");

    @Override
    public String name() {
        return "test";
    }

    @Override
    public Set<String> inherits() {
        return Set.of("basic");
    }

    @Override
    public Set<String> fixedAttributes() {
        return ATTRIBUTES;
    }

    @Override
    public Map<String, ?> defaultValues(Map<String, ?> userDefaults) {
        Map<String, Object> result = new HashMap<>();

        Long bar = 0L;
        Integer baz = 1;
        if (userDefaults.containsKey("test:bar")) {
            bar = checkType("test", "bar", userDefaults.get("test:bar"), Number.class).longValue();
        }
        if (userDefaults.containsKey("test:baz")) {
            baz = checkType("test", "baz", userDefaults.get("test:baz"), Integer.class);
        }

        result.put("test:bar", bar);
        result.put("test:baz", baz);
        return Map.copyOf(result);
    }

    @Override
    public void set(File file, String view, String attribute, Object value, boolean create) {
        switch (attribute) {
            case "bar":
                checkNotCreate(view, attribute, create);
                file.setAttribute(
                        "test", "bar", checkType(view, attribute, value, Number.class).longValue());
                break;
            case "baz":
                file.setAttribute("test", "baz", checkType(view, attribute, value, Integer.class));
                break;
            default:
                throw unsettable(view, attribute, create);
        }
    }

    @Override
    public Object get(File file, String attribute) {
        if (attribute.equals("foo")) {
            return "hello";
        }
        return file.getAttribute("test", attribute);
    }

    @Override
    public Class<TestAttributeView> viewType() {
        return TestAttributeView.class;
    }

    @Override
    public TestAttributeView view(
            FileLookup lookup, Map<String, FileAttributeView> inheritedViews) {
        return new View(lookup, (BasicFileAttributeView) inheritedViews.get("basic"));
    }

    @Override
    public Class<TestAttributes> attributesType() {
        return TestAttributes.class;
    }

    @Override
    public TestAttributes readAttributes(File file) {
        return new Attributes(file);
    }

    static final class View implements TestAttributeView {

        private final FileLookup lookup;
        private final BasicFileAttributeView basicView;

        public View(FileLookup lookup, BasicFileAttributeView basicView) {
            this.lookup = Objects.requireNonNull(lookup);
            this.basicView = Objects.requireNonNull(basicView);
        }

        @Override
        public String name() {
            return "test";
        }

        @Override
        public Attributes readAttributes() throws IOException {
            return new Attributes(lookup.lookup());
        }

        @Override
        public void setTimes(
                FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime)
                throws IOException {
            basicView.setTimes(lastModifiedTime, lastAccessTime, createTime);
        }

        @Override
        public void setBar(long bar) throws IOException {
            lookup.lookup().setAttribute("test", "bar", bar);
        }

        @Override
        public void setBaz(int baz) throws IOException {
            lookup.lookup().setAttribute("test", "baz", baz);
        }
    }

    static final class Attributes implements TestAttributes {

        private final Long bar;
        private final Integer baz;

        public Attributes(File file) {
            this.bar = (Long) file.getAttribute("test", "bar");
            this.baz = (Integer) file.getAttribute("test", "baz");
        }

        @Override
        public String foo() {
            return "hello";
        }

        @Override
        public long bar() {
            return bar;
        }

        @Override
        public int baz() {
            return baz;
        }

        // BasicFileAttributes is just implemented here because readAttributes requires a subtype of
        // BasicFileAttributes -- methods are not implemented

        @Override
        public FileTime lastModifiedTime() {
            return null;
        }

        @Override
        public FileTime lastAccessTime() {
            return null;
        }

        @Override
        public FileTime creationTime() {
            return null;
        }

        @Override
        public boolean isRegularFile() {
            return false;
        }

        @Override
        public boolean isDirectory() {
            return false;
        }

        @Override
        public boolean isSymbolicLink() {
            return false;
        }

        @Override
        public boolean isOther() {
            return false;
        }

        @Override
        public long size() {
            return 0;
        }

        @Override
        public Object fileKey() {
            return null;
        }
    }
}
