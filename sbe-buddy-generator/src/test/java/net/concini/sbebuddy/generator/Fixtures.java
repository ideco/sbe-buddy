package net.concini.sbebuddy.generator;

import static uk.co.real_logic.sbe.PrimitiveType.UINT16;

import java.util.List;

import org.jspecify.annotations.Nullable;

import uk.co.real_logic.sbe.PrimitiveType;

import net.concini.sbebuddy.ByteOrder;
import net.concini.sbebuddy.Presence;

/**
 * Test data for {@link MappingTest} and {@link GeneratorTest}: plain factories
 * for the few hand-built schemas the node rules take, and builders for the
 * annotations, whose optional attributes are named.
 */
public final class Fixtures {

	private Fixtures() {
	}

	// ---- schemas built by hand, for the rules that compare nodes
	// ----------------------------------------------------------------------------------

	/** Schema {@code p}, id 1, at the version; nothing optional is set. */
	public static Schema schema(int version, List<Schema.Declaration> types, List<Schema.Message> messages) {
		return new Schema("p", 1, version, types, messages, null, null, null, null);
	}

	public static Schema schema(int version, Schema.Message... messages) {
		return schema(version, List.of(), List.of(messages));
	}

	public static Schema.Message message(String name, int id, Schema.Field... fields) {
		return message(name, id, List.of(fields), List.of(), List.of());
	}

	public static Schema.Message message(
			String name, int id, List<Schema.Field> fields, List<Schema.Group> groups, List<Schema.Data> data
	) {
		return new Schema.Message(name, id, fields, groups, data, null, null, null, null, null);
	}

	public static Schema.Field field(String name, int id, String type) {
		return field(name, id, type, null, null);
	}

	public static Schema.Field field(
			String name, int id, String type, @Nullable Integer sinceVersion, @Nullable Integer deprecated
	) {
		return new Schema.Field(name, id, type, null, null, null, null, null, null, null, sinceVersion, deprecated);
	}

	public static Schema.Group group(String name, int id, Schema.Field... fields) {
		return new Schema.Group(name, id, List.of(fields), List.of(), List.of(), null, null, null, null, null, null);
	}

	public static Schema.Data data(String name, int id, String type) {
		return new Schema.Data(name, id, type, null, null, null, null, null, null, null, null, null);
	}

	public static Schema.Type type(String name, PrimitiveType primitiveType) {
		return new Schema.Type(
				name, primitiveType, null, null, null, null, null, null, null, null, null, null, null, null, null
		);
	}

	public static Schema.Composite composite(String name, Schema.Member... members) {
		return new Schema.Composite(name, List.of(members), null, null, null, null, null);
	}

	/** The standard header, which sbe-tool requires every schema to declare. */
	public static Schema.Composite messageHeader() {
		return composite(
				"messageHeader", type("blockLength", UINT16), type("templateId", UINT16), type("schemaId", UINT16),
				type("version", UINT16)
		);
	}

	// ---- annotations, for the pipeline's tests and the mapping's guarantees
	// ----------------------------------------------------------------------------------

	/**
	 * The api's {@code MessageHeader}, one instance, as the mapping declares it
	 * once.
	 */
	public static final Annotated.Composite MESSAGE_HEADER = annotatedComposite(
			"MessageHeader", "net.concini.sbebuddy.MessageHeader", "messageHeader",
			annotatedType("blockLength", PrimitiveType.UINT16, primitive(Annotated.JavaPrimitive.INT)),
			annotatedType("templateId", PrimitiveType.UINT16, primitive(Annotated.JavaPrimitive.INT)),
			annotatedType("schemaId", PrimitiveType.UINT16, primitive(Annotated.JavaPrimitive.INT)),
			annotatedType("version", PrimitiveType.UINT16, primitive(Annotated.JavaPrimitive.INT))
	);

	/** The api's {@code GroupSizeEncoding}, one instance. */
	public static final Annotated.Composite GROUP_SIZE_ENCODING = annotatedComposite(
			"GroupSizeEncoding", "net.concini.sbebuddy.GroupSizeEncoding", "groupSizeEncoding",
			annotatedType("blockLength", PrimitiveType.UINT16, primitive(Annotated.JavaPrimitive.INT)),
			annotatedType("numInGroup", PrimitiveType.UINT16, primitive(Annotated.JavaPrimitive.INT))
	);

	/**
	 * Package {@code p}, id 1, at the version, with the api's header; nothing
	 * optional is set.
	 */
	public static Annotated annotated(int version, List<Annotated.Declaration> types, Annotated.Message... messages) {
		return new Annotated(
				"p", 1, version, MESSAGE_HEADER, types, List.of(messages), "", "",
				ByteOrder.LITTLE_ENDIAN, true, 0
		);
	}

	public static Annotated annotated(int version, Annotated.Message... messages) {
		return annotated(version, List.of(), messages);
	}

	public static Annotated.Message annotatedMessage(String javaName, int id, Annotated.Component... components) {
		return new Annotated.Message(javaName, id, List.of(components), List.of(), List.of(), "", 0, "", "", 0, 0);
	}

	public static Annotated.Field annotatedField(String javaName, int id, Annotated.JavaType javaType) {
		return annotatedField(javaName, id, javaType, 0);
	}

	public static Annotated.Field annotatedField(
			String javaName, int id, Annotated.JavaType javaType, int sinceVersion
	) {
		return field(javaName, id, javaType, null, null, sinceVersion, null);
	}

	/** A field of a declared type, which names it with {@code type}. */
	public static Annotated.Field annotatedField(
			String javaName, int id, Annotated.JavaType javaType, Annotated.Declaration type
	) {
		return field(javaName, id, javaType, type, null, 0, null);
	}

	/**
	 * A field of a primitive type, bound to the component's type by a binding over
	 * the wire's.
	 */
	public static Annotated.Field boundField(
			String javaName, int id, Annotated.JavaType javaType, PrimitiveType primitiveType, String binding,
			Annotated.JavaType wire
	) {
		return field(javaName, id, javaType, null, primitiveType, 0, new Annotated.Binding(binding, wire));
	}

	private static Annotated.Field field(
			String javaName, int id, Annotated.JavaType javaType, Annotated.@Nullable Declaration type,
			@Nullable PrimitiveType primitiveType, int sinceVersion, Annotated.@Nullable Binding binding
	) {
		return new Annotated.Field(
				javaName, javaType, id, type, apiPrimitive(primitiveType), "", Presence.REQUIRED,
				"", 0, "", "", "", "", sinceVersion, 0, binding
		);
	}

	/**
	 * A group on a {@code List} of a record named after it, which never reaches the
	 * codec in these tests.
	 */
	public static Annotated.Group annotatedGroup(
			String javaName, int id, int sinceVersion, Annotated.Component... components
	) {
		return new Annotated.Group(
				javaName, new Annotated.ListOfRecord(Character.toUpperCase(javaName.charAt(0)) + javaName.substring(1)),
				id, List.of(components), List.of(), List.of(), GROUP_SIZE_ENCODING, "", 0, "", "", sinceVersion, 0
		);
	}

	public static Annotated.Data annotatedData(
			String javaName, int id, Annotated.JavaType javaType, Annotated.Composite type
	) {
		return annotatedData(javaName, id, javaType, type, 0);
	}

	public static Annotated.Data annotatedData(
			String javaName, int id, Annotated.JavaType javaType, Annotated.Composite type, int sinceVersion
	) {
		return new Annotated.Data(javaName, javaType, id, type, "", 0, "", "", sinceVersion, 0);
	}

	/** An inline type of a composite, with the component's Java type. */
	public static Annotated.Type annotatedType(
			String javaName, PrimitiveType primitiveType, Annotated.JavaType javaType
	) {
		return annotatedType(javaName, primitiveType, 1, "", javaType);
	}

	/**
	 * A type with a length, a declaration when its Java type is null and a
	 * composite's member otherwise.
	 */
	public static Annotated.Type annotatedType(
			String javaName, PrimitiveType primitiveType, int length, String characterEncoding,
			Annotated.@Nullable JavaType javaType
	) {
		return new Annotated.Type(
				javaName, javaType, apiPrimitive(primitiveType), "", "", length, characterEncoding,
				Presence.REQUIRED, "", "", "", "", 0, "", "", 0, 0
		);
	}

	public static Annotated.Composite annotatedComposite(
			String javaName, String qualifiedName, String name, Annotated.Member... members
	) {
		return new Annotated.Composite(
				javaName, qualifiedName, List.of(members), List.of(), List.of(), name, 0, "", "", 0, 0
		);
	}

	public static Annotated.JavaType primitive(Annotated.JavaPrimitive kind) {
		return new Annotated.Primitive(kind, false);
	}

	public static Annotated.JavaType boxed(Annotated.JavaPrimitive kind) {
		return new Annotated.Primitive(kind, true);
	}

	public static Annotated.JavaType text() {
		return new Annotated.Text();
	}

	/** A Java type no mapping knows, named as the problem would name it. */
	public static Annotated.JavaType other(String javaName) {
		return new Annotated.Other(javaName);
	}

	// The api's PrimitiveType, qualified because sbe-tool's, which the schema
	// factories take, is imported under the same name.
	private static net.concini.sbebuddy.PrimitiveType apiPrimitive(@Nullable PrimitiveType primitiveType) {
		return primitiveType == null
				? net.concini.sbebuddy.PrimitiveType.NONE
				: net.concini.sbebuddy.PrimitiveType.valueOf(primitiveType.name());
	}
}
