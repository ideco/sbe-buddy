package net.concini.sbebuddy;

/**
 * The standard message header, which every schema declares unless it names its
 * own: the four members every header carries, and nothing else.
 */
@SbeComposite(name = "messageHeader")
public record DefaultMessageHeader(
		@SbeType(primitiveType = PrimitiveType.UINT16) int blockLength,
		@SbeType(primitiveType = PrimitiveType.UINT16) int templateId,
		@SbeType(primitiveType = PrimitiveType.UINT16) int schemaId,
		@SbeType(primitiveType = PrimitiveType.UINT16) int version
) implements MessageHeader {
}
