package net.concini.sbebuddy;

/**
 * The standard message header, used unless {@link SbeSchema#headerType()}
 * selects a custom header. Each component is encoded as an unsigned 16-bit value.
 *
 * @param blockLength the byte length of the message's fixed-length block
 * @param templateId the message's template ID
 * @param schemaId the schema ID
 * @param version the schema version used to encode the message
 */
@SbeComposite(name = "messageHeader")
public record DefaultMessageHeader(
		@SbeType(primitiveType = PrimitiveType.UINT16) int blockLength,
		@SbeType(primitiveType = PrimitiveType.UINT16) int templateId,
		@SbeType(primitiveType = PrimitiveType.UINT16) int schemaId,
		@SbeType(primitiveType = PrimitiveType.UINT16) int version
) implements MessageHeader {
}
