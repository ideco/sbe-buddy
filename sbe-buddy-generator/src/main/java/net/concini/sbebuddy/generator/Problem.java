package net.concini.sbebuddy.generator;

/**
 * A rule broken at a node of {@link Annotated} or {@link Schema}; the front-end
 * that built the node knows where it is. An error stops generation; a warning
 * is reported and generation goes on.
 */
public record Problem(Object node, String message, Severity severity) {

	public enum Severity {
		ERROR, WARNING
	}

	public Problem(Object node, String message) {
		this(node, message, Severity.ERROR);
	}

	public boolean isError() {
		return severity == Severity.ERROR;
	}
}
