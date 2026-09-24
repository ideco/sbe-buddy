package corpus.schemafirst;

import static net.concini.sbebuddy.PrimitiveType.INT64;
import static net.concini.sbebuddy.PrimitiveType.INT8;

import net.concini.sbebuddy.SbeComposite;
import net.concini.sbebuddy.SbeType;

@SbeComposite
record Price(
		@SbeType(primitiveType = INT64) long mantissa,
		@SbeType(primitiveType = INT8) byte exponent
) {
}
