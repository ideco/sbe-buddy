package com.example.trading;

import net.concini.sbebuddy.BindingContext;
import net.concini.sbebuddy.TypeBinding;

/** A quantity as a {@code long}, over the decimal whose exponent is zero. */
public final class QtyBinding implements TypeBinding<Long, QtyEncoding> {

	public QtyBinding() {
	}

	@Override
	public QtyEncoding toWire(Long value, BindingContext context) {
		if (value < 0 || value > Integer.MAX_VALUE) {
			throw new IllegalArgumentException(context.name() + " is out of range: " + value);
		}
		return new QtyEncoding(value.intValue(), (byte) 0);
	}

	@Override
	public Long fromWire(QtyEncoding wire, BindingContext context) {
		return (long) wire.mantissa();
	}
}
