package net.concini.sbebuddy;

/**
 * What every message header carries, as sbe-tool requires a header composite
 * to: the message's block length, template id, schema id and version. A header
 * is an {@link SbeComposite} record that declares these four as components,
 * which gives it these accessors, and any members of its own after or around
 * them. {@link DefaultMessageHeader} is the standard one; a schema names
 * another through {@link SbeSchema#headerType()}.
 */
public interface MessageHeader {

	int blockLength();

	int templateId();

	int schemaId();

	int version();
}
