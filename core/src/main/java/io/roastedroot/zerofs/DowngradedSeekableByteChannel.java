package io.roastedroot.zerofs;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.util.Objects;

/**
 * A thin wrapper around a {@link FileChannel} that exists only to implement {@link
 * SeekableByteChannel} but NOT extend {@link FileChannel}.
 *
 * @author Colin Decker
 */
final class DowngradedSeekableByteChannel implements SeekableByteChannel {

    private final FileChannel channel;

    DowngradedSeekableByteChannel(FileChannel channel) {
        this.channel = Objects.requireNonNull(channel);
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        return channel.read(dst);
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        return channel.write(src);
    }

    @Override
    public long position() throws IOException {
        return channel.position();
    }

    @Override
    public SeekableByteChannel position(long newPosition) throws IOException {
        channel.position(newPosition);
        return this;
    }

    @Override
    public long size() throws IOException {
        return channel.size();
    }

    @Override
    public SeekableByteChannel truncate(long size) throws IOException {
        channel.truncate(size);
        return this;
    }

    @Override
    public boolean isOpen() {
        return channel.isOpen();
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }
}
