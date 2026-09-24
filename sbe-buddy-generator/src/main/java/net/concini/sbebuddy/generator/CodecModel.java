package net.concini.sbebuddy.generator;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * What one message's codec is made of, every name resolved: its bodies of
 * members, how each member reaches the wire, and the helper methods they call.
 * {@link CodecWalk} builds it from the IR and {@link Annotated};
 * {@link CodecWriter} renders it. Class names are qualified as the generated
 * code writes them.
 */
record CodecModel(
		String packageName,
		String codec,
		String record,
		String flyweights,
		Header header,
		String message,
		int baseline,
		List<Binding> bindings,
		List<Context> contexts,
		Body body,
		List<Helper> helpers
) {

	CodecModel {
		bindings = List.copyOf(bindings);
		contexts = List.copyOf(contexts);
		helpers = List.copyOf(helpers);
	}

	/**
	 * The header every message is framed in, over the flyweights named after
	 * {@code headerClass}; {@code record} is what {@code decodeHeader} returns.
	 * {@code body} reads every component and writes the members of the header's
	 * own, never the standard four, which {@code wrapAndApplyHeader} writes;
	 * {@code nulls} writes those members as their null value when no header is
	 * given.
	 */
	record Header(String headerClass, String record, Body body, List<Member.Unmapped> nulls) {

		Header {
			nulls = List.copyOf(nulls);
		}
	}

	/** A binding the codec holds as a field, one per class, named after it. */
	record Binding(String type, String name) {
	}

	/**
	 * The {@code BindingContext} of one bound component, a constant of the codec
	 * named {@code name}; the rest are its arguments as Java expressions, the text
	 * quoted, {@code null} where absent.
	 */
	record Context(
			String name,
			String component,
			String primitiveType,
			String characterEncoding,
			String epoch,
			String timeUnit
	) {
	}

	/**
	 * A message's, a composite's or a group entry's members over its flyweights: in
	 * the order the wire takes them, and in the order the record's constructor
	 * takes its components, which leaves out what no component carries.
	 */
	record Body(String encoder, String decoder, List<Member> wireOrder, List<Member> constructorOrder) {

		Body {
			wireOrder = List.copyOf(wireOrder);
			constructorOrder = List.copyOf(constructorOrder);
		}
	}

	sealed interface Member {

		/**
		 * A record component on a field or a composite member; {@code binding} is the
		 * codec's field for the binding in front of it and {@code context} the constant
		 * it is handed, both or neither null.
		 */
		record Field(
				String component,
				String property,
				Shape shape,
				Absence absence,
				@Nullable String binding,
				@Nullable String context
		) implements Member {
		}

		/**
		 * A field or member no component carries: written as its null value, an array's
		 * in every element, and never read.
		 */
		record Unmapped(String property, Shape shape) implements Member {
		}

		/**
		 * A repeating group read into a local of its component's name before the
		 * constructor; {@code addedSince} is the flyweight's since-version method when
		 * the group was appended above the baseline, or null; {@code binding} and
		 * {@code context} as on a field, the binding over the list.
		 */
		record Group(
				String component,
				String property,
				String path,
				String record,
				Body entry,
				@Nullable String addedSince,
				@Nullable String binding,
				@Nullable String context
		) implements Member {
		}

		/**
		 * Var-data, read into a local of its component's name before the constructor;
		 * {@code addedSince} is the flyweight's since-version method when the data was
		 * appended above the baseline, or null; {@code binding} and {@code context} as
		 * on a field.
		 */
		record Data(
				String component,
				String property,
				String path,
				Content content,
				@Nullable String addedSince,
				@Nullable String binding,
				@Nullable String context
		) implements Member {
		}
	}

	/**
	 * What var-data holds: the bytes as they are, or text in one of two encodings.
	 */
	enum Content {

		BYTES,

		ASCII,

		UTF_8
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
		 * A char string through the flyweight's String form, checked by {@code ascii}
		 * first.
		 */
		record Text() implements Shape {
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
		ADDED
	}

	/** A method the codec declares once, however many members call it. */
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

		/** The write and read pair of a composite type, over its own body. */
		record CompositePair(String compositeClass, String record, Body body) implements Helper {
		}

		/**
		 * A group's write, read and length methods, over its entry's body;
		 * {@code parent} is the encoder of the body the group is in, which sizes it.
		 */
		record GroupMethods(Member.Group group, String parent) implements Helper {
		}

		/**
		 * The length and write methods of one var-data member, and for bytes its read,
		 * over the body's flyweights: {@code bulk} is the name the flyweight's
		 * {@code put} and {@code get} take, and {@code lengthEncoder} the flyweight of
		 * the encoding, whose length type holds the maximum.
		 */
		record DataMethods(Member.Data data, String encoder, String decoder, String bulk, String lengthEncoder)
				implements
					Helper {
		}

		/** The UTF-8 count before text reaches its flyweight. */
		record Utf8() implements Helper {
		}

		/** The length check before bytes reach their flyweight. */
		record Bytes() implements Helper {
		}
	}
}
