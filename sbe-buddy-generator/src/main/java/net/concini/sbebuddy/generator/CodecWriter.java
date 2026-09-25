package net.concini.sbebuddy.generator;

import static net.concini.sbebuddy.generator.CodecTemplates.*;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.concini.sbebuddy.generator.CodecModel.Body;
import net.concini.sbebuddy.generator.CodecModel.Content;
import net.concini.sbebuddy.generator.CodecModel.Helper;
import net.concini.sbebuddy.generator.CodecModel.Member;
import net.concini.sbebuddy.generator.CodecModel.Shape;

/**
 * A {@link CodecModel} as Java source, through {@link CodecTemplates}. Each
 * question has one switch: how a shape is written and read, how its absence
 * wraps that, and what each helper declares; a binding stands in front of the
 * write and behind the read. A template is filled from the model node it
 * writes, and the names the node does not hold are given beside it.
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
		CodecModel.Header header = model.header();
		String headerClass = header.headerClass();
		List<String> bindings = new ArrayList<>();
		for (CodecModel.Binding binding : model.bindings()) {
			bindings.add(BINDING_FIELD.fill(binding));
		}
		List<String> contexts = new ArrayList<>();
		for (CodecModel.Context context : model.contexts()) {
			contexts.add(CONTEXT_FIELD.fill(context));
		}
		List<String> variableLengths = new ArrayList<>();
		for (Member member : variable(body)) {
			variableLengths.add(lengthTerm(LENGTH_TERM, member));
		}
		List<String> helpers = new ArrayList<>();
		for (Helper helper : model.helpers()) {
			helpers.add(helper(helper));
		}
		return CODEC.fill(
				model,
				"headerClass", headerClass,
				"headerRecord", header.record(),
				"bindings", String.join("\n", bindings),
				"contexts", String.join("\n", contexts),
				"variableLengths", String.join("", variableLengths),
				"writeNullHeader", header.nulls().isEmpty() ? "" : WRITE_NULL_HEADER_CALL.fill(),
				"writeHeader", header.body().wireOrder().isEmpty() ? "" : WRITE_HEADER_CALL.fill(),
				"encodeFields", writes(body),
				"atBaseline", model.baseline() == 0
						? ""
						: AT_BASELINE.fill("baseline", String.valueOf(model.baseline())),
				"refuseBelowBaseline", model.baseline() == 0
						? ""
						: REFUSE_BELOW_BASELINE.fill(model, "baseline", String.valueOf(model.baseline())),
				"decodeVariable", variableReads(body),
				"decodeFields", arguments(body),
				"decodedLength", variable(body).isEmpty()
						? BLOCK_DECODED_LENGTH.fill(model, "headerClass", headerClass)
						: WALKED_DECODED_LENGTH.fill(model, "headerClass", headerClass),
				"headerMethods", headerMethods(header),
				"helpers", helpers.isEmpty() ? "" : "\n" + String.join("\n\n", helpers)
		);
	}

	/**
	 * The header read whole, and, where it has members of its own, written from a
	 * header and as null.
	 */
	private String headerMethods(CodecModel.Header header) {
		Body body = header.body();
		List<String> methods = new ArrayList<>();
		methods.add(READ_HEADER.fill(header, "decoder", body.decoder(), "members", arguments(body)));
		if (!body.wireOrder().isEmpty()) {
			methods.add(WRITE_HEADER.fill(header, "encoder", body.encoder(), "members", writes(body)));
		}
		if (!header.nulls().isEmpty()) {
			List<String> nulls = new ArrayList<>();
			for (Member.Unmapped unmapped : header.nulls()) {
				nulls.add(writeNull(body.encoder(), unmapped));
			}
			methods.add(WRITE_NULL_HEADER.fill("encoder", body.encoder(), "members", String.join("\n", nulls)));
		}
		return String.join("\n\n", methods);
	}

	// ---- writing a body

	/** The body's members in wire order, as encode statements. */
	private String writes(Body body) {
		List<String> writes = new ArrayList<>();
		for (Member member : body.wireOrder()) {
			writes.add(switch (member) {
				case Member.Field field -> write(body, field);
				case Member.Unmapped unmapped -> writeNull(body.encoder(), unmapped);
				case Member.Group group -> ENCODE_CHECKED_FIELD.fill(
						group, "call",
						ENCODE_GROUP_FIELD.fill(
								group, "source", group.binding() == null ? SOURCE.fill(group) : BOUND_SOURCE.fill(group)
						)
				);
				case Member.Data data -> ENCODE_DATA_FIELD.fill(data, "source", nullableSource(data, data.binding()));
			});
		}
		return String.join("\n", writes);
	}

	/**
	 * Decided by the component: an optional field takes the null value for null,
	 * any other reference refuses it, and a primitive is written as it is.
	 */
	private String write(Body body, Member.Field field) {
		String source = field.binding() == null ? SOURCE.fill(field) : BOUND_SOURCE.fill(field);
		return switch (field.absence()) {
			case NONE -> write(body, field, field.shape(), source);
			case REQUIRED, ADDED -> ENCODE_CHECKED_FIELD.fill(field, "call", write(body, field, field.shape(), source));
			case NO_NULL_VALUE, ADDED_NO_NULL_VALUE -> field.binding() == null
					? ENCODE_NO_NULL_VALUE_FIELD.fill(field, "call", write(body, field, field.shape(), source))
					: write(body, field, field.shape(), source);
			case OPTIONAL -> switch (field.shape()) {
				case Shape.Scalar scalar ->
					ENCODE_OPTIONAL_FIELD.fill(field, "source", source, "encoder", body.encoder());
				case Shape.Enum enumeration -> ENCODE_OPTIONAL_ENUM_FIELD.fill(
						field, "flyweights", model.flyweights(), "enumClass", enumeration.enumClass(), "source", source
				);
				case Shape.Text text -> throw noNullValue(field.component());
				case Shape.Array array -> throw noNullValue(field.component());
				case Shape.Set set -> throw noNullValue(field.component());
				case Shape.Composite composite -> throw noNullValue(field.component());
				case Shape.Constant constant -> throw noNullValue(field.component());
			};
		};
	}

	/** The flyweight call that writes the shape, from {@code source}. */
	private String write(Body body, Member.Field field, Shape shape, String source) {
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
	 * A field or member no component carries, written as its null value over the
	 * {@code encoder} class of its body.
	 */
	private String writeNull(String encoder, Member.Unmapped unmapped) {
		return switch (unmapped.shape()) {
			case Shape.Scalar scalar -> ENCODE_UNMAPPED_FIELD.fill(unmapped, "encoder", encoder);
			case Shape.Enum enumeration -> ENCODE_UNMAPPED_ENUM_FIELD.fill(
					unmapped, "flyweights", model.flyweights(), "enumClass", enumeration.enumClass()
			);
			case Shape.Set set -> ENCODE_UNMAPPED_SET_FIELD.fill(unmapped);
			case Shape.Text text -> throw noNullValue(unmapped.property());
			case Shape.Array array -> ENCODE_UNMAPPED_ARRAY_FIELD.fill(unmapped, "encoder", encoder);
			case Shape.Composite composite -> throw noNullValue(unmapped.property());
			case Shape.Constant constant -> throw noNullValue(unmapped.property());
		};
	}

	// ---- reading a body

	/** The constructor's arguments, one per component in declared order. */
	private String arguments(Body body) {
		List<String> arguments = new ArrayList<>();
		for (Member member : body.constructorOrder()) {
			arguments.add(switch (member) {
				case Member.Field field -> read(body, field);
				case Member.Group group -> boundLocal(group, group.binding(), group.addedSince());
				case Member.Data data -> boundLocal(data, data.binding(), data.addedSince());
				case Member.Unmapped unmapped -> throw notRead(unmapped.property());
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
		String read = read(field, field.shape());
		if (field.binding() != null) {
			// The null value and the version are decided before the binding is called.
			read = BOUND_READ.fill(field, "read", read);
		}
		return switch (field.absence()) {
			case NONE, REQUIRED, NO_NULL_VALUE -> read;
			case OPTIONAL -> DECODE_OPTIONAL_FIELD.fill("isNull", isNull(body, field), "read", read);
			case ADDED, ADDED_NO_NULL_VALUE -> DECODE_ADDED_FIELD.fill(field, "decoder", body.decoder(), "read", read);
		};
	}

	/** The flyweight call that reads the shape. */
	private String read(Member.Field field, Shape shape) {
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

	private String isNull(Body body, Member.Field field) {
		return switch (field.shape()) {
			case Shape.Scalar scalar -> scalar.floatBox() == null
					? IS_NULL.fill(field, "decoder", body.decoder())
					: IS_NULL_FLOATING.fill(scalar, "property", field.property(), "decoder", body.decoder());
			case Shape.Enum enumeration -> IS_NULL_ENUM.fill(
					field, "flyweights", model.flyweights(), "enumClass", enumeration.enumClass()
			);
			case Shape.Text text -> throw noNullValue(field.component());
			case Shape.Array array -> throw noNullValue(field.component());
			case Shape.Set set -> throw noNullValue(field.component());
			case Shape.Composite composite -> throw noNullValue(field.component());
			case Shape.Constant constant -> throw noNullValue(field.component());
		};
	}

	/**
	 * The body's groups and var-data read into locals, in wire order, before the
	 * constructor: the flyweight reads them one after another.
	 */
	private String variableReads(Body body) {
		List<String> reads = new ArrayList<>();
		for (Member member : variable(body)) {
			reads.add(switch (member) {
				case Member.Group group -> group.addedSince() == null
						? DECODE_GROUP.fill(group)
						: DECODE_ADDED_GROUP.fill(group, "decoder", body.decoder());
				case Member.Data data -> {
					String read = data.content() == Content.BYTES ? DECODE_BYTES.fill(data) : DECODE_TEXT.fill(data);
					yield data.addedSince() == null
							? DECODE_DATA.fill(data, "face", face(data), "read", read)
							: DECODE_ADDED_DATA.fill(data, "face", face(data), "decoder", body.decoder(), "read", read);
				}
				case Member.Field field -> throw notVariable(field.component());
				case Member.Unmapped unmapped -> throw notVariable(unmapped.property());
			});
		}
		return String.join("\n", reads);
	}

	// ---- the helpers

	private String helper(Helper helper) {
		return switch (helper) {
			case Helper.Ascii ascii -> ASCII.fill();
			case Helper.ArrayPair array -> array.bytes()
					? String.join("\n\n", WRITE_BYTES.fill(array), READ_BYTES.fill(array))
					: String.join("\n\n", WRITE_ARRAY.fill(array), READ_ARRAY.fill(array));
			case Helper.EnumPair enumeration -> enumPair(enumeration);
			case Helper.SetPair set -> setPair(set);
			case Helper.CompositePair composite -> String.join(
					"\n\n",
					WRITE_COMPOSITE.fill(
							composite, "encoder", composite.body().encoder(), "members", writes(composite.body())
					),
					READ_COMPOSITE.fill(
							composite, "decoder", composite.body().decoder(), "members", arguments(composite.body())
					)
			);
			case Helper.GroupMethods methods -> groupMethods(methods);
			case Helper.DataMethods methods -> dataMethods(methods);
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
			toWire.add(
					ENUM_TO_WIRE.fill(value, "flyweights", model.flyweights(), "enumClass", enumeration.enumClass())
			);
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
				ENCODE_ENUM.fill(enumeration, "flyweights", model.flyweights(), "cases", String.join("\n", toWire)),
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
				ENCODE_SET.fill(set, "flyweights", model.flyweights(), "choices", String.join("\n", encodes)),
				DECODE_SET.fill(
						set, "flyweights", model.flyweights(), "knownBits", String.join(" | ", bits), "choices",
						String.join("\n", decodes)
				)
		);
	}

	/**
	 * The entries written with the entry's encode statements, read into a list
	 * sized by the count, and summed without encoding, each nested group through
	 * its own length.
	 */
	private String groupMethods(Helper.GroupMethods methods) {
		Member.Group group = methods.group();
		Body entry = group.entry();
		List<String> terms = new ArrayList<>();
		for (Member member : variable(entry)) {
			terms.add(lengthTerm(NESTED_LENGTH_TERM, member));
		}
		String length = terms.isEmpty()
				? GROUP_LENGTH.fill(group, "length", lengthOf(group.path()), "encoder", entry.encoder())
				: NESTED_GROUP_LENGTH.fill(
						group, "length", lengthOf(group.path()), "encoder", entry.encoder(), "terms",
						String.join("\n", terms)
				);
		return String.join(
				"\n\n",
				WRITE_GROUP.fill(group, "encoder", entry.encoder(), "parent", methods.parent(), "body", writes(entry)),
				READ_GROUP.fill(
						group, "decoder", entry.decoder(), "reads", variableReads(entry), "arguments", arguments(entry)
				),
				length
		);
	}

	/**
	 * The length, write and read of one var-data member, each checking what its
	 * content needs.
	 */
	private String dataMethods(Helper.DataMethods methods) {
		Member.Data data = methods.data();
		String length = lengthOf(data.path());
		String count = switch (data.content()) {
			case BYTES -> COUNT_BYTES.fill(methods, "component", data.component());
			case ASCII -> COUNT_ASCII.fill(methods, "component", data.component());
			case UTF_8 -> COUNT_UTF_8.fill(methods, "component", data.component());
			case ENCODED -> COUNT_ENCODED
					.fill(methods, "component", data.component(), "charset", String.valueOf(data.charset()));
		};
		String lengthMethod = DATA_LENGTH.fill(
				methods, "length", length, "face", face(data), "component", data.component(), "property",
				data.property(), "count", count
		);
		if (data.content() == Content.ENCODED) {
			return String.join(
					"\n\n", lengthMethod,
					WRITE_ENCODED_TEXT.fill(
							methods, "path", data.path(), "component", data.component(), "charset",
							String.valueOf(data.charset())
					)
			);
		}
		if (data.content() != Content.BYTES) {
			return String.join(
					"\n\n", lengthMethod,
					WRITE_TEXT.fill(methods, "path", data.path(), "length", length, "property", data.property())
			);
		}
		return String.join(
				"\n\n", lengthMethod,
				WRITE_DATA_BYTES.fill(methods, "path", data.path(), "length", length),
				READ_DATA_BYTES.fill(methods, "path", data.path(), "property", data.property())
		);
	}

	/** The members that follow the block, groups and var-data, in wire order. */
	private static List<Member> variable(Body body) {
		List<Member> variable = new ArrayList<>();
		for (Member member : body.wireOrder()) {
			if (member instanceof Member.Group || member instanceof Member.Data) {
				variable.add(member);
			}
		}
		return variable;
	}

	/**
	 * A group's or a data member's share of the length, through its method, as a
	 * term of the message's sum, or a statement in an entry's.
	 */
	private static String lengthTerm(Template term, Member member) {
		return switch (member) {
			case Member.Group group -> term
					.fill(group, "length", lengthOf(group.path()), "source", nullableSource(group, group.binding()));
			case Member.Data data -> term
					.fill(data, "length", lengthOf(data.path()), "source", nullableSource(data, data.binding()));
			case Member.Field field -> throw notVariable(field.component());
			case Member.Unmapped unmapped -> throw notVariable(unmapped.property());
		};
	}

	/**
	 * What a group or var-data member hands its method: the component, or its
	 * binding's view of it, a null component passed on as null for the method to
	 * refuse.
	 */
	private static String nullableSource(Record member, @Nullable String binding) {
		return binding == null ? SOURCE.fill(member) : NULLABLE_BOUND_SOURCE.fill(member);
	}

	/**
	 * A group's or var-data's constructor argument: the local it was read into, or
	 * that local through its binding, an absent one staying null.
	 */
	private static String boundLocal(Record member, @Nullable String binding, @Nullable String addedSince) {
		if (binding == null) {
			return LOCAL.fill(member);
		}
		return addedSince == null ? BOUND_LOCAL.fill(member) : BOUND_ADDED_LOCAL.fill(member);
	}

	/** {@code legsLength} for the path {@code Legs}. */
	private static String lengthOf(String path) {
		return Character.toLowerCase(path.charAt(0)) + path.substring(1) + "Length";
	}

	private static String face(Member.Data data) {
		return data.content() == Content.BYTES ? "byte[]" : "String";
	}

	private static IllegalStateException notVariable(String name) {
		return new IllegalStateException(name + " is in the block, not after it");
	}

	private static IllegalStateException notRead(String name) {
		return new IllegalStateException(name + " is read though no component carries it");
	}

	private static IllegalStateException noNullValue(String name) {
		return new IllegalStateException(name + " has no null value on the wire");
	}

	private static IllegalStateException notAConstant(Member.Field field) {
		return new IllegalStateException(field.component() + " is a constant of no constant's shape");
	}
}
