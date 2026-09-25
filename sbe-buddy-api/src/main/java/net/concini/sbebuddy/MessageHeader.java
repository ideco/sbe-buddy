package net.concini.sbebuddy;

/**
 * The four values required in an SBE message header.
 * A custom header is an {@link SbeComposite} record implementing this interface,
 * with these components and any additional header members.
 *
 * <p>Select the header through {@link SbeSchema#headerType()}.
 * Generated codecs write these four values from the message's schema declaration,
 * regardless of the values in a supplied header record.</p>
 */
public interface MessageHeader {

	/**
	 * The byte length of the message's fixed-length block, excluding the header,
	 * groups and variable-length data.
	 */
	int blockLength();

	/**
	 * The message's template ID within its schema.
	 */
	int templateId();

	/**
	 * The ID of the message's schema.
	 */
	int schemaId();

	/**
	 * The schema version used to encode the message.
	 */
	int version();
}
