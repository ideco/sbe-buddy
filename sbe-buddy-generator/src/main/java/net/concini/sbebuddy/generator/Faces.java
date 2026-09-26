package net.concini.sbebuddy.generator;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * What a component is on the wire, one leaf at a time: the shape of the
 * flyweight call, how the field may be absent, the binding in front of it, and
 * the helper methods the shape calls. {@link Join} decides it for every field
 * and member a record maps; {@link FaceWriter} renders it for the codec and the
 * flyweights' bound stages alike. Class names are qualified as the generated
 * code writes them.
 */
final class Faces {

	private Faces() {
	}

	/**
	 * How a field or member reaches the wire, where its message has a record: as a
	 * component, or as its null value.
	 */
	sealed interface Face {

		/**
		 * A record component on a field or a composite member, {@code property} on its
		 * flyweight: the leaf the codec and the flyweights write and read. {@code type}
		 * is the component's type as code names it, the binding's view where one is in
		 * front of it: {@code binding} is the field holding it and {@code context} the
		 * constant it is handed, both or neither null.
		 */
		record Mapped(
				String component,
				String property,
				String type,
				Shape shape,
				Absence absence,
				@Nullable String binding,
				@Nullable String context
		) implements Face {
		}

		/**
		 * A null writer: the field or member {@code property} written as its null
		 * value, an array's in every element, and never read.
		 */
		record Null(String property, Shape shape) implements Face {
		}
	}

	/**
	 * A bound component's binding, the field holding it, and the constant of its
	 * {@code BindingContext}.
	 */
	record Bound(String binding, String context) {
	}

	/** A binding held as a field, one per class, named after it. */
	record Binding(String type, String name) {
	}

	/**
	 * The {@code BindingContext} of one bound component, a constant named
	 * {@code name}; the rest are its arguments as Java expressions, the text
	 * quoted, {@code null} where absent.
	 */
	record Context(
			String name,
			String component,
			String primitiveType,
			String characterEncoding,
			String epoch,
			String timeUnit,
			String presence
	) {
	}

	/**
	 * What var-data holds: the bytes as they are, text in ASCII or UTF-8, counted
	 * without encoding, or text in any other encoding, encoded to be counted.
	 */
	enum Content {

		BYTES,

		ASCII,

		UTF_8,

		ENCODED
	}

	/** What the flyweight call looks like. */
	sealed interface Shape {

		/**
		 * A primitive through its accessor; {@code floatBox} names the box whose
		 * compare finds a NaN null value.
		 */
		record Scalar(@Nullable String floatBox) implements Shape {
		}

		/**
		 * A char string: in ASCII, {@code charset} null, through the flyweight's String
		 * form, checked by {@code ascii} first; in another encoding through the codec's
		 * {@code charset} constant, encoded and padded, and written as bytes named
		 * after {@code bulk}. Either is read through the flyweight's String form.
		 */
		record Text(@Nullable String charset, String bulk) implements Shape {
		}

		/**
		 * Any other fixed-length array, through the pair {@code write<field>} and
		 * {@code read<field>}.
		 */
		record Array(String field) implements Shape {
		}

		/**
		 * An enum, through the pair {@code encode<enumClass>} and
		 * {@code decode<enumClass>} over the raw value.
		 */
		record Enum(String enumClass, String javaEnum) implements Shape {
		}

		/**
		 * A set, through the pair {@code encode<setClass>} and
		 * {@code decode<setClass>}.
		 */
		record Set(String setClass) implements Shape {
		}

		/**
		 * A composite, through the pair {@code write<compositeClass>} and
		 * {@code read<compositeClass>}.
		 */
		record Composite(String compositeClass) implements Shape {
		}

		/**
		 * A constant: no bytes. Encoding checks the component against it, decoding
		 * reads it as {@code read} would; {@code enumConstant} is the record's constant
		 * a valueRef names, for an enum.
		 */
		record Constant(Shape read, @Nullable String enumConstant) implements Shape {
		}
	}

	/**
	 * How a field may be missing, which decides the write's null check and the
	 * read's guard.
	 */
	enum Absence {

		/** A primitive, always there. */
		NONE,

		/** A reference that must not be null; the wire has no null value for it. */
		REQUIRED,

		/** The null value on the wire is null in the record, both ways. */
		OPTIONAL,

		/** Appended above the baseline: null when the message predates it. */
		ADDED,

		/**
		 * Optional on a face with no null value of its own, a composite, a set, an
		 * array or a string: the binding writes and reads what represents null, and
		 * without one null is refused.
		 */
		NO_NULL_VALUE,

		/**
		 * Both of the last two: written as {@link #NO_NULL_VALUE}, and read as
		 * {@link #ADDED}, null when the message predates it.
		 */
		ADDED_NO_NULL_VALUE
	}

	/**
	 * A method a shape calls, declared once however many members call it; a
	 * composite's pair is declared over its members by whoever walks them.
	 */
	sealed interface Helper {

		/** The ASCII check before a char string reaches its flyweight. */
		record Ascii() implements Helper {
		}

		/**
		 * The pair for one array field, keyed by {@code field}, which a group's or a
		 * composite's path prefixes; {@code bytes} for an 8-bit element, which has bulk
		 * accessors named after {@code bulk}, otherwise element by element as
		 * {@code face}.
		 */
		record ArrayPair(
				String field,
				String encoder,
				String decoder,
				String property,
				String bulk,
				String component,
				String face,
				boolean bytes
		) implements Helper {
		}

		/** The switch each way between the record's enum and the wire's raw value. */
		record EnumPair(
				String enumClass,
				String javaEnum,
				String face,
				List<EnumValue> values,
				@Nullable String unknownValue
		) implements Helper {

			public EnumPair {
				values = List.copyOf(values);
			}
		}

		/**
		 * One constant of an enum: the flyweight's name for it and its raw value as a
		 * literal.
		 */
		record EnumValue(String constant, String wireConstant, String literal) {
		}

		/**
		 * The translation each way between a {@code Set} of the record's enum and the
		 * flyweight's bits.
		 */
		record SetPair(String setClass, String javaEnum, List<Choice> choices) implements Helper {

			public SetPair {
				choices = List.copyOf(choices);
			}
		}

		/**
		 * One choice of a set: the flyweight's accessor, the enum's constant and the
		 * bit.
		 */
		record Choice(String property, String constant, String bit) {
		}

		/** The UTF-8 count before text reaches its flyweight. */
		record Utf8() implements Helper {
		}

		/**
		 * Text in an encoding other than ASCII and UTF-8, through a reporting
		 * {@code CharsetEncoder}, and padded to a char array's length.
		 */
		record Encoded() implements Helper {
		}

		/** The constant for one encoding, by the name the schema gives it. */
		record CharsetConstant(String constant, String name) implements Helper {
		}

		/** The length check before bytes reach their flyweight. */
		record Bytes() implements Helper {
		}

		/**
		 * A composite type's write and read, over its own flyweights: {@code members}
		 * in wire order, each written as its component or its null value, and those the
		 * record's components map in the order its constructor takes them.
		 */
		record CompositePair(
				String compositeClass,
				String record,
				String encoder,
				String decoder,
				List<Face> members,
				List<Face.Mapped> constructorOrder
		) implements Helper {

			public CompositePair {
				members = List.copyOf(members);
				constructorOrder = List.copyOf(constructorOrder);
			}
		}

		/**
		 * The methods of one var-data member at {@code path} of the block over
		 * {@code encoder} and {@code decoder}: {@code length}, which counts it as
		 * {@code content} needs, refusing what the flyweight would, the write through
		 * it, and for bytes the read. {@code bulk} is the name the flyweight's
		 * {@code put} and {@code get} take, {@code charset} the constant for text in
		 * another encoding, or null, and {@code lengthEncoder} the flyweight of the
		 * encoding, whose length type holds the maximum.
		 */
		record Data(
				String path,
				String component,
				String property,
				String bulk,
				String length,
				Content content,
				@Nullable String charset,
				String encoder,
				String decoder,
				String lengthEncoder
		) implements Helper {
		}
	}
}
