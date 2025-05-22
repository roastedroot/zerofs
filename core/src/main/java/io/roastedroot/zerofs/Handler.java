package io.roastedroot.zerofs;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

/**
 * {@link URLStreamHandler} implementation for zerofs. Named {@code Handler} so that the class can be
 * found by Java as described in the documentation for {@link URL#URL(String, String, int, String)
 * URL}.
 *
 * <p>This class is only public because it is necessary for Java to find it. It is not intended to
 * be used directly.
 *
 * @author Colin Decker
 * @since 1.1
 */
public final class Handler extends URLStreamHandler {

    private static final String JAVA_PROTOCOL_HANDLER_PACKAGES = "java.protocol.handler.pkgs";

    /**
     * Registers this handler by adding the package {@code com.google.common} to the system property
     * {@code "java.protocol.handler.pkgs"}. Java will then look for this class in the {@code zerofs}
     * (the name of the protocol) package of {@code com.google.common}.
     *
     * @throws SecurityException if the system property that needs to be set to register this handler
     *     can't be read or written.
     */
    static void register() {
        register(Handler.class);
    }

    /** Generic method that would allow registration of any properly placed {@code Handler} class. */
    static void register(Class<? extends URLStreamHandler> handlerClass) {
        if (!"Handler".equals(handlerClass.getSimpleName())) {
            throw new IllegalArgumentException();
        }

        String pkg = handlerClass.getPackage().getName();
        int lastDot = pkg.lastIndexOf('.');
        if (lastDot <= 0) {
            throw new IllegalArgumentException(
                    String.format("package for Handler (%s) must have a parent package", pkg));
        }

        String parentPackage = pkg.substring(0, lastDot);

        String packages = System.getProperty(JAVA_PROTOCOL_HANDLER_PACKAGES);
        if (packages == null) {
            packages = parentPackage;
        } else {
            packages += "|" + parentPackage;
        }
        System.setProperty(JAVA_PROTOCOL_HANDLER_PACKAGES, packages);
    }

    /** @deprecated Not intended to be called directly; this class is only for use by Java itself. */
    @Deprecated
    public Handler() {} // a public, no-arg constructor is required

    @Override
    protected URLConnection openConnection(URL url) throws IOException {
        return new PathURLConnection(url);
    }

    @Override
    @SuppressWarnings(
            "UnsynchronizedOverridesSynchronized") // no need to synchronize to return null
    protected InetAddress getHostAddress(URL url) {
        // zerofs uses the URI host to specify the name of the file system being used.
        // In the default implementation of getHostAddress(URL), a non-null host would cause an
        // attempt
        // to look up the IP address, causing a slowdown on calling equals/hashCode methods on the
        // URL
        // object. By returning null, we speed up equality checks on URL's (since there isn't an IP
        // to
        // connect to).
        return null;
    }
}
