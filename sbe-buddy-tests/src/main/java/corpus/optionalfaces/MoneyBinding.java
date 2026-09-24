package corpus.optionalfaces;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.jspecify.annotations.Nullable;

import net.concini.sbebuddy.BindingContext;
import net.concini.sbebuddy.Presence;
import net.concini.sbebuddy.TypeBinding;

/** A decimal, null as a null mantissa where the field is optional. */
final class MoneyBinding implements TypeBinding<@Nullable BigDecimal, Money> {

	private static final byte EXPONENT = -2;

	public Money toWire(@Nullable BigDecimal value, BindingContext context) {
		if (value == null) {
			if (context.presence() != Presence.OPTIONAL) {
				throw new IllegalArgumentException(context.name() + " is required");
			}
			return new Money(null, EXPONENT);
		}
		return new Money(
				value.setScale(-EXPONENT, RoundingMode.UNNECESSARY).unscaledValue().longValueExact(), EXPONENT
		);
	}

	public @Nullable BigDecimal fromWire(Money wire, BindingContext context) {
		Long mantissa = wire.mantissa();
		return mantissa == null ? null : BigDecimal.valueOf(mantissa, -wire.exponent());
	}
}
