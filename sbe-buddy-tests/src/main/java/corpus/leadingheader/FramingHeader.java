package corpus.leadingheader;

import static net.concini.sbebuddy.PrimitiveType.UINT16;
import static net.concini.sbebuddy.PrimitiveType.UINT32;

import net.concini.sbebuddy.MessageHeader;
import net.concini.sbebuddy.SbeComposite;
import net.concini.sbebuddy.SbeType;

@SbeComposite(name = "framingHeader")
record FramingHeader(
		@SbeType(primitiveType = UINT32) long frameLength,
		@SbeType(primitiveType = UINT16) int blockLength,
		@SbeType(primitiveType = UINT16) int templateId,
		@SbeType(primitiveType = UINT16) int schemaId,
		@SbeType(primitiveType = UINT16) int version
) implements MessageHeader {
}
