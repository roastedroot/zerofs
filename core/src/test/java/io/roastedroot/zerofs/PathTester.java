package io.roastedroot.zerofs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

/** @author Colin Decker */
public final class PathTester {

    private final PathService pathService;
    private final String string;
    private String root;
    private List<String> names = List.of();

    public PathTester(PathService pathService, String string) {
        this.pathService = pathService;
        this.string = string;
    }

    public PathTester root(String root) {
        this.root = root;
        return this;
    }

    public PathTester names(Iterable<String> names) {
        Iterator<String> namesIter = names.iterator();
        List<String> tmpNames = new ArrayList<>();
        while (namesIter.hasNext()) {
            tmpNames.add(namesIter.next());
        }
        this.names = List.copyOf(tmpNames);
        return this;
    }

    public PathTester names(String... names) {
        return names(Arrays.asList(names));
    }

    public void test(String first, String... more) {
        Path path = pathService.parsePath(first, more);
        test(path);
    }

    public void test(Path path) {
        assertEquals(string, path.toString());

        testRoot(path);
        testNames(path);
        testParents(path);
        testStartsWith(path);
        testEndsWith(path);
        testSubpaths(path);
    }

    private void testRoot(Path path) {
        if (root != null) {
            assertTrue(path.isAbsolute(), path + ".isAbsolute() should be true");
            assertNotNull(path.getRoot(), path + ".getRoot() should not be null");
            assertEquals(root, path.getRoot().toString());
        } else {
            assertFalse(path.isAbsolute(), path + ".isAbsolute() should be false");
            assertNull(path.getRoot(), path + ".getRoot() should be null");
        }
    }

    private void testNames(Path path) {
        assertEquals(names.size(), path.getNameCount());
        assertEquals(names, names(path));
        for (int i = 0; i < names.size(); i++) {
            assertEquals(names.get(i), path.getName(i).toString());
            // don't test individual names if this is an individual name
            if (names.size() > 1) {
                new PathTester(pathService, names.get(i)).names(names.get(i)).test(path.getName(i));
            }
        }
        if (names.size() > 0) {
            String fileName = names.get(names.size() - 1);
            assertEquals(fileName, path.getFileName().toString());
            // don't test individual names if this is an individual name
            if (names.size() > 1) {
                new PathTester(pathService, fileName).names(fileName).test(path.getFileName());
            }
        }
    }

    private void testParents(Path path) {
        Path parent = path.getParent();

        if ((root != null && names.size() >= 1) || names.size() > 1) {
            assertNotNull(parent);
        }

        if (parent != null) {
            String parentName =
                    names.size() == 1 ? root : string.substring(0, string.lastIndexOf('/'));
            new PathTester(pathService, parentName)
                    .root(root)
                    .names(names.subList(0, names.size() - 1))
                    .test(parent);
        }
    }

    private void testSubpaths(Path path) {
        if (path.getRoot() == null) {
            assertEquals(path, path.subpath(0, path.getNameCount()));
        }

        if (path.getNameCount() > 1) {
            String stringWithoutRoot = root == null ? string : string.substring(root.length());

            // test start + 1 to end and start to end - 1 subpaths... this recursively tests all
            // subpaths
            // actually tests most possible subpaths multiple times but... eh
            Path startSubpath = path.subpath(1, path.getNameCount());
            List<String> startNames =
                    List.of(stringWithoutRoot.split("/")).subList(1, path.getNameCount());

            new PathTester(pathService, startNames.stream().collect(Collectors.joining("/")))
                    .names(startNames)
                    .test(startSubpath);

            Path endSubpath = path.subpath(0, path.getNameCount() - 1);
            List<String> endNames =
                    List.of(stringWithoutRoot.split("/")).subList(0, path.getNameCount() - 1);

            new PathTester(pathService, endNames.stream().collect(Collectors.joining("/")))
                    .names(endNames)
                    .test(endSubpath);
        }
    }

    private void testStartsWith(Path path) {
        // empty path doesn't start with any path
        if (root != null || !names.isEmpty()) {
            Path other = path;
            while (other != null) {
                assertTrue(
                        path.startsWith(other), path + ".startsWith(" + other + ") should be true");
                assertTrue(
                        path.startsWith(other.toString()),
                        path + ".startsWith(" + other + ") should be true");
                other = other.getParent();
            }
        }
    }

    private void testEndsWith(Path path) {
        // empty path doesn't start with any path
        if (root != null || !names.isEmpty()) {
            Path other = path;
            while (other != null) {
                assertTrue(path.endsWith(other), path + ".endsWith(" + other + ") should be true");
                assertTrue(
                        path.endsWith(other.toString()),
                        path + ".endsWith(" + other + ") should be true");
                if (other.getRoot() != null && other.getNameCount() > 0) {
                    other = other.subpath(0, other.getNameCount());
                } else if (other.getNameCount() > 1) {
                    other = other.subpath(1, other.getNameCount());
                } else {
                    other = null;
                }
            }
        }
    }

    private static List<String> names(Path path) {
        Iterator<Path> pathIter = path.iterator();
        List<String> result = new ArrayList<>();
        while (pathIter.hasNext()) {
            result.add(pathIter.next().toString());
        }
        return List.copyOf(result);
    }
}
