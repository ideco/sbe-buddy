package corpus.bindings;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import net.concini.sbebuddy.BindingContext;
import net.concini.sbebuddy.TypeBinding;

/**
 * An instant over a count since the Unix epoch, in the unit the field's
 * {@code timeUnit} names; a field without one is refused, as is another epoch.
 */
final class EpochTime implements TypeBinding.OfLong<Instant> {

	public long toWire(Instant value, BindingContext context) {
		return unit(context).between(Instant.EPOCH, value);
	}

	public Instant fromWire(long wire, BindingContext context) {
		return Instant.EPOCH.plus(wire, unit(context));
	}

	private static ChronoUnit unit(BindingContext context) {
		if (context.epoch() != null && !context.epoch().equals("unix")) {
			throw new IllegalArgumentException(context.name() + " counts from " + context.epoch());
		}
		if (context.timeUnit() == null) {
			throw new IllegalArgumentException(context.name() + " has no timeUnit");
		}
		return switch (context.timeUnit()) {
			case "second" -> ChronoUnit.SECONDS;
			case "millisecond" -> ChronoUnit.MILLIS;
			case "microsecond" -> ChronoUnit.MICROS;
			case "nanosecond" -> ChronoUnit.NANOS;
			default -> throw new IllegalArgumentException(context.name() + " has no time unit " + context.timeUnit());
		};
	}
}
