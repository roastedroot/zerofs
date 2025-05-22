package io.roastedroot.zerofs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link AttributeService}.
 *
 * @author Colin Decker
 */
public class AttributeServiceTest {

    private AttributeService service;

    private final FakeFileTimeSource fileTimeSource = new FakeFileTimeSource();

    @BeforeEach
    public void setUp() {
        Set<AttributeProvider> providers =
                Set.of(
                        StandardAttributeProviders.get("basic"),
                        StandardAttributeProviders.get("owner"),
                        new TestAttributeProvider());
        service = new AttributeService(providers, Map.<String, Object>of());
    }

    private File createFile() {
        return Directory.create(0, fileTimeSource.now());
    }

    @Test
    public void testSupportedFileAttributeViews() {
        assertEquals(Set.of("basic", "test", "owner"), service.supportedFileAttributeViews());
    }

    @Test
    public void testSupportsFileAttributeView() {
        assertTrue(service.supportsFileAttributeView(BasicFileAttributeView.class));
        assertTrue(service.supportsFileAttributeView(TestAttributeView.class));
        assertFalse(service.supportsFileAttributeView(PosixFileAttributeView.class));
    }

    @Test
    public void testSetInitialAttributes() {
        File file = createFile();
        service.setInitialAttributes(file);

        assertEquals(Set.of("bar", "baz"), file.getAttributeNames("test"));
        assertEquals(Set.of("owner"), file.getAttributeNames("owner"));

        assertTrue(service.getAttribute(file, "basic:lastModifiedTime") instanceof FileTime);
        assertEquals(0L, file.getAttribute("test", "bar"));
        assertEquals(1, file.getAttribute("test", "baz"));
    }

    @Test
    public void testGetAttribute() {
        File file = createFile();
        service.setInitialAttributes(file);

        assertEquals("hello", service.getAttribute(file, "test:foo"));
        assertEquals("hello", service.getAttribute(file, "test", "foo"));
        assertEquals(false, service.getAttribute(file, "basic:isRegularFile"));
        assertEquals(true, service.getAttribute(file, "isDirectory"));
        assertEquals(1, service.getAttribute(file, "test:baz"));
    }

    @Test
    public void testGetAttribute_fromInheritedProvider() {
        File file = createFile();
        assertEquals(false, service.getAttribute(file, "test:isRegularFile"));
        assertEquals(true, service.getAttribute(file, "test:isDirectory"));
        assertEquals(0, service.getAttribute(file, "test", "fileKey"));
    }

    @Test
    public void testGetAttribute_failsForAttributesNotDefinedByProvider() {
        File file = createFile();
        try {
            service.getAttribute(file, "test:blah");
            throw new AssertionError();
        } catch (IllegalArgumentException expected) {
        }

        try {
            // baz is defined by "test", but basic doesn't inherit test
            service.getAttribute(file, "basic", "baz");
            throw new AssertionError();
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void testSetAttribute() {
        File file = createFile();
        service.setAttribute(file, "test:bar", 10L, false);
        assertEquals(10L, file.getAttribute("test", "bar"));

        service.setAttribute(file, "test:baz", 100, false);
        assertEquals(100, file.getAttribute("test", "baz"));
    }

    @Test
    public void testSetAttribute_forInheritedProvider() {
        File file = createFile();
        service.setAttribute(file, "test:lastModifiedTime", FileTime.fromMillis(0), false);
        assertNull(file.getAttribute("test", "lastModifiedTime"));
        assertEquals(FileTime.fromMillis(0), service.getAttribute(file, "basic:lastModifiedTime"));
    }

    @Test
    public void testSetAttribute_withAlternateAcceptedType() {
        File file = createFile();
        service.setAttribute(file, "test:bar", 10F, false);
        assertEquals(10L, file.getAttribute("test", "bar"));

        service.setAttribute(file, "test:bar", BigInteger.valueOf(123), false);
        assertEquals(123L, file.getAttribute("test", "bar"));
    }

    @Test
    public void testSetAttribute_onCreate() {
        File file = createFile();
        service.setInitialAttributes(file, new BasicFileAttribute<>("test:baz", 123));
        assertEquals(123, file.getAttribute("test", "baz"));
    }

    @Test
    public void testSetAttribute_failsForAttributesNotDefinedByProvider() {
        File file = createFile();
        service.setInitialAttributes(file);

        try {
            service.setAttribute(file, "test:blah", "blah", false);
            fail();
        } catch (UnsupportedOperationException expected) {
        }

        try {
            // baz is defined by "test", but basic doesn't inherit test
            service.setAttribute(file, "basic:baz", 5, false);
            fail();
        } catch (UnsupportedOperationException expected) {
        }

        assertEquals(1, file.getAttribute("test", "baz"));
    }

    @Test
    public void testSetAttribute_failsForArgumentThatIsNotOfCorrectType() {
        File file = createFile();
        service.setInitialAttributes(file);
        try {
            service.setAttribute(file, "test:bar", "wrong", false);
            fail();
        } catch (IllegalArgumentException expected) {
        }

        assertEquals(0L, file.getAttribute("test", "bar"));
    }

    @Test
    public void testSetAttribute_failsForNullArgument() {
        File file = createFile();
        service.setInitialAttributes(file);
        try {
            service.setAttribute(file, "test:bar", null, false);
            fail();
        } catch (NullPointerException expected) {
        }

        assertEquals(0L, file.getAttribute("test", "bar"));
    }

    @Test
    public void testSetAttribute_failsForAttributeThatIsNotSettable() {
        File file = createFile();
        try {
            service.setAttribute(file, "test:foo", "world", false);
            fail();
        } catch (IllegalArgumentException expected) {
        }

        assertNull(file.getAttribute("test", "foo"));
    }

    @Test
    public void testSetAttribute_onCreate_failsForAttributeThatIsNotSettableOnCreate() {
        File file = createFile();
        try {
            service.setInitialAttributes(file, new BasicFileAttribute<>("test:foo", "world"));
            fail();
        } catch (UnsupportedOperationException expected) {
            // it turns out that UOE should be thrown on create even if the attribute isn't settable
            // under any circumstances
        }

        try {
            service.setInitialAttributes(file, new BasicFileAttribute<>("test:bar", 5));
            fail();
        } catch (UnsupportedOperationException expected) {
        }
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void testGetFileAttributeView() throws IOException {
        final File file = createFile();
        service.setInitialAttributes(file);

        FileLookup fileLookup =
                new FileLookup() {
                    @Override
                    public File lookup() throws IOException {
                        return file;
                    }
                };

        assertNotNull(service.getFileAttributeView(fileLookup, TestAttributeView.class));
        assertNotNull(service.getFileAttributeView(fileLookup, BasicFileAttributeView.class));

        TestAttributes attrs =
                service.getFileAttributeView(fileLookup, TestAttributeView.class).readAttributes();
        assertEquals("hello", attrs.foo());
        assertEquals(0, attrs.bar());
        assertEquals(1, attrs.baz());
    }

    @Test
    public void testGetFileAttributeView_isNullForUnsupportedView() {
        final File file = createFile();
        FileLookup fileLookup =
                new FileLookup() {
                    @Override
                    public File lookup() throws IOException {
                        return file;
                    }
                };
        assertNull(service.getFileAttributeView(fileLookup, PosixFileAttributeView.class));
    }

    @Test
    public void testReadAttributes_asMap() {
        File file = createFile();
        service.setInitialAttributes(file);

        Map<String, Object> map = service.readAttributes(file, "test:foo,bar,baz");
        assertEquals(Map.of("foo", "hello", "bar", 0L, "baz", 1), map);

        FileTime time = fileTimeSource.now();

        map = service.readAttributes(file, "test:*");
        Map<String, Object> expected1 = new HashMap();
        expected1.put("foo", "hello");
        expected1.put("bar", 0L);
        expected1.put("baz", 1);
        expected1.put("fileKey", 0);
        expected1.put("isDirectory", true);
        expected1.put("isRegularFile", false);
        expected1.put("isSymbolicLink", false);
        expected1.put("isOther", false);
        expected1.put("size", 0L);
        expected1.put("lastModifiedTime", time);
        expected1.put("lastAccessTime", time);
        expected1.put("creationTime", time);
        assertEquals(expected1, map);

        Map<String, Object> expected2 = new HashMap();
        expected2.put("fileKey", 0);
        expected2.put("isDirectory", true);
        expected2.put("isRegularFile", false);
        expected2.put("isSymbolicLink", false);
        expected2.put("isOther", false);
        expected2.put("size", 0L);
        expected2.put("lastModifiedTime", time);
        expected2.put("lastAccessTime", time);
        expected2.put("creationTime", time);
        map = service.readAttributes(file, "basic:*");
        assertEquals(expected2, map);
    }

    @Test
    public void testReadAttributes_asMap_failsForInvalidAttributes() {
        File file = createFile();
        try {
            service.readAttributes(file, "basic:fileKey,isOther,*,creationTime");
            fail();
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("invalid attributes"));
        }

        try {
            service.readAttributes(file, "basic:fileKey,isOther,foo");
            fail();
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("invalid attribute"));
        }
    }

    @Test
    public void testReadAttributes_asObject() {
        File file = createFile();
        service.setInitialAttributes(file);

        BasicFileAttributes basicAttrs = service.readAttributes(file, BasicFileAttributes.class);
        assertEquals(0, basicAttrs.fileKey());
        assertTrue(basicAttrs.isDirectory());
        assertFalse(basicAttrs.isRegularFile());

        TestAttributes testAttrs = service.readAttributes(file, TestAttributes.class);
        assertEquals("hello", testAttrs.foo());
        assertEquals(0, testAttrs.bar());
        assertEquals(1, testAttrs.baz());

        file.setAttribute("test", "baz", 100);
        assertEquals(100, service.readAttributes(file, TestAttributes.class).baz());
    }

    @Test
    public void testReadAttributes_failsForUnsupportedAttributesType() {
        File file = createFile();
        try {
            service.readAttributes(file, PosixFileAttributes.class);
            fail();
        } catch (UnsupportedOperationException expected) {
        }
    }

    @Test
    public void testIllegalAttributeFormats() {
        File file = createFile();
        try {
            service.getAttribute(file, ":bar");
            fail();
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("attribute format"));
        }

        try {
            service.getAttribute(file, "test:");
            fail();
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("attribute format"));
        }

        try {
            service.getAttribute(file, "basic:test:isDirectory");
            fail();
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("attribute format"));
        }

        try {
            service.getAttribute(file, "basic:fileKey,size");
            fail();
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("single attribute"));
        }
    }
}
