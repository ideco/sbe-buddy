package com.example.quotes;

import java.math.BigDecimal;
import java.math.RoundingMode;

import net.concini.sbebuddy.TypeBinding;

/**
 * A price as the record holds it, a {@link BigDecimal}, over the mantissa the
 * wire holds, scaled by the schema's constant {@link PriceExponent}. A price
 * with more decimals than the exponent allows has no wire form, which
 * {@link BigDecimal#setScale} refuses with its own {@link ArithmeticException}.
 */
public final class Price implements TypeBinding<BigDecimal, Long> {

	private static final int SCALE = 4;

	public Price() {
	}

	@Override
	public Long toWire(BigDecimal value) {
		return value.setScale(SCALE, RoundingMode.UNNECESSARY).unscaledValue().longValueExact();
	}

	@Override
	public BigDecimal fromWire(Long wire) {
		return BigDecimal.valueOf(wire, SCALE);
	}
}
