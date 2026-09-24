package com.example.quotes;

import java.math.BigDecimal;
import java.math.RoundingMode;

import net.concini.sbebuddy.BindingContext;
import net.concini.sbebuddy.TypeBinding;

/**
 * A price as the record holds it, a {@link BigDecimal}, over the mantissa the
 * wire holds, scaled by the schema's constant {@link PriceExponent}. A price
 * with more decimals than the exponent allows, or too large for the mantissa,
 * has no wire form, which it refuses naming the field its context gives.
 */
public final class Price implements TypeBinding.OfLong<BigDecimal> {

	private static final int SCALE = 4;

	public Price() {
	}

	@Override
	public long toWire(BigDecimal value, BindingContext context) {
		try {
			return value.setScale(SCALE, RoundingMode.UNNECESSARY).unscaledValue().longValueExact();
		} catch (ArithmeticException e) {
			throw new IllegalArgumentException(
					context.name() + " has no wire form with " + SCALE + " decimals: " + value, e
			);
		}
	}

	@Override
	public BigDecimal fromWire(long wire, BindingContext context) {
		return BigDecimal.valueOf(wire, SCALE);
	}
}
