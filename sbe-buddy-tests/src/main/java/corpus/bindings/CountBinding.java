package corpus.bindings;

import java.math.BigInteger;

import net.concini.sbebuddy.BindingContext;
import net.concini.sbebuddy.PrimitiveType;
import net.concini.sbebuddy.TypeBinding;

/**
 * A count over a {@code long} face, which is an {@code int64} or a
 * {@code uint64}; the context tells which, and the range follows.
 */
final class CountBinding implements TypeBinding.OfLong<BigInteger> {

	private static final BigInteger UINT64_MAX = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);

	public long toWire(BigInteger value, BindingContext context) {
		if (context.primitiveType() == PrimitiveType.UINT64) {
			if (value.signum() < 0 || value.compareTo(UINT64_MAX) > 0) {
				throw new IllegalArgumentException(context.name() + " is out of uint64's range: " + value);
			}
			return value.longValue();
		}
		return value.longValueExact();
	}

	public BigInteger fromWire(long wire, BindingContext context) {
		return context.primitiveType() == PrimitiveType.UINT64
				? new BigInteger(Long.toUnsignedString(wire))
				: BigInteger.valueOf(wire);
	}
}
