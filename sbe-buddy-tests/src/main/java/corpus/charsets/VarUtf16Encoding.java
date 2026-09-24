package corpus.charsets;

import static net.concini.sbebuddy.PrimitiveType.CHAR;
import static net.concini.sbebuddy.PrimitiveType.UINT16;

import net.concini.sbebuddy.SbeComposite;
import net.concini.sbebuddy.SbeType;

@SbeComposite(name = "varUtf16Encoding")
record VarUtf16Encoding(
		@SbeType(primitiveType = UINT16) int length,
		@SbeType(primitiveType = CHAR, length = 0, characterEncoding = "UTF-16") String varData
) {
}
