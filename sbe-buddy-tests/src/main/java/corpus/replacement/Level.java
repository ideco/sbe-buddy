package corpus.replacement;

import static net.concini.sbebuddy.PrimitiveType.INT32;
import static net.concini.sbebuddy.PrimitiveType.INT64;
import static net.concini.sbebuddy.PrimitiveType.INT8;

import net.concini.sbebuddy.SbeComposite;
import net.concini.sbebuddy.SbeType;

/**
 * A price with the quantity at it: what {@link Price} would have grown into,
 * had a composite been able to.
 */
@SbeComposite
public record Level(
		@SbeType(primitiveType = INT64) long mantissa,
		@SbeType(primitiveType = INT8) byte exponent,
		@SbeType(primitiveType = INT32) int quantity
) {
}
