package net.concini.sbebuddy.generator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import net.concini.sbebuddy.ByteOrder;
import net.concini.sbebuddy.Presence;
import net.concini.sbebuddy.PrimitiveType;

/**
 * The function from {@link Annotated} to {@link Schema} that type-mappings.md
 * specifies: a wire name defaults to the Java name, a member left at the XSD's
 * default maps to absent, a Java primitive takes the default mapping, and a
 * reference resolves to the declared type's wire name. {@code types} holds the
 * header, then what the schema declares in its own order, then what a reference
 * reaches elsewhere in the order first reached, each once and every one after
 * the declarations it refers to. The rules decidable from one node fire here; a
 * schema mapped with problems is not for use.
 */
public final class Mapping {

	/**
	 * The schema, the problems, and for every schema node the annotated node it
	 * came from, by identity.
	 */
	public record Mapped(Schema schema, List<Problem> problems, Map<Object, Object> origins) {
	}

	// sbe.xsd's symbolicName_t, which every name attribute is typed as.
	private static final Pattern SYMBOLIC_NAME = Pattern.compile("[A-Za-z_][0-9A-Za-z_]*");

	// sbe.xsd types a message's and a field's id as xs:unsignedShort.
	private static final int MAX_ID = 65535;

	private final List<Problem> problems = new ArrayList<>();
	private final Map<Object, Object> origins = new IdentityHashMap<>();
	private final List<Schema.Declaration> types = new ArrayList<>();
	private final Map<Annotated.Declaration, String> wireNames = new IdentityHashMap<>();

	private Mapping() {
	}

	public static Mapped map(Annotated annotated) {
		Mapping mapping = new Mapping();
		Schema schema = mapping.schema(annotated);
		return new Mapped(schema, List.copyOf(mapping.problems), Collections.unmodifiableMap(mapping.origins));
	}

	private Schema schema(Annotated annotated) {
		String headerType = declare(annotated.headerType());
		for (Annotated.Declaration declaration : annotated.types()) {
			declare(declaration);
		}
		List<Schema.Message> messages = new ArrayList<>();
		for (Annotated.Message message : annotated.messages()) {
			messages.add(message(message));
		}
		Schema schema = new Schema(
				annotated.packageName(),
				annotated.id(),
				annotated.version(),
				types,
				messages,
				absentIfEmpty(annotated.semanticVersion()),
				absentIfEmpty(annotated.description()),
				annotated.byteOrder() == ByteOrder.BIG_ENDIAN ? java.nio.ByteOrder.BIG_ENDIAN : null,
				headerType.equals("messageHeader") ? null : headerType
		);
		origins.put(schema, annotated);
		return schema;
	}

	private Schema.Message message(Annotated.Message message) {
		Body body = body(message.components());
		Schema.Message result = new Schema.Message(
				name(message, message.name(), message.javaName()),
				id(message, message.id()),
				body.fields(),
				body.groups(),
				body.data(),
				absentIfZero(message.blockLength()),
				absentIfEmpty(message.semanticType()),
				absentIfEmpty(message.description()),
				absentIfZero(message.sinceVersion()),
				absentIfZero(message.deprecated())
		);
		origins.put(result, message);
		return result;
	}

	private record Body(List<Schema.Field> fields, List<Schema.Group> groups, List<Schema.Data> data) {
	}

	/**
	 * Fields, then groups, then data: the XSD orders them, so a component out of
	 * order is a problem.
	 */
	private Body body(List<Annotated.Component> components) {
		List<Schema.Field> fields = new ArrayList<>();
		List<Schema.Group> groups = new ArrayList<>();
		List<Schema.Data> data = new ArrayList<>();
		for (Annotated.Component component : components) {
			switch (component) {
				case Annotated.Field field -> {
					if (!groups.isEmpty() || !data.isEmpty()) {
						problem(field, "a field must come before every group and data");
					}
					fields.add(field(field));
				}
				case Annotated.Group group -> {
					if (!data.isEmpty()) {
						problem(group, "a group must come before every data");
					}
					groups.add(group(group));
				}
				case Annotated.Data datum -> data.add(data(datum));
			}
		}
		return new Body(fields, groups, data);
	}

	private Schema.Field field(Annotated.Field field) {
		String type = componentType(field, field.type(), field.primitiveType(), field.javaType());
		Schema.Field result = new Schema.Field(
				name(field, field.name(), field.javaName()),
				id(field, field.id()),
				type,
				presence(field.presence()),
				absentIfEmpty(field.valueRef()),
				absentIfZero(field.offset()),
				absentIfDefault(field.epoch(), "unix"),
				absentIfDefault(field.timeUnit(), "nanosecond"),
				absentIfEmpty(field.semanticType()),
				absentIfEmpty(field.description()),
				absentIfZero(field.sinceVersion()),
				absentIfZero(field.deprecated())
		);
		origins.put(result, field);
		return result;
	}

	private Schema.Group group(Annotated.Group group) {
		if (!(group.javaType() instanceof Annotated.ListOfRecord)) {
			problem(group, "a group must be a List of a record");
		}
		String dimensionType = declare(group.dimensionType());
		Body body = body(group.components());
		Schema.Group result = new Schema.Group(
				name(group, group.name(), group.javaName()),
				id(group, group.id()),
				body.fields(),
				body.groups(),
				body.data(),
				dimensionType.equals("groupSizeEncoding") ? null : dimensionType,
				absentIfZero(group.blockLength()),
				absentIfEmpty(group.semanticType()),
				absentIfEmpty(group.description()),
				absentIfZero(group.sinceVersion()),
				absentIfZero(group.deprecated())
		);
		origins.put(result, group);
		return result;
	}

	private Schema.Data data(Annotated.Data data) {
		if (!(data.javaType() instanceof Annotated.Text) && !(data.javaType() instanceof Annotated.Bytes)) {
			problem(data, "data must be a String or a byte[]");
		}
		Schema.Data result = new Schema.Data(
				name(data, data.name(), data.javaName()),
				id(data, data.id()),
				declare(data.type()),
				null,
				null,
				absentIfZero(data.offset()),
				null,
				null,
				absentIfEmpty(data.semanticType()),
				absentIfEmpty(data.description()),
				absentIfZero(data.sinceVersion()),
				absentIfZero(data.deprecated())
		);
		origins.put(result, data);
		return result;
	}

	/**
	 * A component's wire type: the declared type, the primitive, or what its own
	 * Java type decides.
	 */
	private String componentType(
			Object node, Annotated.@Nullable Declaration type, PrimitiveType primitiveType,
			Annotated.JavaType javaType
	) {
		if (type != null && primitiveType != PrimitiveType.NONE) {
			problem(node, "type and primitiveType both given; give one");
		}
		if (type != null) {
			return declare(type);
		}
		if (primitiveType != PrimitiveType.NONE) {
			return primitive(primitiveType).primitiveName();
		}
		return switch (javaType) {
			case Annotated.Declared declared -> declare(declared.declaration());
			case Annotated.Primitive primitive -> defaultMapping(node, primitive.kind());
			case Annotated.Text text -> unmappable(node, "String");
			case Annotated.Bytes bytes -> unmappable(node, "byte[]");
			case Annotated.ListOfRecord list -> unmappable(node, "List");
			case Annotated.Other other -> unmappable(node, other.javaName());
		};
	}

	/**
	 * Always the same-width signed primitive; unsigned types and SBE's one-byte
	 * char are written explicitly.
	 */
	private String defaultMapping(Object node, Annotated.JavaPrimitive kind) {
		return switch (kind) {
			case BYTE -> "int8";
			case SHORT -> "int16";
			case INT -> "int32";
			case LONG -> "int64";
			case FLOAT -> "float";
			case DOUBLE -> "double";
			case CHAR -> unmappable(node, "char, 16 bits where SBE's char is one byte,");
			case BOOLEAN -> unmappable(node, "boolean");
		};
	}

	private String unmappable(Object node, String javaType) {
		problem(node, javaType + " maps to no SBE type; give type or primitiveType");
		return "?";
	}

	/**
	 * The wire name of a declaration, adding it to {@code types} the first time it
	 * is reached.
	 */
	private String declare(Annotated.Declaration declaration) {
		String known = wireNames.get(declaration);
		if (known != null) {
			return known;
		}
		String wireName = switch (declaration) {
			case Annotated.Type type -> wireName(type.name(), type.javaName());
			case Annotated.Composite composite -> wireName(composite.name(), composite.javaName());
			case Annotated.Enum enumeration -> wireName(enumeration.name(), enumeration.javaName());
			case Annotated.Set set -> wireName(set.name(), set.javaName());
		};
		wireNames.put(declaration, wireName);
		Schema.Declaration mapped = switch (declaration) {
			case Annotated.Type type -> type(type);
			case Annotated.Composite composite -> composite(composite);
			case Annotated.Enum enumeration -> enumeration(enumeration);
			case Annotated.Set set -> set(set);
		};
		types.add(mapped);
		return wireName;
	}

	private Schema.Type type(Annotated.Type type) {
		if (type.primitiveType() == PrimitiveType.NONE) {
			problem(type, "a type needs a primitiveType");
		}
		Schema.Type result = new Schema.Type(
				name(type, type.name(), type.javaName()),
				primitive(type.primitiveType()),
				absentIfEmpty(type.value()),
				absentIfDefault(type.length(), 1),
				absentIfEmpty(type.characterEncoding()),
				presence(type.presence()),
				absentIfEmpty(type.valueRef()),
				absentIfEmpty(type.nullValue()),
				absentIfEmpty(type.minValue()),
				absentIfEmpty(type.maxValue()),
				absentIfZero(type.offset()),
				absentIfEmpty(type.semanticType()),
				absentIfEmpty(type.description()),
				absentIfZero(type.sinceVersion()),
				absentIfZero(type.deprecated())
		);
		origins.put(result, type);
		return result;
	}

	private Schema.Composite composite(Annotated.Composite composite) {
		List<Schema.Member> members = new ArrayList<>();
		for (Annotated.Member member : composite.members()) {
			members.add(switch (member) {
				case Annotated.Type type -> type(type);
				case Annotated.Ref ref -> ref(ref);
				case Annotated.Enum enumeration -> enumeration(enumeration);
				case Annotated.Set set -> set(set);
				case Annotated.Composite nested -> composite(nested);
			});
		}
		Schema.Composite result = new Schema.Composite(
				name(composite, composite.name(), composite.javaName()),
				members,
				absentIfZero(composite.offset()),
				absentIfEmpty(composite.semanticType()),
				absentIfEmpty(composite.description()),
				absentIfZero(composite.sinceVersion()),
				absentIfZero(composite.deprecated())
		);
		origins.put(result, composite);
		return result;
	}

	private Schema.Ref ref(Annotated.Ref ref) {
		Annotated.Declaration target = ref.value();
		if (target == null && ref.javaType() instanceof Annotated.Declared declared) {
			target = declared.declaration();
		}
		String type;
		if (target == null) {
			problem(ref, "a ref needs a value or a component of a declared type");
			type = "?";
		} else {
			type = declare(target);
		}
		Schema.Ref result = new Schema.Ref(
				name(ref, ref.name(), ref.javaName()),
				type,
				absentIfZero(ref.offset()),
				absentIfZero(ref.sinceVersion()),
				absentIfZero(ref.deprecated())
		);
		origins.put(result, ref);
		return result;
	}

	private Schema.Enum enumeration(Annotated.Enum enumeration) {
		String encodingType = encodingType(enumeration, enumeration.encodingType(), enumeration.primitiveType());
		List<Schema.ValidValue> values = new ArrayList<>();
		for (Annotated.ValidValue value : enumeration.values()) {
			Schema.ValidValue mapped = new Schema.ValidValue(
					name(value, value.name(), value.javaName()),
					value.value(),
					absentIfEmpty(value.description()),
					absentIfZero(value.sinceVersion()),
					absentIfZero(value.deprecated())
			);
			origins.put(mapped, value);
			values.add(mapped);
		}
		Schema.Enum result = new Schema.Enum(
				name(enumeration, enumeration.name(), enumeration.javaName()),
				encodingType,
				values,
				absentIfZero(enumeration.offset()),
				absentIfEmpty(enumeration.semanticType()),
				absentIfEmpty(enumeration.description()),
				absentIfZero(enumeration.sinceVersion()),
				absentIfZero(enumeration.deprecated())
		);
		origins.put(result, enumeration);
		return result;
	}

	private Schema.Set set(Annotated.Set set) {
		String encodingType = encodingType(set, set.encodingType(), set.primitiveType());
		List<Schema.Choice> choices = new ArrayList<>();
		for (Annotated.Choice choice : set.choices()) {
			Schema.Choice mapped = new Schema.Choice(
					name(choice, choice.name(), choice.javaName()),
					choice.value(),
					absentIfEmpty(choice.description()),
					absentIfZero(choice.sinceVersion()),
					absentIfZero(choice.deprecated())
			);
			origins.put(mapped, choice);
			choices.add(mapped);
		}
		Schema.Set result = new Schema.Set(
				name(set, set.name(), set.javaName()),
				encodingType,
				choices,
				absentIfZero(set.offset()),
				absentIfEmpty(set.semanticType()),
				absentIfEmpty(set.description()),
				absentIfZero(set.sinceVersion()),
				absentIfZero(set.deprecated())
		);
		origins.put(result, set);
		return result;
	}

	/**
	 * An enum's or a set's encoding: exactly one of a declared type and a
	 * primitive.
	 */
	private String encodingType(
			Object node, Annotated.@Nullable Declaration encodingType,
			PrimitiveType primitiveType
	) {
		if (encodingType != null && primitiveType != PrimitiveType.NONE) {
			problem(node, "encodingType and primitiveType both given; give one");
		}
		if (encodingType != null) {
			return declare(encodingType);
		}
		if (primitiveType == PrimitiveType.NONE) {
			problem(node, "an encoding needs an encodingType or a primitiveType");
			return "?";
		}
		return primitive(primitiveType).primitiveName();
	}

	private void problem(Object node, String message) {
		problems.add(new Problem(node, message));
	}

	/**
	 * The wire name, blaming the node for one the XSD would refuse; a declaration
	 * is named once, where it is declared, not again at every reference.
	 */
	private String name(Object node, String name, String javaName) {
		String wireName = wireName(name, javaName);
		if (!SYMBOLIC_NAME.matcher(wireName).matches()) {
			problem(node, "\"" + wireName + "\" is not a name SBE allows: a letter or _, then letters, digits and _");
		}
		return wireName;
	}

	private static String wireName(String name, String javaName) {
		return name.isEmpty() ? javaName : name;
	}

	private int id(Object node, int id) {
		if (id < 0 || id > MAX_ID) {
			problem(node, "an id is 0 to " + MAX_ID + ", not " + id);
		}
		return id;
	}

	private static @Nullable String absentIfEmpty(String value) {
		return value.isEmpty() ? null : value;
	}

	private static @Nullable String absentIfDefault(String value, String xsdDefault) {
		return value.equals(xsdDefault) ? null : value;
	}

	private static @Nullable Integer absentIfZero(int value) {
		return value == 0 ? null : value;
	}

	private static @Nullable Integer absentIfDefault(int value, int xsdDefault) {
		return value == xsdDefault ? null : value;
	}

	// The api's enums are the ones users write; sbe-tool's, which Schema holds, are
	// qualified where they collide.
	private static uk.co.real_logic.sbe.xml.@Nullable Presence presence(Presence presence) {
		return presence == Presence.REQUIRED ? null : uk.co.real_logic.sbe.xml.Presence.valueOf(presence.name());
	}

	private static uk.co.real_logic.sbe.PrimitiveType primitive(PrimitiveType primitiveType) {
		if (primitiveType == PrimitiveType.NONE) {
			return uk.co.real_logic.sbe.PrimitiveType.INT8; // reported already; any primitive keeps the schema
															// well-formed
		}
		return uk.co.real_logic.sbe.PrimitiveType.valueOf(primitiveType.name());
	}
}
