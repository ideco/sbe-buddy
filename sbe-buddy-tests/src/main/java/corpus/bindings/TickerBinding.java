package corpus.bindings;

import net.concini.sbebuddy.TypeBinding;

final class TickerBinding implements TypeBinding<Ticker, String> {

	public String toWire(Ticker value) {
		return value.value();
	}

	public Ticker fromWire(String wire) {
		return new Ticker(wire);
	}
}
