package com.example.trading;

import java.time.LocalDate;

import net.concini.sbebuddy.BindingContext;
import net.concini.sbebuddy.TypeBinding;

/**
 * A {@link LocalDate} over a count of days since 1970-01-01 in a
 * {@code uint16}.
 */
public final class LocalMktDateBinding implements TypeBinding.OfInt<LocalDate> {

	public LocalMktDateBinding() {
	}

	@Override
	public int toWire(LocalDate value, BindingContext context) {
		long days = value.toEpochDay();
		if (days < 0 || days > 0xFFFE) {
			throw new IllegalArgumentException(context.name() + " is outside 1970 to 2149: " + value);
		}
		return (int) days;
	}

	@Override
	public LocalDate fromWire(int wire, BindingContext context) {
		return LocalDate.ofEpochDay(wire);
	}
}
