package io.roastedroot.zerofs;

import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Random;

/** Fake implementation of {@link FileTimeSource}. */
final class FakeFileTimeSource implements FileTimeSource {

    private final Random random = new Random(System.currentTimeMillis());
    private Instant now;

    FakeFileTimeSource() {
        randomize();
    }

    FakeFileTimeSource randomize() {
        now =
                Instant.ofEpochSecond(
                        random.longs(Instant.MIN.getEpochSecond(), Instant.MAX.getEpochSecond())
                                .findAny()
                                .getAsLong(),
                        random.nextInt(1_000_000_000));
        return this;
    }

    FakeFileTimeSource advance(Duration duration) {
        this.now = now.plus(duration);
        return this;
    }

    @Override
    public FileTime now() {
        return FileTime.from(now);
    }
}
