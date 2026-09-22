package corpus.vardata;

import static net.concini.sbebuddy.PrimitiveType.UINT8;

import net.concini.sbebuddy.SbeComposite;
import net.concini.sbebuddy.SbeType;

@SbeComposite(name = "varByteEncoding")
record VarByteEncoding(
		@SbeType(primitiveType = UINT8) short length,
		@SbeType(primitiveType = UINT8, length = 0) byte[] varData
) {
}
