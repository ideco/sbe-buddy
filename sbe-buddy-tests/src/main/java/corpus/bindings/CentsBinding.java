package corpus.bindings;

import java.math.BigDecimal;

import net.concini.sbebuddy.TypeBinding;

final class CentsBinding implements TypeBinding.OfLong<BigDecimal> {

	public long toWire(BigDecimal value) {
		return value.movePointRight(2).longValueExact();
	}

	public BigDecimal fromWire(long wire) {
		return BigDecimal.valueOf(wire, 2);
	}
}
