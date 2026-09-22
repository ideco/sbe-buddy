package net.concini.sbebuddy;

/**
 * How a record holds a field as a type of its own: {@code J} is the component's
 * type, {@code W} the face of the field's wire type, boxed where the face is a
 * primitive. A binding is stateless, has a no-arg constructor the schema
 * package can call, public when the class lives elsewhere, and is named by the
 * field, {@code @SbeField(binding = X.class)}. The codec never hands it
 * {@code null}: absence passes through as {@code null} on both sides. Whatever
 * a binding throws passes through the codec unwrapped. The Java side;
 * contributes nothing to the schema.
 */
public interface TypeBinding<J, W> {

	W toWire(J value);

	J fromWire(W wire);
}
