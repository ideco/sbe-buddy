package corpus.bindings;

import net.concini.sbebuddy.BindingContext;
import net.concini.sbebuddy.TypeBinding;

final class TickerBinding implements TypeBinding<Ticker, String> {

	public String toWire(Ticker value, BindingContext context) {
		return value.value();
	}

	public Ticker fromWire(String wire, BindingContext context) {
		return new Ticker(wire);
	}
}
