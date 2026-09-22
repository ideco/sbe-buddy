package net.concini.sbebuddy.generator;

import static uk.co.real_logic.sbe.PrimitiveType.UINT16;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import uk.co.real_logic.sbe.PrimitiveType;
import uk.co.real_logic.sbe.xml.Presence;

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

	// ---- the annotated twins
	// ----------------------------------------------------------------------------------

	/**
	 * The api's {@code MessageHeader}, one instance, as every twin refers to it by
	 * identity.
	 */
	public static final Annotated.Composite MESSAGE_HEADER = annotatedComposite("MessageHeader")
			.qualifiedName("net.concini.sbebuddy.MessageHeader")
			.name("messageHeader")
			.members(
					annotatedType("blockLength", PrimitiveType.UINT16).javaType(primitive(Annotated.JavaPrimitive.INT)),
					annotatedType("templateId", PrimitiveType.UINT16).javaType(primitive(Annotated.JavaPrimitive.INT)),
					annotatedType("schemaId", PrimitiveType.UINT16).javaType(primitive(Annotated.JavaPrimitive.INT)),
					annotatedType("version", PrimitiveType.UINT16).javaType(primitive(Annotated.JavaPrimitive.INT))
			)
			.build();

	/** The api's {@code GroupSizeEncoding}, one instance. */
	public static final Annotated.Composite GROUP_SIZE_ENCODING = annotatedComposite("GroupSizeEncoding")
			.qualifiedName("net.concini.sbebuddy.GroupSizeEncoding")
			.name("groupSizeEncoding")
			.members(
					annotatedType("blockLength", PrimitiveType.UINT16).javaType(primitive(Annotated.JavaPrimitive.INT)),
					annotatedType("numInGroup", PrimitiveType.UINT16).javaType(primitive(Annotated.JavaPrimitive.INT))
			)
			.build();

	public static AnnotatedSchemaBuilder annotatedSchema(String packageName, int id, int version) {
		return new AnnotatedSchemaBuilder(packageName, id, version);
	}

	public static AnnotatedTypeBuilder annotatedType(String javaName, PrimitiveType primitiveType) {
		return new AnnotatedTypeBuilder(javaName, primitiveType);
	}

	public static AnnotatedCompositeBuilder annotatedComposite(String javaName) {
		return new AnnotatedCompositeBuilder(javaName);
	}

	public static AnnotatedRefBuilder annotatedRef(String javaName, Annotated.JavaType javaType) {
		return new AnnotatedRefBuilder(javaName, javaType);
	}

	public static AnnotatedEnumBuilder annotatedEnum(String javaName) {
		return new AnnotatedEnumBuilder(javaName);
	}

	public static AnnotatedSetBuilder annotatedSet(String javaName) {
		return new AnnotatedSetBuilder(javaName);
	}

	public static AnnotatedMessageBuilder annotatedMessage(String javaName, int id) {
		return new AnnotatedMessageBuilder(javaName, id);
	}

	public static AnnotatedFieldBuilder annotatedField(String javaName, int id, Annotated.JavaType javaType) {
		return new AnnotatedFieldBuilder(javaName, id, javaType);
	}

	public static AnnotatedGroupBuilder annotatedGroup(String javaName, int id) {
		return new AnnotatedGroupBuilder(javaName, id);
	}

	public static AnnotatedDataBuilder annotatedData(
			String javaName, int id, Annotated.JavaType javaType,
			AnnotatedCompositeBuilder type
	) {
		return new AnnotatedDataBuilder(javaName, id, javaType, type.build());
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

	public static Annotated.JavaType bytes() {
		return new Annotated.Bytes();
	}

	public static Annotated.JavaType array(Annotated.JavaPrimitive kind) {
		return new Annotated.Array(kind);
	}

	public static Annotated.JavaType declared(AnnotatedDeclarationBuilder declaration) {
		return new Annotated.Declared(declaration.build());
	}

	/** A Java type no mapping knows, named as the problem would name it. */
	public static Annotated.JavaType other(String javaName) {
		return new Annotated.Other(javaName);
	}

	public static Annotated.JavaType setOf(AnnotatedDeclarationBuilder set) {
		return new Annotated.SetOf(set.build());
	}

	public static Annotated.JavaType unmapped() {
		return new Annotated.Unmapped();
	}

	public interface AnnotatedDeclarationBuilder {
		Annotated.Declaration build();
	}

	public interface AnnotatedMemberBuilder {
		Annotated.Member build();
	}

	public interface AnnotatedComponentBuilder {
		Annotated.Component build();
	}

	public static final class AnnotatedSchemaBuilder {

		private final String packageName;
		private final int id;
		private final int version;
		private final List<Annotated.Declaration> types = new ArrayList<>();
		private final List<Annotated.Message> messages = new ArrayList<>();
		private Annotated.Composite headerType = MESSAGE_HEADER;
		private String semanticVersion = "";
		private String description = "";
		private ByteOrder byteOrder = ByteOrder.LITTLE_ENDIAN;
		private boolean codecs = true;
		private int baselineVersion;

		private AnnotatedSchemaBuilder(String packageName, int id, int version) {
			this.packageName = packageName;
			this.id = id;
			this.version = version;
		}

		public AnnotatedSchemaBuilder types(AnnotatedDeclarationBuilder... builders) {
			for (AnnotatedDeclarationBuilder builder : builders) {
				types.add(builder.build());
			}
			return this;
		}

		public AnnotatedSchemaBuilder messages(AnnotatedMessageBuilder... builders) {
			for (AnnotatedMessageBuilder builder : builders) {
				messages.add(builder.build());
			}
			return this;
		}

		public AnnotatedSchemaBuilder baselineVersion(int baselineVersion) {
			this.baselineVersion = baselineVersion;
			return this;
		}

		public Annotated build() {
			return new Annotated(
					packageName, id, version, headerType, types, messages, semanticVersion, description,
					apiByteOrder(byteOrder), codecs, baselineVersion
			);
		}
	}

	public static final class AnnotatedTypeBuilder implements AnnotatedDeclarationBuilder, AnnotatedMemberBuilder {

		private final String javaName;
		private final PrimitiveType primitiveType;
		private Annotated.@Nullable JavaType javaType;
		private String name = "";
		private String value = "";
		private int length = 1;
		private String characterEncoding = "";
		private Presence presence = Presence.REQUIRED;
		private String valueRef = "";
		private String nullValue = "";
		private String minValue = "";
		private String maxValue = "";
		private int offset;
		private String semanticType = "";
		private String description = "";
		private int sinceVersion;
		private int deprecated;
		private Annotated.@Nullable Type built;

		private AnnotatedTypeBuilder(String javaName, PrimitiveType primitiveType) {
			this.javaName = javaName;
			this.primitiveType = primitiveType;
		}

		public AnnotatedTypeBuilder name(String name) {
			this.name = name;
			return this;
		}

		public AnnotatedTypeBuilder value(String value) {
			this.value = value;
			return this;
		}

		public AnnotatedTypeBuilder length(int length) {
			this.length = length;
			return this;
		}

		public AnnotatedTypeBuilder characterEncoding(String characterEncoding) {
			this.characterEncoding = characterEncoding;
			return this;
		}

		public AnnotatedTypeBuilder presence(Presence presence) {
			this.presence = presence;
			return this;
		}

		public AnnotatedTypeBuilder valueRef(String valueRef) {
			this.valueRef = valueRef;
			return this;
		}

		public AnnotatedTypeBuilder sinceVersion(int sinceVersion) {
			this.sinceVersion = sinceVersion;
			return this;
		}

		public AnnotatedTypeBuilder deprecated(int deprecated) {
			this.deprecated = deprecated;
			return this;
		}

		/** The component's type, for a composite's member. */
		public AnnotatedTypeBuilder javaType(Annotated.JavaType javaType) {
			this.javaType = javaType;
			return this;
		}

		@Override
		public Annotated.Type build() {
			if (built == null) {
				built = new Annotated.Type(
						javaName, javaType, apiPrimitive(primitiveType), name, value, length, characterEncoding,
						apiPresence(presence), valueRef, nullValue, minValue, maxValue, offset, semanticType,
						description, sinceVersion, deprecated
				);
			}
			return built;
		}
	}

	public static final class AnnotatedCompositeBuilder implements AnnotatedDeclarationBuilder, AnnotatedMemberBuilder {

		private final String javaName;
		private final List<Annotated.Member> members = new ArrayList<>();
		private final List<Annotated.Type> unmapped = new ArrayList<>();
		private final List<String> layout = new ArrayList<>();
		private String qualifiedName;
		private String name = "";
		private int offset;
		private String semanticType = "";
		private String description = "";
		private int sinceVersion;
		private int deprecated;
		private Annotated.@Nullable Composite built;

		private AnnotatedCompositeBuilder(String javaName) {
			this.javaName = javaName;
			this.qualifiedName = javaName;
		}

		public AnnotatedCompositeBuilder members(AnnotatedMemberBuilder... builders) {
			for (AnnotatedMemberBuilder builder : builders) {
				members.add(builder.build());
			}
			return this;
		}

		public AnnotatedCompositeBuilder qualifiedName(String qualifiedName) {
			this.qualifiedName = qualifiedName;
			return this;
		}

		public AnnotatedCompositeBuilder unmapped(AnnotatedTypeBuilder... builders) {
			for (AnnotatedTypeBuilder builder : builders) {
				unmapped.add(builder.build());
			}
			return this;
		}

		public AnnotatedCompositeBuilder layout(String... names) {
			layout.addAll(List.of(names));
			return this;
		}

		public AnnotatedCompositeBuilder name(String name) {
			this.name = name;
			return this;
		}

		public AnnotatedCompositeBuilder sinceVersion(int sinceVersion) {
			this.sinceVersion = sinceVersion;
			return this;
		}

		public AnnotatedCompositeBuilder deprecated(int deprecated) {
			this.deprecated = deprecated;
			return this;
		}

		@Override
		public Annotated.Composite build() {
			if (built == null) {
				built = new Annotated.Composite(
						javaName, qualifiedName, members, unmapped, layout, name, offset, semanticType, description,
						sinceVersion, deprecated
				);
			}
			return built;
		}
	}

	public static final class AnnotatedMessageBuilder {

		private final String javaName;
		private final int id;
		private final List<Annotated.Component> components = new ArrayList<>();
		private final List<Annotated.Field> unmapped = new ArrayList<>();
		private final List<String> layout = new ArrayList<>();
		private String name = "";
		private int blockLength;
		private String semanticType = "";
		private String description = "";
		private int sinceVersion;
		private int deprecated;

		private AnnotatedMessageBuilder(String javaName, int id) {
			this.javaName = javaName;
			this.id = id;
		}

		public AnnotatedMessageBuilder components(AnnotatedComponentBuilder... builders) {
			for (AnnotatedComponentBuilder builder : builders) {
				components.add(builder.build());
			}
			return this;
		}

		public AnnotatedMessageBuilder unmapped(AnnotatedFieldBuilder... builders) {
			for (AnnotatedFieldBuilder builder : builders) {
				unmapped.add(builder.build());
			}
			return this;
		}

		public AnnotatedMessageBuilder layout(String... names) {
			layout.addAll(List.of(names));
			return this;
		}

		public AnnotatedMessageBuilder name(String name) {
			this.name = name;
			return this;
		}

		public AnnotatedMessageBuilder sinceVersion(int sinceVersion) {
			this.sinceVersion = sinceVersion;
			return this;
		}

		public AnnotatedMessageBuilder deprecated(int deprecated) {
			this.deprecated = deprecated;
			return this;
		}

		public Annotated.Message build() {
			return new Annotated.Message(
					javaName, id, components, unmapped, layout, name, blockLength, semanticType, description,
					sinceVersion, deprecated
			);
		}
	}

	public static final class AnnotatedFieldBuilder implements AnnotatedComponentBuilder {

		private final String javaName;
		private final int id;
		private final Annotated.JavaType javaType;
		private Annotated.@Nullable Declaration type;
		private @Nullable PrimitiveType primitiveType;
		private String name = "";
		private Presence presence = Presence.REQUIRED;
		private String valueRef = "";
		private int offset;
		private String epoch = "";
		private String timeUnit = "";
		private String semanticType = "";
		private String description = "";
		private int sinceVersion;
		private int deprecated;
		private Annotated.@Nullable Binding binding;
		private Annotated.@Nullable Field built;

		private AnnotatedFieldBuilder(String javaName, int id, Annotated.JavaType javaType) {
			this.javaName = javaName;
			this.id = id;
			this.javaType = javaType;
		}

		public AnnotatedFieldBuilder type(AnnotatedDeclarationBuilder type) {
			this.type = type.build();
			return this;
		}

		public AnnotatedFieldBuilder primitiveType(PrimitiveType primitiveType) {
			this.primitiveType = primitiveType;
			return this;
		}

		public AnnotatedFieldBuilder name(String name) {
			this.name = name;
			return this;
		}

		public AnnotatedFieldBuilder presence(Presence presence) {
			this.presence = presence;
			return this;
		}

		public AnnotatedFieldBuilder valueRef(String valueRef) {
			this.valueRef = valueRef;
			return this;
		}

		public AnnotatedFieldBuilder sinceVersion(int sinceVersion) {
			this.sinceVersion = sinceVersion;
			return this;
		}

		public AnnotatedFieldBuilder deprecated(int deprecated) {
			this.deprecated = deprecated;
			return this;
		}

		public AnnotatedFieldBuilder binding(String qualifiedName, Annotated.JavaType wire) {
			this.binding = new Annotated.Binding(qualifiedName, wire);
			return this;
		}

		@Override
		public Annotated.Field build() {
			if (built == null) {
				built = new Annotated.Field(
						javaName, javaType, id, type, apiPrimitive(primitiveType), name,
						apiPresence(presence), valueRef,
						offset, epoch, timeUnit, semanticType, description, sinceVersion, deprecated, binding
				);
			}
			return built;
		}
	}

	public static final class AnnotatedGroupBuilder implements AnnotatedComponentBuilder {

		private final String javaName;
		private final int id;
		private final List<Annotated.Component> components = new ArrayList<>();
		private final List<Annotated.Field> unmapped = new ArrayList<>();
		private final List<String> layout = new ArrayList<>();
		private Annotated.JavaType javaType;
		private Annotated.Composite dimensionType = GROUP_SIZE_ENCODING;
		private String name = "";
		private int blockLength;
		private String semanticType = "";
		private String description = "";
		private int sinceVersion;
		private int deprecated;
		private Annotated.@Nullable Group built;

		private AnnotatedGroupBuilder(String javaName, int id) {
			this.javaName = javaName;
			this.id = id;
			// A record named after the group, for a test that never reaches the codec.
			this.javaType = new Annotated.ListOfRecord(
					Character.toUpperCase(javaName.charAt(0)) + javaName.substring(1)
			);
		}

		public AnnotatedGroupBuilder javaType(Annotated.JavaType javaType) {
			this.javaType = javaType;
			return this;
		}

		public AnnotatedGroupBuilder components(AnnotatedComponentBuilder... builders) {
			for (AnnotatedComponentBuilder builder : builders) {
				components.add(builder.build());
			}
			return this;
		}

		public AnnotatedGroupBuilder unmapped(AnnotatedFieldBuilder... builders) {
			for (AnnotatedFieldBuilder builder : builders) {
				unmapped.add(builder.build());
			}
			return this;
		}

		public AnnotatedGroupBuilder layout(String... names) {
			layout.addAll(List.of(names));
			return this;
		}

		public AnnotatedGroupBuilder name(String name) {
			this.name = name;
			return this;
		}

		public AnnotatedGroupBuilder sinceVersion(int sinceVersion) {
			this.sinceVersion = sinceVersion;
			return this;
		}

		public AnnotatedGroupBuilder deprecated(int deprecated) {
			this.deprecated = deprecated;
			return this;
		}

		@Override
		public Annotated.Group build() {
			if (built == null) {
				built = new Annotated.Group(
						javaName, javaType, id, components, unmapped, layout, dimensionType, name, blockLength,
						semanticType, description, sinceVersion, deprecated
				);
			}
			return built;
		}
	}

	public static final class AnnotatedDataBuilder implements AnnotatedComponentBuilder {

		private final String javaName;
		private final int id;
		private final Annotated.JavaType javaType;
		private final Annotated.Composite type;
		private String name = "";
		private int offset;
		private String semanticType = "";
		private String description = "";
		private int sinceVersion;
		private int deprecated;
		private Annotated.@Nullable Data built;

		private AnnotatedDataBuilder(String javaName, int id, Annotated.JavaType javaType, Annotated.Composite type) {
			this.javaName = javaName;
			this.id = id;
			this.javaType = javaType;
			this.type = type;
		}

		public AnnotatedDataBuilder name(String name) {
			this.name = name;
			return this;
		}

		public AnnotatedDataBuilder sinceVersion(int sinceVersion) {
			this.sinceVersion = sinceVersion;
			return this;
		}

		public AnnotatedDataBuilder deprecated(int deprecated) {
			this.deprecated = deprecated;
			return this;
		}

		@Override
		public Annotated.Data build() {
			if (built == null) {
				built = new Annotated.Data(
						javaName, javaType, id, type, name, offset, semanticType, description,
						sinceVersion, deprecated
				);
			}
			return built;
		}
	}

	public static final class AnnotatedRefBuilder implements AnnotatedMemberBuilder {

		private final String javaName;
		private final Annotated.JavaType javaType;
		private Annotated.@Nullable Declaration value;
		private String name = "";
		private int offset;
		private int sinceVersion;
		private int deprecated;
		private Annotated.@Nullable Ref built;

		private AnnotatedRefBuilder(String javaName, Annotated.JavaType javaType) {
			this.javaName = javaName;
			this.javaType = javaType;
		}

		public AnnotatedRefBuilder value(AnnotatedDeclarationBuilder value) {
			this.value = value.build();
			return this;
		}

		public AnnotatedRefBuilder name(String name) {
			this.name = name;
			return this;
		}

		public AnnotatedRefBuilder sinceVersion(int sinceVersion) {
			this.sinceVersion = sinceVersion;
			return this;
		}

		public AnnotatedRefBuilder deprecated(int deprecated) {
			this.deprecated = deprecated;
			return this;
		}

		@Override
		public Annotated.Ref build() {
			if (built == null) {
				built = new Annotated.Ref(javaName, javaType, value, name, offset, sinceVersion, deprecated);
			}
			return built;
		}
	}

	public static final class AnnotatedEnumBuilder implements AnnotatedDeclarationBuilder, AnnotatedMemberBuilder {

		private final String javaName;
		private String qualifiedName = "";
		private final List<Annotated.ValidValue> values = new ArrayList<>();
		private @Nullable String unknownValue;
		private Annotated.@Nullable Declaration encodingType;
		private @Nullable PrimitiveType primitiveType;
		private String name = "";
		private int offset;
		private String semanticType = "";
		private String description = "";
		private int sinceVersion;
		private int deprecated;
		private Annotated.@Nullable Enum built;

		private AnnotatedEnumBuilder(String javaName) {
			this.javaName = javaName;
		}

		/** The name code uses for the enum; every twin says it, as discovery does. */
		public AnnotatedEnumBuilder qualifiedName(String qualifiedName) {
			this.qualifiedName = qualifiedName;
			return this;
		}

		public AnnotatedEnumBuilder primitiveType(PrimitiveType primitiveType) {
			this.primitiveType = primitiveType;
			return this;
		}

		public AnnotatedEnumBuilder name(String name) {
			this.name = name;
			return this;
		}

		public AnnotatedEnumBuilder sinceVersion(int sinceVersion) {
			this.sinceVersion = sinceVersion;
			return this;
		}

		public AnnotatedEnumBuilder deprecated(int deprecated) {
			this.deprecated = deprecated;
			return this;
		}

		@Override
		public Annotated.Enum build() {
			if (built == null) {
				built = new Annotated.Enum(
						javaName, qualifiedName, values, unknownValue, encodingType, apiPrimitive(primitiveType), name,
						offset,
						semanticType, description, sinceVersion, deprecated
				);
			}
			return built;
		}
	}

	public static final class AnnotatedSetBuilder implements AnnotatedDeclarationBuilder, AnnotatedMemberBuilder {

		private final String javaName;
		private String qualifiedName = "";
		private final List<Annotated.Choice> choices = new ArrayList<>();
		private Annotated.@Nullable Declaration encodingType;
		private @Nullable PrimitiveType primitiveType;
		private String name = "";
		private int offset;
		private String semanticType = "";
		private String description = "";
		private int sinceVersion;
		private int deprecated;
		private Annotated.@Nullable Set built;

		private AnnotatedSetBuilder(String javaName) {
			this.javaName = javaName;
		}

		/** The name code uses for the enum; every twin says it, as discovery does. */
		public AnnotatedSetBuilder qualifiedName(String qualifiedName) {
			this.qualifiedName = qualifiedName;
			return this;
		}

		public AnnotatedSetBuilder primitiveType(PrimitiveType primitiveType) {
			this.primitiveType = primitiveType;
			return this;
		}

		public AnnotatedSetBuilder name(String name) {
			this.name = name;
			return this;
		}

		public AnnotatedSetBuilder sinceVersion(int sinceVersion) {
			this.sinceVersion = sinceVersion;
			return this;
		}

		public AnnotatedSetBuilder deprecated(int deprecated) {
			this.deprecated = deprecated;
			return this;
		}

		@Override
		public Annotated.Set build() {
			if (built == null) {
				built = new Annotated.Set(
						javaName, qualifiedName, choices, encodingType, apiPrimitive(primitiveType), name, offset,
						semanticType, description, sinceVersion, deprecated
				);
			}
			return built;
		}
	}

	// The twins are written in the corpus's vocabulary, sbe-tool's enums, and the
	// api's enums, which the annotations hold, are made from them here by name. The
	// api's are qualified because they share their simple names with sbe-tool's.

	private static net.concini.sbebuddy.PrimitiveType apiPrimitive(@Nullable PrimitiveType primitiveType) {
		return primitiveType == null
				? net.concini.sbebuddy.PrimitiveType.NONE
				: net.concini.sbebuddy.PrimitiveType.valueOf(primitiveType.name());
	}

	private static net.concini.sbebuddy.Presence apiPresence(Presence presence) {
		return net.concini.sbebuddy.Presence.valueOf(presence.name());
	}

	private static net.concini.sbebuddy.ByteOrder apiByteOrder(ByteOrder byteOrder) {
		return byteOrder == ByteOrder.BIG_ENDIAN
				? net.concini.sbebuddy.ByteOrder.BIG_ENDIAN
				: net.concini.sbebuddy.ByteOrder.LITTLE_ENDIAN;
	}
}
