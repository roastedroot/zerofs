package io.roastedroot.zerofs.benchmarks;

import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

import io.roastedroot.zerofs.Configuration;
import io.roastedroot.zerofs.ZeroFs;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

/** Per-operation costs of the calls a WASI layer makes, on the configuration Endive uses. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Thread)
public class FileSystemBenchmark {

    private FileSystem fs;
    private Path file;
    private Path missing;
    private Path bigDir;
    private FileChannel channel;
    private ByteBuffer buffer;

    @Setup
    public void setUp() throws IOException {
        fs =
                ZeroFs.newFileSystem(
                        Configuration.unix().toBuilder().setAttributeViews("unix").build());
        Path dir = fs.getPath("/work/dir");
        Files.createDirectories(dir);
        file = dir.resolve("file.txt");
        Files.write(file, new byte[1024 * 1024]);
        missing = dir.resolve("missing.txt");

        bigDir = fs.getPath("/big");
        Files.createDirectory(bigDir);
        for (int i = 0; i < 10_000; i++) {
            Files.createFile(bigDir.resolve("file" + i));
        }

        channel = FileChannel.open(file, READ, WRITE);
        buffer = ByteBuffer.allocate(4096);
    }

    @TearDown
    public void tearDown() throws IOException {
        channel.close();
        fs.close();
    }

    @Benchmark
    public boolean existsMissing() {
        return Files.exists(missing);
    }

    @Benchmark
    public boolean existsFile() {
        return Files.exists(file);
    }

    @Benchmark
    public Map<String, Object> readUnixAttributes() throws IOException {
        return Files.readAttributes(file, "unix:*");
    }

    @Benchmark
    public BasicFileAttributes readBasicAttributes() throws IOException {
        return Files.readAttributes(file, BasicFileAttributes.class);
    }

    @Benchmark
    public int pread4k() throws IOException {
        buffer.clear();
        return channel.read(buffer, 8192);
    }

    @Benchmark
    public int pwrite4k() throws IOException {
        buffer.clear();
        return channel.write(buffer, 8192);
    }

    @Benchmark
    public long seekAndTell() throws IOException {
        channel.position(8192);
        return channel.position();
    }

    @Benchmark
    public void openClose() throws IOException {
        FileChannel.open(file, READ).close();
    }

    @Benchmark
    public Path parseAndResolve() {
        return fs.getPath("/work").resolve("dir/file.txt");
    }

    @Benchmark
    public int listBigDirectory() throws IOException {
        int count = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(bigDir)) {
            for (Path unused : stream) {
                count++;
            }
        }
        return count;
    }
}
