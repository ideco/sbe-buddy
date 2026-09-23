package net.concini.sbebuddy.tests;

import java.util.List;

import net.concini.sbebuddy.Codec;

/**
 * One schema of the corpus as a test: the package's hand-written oracle and the
 * values its codecs must carry to the wire and back. A case lives in its
 * schema's package, beside the records, with a no-arg constructor; the tests
 * module finds every case on its classpath, and {@code SchemaCasesTest} runs
 * the checks every schema owes. What only this schema owes, a refusal with its
 * message, an older version's bytes, a binding's exception, is a {@code @Test}
 * of the case's own.
 */
public interface SchemaCase {

	/** One line naming what the schema shows, printed above its checks. */
	String description();

	/**
	 * The schema the processor must have written into the jar for the package,
	 * written by hand as a person would write it.
	 */
	String oracle();

	/** Every value worth carrying through a codec, edge values first of all. */
	List<RoundTrip<?>> roundTrips();

	/**
	 * A value and the codec that carries it; the description names what the value
	 * shows and is printed as the check's name.
	 */
	record RoundTrip<T>(String description, Codec<T, ?> codec, T value) {
	}
}
