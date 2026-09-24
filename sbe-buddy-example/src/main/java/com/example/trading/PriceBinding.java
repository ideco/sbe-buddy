package com.example.trading;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.jspecify.annotations.Nullable;

import net.concini.sbebuddy.BindingContext;
import net.concini.sbebuddy.Presence;
import net.concini.sbebuddy.TypeBinding;

/**
 * A price as the record holds it, a {@link BigDecimal}, over the price record
 * the wire holds: scaled by the exponent the record carries, and on an optional
 * field null as a null mantissa, SBE's convention for an optional composite.
 */
public final class PriceBinding implements TypeBinding<@Nullable BigDecimal, PriceEncoding> {

	public PriceBinding() {
	}

	@Override
	public PriceEncoding toWire(@Nullable BigDecimal value, BindingContext context) {
		if (value == null) {
			if (context.presence() != Presence.OPTIONAL) {
				throw new IllegalArgumentException(context.name() + " is required");
			}
			return new PriceEncoding(null, PriceEncoding.EXPONENT);
		}
		try {
			long mantissa = value.setScale(-PriceEncoding.EXPONENT, RoundingMode.UNNECESSARY).unscaledValue()
					.longValueExact();
			return new PriceEncoding(mantissa, PriceEncoding.EXPONENT);
		} catch (ArithmeticException e) {
			throw new IllegalArgumentException(
					context.name() + " has no wire form with " + -PriceEncoding.EXPONENT + " decimals: " + value, e
			);
		}
	}

	@Override
	public @Nullable BigDecimal fromWire(PriceEncoding wire, BindingContext context) {
		Long mantissa = wire.mantissa();
		return mantissa == null ? null : BigDecimal.valueOf(mantissa, -wire.exponent());
	}
}
