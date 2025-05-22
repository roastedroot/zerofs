package io.roastedroot.zerofs;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.util.Iterator;
import java.util.Objects;

/**
 * A thin wrapper around a {@link SecureDirectoryStream} that exists only to implement {@link
 * DirectoryStream} and NOT implement {@link SecureDirectoryStream}.
 *
 * @author Colin Decker
 */
final class DowngradedDirectoryStream implements DirectoryStream<Path> {

    private final SecureDirectoryStream<Path> secureDirectoryStream;

    DowngradedDirectoryStream(SecureDirectoryStream<Path> secureDirectoryStream) {
        this.secureDirectoryStream = Objects.requireNonNull(secureDirectoryStream);
    }

    @Override
    public Iterator<Path> iterator() {
        return secureDirectoryStream.iterator();
    }

    @Override
    public void close() throws IOException {
        secureDirectoryStream.close();
    }
}
