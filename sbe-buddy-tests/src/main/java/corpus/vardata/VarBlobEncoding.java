package corpus.vardata;

import static net.concini.sbebuddy.PrimitiveType.UINT32;
import static net.concini.sbebuddy.PrimitiveType.UINT8;

import net.concini.sbebuddy.SbeComposite;
import net.concini.sbebuddy.SbeType;

@SbeComposite(name = "varBlobEncoding")
record VarBlobEncoding(
		@SbeType(primitiveType = UINT32, maxValue = "1073741824") long length,
		@SbeType(primitiveType = UINT8, length = 0) byte[] varData
) {
}
