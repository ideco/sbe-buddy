package corpus.header;

import static net.concini.sbebuddy.PrimitiveType.UINT16;
import static net.concini.sbebuddy.PrimitiveType.UINT32;

import net.concini.sbebuddy.SbeComposite;
import net.concini.sbebuddy.SbeType;

@SbeComposite(name = "applicationHeader", description = "The standard header and a sequence number")
record ApplicationHeader(
		@SbeType(primitiveType = UINT16) int blockLength,
		@SbeType(primitiveType = UINT16) int templateId,
		@SbeType(primitiveType = UINT16) int schemaId,
		@SbeType(primitiveType = UINT16) int version,
		@SbeType(primitiveType = UINT32) long sequenceNumber
) {
}
