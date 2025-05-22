package io.roastedroot.zerofs;

import java.nio.file.attribute.FileAttribute;
import java.util.Objects;

/** @author Colin Decker */
public class BasicFileAttribute<T> implements FileAttribute<T> {

    private final String name;
    private final T value;

    public BasicFileAttribute(String name, T value) {
        this.name = Objects.requireNonNull(name);
        this.value = Objects.requireNonNull(value);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public T value() {
        return value;
    }
}
