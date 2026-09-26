package net.concini.sbebuddy.generator;

import static net.concini.sbebuddy.generator.FaceHelperTemplates.*;
import static net.concini.sbebuddy.generator.FaceTemplates.*;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.concini.sbebuddy.generator.Faces.Content;
import net.concini.sbebuddy.generator.Faces.Face;
import net.concini.sbebuddy.generator.Faces.Helper;
import net.concini.sbebuddy.generator.Faces.Shape;

/**
 * One {@link Faces} leaf as Java source, through {@link FaceTemplates} and
 * {@link FaceHelperTemplates}: how a component is written and read, its absence
 * and its binding included, how a shape is written as its null value, and what
 * each helper declares, one switch per question. The codec and the flyweights
 * both write their leaves here, each naming the variable that holds the
 * flyweight and the expression of the component's value. A template is filled
 * from the model node it writes, and the names the node does not hold are given
 * beside it.
 */
final class FaceWriter {

	private final String flyweights;
	private final String qualifier;
	private final String instance;

	/**
	 * Over the flyweights of the package {@code flyweights}, calling the static
	 * helpers through {@code qualifier}, nothing or a class and its dot, and a
	 * composite's pair through {@code instance}, nothing or the class's
	 * {@code this} and its dot.
	 */
	FaceWriter(String flyweights, String qualifier, String instance) {
		this.flyweights = flyweights;
		this.qualifier = qualifier;
		this.instance = instance;
	}

	// ---- a field or member

	/**
	 * The component {@code value} written to the flyweight {@code flyweight}, of
	 * the class {@code encoder}. Decided by the component: an optional field takes
	 * the null value for null, any other reference refuses it, and a primitive is
	 * written as it is; a binding stands in front of the flyweight.
	 */
	String write(Face.Mapped leaf, String flyweight, String encoder, String value) {
		String source = leaf.binding() == null ? value : BOUND_SOURCE.fill(leaf, "value", value);
		String call = write(leaf, leaf.shape(), flyweight, encoder, source);
		return switch (leaf.absence()) {
			case NONE -> call;
			case REQUIRED, ADDED -> ENCODE_CHECKED_FIELD.fill(leaf, "value", value, "call", call);
			case NO_NULL_VALUE, ADDED_NO_NULL_VALUE -> leaf.binding() == null
					? ENCODE_NO_NULL_VALUE_FIELD.fill(leaf, "value", value, "call", call)
					: call;
			case OPTIONAL -> writeOptional(leaf, flyweight, encoder, value, source);
		};
	}

	/**
	 * The component read from the flyweight {@code flyweight}, of the class
	 * {@code decoder}, and null where it is absent. Decided by the wire: the null
	 * value for an optional field, whatever its version, since below the acting
	 * version the getter returns it; the version for a field appended above the
	 * baseline, which a required field may hold the null value of. Both are decided
	 * before the binding is called.
	 */
	String read(Face.Mapped leaf, String flyweight, String decoder) {
		String read = read(leaf, leaf.shape(), flyweight);
		if (leaf.binding() != null) {
			read = BOUND_READ.fill(leaf, "read", read);
		}
		return switch (leaf.absence()) {
			case NONE, REQUIRED, NO_NULL_VALUE -> read;
			case OPTIONAL -> DECODE_OPTIONAL_FIELD.fill("isNull", isNull(leaf, flyweight, decoder), "read", read);
			case ADDED, ADDED_NO_NULL_VALUE -> DECODE_ADDED_FIELD
					.fill(leaf, "flyweight", flyweight, "decoder", decoder, "read", read);
		};
	}

	/** The flyweight call that writes the shape, from {@code source}. */
	private String write(Face.Mapped leaf, Shape shape, String flyweight, String encoder, String source) {
		return switch (shape) {
			case Shape.Scalar scalar -> ENCODE_FIELD.fill(leaf, "flyweight", flyweight, "source", source);
			case Shape.Text text -> text.charset() == null
					? ENCODE_STRING_FIELD.fill(
							leaf, "flyweight", flyweight, "qualifier", qualifier, "source", source, "encoder", encoder
					)
					: ENCODE_ENCODED_STRING_FIELD.fill(
							leaf, "flyweight", flyweight, "qualifier", qualifier, "source", source, "encoder", encoder,
							"charset", text.charset(), "bulk", text.bulk()
					);
			case Shape.Array array -> ENCODE_ARRAY_FIELD
					.fill(array, "qualifier", qualifier, "source", source, "flyweight", flyweight);
			case Shape.Enum enumeration -> ENCODE_ENUM_FIELD.fill(
					leaf, "flyweight", flyweight, "qualifier", qualifier, "enumClass", enumeration.enumClass(),
					"source",
					source
			);
			case Shape.Set set -> ENCODE_SET_FIELD.fill(
					leaf, "qualifier", qualifier, "setClass", set.setClass(), "source", source, "flyweight", flyweight
			);
			case Shape.Composite composite -> ENCODE_COMPOSITE_FIELD.fill(
					leaf, "instance", instance, "compositeClass", composite.compositeClass(), "source", source,
					"flyweight", flyweight
			);
			case Shape.Constant constant -> switch (constant.read()) {
				case Shape.Enum enumeration -> CHECK_CONSTANT_ENUM_FIELD.fill(
						leaf, "javaEnum", enumeration.javaEnum(), "constant", String.valueOf(constant.enumConstant()),
						"source", source
				);
				case Shape.Text text -> CHECK_CONSTANT_STRING_FIELD
						.fill(leaf, "flyweight", flyweight, "source", source);
				case Shape.Scalar scalar -> CHECK_CONSTANT_FIELD.fill(leaf, "flyweight", flyweight, "source", source);
				case Shape.Array array -> throw notAConstant(leaf);
				case Shape.Set set -> throw notAConstant(leaf);
				case Shape.Composite composite -> throw notAConstant(leaf);
				case Shape.Constant inner -> throw notAConstant(leaf);
			};
		};
	}

	/**
	 * An optional field's write, the null value for null: only a shape with a null
	 * value of its own has one.
	 */
	private String writeOptional(Face.Mapped leaf, String flyweight, String encoder, String value, String source) {
		return switch (leaf.shape()) {
			case Shape.Scalar scalar -> ENCODE_OPTIONAL_FIELD
					.fill(leaf, "flyweight", flyweight, "value", value, "source", source, "encoder", encoder);
			case Shape.Enum enumeration -> ENCODE_OPTIONAL_ENUM_FIELD.fill(
					leaf, "flyweight", flyweight, "value", value, "flyweights", flyweights, "enumClass",
					enumeration.enumClass(), "qualifier", qualifier, "source", source
			);
			case Shape.Text text -> throw noNullValue(leaf.component());
			case Shape.Array array -> throw noNullValue(leaf.component());
			case Shape.Set set -> throw noNullValue(leaf.component());
			case Shape.Composite composite -> throw noNullValue(leaf.component());
			case Shape.Constant constant -> throw noNullValue(leaf.component());
		};
	}

	/** The flyweight call that reads the shape. */
	private String read(Face.Mapped leaf, Shape shape, String flyweight) {
		return switch (shape) {
			case Shape.Scalar scalar -> DECODE_FIELD.fill(leaf, "flyweight", flyweight);
			case Shape.Text text -> DECODE_FIELD.fill(leaf, "flyweight", flyweight);
			case Shape.Array array -> DECODE_ARRAY_FIELD.fill(array, "qualifier", qualifier, "flyweight", flyweight);
			case Shape.Enum enumeration -> DECODE_ENUM_FIELD.fill(
					enumeration, "qualifier", qualifier, "flyweight", flyweight, "property", leaf.property()
			);
			case Shape.Set set -> DECODE_SET_FIELD
					.fill(set, "qualifier", qualifier, "flyweight", flyweight, "property", leaf.property());
			case Shape.Composite composite -> DECODE_COMPOSITE_FIELD.fill(
					composite, "instance", instance, "flyweight", flyweight, "property", leaf.property()
			);
			case Shape.Constant constant -> read(leaf, constant.read(), flyweight);
		};
	}

	/** Whether an optional field holds its null value on the wire. */
	private String isNull(Face.Mapped leaf, String flyweight, String decoder) {
		return switch (leaf.shape()) {
			case Shape.Scalar scalar -> scalar.floatBox() == null
					? IS_NULL.fill(leaf, "flyweight", flyweight, "decoder", decoder)
					: IS_NULL_FLOATING
							.fill(scalar, "flyweight", flyweight, "property", leaf.property(), "decoder", decoder);
			case Shape.Enum enumeration -> IS_NULL_ENUM.fill(
					leaf, "flyweight", flyweight, "flyweights", flyweights, "enumClass", enumeration.enumClass()
			);
			case Shape.Text text -> throw noNullValue(leaf.component());
			case Shape.Array array -> throw noNullValue(leaf.component());
			case Shape.Set set -> throw noNullValue(leaf.component());
			case Shape.Composite composite -> throw noNullValue(leaf.component());
			case Shape.Constant constant -> throw noNullValue(leaf.component());
		};
	}

	/**
	 * The field or member {@code property} of the flyweight {@code encoder} holds,
	 * written as its null value: a composite through the {@code nulls} overload of
	 * its own flyweight, which only the writers declare.
	 */
	String writeNull(String encoder, String property, Shape shape) {
		return switch (shape) {
			case Shape.Scalar scalar -> ENCODE_UNMAPPED_FIELD.fill("property", property, "encoder", encoder);
			case Shape.Enum enumeration -> ENCODE_UNMAPPED_ENUM_FIELD.fill(
					"property", property, "flyweights", flyweights, "enumClass", enumeration.enumClass()
			);
			case Shape.Set set -> ENCODE_UNMAPPED_SET_FIELD.fill("property", property);
			case Shape.Text text -> throw noNullValue(property);
			case Shape.Array array -> ENCODE_UNMAPPED_ARRAY_FIELD.fill("property", property, "encoder", encoder);
			case Shape.Composite composite -> ENCODE_UNMAPPED_COMPOSITE_FIELD.fill("property", property);
			case Shape.Constant constant -> throw noNullValue(property);
		};
	}

	// ---- var-data

	/**
	 * The var-data's {@code value} written to {@code flyweight} through its method,
	 * which refuses null; a binding's view of it stays null for the method to
	 * refuse.
	 */
	String writeData(
			Helper.Data data, @Nullable String binding, @Nullable String context, String flyweight, String value
	) {
		String source = binding == null
				? value
				: NULLABLE_BOUND_SOURCE.fill("value", value, "binding", binding, "context", String.valueOf(context));
		return ENCODE_DATA_FIELD.fill(data, "qualifier", qualifier, "source", source, "flyweight", flyweight);
	}

	/**
	 * The var-data read from {@code flyweight} where its limit stands: text through
	 * the flyweight's String form, bytes through the var-data's method.
	 */
	String readData(Helper.Data data, String flyweight) {
		return data.content() == Content.BYTES
				? DECODE_BYTES.fill(data, "qualifier", qualifier, "flyweight", flyweight)
				: DECODE_TEXT.fill(data, "flyweight", flyweight);
	}

	/** What var-data is before its binding: its bytes, or its text. */
	static String face(Content content) {
		return content == Content.BYTES ? "byte[]" : "String";
	}

	// ---- the helpers

	/** What a helper declares, both ways. */
	String helper(Helper helper) {
		return String.join("\n\n", helper(helper, true, true));
	}

	/**
	 * What a helper declares for writing, for reading, or both: a pair's halves,
	 * each apart, and a check only where the direction needs it; nothing where it
	 * needs none.
	 */
	List<String> helper(Helper helper, boolean write, boolean read) {
		List<String> methods = new ArrayList<>();
		switch (helper) {
			case Helper.Ascii ascii -> add(methods, write, ASCII.fill());
			case Helper.ArrayPair array -> {
				add(methods, write, array.bytes() ? WRITE_BYTES.fill(array) : WRITE_ARRAY.fill(array));
				add(methods, read, array.bytes() ? READ_BYTES.fill(array) : READ_ARRAY.fill(array));
			}
			case Helper.EnumPair enumeration -> enumPair(enumeration, methods, write, read);
			case Helper.SetPair set -> setPair(set, methods, write, read);
			case Helper.Utf8 utf8 -> add(methods, write, UTF_8.fill());
			case Helper.Encoded encoded -> add(methods, write, ENCODED.fill());
			case Helper.CharsetConstant charset -> add(methods, write, CHARSET_CONSTANT.fill(charset));
			case Helper.Bytes bytes -> add(methods, write, BYTES.fill());
			case Helper.CompositePair composite -> compositePair(composite, methods, write, read);
			case Helper.Data data -> dataMethods(data, methods, write, read);
		}
		return methods;
	}

	private static void add(List<String> methods, boolean wanted, String method) {
		if (wanted) {
			methods.add(method);
		}
	}

	/**
	 * The raw value mapped by the valid values' text, so an unknown value is ours
	 * to decide: the constant the enum designates, or an exception.
	 */
	private void enumPair(Helper.EnumPair enumeration, List<String> methods, boolean write, boolean read) {
		List<String> toWire = new ArrayList<>();
		List<String> fromWire = new ArrayList<>();
		for (Helper.EnumValue value : enumeration.values()) {
			toWire.add(ENUM_TO_WIRE.fill(value, "flyweights", flyweights, "enumClass", enumeration.enumClass()));
			fromWire.add(WIRE_TO_ENUM.fill(value, "javaEnum", enumeration.javaEnum()));
		}
		if (enumeration.unknownValue() == null) {
			fromWire.add(WIRE_TO_NOTHING.fill(enumeration));
		} else {
			toWire.add(UNKNOWN_TO_WIRE.fill(enumeration));
			fromWire.add(WIRE_TO_UNKNOWN.fill(enumeration));
		}
		add(
				methods, write,
				ENCODE_ENUM.fill(enumeration, "flyweights", flyweights, "cases", String.join("\n", toWire))
		);
		add(methods, read, DECODE_ENUM.fill(enumeration, "cases", String.join("\n", fromWire)));
	}

	private void setPair(Helper.SetPair set, List<String> methods, boolean write, boolean read) {
		List<String> encodes = new ArrayList<>();
		List<String> decodes = new ArrayList<>();
		List<String> bits = new ArrayList<>();
		for (Helper.Choice choice : set.choices()) {
			encodes.add(ENCODE_CHOICE.fill(choice, "javaEnum", set.javaEnum()));
			decodes.add(DECODE_CHOICE.fill(choice, "javaEnum", set.javaEnum()));
			bits.add(KNOWN_BIT.fill(choice));
		}
		add(methods, write, ENCODE_SET.fill(set, "flyweights", flyweights, "choices", String.join("\n", encodes)));
		add(
				methods, read,
				DECODE_SET.fill(
						set, "flyweights", flyweights, "knownBits", String.join(" | ", bits), "choices",
						String.join("\n", decodes)
				)
		);
	}

	/**
	 * A composite written member by member in wire order, from the record the
	 * parameter names {@code value}, and read into its record in the order its
	 * constructor takes the components.
	 */
	private void compositePair(Helper.CompositePair composite, List<String> methods, boolean write, boolean read) {
		List<String> writes = new ArrayList<>();
		for (Face member : composite.members()) {
			writes.add(switch (member) {
				case Face.Mapped leaf -> write(leaf, "encoder", composite.encoder(), SOURCE.fill(leaf));
				case Face.Null unmapped -> writeNull(composite.encoder(), unmapped.property(), unmapped.shape());
			});
		}
		List<String> reads = new ArrayList<>();
		for (Face.Mapped leaf : composite.constructorOrder()) {
			reads.add(read(leaf, "decoder", composite.decoder()));
		}
		add(methods, write, WRITE_COMPOSITE.fill(composite, "members", String.join("\n", writes)));
		add(methods, read, READ_COMPOSITE.fill(composite, "members", String.join(",\n", reads)));
	}

	/**
	 * The length, write and read of one var-data member, each checking what its
	 * content needs.
	 */
	private static void dataMethods(Helper.Data data, List<String> methods, boolean write, boolean read) {
		String charset = String.valueOf(data.charset());
		String count = switch (data.content()) {
			case BYTES -> COUNT_BYTES.fill(data);
			case ASCII -> COUNT_ASCII.fill(data);
			case UTF_8 -> COUNT_UTF_8.fill(data);
			case ENCODED -> COUNT_ENCODED.fill(data, "charset", charset);
		};
		add(methods, write, DATA_LENGTH.fill(data, "face", face(data.content()), "count", count));
		switch (data.content()) {
			case ENCODED -> add(methods, write, WRITE_ENCODED_TEXT.fill(data, "charset", charset));
			case ASCII, UTF_8 -> add(methods, write, WRITE_TEXT.fill(data));
			case BYTES -> {
				add(methods, write, WRITE_DATA_BYTES.fill(data));
				add(methods, read, READ_DATA_BYTES.fill(data));
			}
		}
	}

	private static IllegalStateException noNullValue(String name) {
		return new IllegalStateException(name + " has no null value on the wire");
	}

	private static IllegalStateException notAConstant(Face.Mapped leaf) {
		return new IllegalStateException(leaf.component() + " is a constant of no constant's shape");
	}
}
