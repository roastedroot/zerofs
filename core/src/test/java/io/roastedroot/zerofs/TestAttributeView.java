package io.roastedroot.zerofs;

import java.io.IOException;
import java.nio.file.attribute.BasicFileAttributeView;

/** @author Colin Decker */
public interface TestAttributeView extends BasicFileAttributeView {

    TestAttributes readAttributes() throws IOException;

    void setBar(long bar) throws IOException;

    void setBaz(int baz) throws IOException;
}
