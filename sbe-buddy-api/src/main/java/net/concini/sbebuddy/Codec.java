package net.concini.sbebuddy;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * A record's way onto the wire and back, generated per message into the schema
 * package as {@code <Msg>Codec}. A codec is a stateful instance, one per
 * thread; every failure is an {@link IllegalArgumentException}. Implemented
 * only by generated code.
 */
public interface Codec<T> {

	/**
	 * The exact number of bytes {@link #encode} writes for the value, header
	 * included.
	 */
	int encodedLength(T value);

	/**
	 * Writes the value at the offset, header first, and returns the bytes written,
	 * which is {@link #encodedLength} of it.
	 */
	int encode(T value, MutableDirectBuffer buffer, int offset);

	/**
	 * Reads the value at the offset, taking the acting version and block length
	 * from the header; a header of another schema or template is an
	 * {@link IllegalArgumentException}.
	 */
	T decode(DirectBuffer buffer, int offset);

	/** The bytes the last {@link #decode} consumed. */
	int lastDecodedLength();

	/**
	 * The bytes a {@link #decode} at the offset would consume, without decoding.
	 */
	int decodedLength(DirectBuffer buffer, int offset);
}
