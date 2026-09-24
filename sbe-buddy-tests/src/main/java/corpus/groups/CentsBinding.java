package corpus.groups;

import java.math.BigDecimal;

import net.concini.sbebuddy.BindingContext;
import net.concini.sbebuddy.TypeBinding;

final class CentsBinding implements TypeBinding.OfLong<BigDecimal> {

	public long toWire(BigDecimal value, BindingContext context) {
		return value.movePointRight(2).longValueExact();
	}

	public BigDecimal fromWire(long wire, BindingContext context) {
		return BigDecimal.valueOf(wire, 2);
	}
}
