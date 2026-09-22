package net.concini.sbebuddy;

/**
 * How a record holds a field as a type of its own: {@code J} is the component's
 * type, {@code W} the face of the field's wire type. The face decides the
 * interface: a reference face, a {@code String} or an array, takes this one,
 * and a primitive face takes its specialization, {@link OfLong} for
 * {@code int64}, {@code uint64} and {@code uint32}, {@link OfInt} for
 * {@code int32} and {@code uint16}, {@link OfShort} for {@code int16} and
 * {@code uint8}, {@link OfByte} for {@code int8} and {@code char},
 * {@link OfFloat} and {@link OfDouble}, so no primitive is ever boxed on the
 * way. A binding is stateless, has a no-arg constructor the schema package can
 * call, public when the class lives elsewhere, and is named by the field,
 * {@code @SbeField(binding = X.class)}. The codec never hands it {@code null}:
 * absence passes through as {@code null} on both sides. Whatever a binding
 * throws passes through the codec unwrapped. The Java side; contributes nothing
 * to the schema.
 */
public interface TypeBinding<J, W> {

	W toWire(J value);

	J fromWire(W wire);

	/** Over a {@code byte} face: {@code int8} and {@code char}. */
	interface OfByte<J> {

		byte toWire(J value);

		J fromWire(byte wire);
	}

	/** Over a {@code short} face: {@code int16} and {@code uint8}. */
	interface OfShort<J> {

		short toWire(J value);

		J fromWire(short wire);
	}

	/** Over an {@code int} face: {@code int32} and {@code uint16}. */
	interface OfInt<J> {

		int toWire(J value);

		J fromWire(int wire);
	}

	/**
	 * Over a {@code long} face: {@code int64}, {@code uint64} and {@code uint32}; a
	 * {@code uint64} is its bit pattern, as the face is.
	 */
	interface OfLong<J> {

		long toWire(J value);

		J fromWire(long wire);
	}

	/** Over a {@code float} face. */
	interface OfFloat<J> {

		float toWire(J value);

		J fromWire(float wire);
	}

	/** Over a {@code double} face. */
	interface OfDouble<J> {

		double toWire(J value);

		J fromWire(double wire);
	}
}
