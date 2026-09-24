package corpus.composites;

import java.math.BigDecimal;

import net.concini.sbebuddy.BindingContext;
import net.concini.sbebuddy.TypeBinding;

/** A Decimal as a BigDecimal, the exponent being the negated scale. */
final class DecimalBinding implements TypeBinding<BigDecimal, Decimal> {

	@Override
	public Decimal toWire(BigDecimal value, BindingContext context) {
		return new Decimal(value.unscaledValue().longValueExact(), (byte) -value.scale());
	}

	@Override
	public BigDecimal fromWire(Decimal wire, BindingContext context) {
		return BigDecimal.valueOf(wire.mantissa(), -wire.exponent());
	}
}
