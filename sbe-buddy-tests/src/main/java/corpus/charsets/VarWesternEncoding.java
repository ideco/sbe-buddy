package corpus.charsets;

import static net.concini.sbebuddy.PrimitiveType.CHAR;
import static net.concini.sbebuddy.PrimitiveType.UINT16;

import net.concini.sbebuddy.SbeComposite;
import net.concini.sbebuddy.SbeType;

@SbeComposite(name = "varWesternEncoding")
record VarWesternEncoding(
		@SbeType(primitiveType = UINT16) int length,
		@SbeType(primitiveType = CHAR, length = 0, characterEncoding = "windows-1252") String varData
) {
}
