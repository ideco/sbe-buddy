package net.concini.sbebuddy.generator;

import static net.concini.sbebuddy.generator.FaceHelperTemplates.*;
import static net.concini.sbebuddy.generator.FaceTemplates.*;

import java.util.ArrayList;
import java.util.List;

import net.concini.sbebuddy.generator.CodecModel.Body;
import net.concini.sbebuddy.generator.CodecModel.Member;
import net.concini.sbebuddy.generator.Faces.Helper;
import net.concini.sbebuddy.generator.Faces.Shape;

/**
 * One {@link Faces} leaf as Java source, through {@link FaceTemplates} and
 * {@link FaceHelperTemplates}: how a shape is written, read, written as its
 * null value and tested for it, and what each helper declares, one switch per
 * question. A template is filled from the model node it writes, and the names
 * the node does not hold are given beside it.
 */
final class FaceWriter {

	private final String flyweights;

	/** Over the flyweights of the package {@code flyweights}. */
	FaceWriter(String flyweights) {
		this.flyweights = flyweights;
	}

	/** The flyweight call that writes the shape, from {@code source}. */
	String write(Body body, Member.Field field, Shape shape, String source) {
		return switch (shape) {
			case Shape.Scalar scalar -> ENCODE_FIELD.fill(field, "source", source);
			case Shape.Text text -> text.charset() == null
					? ENCODE_STRING_FIELD.fill(field, "source", source, "encoder", body.encoder())
					: ENCODE_ENCODED_STRING_FIELD.fill(
							field, "source", source, "encoder", body.encoder(), "charset", text.charset(), "bulk",
							text.bulk()
					);
			case Shape.Array array -> ENCODE_ARRAY_FIELD.fill(array, "source", source);
			case Shape.Enum enumeration -> ENCODE_ENUM_FIELD
					.fill(field, "enumClass", enumeration.enumClass(), "source", source);
			case Shape.Set set -> ENCODE_SET_FIELD.fill(field, "setClass", set.setClass(), "source", source);
			case Shape.Composite composite -> ENCODE_COMPOSITE_FIELD
					.fill(field, "compositeClass", composite.compositeClass(), "source", source);
			case Shape.Constant constant -> switch (constant.read()) {
				case Shape.Enum enumeration -> CHECK_CONSTANT_ENUM_FIELD.fill(
						field, "javaEnum", enumeration.javaEnum(), "constant", String.valueOf(constant.enumConstant()),
						"source", source
				);
				case Shape.Text text -> CHECK_CONSTANT_STRING_FIELD.fill(field, "source", source);
				case Shape.Scalar scalar -> CHECK_CONSTANT_FIELD.fill(field, "source", source);
				case Shape.Array array -> throw notAConstant(field);
				case Shape.Set set -> throw notAConstant(field);
				case Shape.Composite composite -> throw notAConstant(field);
				case Shape.Constant inner -> throw notAConstant(field);
			};
		};
	}

	/**
	 * An optional field's write from {@code source}, the null value for null: only
	 * a shape with a null value of its own has one.
	 */
	String writeOptional(Body body, Member.Field field, String source) {
		return switch (field.shape()) {
			case Shape.Scalar scalar -> ENCODE_OPTIONAL_FIELD.fill(field, "source", source, "encoder", body.encoder());
			case Shape.Enum enumeration -> ENCODE_OPTIONAL_ENUM_FIELD.fill(
					field, "flyweights", flyweights, "enumClass", enumeration.enumClass(), "source", source
			);
			case Shape.Text text -> throw noNullValue(field.component());
			case Shape.Array array -> throw noNullValue(field.component());
			case Shape.Set set -> throw noNullValue(field.component());
			case Shape.Composite composite -> throw noNullValue(field.component());
			case Shape.Constant constant -> throw noNullValue(field.component());
		};
	}

	/**
	 * A field or member no component carries, written as its null value over the
	 * {@code encoder} class of its body.
	 */
	String writeNull(String encoder, Member.Unmapped unmapped) {
		return switch (unmapped.shape()) {
			case Shape.Scalar scalar -> ENCODE_UNMAPPED_FIELD.fill(unmapped, "encoder", encoder);
			case Shape.Enum enumeration -> ENCODE_UNMAPPED_ENUM_FIELD.fill(
					unmapped, "flyweights", flyweights, "enumClass", enumeration.enumClass()
			);
			case Shape.Set set -> ENCODE_UNMAPPED_SET_FIELD.fill(unmapped);
			case Shape.Text text -> throw noNullValue(unmapped.property());
			case Shape.Array array -> ENCODE_UNMAPPED_ARRAY_FIELD.fill(unmapped, "encoder", encoder);
			case Shape.Composite composite -> throw noNullValue(unmapped.property());
			case Shape.Constant constant -> throw noNullValue(unmapped.property());
		};
	}

	/** The flyweight call that reads the shape. */
	String read(Member.Field field, Shape shape) {
		return switch (shape) {
			case Shape.Scalar scalar -> DECODE_FIELD.fill(field);
			case Shape.Text text -> DECODE_FIELD.fill(field);
			case Shape.Array array -> DECODE_ARRAY_FIELD.fill(array);
			case Shape.Enum enumeration -> DECODE_ENUM_FIELD.fill(enumeration, "property", field.property());
			case Shape.Set set -> DECODE_SET_FIELD.fill(set, "property", field.property());
			case Shape.Composite composite -> DECODE_COMPOSITE_FIELD.fill(composite, "property", field.property());
			case Shape.Constant constant -> read(field, constant.read());
		};
	}

	/** Whether an optional field holds its null value on the wire. */
	String isNull(Body body, Member.Field field) {
		return switch (field.shape()) {
			case Shape.Scalar scalar -> scalar.floatBox() == null
					? IS_NULL.fill(field, "decoder", body.decoder())
					: IS_NULL_FLOATING.fill(scalar, "property", field.property(), "decoder", body.decoder());
			case Shape.Enum enumeration -> IS_NULL_ENUM.fill(
					field, "flyweights", flyweights, "enumClass", enumeration.enumClass()
			);
			case Shape.Text text -> throw noNullValue(field.component());
			case Shape.Array array -> throw noNullValue(field.component());
			case Shape.Set set -> throw noNullValue(field.component());
			case Shape.Composite composite -> throw noNullValue(field.component());
			case Shape.Constant constant -> throw noNullValue(field.component());
		};
	}

	/** What a helper declares. */
	String helper(Helper helper) {
		return switch (helper) {
			case Helper.Ascii ascii -> ASCII.fill();
			case Helper.ArrayPair array -> array.bytes()
					? String.join("\n\n", WRITE_BYTES.fill(array), READ_BYTES.fill(array))
					: String.join("\n\n", WRITE_ARRAY.fill(array), READ_ARRAY.fill(array));
			case Helper.EnumPair enumeration -> enumPair(enumeration);
			case Helper.SetPair set -> setPair(set);
			case Helper.Utf8 utf8 -> UTF_8.fill();
			case Helper.Encoded encoded -> ENCODED.fill();
			case Helper.CharsetConstant charset -> CHARSET_CONSTANT.fill(charset);
			case Helper.Bytes bytes -> BYTES.fill();
		};
	}

	/**
	 * The raw value mapped by the valid values' text, so an unknown value is ours
	 * to decide: the constant the enum designates, or an exception.
	 */
	private String enumPair(Helper.EnumPair enumeration) {
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
		return String.join(
				"\n\n",
				ENCODE_ENUM.fill(enumeration, "flyweights", flyweights, "cases", String.join("\n", toWire)),
				DECODE_ENUM.fill(enumeration, "cases", String.join("\n", fromWire))
		);
	}

	private String setPair(Helper.SetPair set) {
		List<String> encodes = new ArrayList<>();
		List<String> decodes = new ArrayList<>();
		List<String> bits = new ArrayList<>();
		for (Helper.Choice choice : set.choices()) {
			encodes.add(ENCODE_CHOICE.fill(choice, "javaEnum", set.javaEnum()));
			decodes.add(DECODE_CHOICE.fill(choice, "javaEnum", set.javaEnum()));
			bits.add(KNOWN_BIT.fill(choice));
		}
		return String.join(
				"\n\n",
				ENCODE_SET.fill(set, "flyweights", flyweights, "choices", String.join("\n", encodes)),
				DECODE_SET.fill(
						set, "flyweights", flyweights, "knownBits", String.join(" | ", bits), "choices",
						String.join("\n", decodes)
				)
		);
	}

	private static IllegalStateException noNullValue(String name) {
		return new IllegalStateException(name + " has no null value on the wire");
	}

	private static IllegalStateException notAConstant(Member.Field field) {
		return new IllegalStateException(field.component() + " is a constant of no constant's shape");
	}
}
