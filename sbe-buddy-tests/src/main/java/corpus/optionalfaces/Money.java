package corpus.optionalfaces;

import static net.concini.sbebuddy.Presence.CONSTANT;
import static net.concini.sbebuddy.Presence.OPTIONAL;
import static net.concini.sbebuddy.PrimitiveType.INT64;
import static net.concini.sbebuddy.PrimitiveType.INT8;

import org.jspecify.annotations.Nullable;

import net.concini.sbebuddy.SbeComposite;
import net.concini.sbebuddy.SbeType;

/** A decimal whose optional mantissa, by SBE's convention, is its null. */
@SbeComposite
record Money(
		@SbeType(primitiveType = INT64, presence = OPTIONAL) @Nullable Long mantissa,
		@SbeType(primitiveType = INT8, presence = CONSTANT, value = "-2") byte exponent
) {
}
