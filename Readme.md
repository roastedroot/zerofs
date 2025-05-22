# ZeroFs

[![Build Status](https://github.com/roastedroot/zerofs/workflows/CI/badge.svg?branch=main)](https://github.com/roastedroot/zerofs/actions)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.roastedroot/zerofs/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.roastedroot/zerofs)

ZeroFs is a port of the original [Jimfs](https://github.com/google/jimfs) project, with the following key differences:

- Zero dependencies
- Java 11+

The goal is to make it the "go to" Virtual FileSystem for Java, especially to support the WASI layer of WASM payloads.
To achieve it we build on the amazing decade old [Jimfs](https://github.com/google/jimfs) foundation.

### Why

[Jimfs](https://github.com/google/jimfs) is a great library, but having Guava as a transitive dependency causes maintenance
burden in larger projects.

### Getting started

Add the ZeroFs dependency to your project, for example with Maven:

```xml
<dependency>
  <groupId>io.roastedroot</groupId>
  <artifactId>zerofs</artifactId>
</dependency>
```

## Basic use

The simplest way to use ZeroFs is to just get a new `FileSystem` instance from the `ZeroFs` class and
start using it:

```java
import io.roastedroot.zerofs.Configuration;
import io.roastedroot.zerofs.ZeroFs;
...

// For a simple file system with Unix-style paths and behavior:
FileSystem fs = Jimfs.newFileSystem(Configuration.unix());
Path foo = fs.getPath("/foo");
Files.createDirectory(foo);

Path hello = foo.resolve("hello.txt"); // /foo/hello.txt
Files.write(hello, ImmutableList.of("hello world"), StandardCharsets.UTF_8);
```

### What's supported?

ZeroFs supports almost all the APIs under `java.nio.file`. It supports:

- Creating, deleting, moving and copying files and directories.
- Reading and writing files with `FileChannel` or `SeekableByteChannel`, `InputStream`,
  `OutputStream`, etc.
- Symbolic links.
- Hard links to regular files.
- `SecureDirectoryStream`, for operations relative to an _open_ directory.
- Glob and regex path filtering with `PathMatcher`.
- Watching for changes to a directory with a `WatchService`.
- File attributes. Built-in attribute views that can be supported include "basic", "owner",
  "posix", "unix", "dos", "acl" and "user". Do note, however, that not all attribute views provide
  _useful_ attributes. For example, while setting and reading POSIX file permissions is possible
  with the "posix" view, those permissions will not actually affect the behavior of the file system.

ZeroFs also supports creating file systems that, for example, use Windows-style paths and (to an
extent) behavior. In general, however, file system behavior is modeled after UNIX and may not
exactly match any particular real file system or platform.

### Original License

```
Copyright 2013 Google Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
