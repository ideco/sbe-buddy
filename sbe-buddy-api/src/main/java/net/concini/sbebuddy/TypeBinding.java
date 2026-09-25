package net.concini.sbebuddy;

import org.jspecify.annotations.Nullable;

/**
 * Converts between an application type and the Java representation of an SBE
 * wire value without changing the schema.
 *
 * <p>
 * Use this interface for reference representations such as strings, arrays,
 * enums, sets, composite records and lists of group entries. For primitive
 * representations, use the corresponding specialization to avoid boxing:
 * </p>
 * <ul>
 * <li>{@link OfByte}: {@code int8} and {@code char}.</li>
 * <li>{@link OfShort}: {@code int16} and {@code uint8}.</li>
 * <li>{@link OfInt}: {@code int32} and {@code uint16}.</li>
 * <li>{@link OfLong}: {@code int64}, {@code uint64} and {@code uint32}.</li>
 * <li>{@link OfFloat}: {@code float}.</li>
 * <li>{@link OfDouble}: {@code double}.</li>
 * </ul>
 *
 * <p>
 * Declare a binding with {@code binding = MyBinding.class} on the component's
 * annotation. The binding must be stateless and have a no-argument constructor
 * accessible from the schema package. Each codec instance holds one instance of
 * each binding class its message uses, and a union codec one message codec per
 * member. Every call receives the component's {@link BindingContext}.
 * </p>
 *
 * <p>
 * The codec handles absent values without invoking the binding, except for
 * optional fields whose Java wire representation has no scalar null sentinel,
 * namely composites, sets, strings and arrays. For those fields, the binding
 * receives null application values and defines their wire representation; a
 * message that predates such a field still decodes it to null without invoking
 * the binding. A group's or variable-length data's binding never receives null.
 * </p>
 *
 * <p>
 * Exceptions thrown by a binding propagate unchanged through the codec. These
 * requirements also apply to the primitive specializations.
 * </p>
 *
 * @param <J>
 *            the application type
 * @param <W>
 *            the Java representation of the wire value
 */
public interface TypeBinding<J extends @Nullable Object, W> {

	/**
	 * Converts an application value to the Java representation expected by the
	 * codec for encoding.
	 *
	 * <p>
	 * For an optional field whose null representation is defined by this binding, a
	 * null input must produce a non-null wire representation.
	 * </p>
	 */
	W toWire(J value, BindingContext context);

	/**
	 * Converts a decoded wire value to the application type.
	 *
	 * <p>
	 * For an optional field whose null representation is defined by this binding,
	 * that representation may be converted to null.
	 * </p>
	 */
	J fromWire(W wire, BindingContext context);

	/**
	 * A binding using {@code byte} for SBE {@code int8} or {@code char}.
	 *
	 * @param <J>
	 *            the application type
	 */
	interface OfByte<J extends @Nullable Object> {

		/**
		 * Converts an application value to its wire value.
		 */
		byte toWire(J value, BindingContext context);

		/**
		 * Converts a decoded wire value to the application type.
		 */
		J fromWire(byte wire, BindingContext context);
	}

	/**
	 * A binding using {@code short} for SBE {@code int16} or {@code uint8}.
	 *
	 * @param <J>
	 *            the application type
	 */
	interface OfShort<J extends @Nullable Object> {

		/**
		 * Converts an application value to its wire value.
		 */
		short toWire(J value, BindingContext context);

		/**
		 * Converts a decoded wire value to the application type.
		 */
		J fromWire(short wire, BindingContext context);
	}

	/**
	 * A binding using {@code int} for SBE {@code int32} or {@code uint16}.
	 *
	 * @param <J>
	 *            the application type
	 */
	interface OfInt<J extends @Nullable Object> {

		/**
		 * Converts an application value to its wire value.
		 */
		int toWire(J value, BindingContext context);

		/**
		 * Converts a decoded wire value to the application type.
		 */
		J fromWire(int wire, BindingContext context);
	}

	/**
	 * A binding using {@code long} for SBE {@code int64}, {@code uint64} or
	 * {@code uint32}.
	 *
	 * <p>
	 * For {@code uint64}, the long carries the unsigned value's bit pattern.
	 * </p>
	 *
	 * @param <J>
	 *            the application type
	 */
	interface OfLong<J extends @Nullable Object> {

		/**
		 * Converts an application value to its wire value.
		 */
		long toWire(J value, BindingContext context);

		/**
		 * Converts a decoded wire value to the application type.
		 */
		J fromWire(long wire, BindingContext context);
	}

	/**
	 * A binding using {@code float} for SBE {@code float}.
	 *
	 * @param <J>
	 *            the application type
	 */
	interface OfFloat<J extends @Nullable Object> {

		/**
		 * Converts an application value to its wire value.
		 */
		float toWire(J value, BindingContext context);

		/**
		 * Converts a decoded wire value to the application type.
		 */
		J fromWire(float wire, BindingContext context);
	}

	/**
	 * A binding using {@code double} for SBE {@code double}.
	 *
	 * @param <J>
	 *            the application type
	 */
	interface OfDouble<J extends @Nullable Object> {

		/**
		 * Converts an application value to its wire value.
		 */
		double toWire(J value, BindingContext context);

		/**
		 * Converts a decoded wire value to the application type.
		 */
		J fromWire(double wire, BindingContext context);
	}
}
