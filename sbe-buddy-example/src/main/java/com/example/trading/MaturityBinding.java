package com.example.trading;

import java.time.YearMonth;

import net.concini.sbebuddy.BindingContext;
import net.concini.sbebuddy.TypeBinding;

/**
 * A maturity as a {@link YearMonth}, over the year-month composite; a day or a
 * week of the month, which a {@code YearMonth} cannot hold, is refused.
 */
public final class MaturityBinding implements TypeBinding<YearMonth, MonthYear> {

	public MaturityBinding() {
	}

	@Override
	public MonthYear toWire(YearMonth value, BindingContext context) {
		return new MonthYear(value.getYear(), (short) value.getMonthValue(), null, null);
	}

	@Override
	public YearMonth fromWire(MonthYear wire, BindingContext context) {
		Integer year = wire.year();
		if (year == null || wire.day() != null || wire.week() != null) {
			throw new IllegalArgumentException(context.name() + " is no year and month alone: " + wire);
		}
		return YearMonth.of(year, wire.month());
	}
}
