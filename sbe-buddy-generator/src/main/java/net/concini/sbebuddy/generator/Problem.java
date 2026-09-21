package net.concini.sbebuddy.generator;

/**
 * A rule broken at a node of {@link Annotated} or {@link Schema}; the front-end
 * that built the node knows where it is.
 */
public record Problem(Object node, String message) {
}
