package io.roastedroot.zerofs;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TimeZone;

/**
 * {@code URLConnection} implementation.
 *
 * @author Colin Decker
 */
final class PathURLConnection extends URLConnection {

    /*
     * This implementation should be able to work for any proper file system implementation... it
     * might be useful to release it and make it usable by other file systems.
     */

    private static final String HTTP_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss \'GMT\'";
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private InputStream stream;
    private Map<String, List<String>> headers = new HashMap<>();

    PathURLConnection(URL url) {
        super(Objects.requireNonNull(url));
    }

    public static <T> T firstNonNull(T first, T second) {
        if (first != null) {
            return first;
        }
        if (second != null) {
            return second;
        }
        throw new NullPointerException("Both parameters are null");
    }

    @Override
    public void connect() throws IOException {
        if (stream != null) {
            return;
        }

        Path path = Paths.get(toUri(url));

        long length;
        if (Files.isDirectory(path)) {
            // Match File URL behavior for directories by having the stream contain the filenames in
            // the directory separated by newlines.
            StringBuilder builder = new StringBuilder();
            try (DirectoryStream<Path> files = Files.newDirectoryStream(path)) {
                for (Path file : files) {
                    builder.append(file.getFileName()).append('\n');
                }
            }
            byte[] bytes = builder.toString().getBytes(UTF_8);
            stream = new ByteArrayInputStream(bytes);
            length = bytes.length;
        } else {
            stream = Files.newInputStream(path);
            length = Files.size(path);
        }

        FileTime lastModified = Files.getLastModifiedTime(path);
        String contentType = firstNonNull(Files.probeContentType(path), DEFAULT_CONTENT_TYPE);

        Map<String, List<String>> builder = new HashMap<>();
        builder.put("content-length", List.of("" + length));
        builder.put("content-type", List.of(contentType));
        if (lastModified != null) {
            DateFormat format = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
            format.setTimeZone(TimeZone.getTimeZone("GMT"));
            builder.put("last-modified", List.of(format.format(new Date(lastModified.toMillis()))));
        }

        headers = Map.copyOf(builder);
    }

    private static URI toUri(URL url) throws IOException {
        try {
            return url.toURI();
        } catch (URISyntaxException e) {
            throw new IOException("URL " + url + " cannot be converted to a URI", e);
        }
    }

    @Override
    public InputStream getInputStream() throws IOException {
        connect();
        return stream;
    }

    @SuppressWarnings("unchecked") // safe by specification of ListMultimap.asMap()
    @Override
    public Map<String, List<String>> getHeaderFields() {
        try {
            connect();
        } catch (IOException e) {
            return Map.of();
        }
        return Map.copyOf(headers);
    }

    @Override
    public String getHeaderField(String name) {
        try {
            connect();
        } catch (IOException e) {
            return null;
        }

        // no header should have more than one value
        List<String> result = headers.get(Util.toLowerCase(name));
        if (result != null && result.size() > 0) {
            return result.get(0);
        } else {
            return null;
        }
    }
}
