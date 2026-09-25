package corpus.boundpaths;

import java.math.BigDecimal;

import org.jspecify.annotations.Nullable;

import net.concini.sbebuddy.BindingContext;
import net.concini.sbebuddy.TypeBinding;

/**
 * Cents, naming its component and its presence when it refuses a value, so a
 * test sees which context it was handed.
 */
final class Cents implements TypeBinding.OfLong<@Nullable BigDecimal> {

	public long toWire(@Nullable BigDecimal value, BindingContext context) {
		if (value == null || value.scale() > 2) {
			throw new IllegalArgumentException(context.name() + " (" + context.presence() + ") refuses " + value);
		}
		return value.movePointRight(2).longValueExact();
	}

	public BigDecimal fromWire(long wire, BindingContext context) {
		return BigDecimal.valueOf(wire, 2);
	}
}
