package net.concini.sbebuddy;

/**
 * The standard message header, which every schema declares unless it names its
 * own.
 */
@SbeComposite(name = "messageHeader")
public record MessageHeader(
		@SbeType(primitiveType = PrimitiveType.UINT16) int blockLength,
		@SbeType(primitiveType = PrimitiveType.UINT16) int templateId,
		@SbeType(primitiveType = PrimitiveType.UINT16) int schemaId,
		@SbeType(primitiveType = PrimitiveType.UINT16) int version
) {
}
