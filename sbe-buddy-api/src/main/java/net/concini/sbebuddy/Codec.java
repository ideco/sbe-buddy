package net.concini.sbebuddy;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * A record's way onto the wire and back, generated per message into the schema
 * package as {@code <Msg>Codec}; {@code H} is the schema's header. A codec is a
 * stateful instance, one per thread. The one exception of its own is
 * {@link IllegalArgumentException}, for what it is handed and cannot represent:
 * a value with no wire form, or bytes that are not its message. Everything else
 * passes through unwrapped: a buffer too small is Agrona's
 * {@link IndexOutOfBoundsException}, a binding's exception is the binding's,
 * and a state generated code cannot reach is an {@link IllegalStateException}.
 * Implemented only by generated code.
 */
public interface Codec<T, H extends MessageHeader> {

	/**
	 * The exact number of bytes {@link #encode} writes for the value, header
	 * included.
	 */
	int encodedLength(T value);

	/**
	 * Writes the value at the offset, header first, and returns the bytes written,
	 * which is {@link #encodedLength} of it. The header's block length, template
	 * id, schema id and version are the message's, and any other member of the
	 * header is its null value. A value the wire cannot carry, such as {@code null}
	 * in a required field, an array of the wrong length or a string that does not
	 * fit, is an {@link IllegalArgumentException}, thrown before or while writing;
	 * the buffer's bounds are the buffer's to check.
	 */
	int encode(T value, MutableDirectBuffer buffer, int offset);

	/**
	 * Writes the value as {@link #encode(Object, MutableDirectBuffer, int)} does,
	 * with the header's members of its own taken from {@code header}. Its block
	 * length, template id, schema id and version are ignored: those are always the
	 * message's, so a header read from another message can be passed on as it is.
	 */
	int encode(T value, H header, MutableDirectBuffer buffer, int offset);

	/**
	 * Reads the value at the offset, taking the acting version and block length
	 * from the header. A header of another schema or template, or a wire value the
	 * schema does not know, is an {@link IllegalArgumentException}.
	 */
	T decode(DirectBuffer buffer, int offset);

	/**
	 * Reads the header at the offset, every member of it, and checks nothing: the
	 * message after it may be of any template, so it can be read before choosing a
	 * codec.
	 */
	H decodeHeader(DirectBuffer buffer, int offset);

	/** The bytes the last {@link #decode} consumed. */
	int lastDecodedLength();

	/**
	 * The bytes a {@link #decode} at the offset would consume, without decoding.
	 */
	int decodedLength(DirectBuffer buffer, int offset);
}
