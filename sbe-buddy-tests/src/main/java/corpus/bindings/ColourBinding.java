package corpus.bindings;

import net.concini.sbebuddy.BindingContext;
import net.concini.sbebuddy.TypeBinding;

final class ColourBinding implements TypeBinding<Colour, byte[]> {

	public byte[] toWire(Colour value, BindingContext context) {
		return new byte[]{(byte) value.red(), (byte) value.green(), (byte) value.blue()};
	}

	public Colour fromWire(byte[] wire, BindingContext context) {
		return new Colour(wire[0] & 0xFF, wire[1] & 0xFF, wire[2] & 0xFF);
	}
}
