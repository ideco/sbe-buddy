package corpus.composites;

import static net.concini.sbebuddy.PrimitiveType.INT64;
import static net.concini.sbebuddy.PrimitiveType.INT8;

import net.concini.sbebuddy.SbeComposite;
import net.concini.sbebuddy.SbeType;

@SbeComposite(semanticType = "Price", description = "A price as mantissa and exponent")
record Decimal(
		@SbeType(primitiveType = INT64) long mantissa,
		@SbeType(primitiveType = INT8, offset = 8) byte exponent
) {
}
