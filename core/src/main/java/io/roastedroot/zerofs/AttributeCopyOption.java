package io.roastedroot.zerofs;

/**
 * Options for how to handle copying of file attributes when copying a file.
 *
 * @author Colin Decker
 */
enum AttributeCopyOption {
    /** Copy all attributes on the file. */
    ALL,
    /** Copy only the basic attributes (file times) of the file. */
    BASIC,
    /** Do not copy any of the file's attributes. */
    NONE
}
