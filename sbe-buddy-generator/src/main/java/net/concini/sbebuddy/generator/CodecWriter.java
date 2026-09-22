package net.concini.sbebuddy.generator;

import static net.concini.sbebuddy.generator.CodecTemplates.*;

import java.util.ArrayList;
import java.util.List;

import net.concini.sbebuddy.generator.CodecModel.Body;
import net.concini.sbebuddy.generator.CodecModel.Helper;
import net.concini.sbebuddy.generator.CodecModel.Member;
import net.concini.sbebuddy.generator.CodecModel.Shape;

/**
 * A {@link CodecModel} as Java source, through {@link CodecTemplates}. Each
 * question has one switch: how a shape is written and read, how its absence
 * wraps that, and what each helper declares; a binding stands in front of the
 * write and behind the read.
 */
final class CodecWriter {

	private final CodecModel model;

	private CodecWriter(CodecModel model) {
		this.model = model;
	}

	static String write(CodecModel model) {
		return new CodecWriter(model).codec();
	}

	private String codec() {
		Body body = model.body();
		List<String> bindings = new ArrayList<>();
		for (CodecModel.Binding binding : model.bindings()) {
			bindings.add(BINDING_FIELD.fill("binding", binding.type(), "name", binding.name()));
		}
		List<String> groupLengths = new ArrayList<>();
		for (Member.Group group : groups(body)) {
			groupLengths.add(GROUP_LENGTH_TERM.fill("length", lengthOf(group.path()), "component", group.component()));
		}
		List<String> helpers = new ArrayList<>();
		for (Helper helper : model.helpers()) {
			helpers.add(helper(helper));
		}
		return CODEC.fill(
				"package", model.packageName(),
				"codec", model.codec(),
				"record", model.record(),
				"flyweights", model.flyweights(),
				"header", model.header(),
				"message", model.message(),
				"bindings", String.join("\n", bindings),
				"groupLengths", String.join("", groupLengths),
				"encodeFields", writes(body),
				"refuseBelowBaseline", model.baseline() == 0
						? ""
						: REFUSE_BELOW_BASELINE
								.fill("message", model.message(), "baseline", String.valueOf(model.baseline())),
				"decodeGroups", groupReads(body),
				"decodeFields", arguments(body),
				"decodedLength", groups(body).isEmpty()
						? BLOCK_DECODED_LENGTH.fill("flyweights", model.flyweights(), "header", model.header())
						: WALKED_DECODED_LENGTH.fill("flyweights", model.flyweights(), "header", model.header()),
				"helpers", helpers.isEmpty() ? "" : "\n" + String.join("\n\n", helpers)
		);
	}

	// ---- writing a body

	/** The body's members in wire order, as encode statements. */
	private String writes(Body body) {
		List<String> writes = new ArrayList<>();
		for (Member member : body.wireOrder()) {
			writes.add(write(body, member));
		}
		return String.join("\n", writes);
	}

	private String write(Body body, Member member) {
		return switch (member) {
			case Member.Field field -> write(body, field);
			case Member.Unmapped unmapped -> writeNull(body, unmapped);
			case Member.Group group -> ENCODE_CHECKED_FIELD.fill(
					"component", group.component(),
					"call", ENCODE_GROUP_FIELD.fill(
							"path", group.path(), "source", SOURCE.fill("component", group.component()), "property",
							group.property()
					)
			);
		};
	}

	/**
	 * Decided by the component: an optional field takes the null value for null,
	 * any other reference refuses it, and a primitive is written as it is.
	 */
	private String write(Body body, Member.Field field) {
		String source = field.binding() == null
				? SOURCE.fill("component", field.component())
				: BOUND_SOURCE.fill("name", field.binding(), "component", field.component());
		return switch (field.absence()) {
			case NONE -> write(body, field, field.shape(), source);
			case REQUIRED, ADDED -> ENCODE_CHECKED_FIELD.fill(
					"component", field.component(), "call", write(body, field, field.shape(), source)
			);
			case OPTIONAL -> switch (field.shape()) {
				case Shape.Scalar scalar -> ENCODE_OPTIONAL_FIELD.fill(
						"property", field.property(), "component", field.component(), "source", source,
						"encoder", body.encoder()
				);
				case Shape.Enum enumeration -> ENCODE_OPTIONAL_ENUM_FIELD.fill(
						"property", field.property(), "component", field.component(), "flyweights",
						model.flyweights(), "enum", enumeration.enumClass()
				);
				case Shape.Text text -> throw noNullValue(field);
				case Shape.Array array -> throw noNullValue(field);
				case Shape.Set set -> throw noNullValue(field);
				case Shape.Composite composite -> throw noNullValue(field);
				case Shape.Constant constant -> throw noNullValue(field);
			};
		};
	}

	/** A field or member no component carries, written as its null value. */
	private String writeNull(Body body, Member.Unmapped unmapped) {
		return switch (unmapped.shape()) {
			case Shape.Scalar scalar -> ENCODE_UNMAPPED_FIELD.fill(
					"property", unmapped.property(), "encoder", body.encoder()
			);
			case Shape.Enum enumeration -> ENCODE_UNMAPPED_ENUM_FIELD.fill(
					"property", unmapped.property(), "flyweights", model.flyweights(), "enum", enumeration.enumClass()
			);
			case Shape.Set set -> ENCODE_UNMAPPED_SET_FIELD.fill("property", unmapped.property());
			case Shape.Text text -> throw noNullValue(unmapped.property());
			case Shape.Array array -> throw noNullValue(unmapped.property());
			case Shape.Composite composite -> throw noNullValue(unmapped.property());
			case Shape.Constant constant -> throw noNullValue(unmapped.property());
		};
	}

	/** The flyweight call that writes the shape, from {@code source}. */
	private String write(Body body, Member.Field field, Shape shape, String source) {
		String property = field.property();
		return switch (shape) {
			case Shape.Scalar scalar -> ENCODE_FIELD.fill("property", property, "source", source);
			case Shape.Text text -> ENCODE_STRING_FIELD.fill(
					"property", property, "source", source, "component", field.component(), "encoder", body.encoder()
			);
			case Shape.Array array -> ENCODE_ARRAY_FIELD.fill("field", array.field(), "source", source);
			case Shape.Enum enumeration -> ENCODE_ENUM_FIELD.fill(
					"property", property, "enum", enumeration.enumClass(), "component", field.component()
			);
			case Shape.Set set -> ENCODE_SET_FIELD.fill(
					"set", set.setClass(), "component", field.component(), "property", property
			);
			case Shape.Composite composite -> ENCODE_COMPOSITE_FIELD.fill(
					"composite", composite.compositeClass(), "source", source, "property", property
			);
			case Shape.Constant constant -> switch (constant.read()) {
				case Shape.Enum enumeration -> CHECK_CONSTANT_ENUM_FIELD.fill(
						"component", field.component(), "javaEnum", enumeration.javaEnum(), "constant",
						String.valueOf(constant.enumConstant())
				);
				case Shape.Text text -> CHECK_CONSTANT_STRING_FIELD.fill(
						"source", source, "component", field.component(), "property", property
				);
				case Shape.Scalar scalar -> CHECK_CONSTANT_FIELD.fill(
						"source", source, "component", field.component(), "property", property
				);
				case Shape.Array array -> throw notAConstant(field);
				case Shape.Set set -> throw notAConstant(field);
				case Shape.Composite composite -> throw notAConstant(field);
				case Shape.Constant inner -> throw notAConstant(field);
			};
		};
	}

	// ---- reading a body

	/** The constructor's arguments, one per component in declared order. */
	private String arguments(Body body) {
		List<String> arguments = new ArrayList<>();
		for (Member member : body.constructorOrder()) {
			arguments.add(switch (member) {
				case Member.Field field -> read(body, field);
				case Member.Group group -> group.component();
				case Member.Unmapped unmapped -> throw new IllegalStateException(
						unmapped.property() + " is read though no component carries it"
				);
			});
		}
		return String.join(",\n", arguments);
	}

	/**
	 * Decided by the wire: the null value for an optional field, whatever its
	 * version, since below the acting version the getter returns it; the version
	 * for a field appended above the baseline, which a required field may hold the
	 * null value of.
	 */
	private String read(Body body, Member.Field field) {
		String read = read(field.property(), field.shape());
		if (field.binding() != null) {
			// The null value and the version are decided before the binding is called.
			read = BOUND_READ.fill("name", field.binding(), "read", read);
		}
		return switch (field.absence()) {
			case NONE, REQUIRED -> read;
			case OPTIONAL -> DECODE_OPTIONAL_FIELD.fill("isNull", isNull(body, field), "read", read);
			case ADDED -> DECODE_ADDED_FIELD.fill(
					"decoder", body.decoder(), "property", field.property(), "read", read
			);
		};
	}

	/** The flyweight call that reads the shape. */
	private String read(String property, Shape shape) {
		return switch (shape) {
			case Shape.Scalar scalar -> DECODE_FIELD.fill("property", property);
			case Shape.Text text -> DECODE_FIELD.fill("property", property);
			case Shape.Array array -> DECODE_ARRAY_FIELD.fill("field", array.field());
			case Shape.Enum enumeration ->
				DECODE_ENUM_FIELD.fill("enum", enumeration.enumClass(), "property", property);
			case Shape.Set set -> DECODE_SET_FIELD.fill("set", set.setClass(), "property", property);
			case Shape.Composite composite -> DECODE_COMPOSITE_FIELD.fill(
					"composite", composite.compositeClass(), "property", property
			);
			case Shape.Constant constant -> read(property, constant.read());
		};
	}

	private String isNull(Body body, Member.Field field) {
		return switch (field.shape()) {
			case Shape.Scalar scalar -> scalar.floatBox() == null
					? IS_NULL.fill("property", field.property(), "decoder", body.decoder())
					: IS_NULL_FLOATING.fill(
							"box", scalar.floatBox(), "property", field.property(), "decoder", body.decoder()
					);
			case Shape.Enum enumeration -> IS_NULL_ENUM.fill(
					"property", field.property(), "flyweights", model.flyweights(), "enum", enumeration.enumClass()
			);
			case Shape.Text text -> throw noNullValue(field);
			case Shape.Array array -> throw noNullValue(field);
			case Shape.Set set -> throw noNullValue(field);
			case Shape.Composite composite -> throw noNullValue(field);
			case Shape.Constant constant -> throw noNullValue(field);
		};
	}

	/**
	 * The body's groups read into locals, in wire order, before the constructor.
	 */
	private String groupReads(Body body) {
		List<String> reads = new ArrayList<>();
		for (Member.Group group : groups(body)) {
			reads.add(
					group.addedSince() == null
							? DECODE_GROUP.fill(
									"record", group.record(), "local", group.component(), "path", group.path(),
									"property", group.property()
							)
							: DECODE_ADDED_GROUP.fill(
									"record", group.record(), "local", group.component(), "decoder", body.decoder(),
									"since", group.addedSince(), "property", group.property(), "path", group.path()
							)
			);
		}
		return String.join("\n", reads);
	}

	// ---- the helpers

	private String helper(Helper helper) {
		return switch (helper) {
			case Helper.Ascii ascii -> ASCII.fill();
			case Helper.ArrayPair array -> arrayPair(array);
			case Helper.EnumPair enumeration -> enumPair(enumeration);
			case Helper.SetPair set -> setPair(set);
			case Helper.CompositePair composite -> String.join(
					"\n\n",
					WRITE_COMPOSITE.fill(
							"composite", composite.compositeClass(), "record", composite.record(), "encoder",
							composite.body().encoder(), "members", writes(composite.body())
					),
					READ_COMPOSITE.fill(
							"record", composite.record(), "composite", composite.compositeClass(), "decoder",
							composite.body().decoder(), "members", arguments(composite.body())
					)
			);
			case Helper.GroupMethods methods -> groupMethods(methods.group());
		};
	}

	private static String arrayPair(Helper.ArrayPair array) {
		if (array.bytes()) {
			return String.join(
					"\n\n",
					WRITE_BYTES.fill(
							"field", array.field(), "encoder", array.encoder(), "property", array.property(),
							"component", array.component()
					),
					READ_BYTES.fill(
							"field", array.field(), "decoder", array.decoder(), "property", array.property()
					)
			);
		}
		return String.join(
				"\n\n",
				WRITE_ARRAY.fill(
						"field", array.field(), "face", array.face(), "encoder", array.encoder(), "property",
						array.property(), "component", array.component()
				),
				READ_ARRAY.fill(
						"field", array.field(), "face", array.face(), "decoder", array.decoder(), "property",
						array.property()
				)
		);
	}

	/**
	 * The raw value mapped by the valid values' text, so an unknown value is ours
	 * to decide: the constant the enum designates, or an exception.
	 */
	private String enumPair(Helper.EnumPair enumeration) {
		List<String> toWire = new ArrayList<>();
		List<String> fromWire = new ArrayList<>();
		for (Helper.EnumValue value : enumeration.values()) {
			toWire.add(
					ENUM_TO_WIRE.fill(
							"constant", value.constant(), "flyweights", model.flyweights(), "enum",
							enumeration.enumClass(), "wireConstant", value.wireConstant()
					)
			);
			fromWire.add(
					WIRE_TO_ENUM.fill(
							"literal", value.literal(), "javaEnum", enumeration.javaEnum(), "constant", value.constant()
					)
			);
		}
		String unknown = enumeration.unknownValue();
		if (unknown == null) {
			fromWire.add(WIRE_TO_NOTHING.fill("enum", enumeration.enumClass()));
		} else {
			toWire.add(UNKNOWN_TO_WIRE.fill("constant", unknown, "enum", enumeration.enumClass()));
			fromWire.add(WIRE_TO_UNKNOWN.fill("javaEnum", enumeration.javaEnum(), "constant", unknown));
		}
		return String.join(
				"\n\n",
				ENCODE_ENUM.fill(
						"flyweights", model.flyweights(), "enum", enumeration.enumClass(), "javaEnum",
						enumeration.javaEnum(), "cases", String.join("\n", toWire)
				),
				DECODE_ENUM.fill(
						"javaEnum", enumeration.javaEnum(), "enum", enumeration.enumClass(), "face", enumeration.face(),
						"cases", String.join("\n", fromWire)
				)
		);
	}

	private String setPair(Helper.SetPair set) {
		List<String> encodes = new ArrayList<>();
		List<String> decodes = new ArrayList<>();
		List<String> bits = new ArrayList<>();
		for (Helper.Choice choice : set.choices()) {
			encodes.add(
					ENCODE_CHOICE.fill(
							"choice", choice.property(), "javaEnum", set.javaEnum(), "constant", choice.constant()
					)
			);
			decodes.add(
					DECODE_CHOICE.fill(
							"choice", choice.property(), "javaEnum", set.javaEnum(), "constant", choice.constant()
					)
			);
			bits.add(KNOWN_BIT.fill("bit", choice.bit()));
		}
		return String.join(
				"\n\n",
				ENCODE_SET.fill(
						"set", set.setClass(), "javaEnum", set.javaEnum(), "flyweights", model.flyweights(),
						"choices", String.join("\n", encodes)
				),
				DECODE_SET.fill(
						"javaEnum", set.javaEnum(), "set", set.setClass(), "flyweights", model.flyweights(),
						"knownBits", String.join(" | ", bits), "choices", String.join("\n", decodes)
				)
		);
	}

	/**
	 * The entries written with the entry's encode statements, read into a list
	 * sized by the count, and summed without encoding, each nested group through
	 * its own length.
	 */
	private String groupMethods(Member.Group group) {
		Body entry = group.entry();
		List<String> terms = new ArrayList<>();
		for (Member.Group nested : groups(entry)) {
			terms.add(
					NESTED_GROUP_LENGTH_TERM.fill("length", lengthOf(nested.path()), "component", nested.component())
			);
		}
		String length = terms.isEmpty()
				? GROUP_LENGTH.fill(
						"length", lengthOf(group.path()), "record", group.record(), "component", group.component(),
						"encoder", entry.encoder()
				)
				: NESTED_GROUP_LENGTH.fill(
						"length", lengthOf(group.path()), "record", group.record(), "component", group.component(),
						"encoder", entry.encoder(), "terms", String.join("\n", terms)
				);
		return String.join(
				"\n\n",
				WRITE_GROUP.fill(
						"path", group.path(), "record", group.record(), "encoder", entry.encoder(), "body",
						writes(entry)
				),
				READ_GROUP.fill(
						"path", group.path(), "record", group.record(), "decoder", entry.decoder(), "groups",
						groupReads(entry), "arguments", arguments(entry)
				),
				length
		);
	}

	private static IllegalStateException noNullValue(Member.Field field) {
		return noNullValue(field.component());
	}

	private static IllegalStateException noNullValue(String name) {
		return new IllegalStateException(name + " has no null value on the wire");
	}

	private static IllegalStateException notAConstant(Member.Field field) {
		return new IllegalStateException(field.component() + " is a constant of no constant's shape");
	}

	private static List<Member.Group> groups(Body body) {
		List<Member.Group> groups = new ArrayList<>();
		for (Member member : body.wireOrder()) {
			if (member instanceof Member.Group group) {
				groups.add(group);
			}
		}
		return groups;
	}

	/** {@code legsLength} for the path {@code Legs}. */
	private static String lengthOf(String path) {
		return Character.toLowerCase(path.charAt(0)) + path.substring(1) + "Length";
	}
}
