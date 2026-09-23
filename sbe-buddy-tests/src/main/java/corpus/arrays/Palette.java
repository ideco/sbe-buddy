package corpus.arrays;

import static net.concini.sbebuddy.PrimitiveType.INT16;
import static net.concini.sbebuddy.PrimitiveType.UINT8;

import net.concini.sbebuddy.SbeComposite;
import net.concini.sbebuddy.SbeType;

@SbeComposite
record Palette(
		@SbeType(primitiveType = UINT8, length = 3) byte[] accent,
		@SbeType(primitiveType = INT16) short weight
) {
}
