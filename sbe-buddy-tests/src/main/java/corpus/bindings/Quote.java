package corpus.bindings;

import static net.concini.sbebuddy.PrimitiveType.INT64;

import java.math.BigDecimal;

import net.concini.sbebuddy.SbeComposite;
import net.concini.sbebuddy.SbeRef;
import net.concini.sbebuddy.SbeType;

/** A composite whose inline member and ref each take a binding. */
@SbeComposite
record Quote(
		@SbeType(primitiveType = INT64, binding = CentsBinding.class) BigDecimal bid,
		@SbeRef(value = Flag.class, binding = FlagBinding.class) boolean firm
) {
}
