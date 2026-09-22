package corpus.groups;

import static net.concini.sbebuddy.PrimitiveType.UINT8;

import net.concini.sbebuddy.SbeComposite;
import net.concini.sbebuddy.SbeType;

@SbeComposite(name = "smallGroupSizeEncoding")
record SmallGroupSizeEncoding(
		@SbeType(primitiveType = UINT8) short blockLength,
		@SbeType(primitiveType = UINT8) short numInGroup
) {
}
