package io.roastedroot.zerofs;

import java.io.IOException;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileTime;
import java.util.Map;
import java.util.Set;

/**
 * Attribute provider that provides attributes common to all file systems, the {@link
 * BasicFileAttributeView} ("basic" or no view prefix), and allows the reading of {@link
 * BasicFileAttributes}.
 *
 * @author Colin Decker
 */
final class BasicAttributeProvider extends AttributeProvider {

    private static final Set<String> ATTRIBUTES =
            Set.of(
                    "size",
                    "fileKey",
                    "isDirectory",
                    "isRegularFile",
                    "isSymbolicLink",
                    "isOther",
                    "creationTime",
                    "lastAccessTime",
                    "lastModifiedTime");

    @Override
    public String name() {
        return "basic";
    }

    @Override
    public Set<String> fixedAttributes() {
        return ATTRIBUTES;
    }

    @Override
    public Object get(File file, String attribute) {
        switch (attribute) {
            case "size":
                return file.size();
            case "fileKey":
                return file.id();
            case "isDirectory":
                return file.isDirectory();
            case "isRegularFile":
                return file.isRegularFile();
            case "isSymbolicLink":
                return file.isSymbolicLink();
            case "isOther":
                return !file.isDirectory() && !file.isRegularFile() && !file.isSymbolicLink();
            case "creationTime":
                return file.getCreationTime();
            case "lastAccessTime":
                return file.getLastAccessTime();
            case "lastModifiedTime":
                return file.getLastModifiedTime();
            default:
                return null;
        }
    }

    @Override
    public void set(File file, String view, String attribute, Object value, boolean create) {
        switch (attribute) {
            case "creationTime":
                checkNotCreate(view, attribute, create);
                file.setCreationTime(checkType(view, attribute, value, FileTime.class));
                break;
            case "lastAccessTime":
                checkNotCreate(view, attribute, create);
                file.setLastAccessTime(checkType(view, attribute, value, FileTime.class));
                break;
            case "lastModifiedTime":
                checkNotCreate(view, attribute, create);
                file.setLastModifiedTime(checkType(view, attribute, value, FileTime.class));
                break;
            case "size":
            case "fileKey":
            case "isDirectory":
            case "isRegularFile":
            case "isSymbolicLink":
            case "isOther":
                throw unsettable(view, attribute, create);
            default:
        }
    }

    @Override
    public Class<BasicFileAttributeView> viewType() {
        return BasicFileAttributeView.class;
    }

    @Override
    public BasicFileAttributeView view(
            FileLookup lookup, Map<String, FileAttributeView> inheritedViews) {
        return new View(lookup);
    }

    @Override
    public Class<BasicFileAttributes> attributesType() {
        return BasicFileAttributes.class;
    }

    @Override
    public BasicFileAttributes readAttributes(File file) {
        return new Attributes(file);
    }

    /** Implementation of {@link BasicFileAttributeView}. */
    private static final class View extends AbstractAttributeView
            implements BasicFileAttributeView {

        protected View(FileLookup lookup) {
            super(lookup);
        }

        @Override
        public String name() {
            return "basic";
        }

        @Override
        public BasicFileAttributes readAttributes() throws IOException {
            return new Attributes(lookupFile());
        }

        @Override
        public void setTimes(
                FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime)
                throws IOException {
            File file = lookupFile();

            if (lastModifiedTime != null) {
                file.setLastModifiedTime(lastModifiedTime);
            }

            if (lastAccessTime != null) {
                file.setLastAccessTime(lastAccessTime);
            }

            if (createTime != null) {
                file.setCreationTime(createTime);
            }
        }
    }

    /** Implementation of {@link BasicFileAttributes}. */
    static class Attributes implements BasicFileAttributes {

        private final FileTime lastModifiedTime;
        private final FileTime lastAccessTime;
        private final FileTime creationTime;
        private final boolean regularFile;
        private final boolean directory;
        private final boolean symbolicLink;
        private final long size;
        private final Object fileKey;

        protected Attributes(File file) {
            this.lastModifiedTime = file.getLastModifiedTime();
            this.lastAccessTime = file.getLastAccessTime();
            this.creationTime = file.getCreationTime();
            this.regularFile = file.isRegularFile();
            this.directory = file.isDirectory();
            this.symbolicLink = file.isSymbolicLink();
            this.size = file.size();
            this.fileKey = file.id();
        }

        @Override
        public FileTime lastModifiedTime() {
            return lastModifiedTime;
        }

        @Override
        public FileTime lastAccessTime() {
            return lastAccessTime;
        }

        @Override
        public FileTime creationTime() {
            return creationTime;
        }

        @Override
        public boolean isRegularFile() {
            return regularFile;
        }

        @Override
        public boolean isDirectory() {
            return directory;
        }

        @Override
        public boolean isSymbolicLink() {
            return symbolicLink;
        }

        @Override
        public boolean isOther() {
            return false;
        }

        @Override
        public long size() {
            return size;
        }

        @Override
        public Object fileKey() {
            return fileKey;
        }
    }
}
