package corpus.versions;

import static net.concini.sbebuddy.PrimitiveType.INT32;

import net.concini.sbebuddy.SbeComposite;
import net.concini.sbebuddy.SbeRef;
import net.concini.sbebuddy.SbeType;

@SbeComposite(sinceVersion = 1, deprecated = 3)
record Pair(
		@SbeType(primitiveType = INT32) int first,
		@SbeRef(value = Added.class, offset = 4, sinceVersion = 1, deprecated = 3) int second
) {
}
