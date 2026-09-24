package net.concini.sbebuddy;

import org.jspecify.annotations.Nullable;

/**
 * How a record holds a component as a type of its own: {@code J} is the
 * component's type, {@code W} the face of its wire type. The face decides the
 * interface: a reference face, a {@code String}, an array, an enum, a
 * {@code Set} of a set's enum, a composite's record or a group's {@code List},
 * takes this one, and a primitive face takes its specialization, {@link OfLong}
 * for {@code int64}, {@code uint64} and {@code uint32}, {@link OfInt} for
 * {@code int32} and {@code uint16}, {@link OfShort} for {@code int16} and
 * {@code uint8}, {@link OfByte} for {@code int8} and {@code char},
 * {@link OfFloat} and {@link OfDouble}, so no primitive is ever boxed on the
 * way. Every call is handed the {@link BindingContext} of its component, which
 * tells, among others, which of those wire types the face stands for. A binding
 * is stateless, has a no-arg constructor the schema package can call, public
 * when the class lives elsewhere, and is named by the component,
 * {@code binding = X.class} on its annotation. The codec hands it {@code null}
 * only on an optional field whose face has no null value of its own, a
 * composite, a set or an array, where the binding chooses what represents null
 * on the wire and reads it back as {@code null}; everywhere else absence passes
 * through as {@code null} on both sides without calling it. Whatever a binding
 * throws passes through the codec unwrapped. The Java side; contributes nothing
 * to the schema.
 */
public interface TypeBinding<J extends @Nullable Object, W> {

	W toWire(J value, BindingContext context);

	J fromWire(W wire, BindingContext context);

	/** Over a {@code byte} face: {@code int8} and {@code char}. */
	interface OfByte<J extends @Nullable Object> {

		byte toWire(J value, BindingContext context);

		J fromWire(byte wire, BindingContext context);
	}

	/** Over a {@code short} face: {@code int16} and {@code uint8}. */
	interface OfShort<J extends @Nullable Object> {

		short toWire(J value, BindingContext context);

		J fromWire(short wire, BindingContext context);
	}

	/** Over an {@code int} face: {@code int32} and {@code uint16}. */
	interface OfInt<J extends @Nullable Object> {

		int toWire(J value, BindingContext context);

		J fromWire(int wire, BindingContext context);
	}

	/**
	 * Over a {@code long} face: {@code int64}, {@code uint64} and {@code uint32}; a
	 * {@code uint64} is its bit pattern, as the face is.
	 */
	interface OfLong<J extends @Nullable Object> {

		long toWire(J value, BindingContext context);

		J fromWire(long wire, BindingContext context);
	}

	/** Over a {@code float} face. */
	interface OfFloat<J extends @Nullable Object> {

		float toWire(J value, BindingContext context);

		J fromWire(float wire, BindingContext context);
	}

	/** Over a {@code double} face. */
	interface OfDouble<J extends @Nullable Object> {

		double toWire(J value, BindingContext context);

		J fromWire(double wire, BindingContext context);
	}
}
