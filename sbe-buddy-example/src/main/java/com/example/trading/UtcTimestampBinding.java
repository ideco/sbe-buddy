package com.example.trading;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import net.concini.sbebuddy.BindingContext;
import net.concini.sbebuddy.TypeBinding;

/**
 * An {@link Instant} over a count since the Unix epoch, in the unit the field's
 * {@code timeUnit} names; this schema gives none, which it defines as
 * nanoseconds. The wire is a {@code uint64}, so nothing before 1970.
 */
public final class UtcTimestampBinding implements TypeBinding.OfLong<Instant> {

	public UtcTimestampBinding() {
	}

	@Override
	public long toWire(Instant value, BindingContext context) {
		if (value.isBefore(Instant.EPOCH)) {
			throw new IllegalArgumentException(context.name() + " is before 1970: " + value);
		}
		return unit(context).between(Instant.EPOCH, value);
	}

	@Override
	public Instant fromWire(long wire, BindingContext context) {
		return Instant.EPOCH.plus(wire, unit(context));
	}

	private static ChronoUnit unit(BindingContext context) {
		String timeUnit = context.timeUnit();
		if (timeUnit == null) {
			return ChronoUnit.NANOS;
		}
		return switch (timeUnit) {
			case "second" -> ChronoUnit.SECONDS;
			case "millisecond" -> ChronoUnit.MILLIS;
			case "microsecond" -> ChronoUnit.MICROS;
			case "nanosecond" -> ChronoUnit.NANOS;
			default -> throw new IllegalArgumentException(context.name() + " has no time unit " + timeUnit);
		};
	}
}
