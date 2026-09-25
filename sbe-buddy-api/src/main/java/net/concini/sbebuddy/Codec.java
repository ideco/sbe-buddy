package net.concini.sbebuddy;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * Encodes and decodes Java records using SBE flyweights. Implementations are
 * generated for {@link SbeMessage} records and {@link SbeUnion} interfaces.
 *
 * <p>
 * Instances reuse mutable flyweights and must not be shared between threads.
 * Decoding creates records and their values. All offsets are byte offsets
 * pointing to the start of the message header; all lengths include the header.
 * </p>
 *
 * <p>
 * Buffer and binding exceptions propagate unchanged. A failed encode may leave
 * the buffer partially written.
 * </p>
 *
 * @param <T>
 *            the message record or union interface
 * @param <H>
 *            the schema's message header
 */
public interface Codec<T, H extends MessageHeader> {

	/**
	 * Returns the exact number of bytes needed to encode the value, including the
	 * message header.
	 *
	 * <p>
	 * This calculates the size without writing to a buffer. It does not validate
	 * every field; encoding may still reject the value.
	 * </p>
	 *
	 * @throws IllegalArgumentException
	 *             if the value is null or a value needed to calculate its size
	 *             cannot be encoded
	 */
	int encodedLength(T value);

	/**
	 * Encodes the value at the given offset and returns the number of bytes
	 * written, including the message header.
	 *
	 * <p>
	 * The codec writes the message's block length, template ID, schema ID and
	 * schema version into the header. Additional header members receive their null
	 * values.
	 * </p>
	 *
	 * <p>
	 * On success, the returned length equals {@link #encodedLength(Object)} for the
	 * same value. Validation may fail before or during writing.
	 * </p>
	 *
	 * @throws IllegalArgumentException
	 *             if the value is null or cannot be encoded
	 */
	int encode(T value, MutableDirectBuffer buffer, int offset);

	/**
	 * Encodes the value using the supplied header's additional members. Otherwise
	 * behaves as {@link #encode(Object, MutableDirectBuffer, int)}.
	 *
	 * <p>
	 * The supplied block length, template ID, schema ID and schema version are
	 * ignored. The codec always writes those values from the message's schema
	 * declaration.
	 * </p>
	 *
	 * @throws IllegalArgumentException
	 *             if the value or header is null, or a supplied value cannot be
	 *             encoded
	 */
	int encode(T value, H header, MutableDirectBuffer buffer, int offset);

	/**
	 * Decodes the message at the given offset into a Java record. Uses the schema
	 * version and block length carried in the message header.
	 *
	 * <p>
	 * A union codec selects the message record by template ID. After successful
	 * decoding, {@link #lastDecodedLength()} reports the number of bytes consumed,
	 * including the header.
	 * </p>
	 *
	 * @throws IllegalArgumentException
	 *             if the header identifies an unsupported schema, template or
	 *             version, or a wire value cannot be represented
	 */
	T decode(DirectBuffer buffer, int offset);

	/**
	 * Returns whether the message header identifies this schema, a message
	 * supported by this codec and a version at or above the configured baseline.
	 *
	 * <p>
	 * Reads only the header. A true result does not guarantee successful decoding:
	 * the body may be incomplete or contain unsupported values. Reading an
	 * inaccessible header may still throw a buffer exception.
	 * </p>
	 */
	boolean canDecode(DirectBuffer buffer, int offset);

	/**
	 * Decodes every member of the message header at the given offset.
	 *
	 * <p>
	 * Does not check the schema ID, template ID or version, and does not read the
	 * message body. The bytes must use this schema's header layout.
	 * </p>
	 */
	H decodeHeader(DirectBuffer buffer, int offset);

	/**
	 * Returns the number of bytes consumed by the preceding successful
	 * {@link #decode(DirectBuffer, int)}, including the message header.
	 *
	 * <p>
	 * Returns zero before the first decode. The result is unspecified after a
	 * failed decode.
	 * </p>
	 */
	int lastDecodedLength();

	/**
	 * Calculates the number of bytes decoding would consume, including the message
	 * header, without constructing records or invoking bindings.
	 *
	 * <p>
	 * Reads the header and traverses any groups and variable-length data. Does not
	 * perform all checks made by {@link #decode(DirectBuffer, int)}; use it only
	 * for messages this codec supports.
	 * </p>
	 *
	 * <p>
	 * Does not update {@link #lastDecodedLength()}.
	 * </p>
	 */
	int decodedLength(DirectBuffer buffer, int offset);
}
