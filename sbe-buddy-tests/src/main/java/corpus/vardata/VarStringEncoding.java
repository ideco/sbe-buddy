package corpus.vardata;

import static net.concini.sbebuddy.PrimitiveType.CHAR;
import static net.concini.sbebuddy.PrimitiveType.UINT16;

import net.concini.sbebuddy.SbeComposite;
import net.concini.sbebuddy.SbeType;

@SbeComposite(name = "varStringEncoding")
record VarStringEncoding(
		@SbeType(primitiveType = UINT16) int length,
		@SbeType(primitiveType = CHAR, length = 0, characterEncoding = "UTF-8") String varData
) {
}
