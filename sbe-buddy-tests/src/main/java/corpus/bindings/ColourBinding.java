package corpus.bindings;

import net.concini.sbebuddy.TypeBinding;

final class ColourBinding implements TypeBinding<Colour, byte[]> {

	public byte[] toWire(Colour value) {
		return new byte[]{(byte) value.red(), (byte) value.green(), (byte) value.blue()};
	}

	public Colour fromWire(byte[] wire) {
		return new Colour(wire[0] & 0xFF, wire[1] & 0xFF, wire[2] & 0xFF);
	}
}
