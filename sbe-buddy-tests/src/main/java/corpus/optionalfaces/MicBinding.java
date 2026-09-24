package corpus.optionalfaces;

import org.jspecify.annotations.Nullable;

import net.concini.sbebuddy.BindingContext;
import net.concini.sbebuddy.TypeBinding;

/** A code, null as the empty string, which the wire writes as zeros. */
final class MicBinding implements TypeBinding<@Nullable Mic, String> {

	public String toWire(@Nullable Mic value, BindingContext context) {
		return value == null ? "" : value.code();
	}

	public @Nullable Mic fromWire(String wire, BindingContext context) {
		return wire.isEmpty() ? null : new Mic(wire);
	}
}
