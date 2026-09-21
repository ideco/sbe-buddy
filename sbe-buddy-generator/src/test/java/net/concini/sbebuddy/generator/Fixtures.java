package net.concini.sbebuddy.generator;

import static uk.co.real_logic.sbe.PrimitiveType.UINT16;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import uk.co.real_logic.sbe.PrimitiveType;
import uk.co.real_logic.sbe.xml.Presence;

/**
 * Builders for corpus cases, the only builders in the repository: a case reads
 * like its oracle. Required attributes are positional, optional ones are named,
 * {@code build()} is called once at the top.
 */
public final class Fixtures {

	private Fixtures() {
	}

	public static SchemaBuilder messageSchema(String packageName, int id, int version) {
		return new SchemaBuilder(packageName, id, version);
	}

	public static TypeBuilder type(String name, PrimitiveType primitiveType) {
		return new TypeBuilder(name, primitiveType);
	}

	public static CompositeBuilder composite(String name) {
		return new CompositeBuilder(name);
	}

	public static RefBuilder ref(String name, String type) {
		return new RefBuilder(name, type);
	}

	/** The {@code enum} element; {@code enum} is a keyword. */
	public static EnumBuilder enumeration(String name, String encodingType) {
		return new EnumBuilder(name, encodingType);
	}

	public static ValidValueBuilder validValue(String name, String value) {
		return new ValidValueBuilder(name, value);
	}

	public static SetBuilder set(String name, String encodingType) {
		return new SetBuilder(name, encodingType);
	}

	public static ChoiceBuilder choice(String name, int value) {
		return new ChoiceBuilder(name, value);
	}

	public static MessageBuilder message(String name, int id) {
		return new MessageBuilder(name, id);
	}

	public static FieldBuilder field(String name, int id, String type) {
		return new FieldBuilder(name, id, type);
	}

	public static GroupBuilder group(String name, int id) {
		return new GroupBuilder(name, id);
	}

	public static DataBuilder data(String name, int id, String type) {
		return new DataBuilder(name, id, type);
	}

	/** The standard header, which sbe-tool requires every schema to declare. */
	public static CompositeBuilder messageHeader() {
		return composite("messageHeader").members(
				type("blockLength", UINT16),
				type("templateId", UINT16),
				type("schemaId", UINT16),
				type("version", UINT16)
		);
	}

	/** The dimensions a group takes when it names no {@code dimensionType}. */
	public static CompositeBuilder groupSizeEncoding() {
		return composite("groupSizeEncoding").members(
				type("blockLength", UINT16),
				type("numInGroup", UINT16)
		);
	}

	public interface DeclarationBuilder {
		public Schema.Declaration build();
	}

	public interface MemberBuilder {
		public Schema.Member build();
	}

	public static final class SchemaBuilder {

		private final String packageName;
		private final int id;
		private final int version;
		private final List<Schema.Declaration> types = new ArrayList<>();
		private final List<Schema.Message> messages = new ArrayList<>();
		private @Nullable String semanticVersion;
		private @Nullable String description;
		private @Nullable ByteOrder byteOrder;
		private @Nullable String headerType;

		private SchemaBuilder(String packageName, int id, int version) {
			this.packageName = packageName;
			this.id = id;
			this.version = version;
		}

		public SchemaBuilder types(DeclarationBuilder... declarations) {
			for (DeclarationBuilder declaration : declarations) {
				types.add(declaration.build());
			}
			return this;
		}

		public SchemaBuilder messages(MessageBuilder... builders) {
			for (MessageBuilder builder : builders) {
				messages.add(builder.build());
			}
			return this;
		}

		public SchemaBuilder semanticVersion(String semanticVersion) {
			this.semanticVersion = semanticVersion;
			return this;
		}

		public SchemaBuilder description(String description) {
			this.description = description;
			return this;
		}

		public SchemaBuilder byteOrder(ByteOrder byteOrder) {
			this.byteOrder = byteOrder;
			return this;
		}

		public SchemaBuilder headerType(String headerType) {
			this.headerType = headerType;
			return this;
		}

		public Schema build() {
			return new Schema(
					packageName, id, version, types, messages, semanticVersion, description, byteOrder,
					headerType
			);
		}
	}

	public static final class TypeBuilder implements DeclarationBuilder, MemberBuilder {

		private final String name;
		private final PrimitiveType primitiveType;
		private @Nullable String value;
		private @Nullable Integer length;
		private @Nullable String characterEncoding;
		private @Nullable Presence presence;
		private @Nullable String valueRef;
		private @Nullable String nullValue;
		private @Nullable String minValue;
		private @Nullable String maxValue;
		private @Nullable Integer offset;
		private @Nullable String semanticType;
		private @Nullable String description;
		private @Nullable Integer sinceVersion;
		private @Nullable Integer deprecated;

		private TypeBuilder(String name, PrimitiveType primitiveType) {
			this.name = name;
			this.primitiveType = primitiveType;
		}

		public TypeBuilder value(String value) {
			this.value = value;
			return this;
		}

		public TypeBuilder length(int length) {
			this.length = length;
			return this;
		}

		public TypeBuilder characterEncoding(String characterEncoding) {
			this.characterEncoding = characterEncoding;
			return this;
		}

		public TypeBuilder presence(Presence presence) {
			this.presence = presence;
			return this;
		}

		public TypeBuilder valueRef(String valueRef) {
			this.valueRef = valueRef;
			return this;
		}

		public TypeBuilder nullValue(String nullValue) {
			this.nullValue = nullValue;
			return this;
		}

		public TypeBuilder minValue(String minValue) {
			this.minValue = minValue;
			return this;
		}

		public TypeBuilder maxValue(String maxValue) {
			this.maxValue = maxValue;
			return this;
		}

		public TypeBuilder offset(int offset) {
			this.offset = offset;
			return this;
		}

		public TypeBuilder semanticType(String semanticType) {
			this.semanticType = semanticType;
			return this;
		}

		public TypeBuilder description(String description) {
			this.description = description;
			return this;
		}

		public TypeBuilder sinceVersion(int sinceVersion) {
			this.sinceVersion = sinceVersion;
			return this;
		}

		public TypeBuilder deprecated(int deprecated) {
			this.deprecated = deprecated;
			return this;
		}

		@Override
		public Schema.Type build() {
			return new Schema.Type(
					name, primitiveType, value, length, characterEncoding, presence, valueRef, nullValue,
					minValue, maxValue, offset, semanticType, description, sinceVersion, deprecated
			);
		}
	}

	public static final class CompositeBuilder implements DeclarationBuilder, MemberBuilder {

		private final String name;
		private final List<Schema.Member> members = new ArrayList<>();
		private @Nullable Integer offset;
		private @Nullable String semanticType;
		private @Nullable String description;
		private @Nullable Integer sinceVersion;
		private @Nullable Integer deprecated;

		private CompositeBuilder(String name) {
			this.name = name;
		}

		public CompositeBuilder members(MemberBuilder... builders) {
			for (MemberBuilder builder : builders) {
				members.add(builder.build());
			}
			return this;
		}

		public CompositeBuilder offset(int offset) {
			this.offset = offset;
			return this;
		}

		public CompositeBuilder semanticType(String semanticType) {
			this.semanticType = semanticType;
			return this;
		}

		public CompositeBuilder description(String description) {
			this.description = description;
			return this;
		}

		public CompositeBuilder sinceVersion(int sinceVersion) {
			this.sinceVersion = sinceVersion;
			return this;
		}

		public CompositeBuilder deprecated(int deprecated) {
			this.deprecated = deprecated;
			return this;
		}

		@Override
		public Schema.Composite build() {
			return new Schema.Composite(name, members, offset, semanticType, description, sinceVersion, deprecated);
		}
	}

	public static final class MessageBuilder {

		private final String name;
		private final int id;
		private final List<Schema.Field> fields = new ArrayList<>();
		private final List<Schema.Group> groups = new ArrayList<>();
		private final List<Schema.Data> data = new ArrayList<>();
		private @Nullable Integer blockLength;
		private @Nullable String semanticType;
		private @Nullable String description;
		private @Nullable Integer sinceVersion;
		private @Nullable Integer deprecated;

		private MessageBuilder(String name, int id) {
			this.name = name;
			this.id = id;
		}

		public MessageBuilder fields(FieldBuilder... builders) {
			for (FieldBuilder builder : builders) {
				fields.add(builder.build());
			}
			return this;
		}

		public MessageBuilder groups(GroupBuilder... builders) {
			for (GroupBuilder builder : builders) {
				groups.add(builder.build());
			}
			return this;
		}

		public MessageBuilder data(DataBuilder... builders) {
			for (DataBuilder builder : builders) {
				data.add(builder.build());
			}
			return this;
		}

		public MessageBuilder blockLength(int blockLength) {
			this.blockLength = blockLength;
			return this;
		}

		public MessageBuilder semanticType(String semanticType) {
			this.semanticType = semanticType;
			return this;
		}

		public MessageBuilder description(String description) {
			this.description = description;
			return this;
		}

		public MessageBuilder sinceVersion(int sinceVersion) {
			this.sinceVersion = sinceVersion;
			return this;
		}

		public MessageBuilder deprecated(int deprecated) {
			this.deprecated = deprecated;
			return this;
		}

		public Schema.Message build() {
			return new Schema.Message(
					name, id, fields, groups, data, blockLength, semanticType, description,
					sinceVersion, deprecated
			);
		}
	}

	public static final class FieldBuilder {

		private final String name;
		private final int id;
		private final String type;
		private @Nullable Presence presence;
		private @Nullable String valueRef;
		private @Nullable Integer offset;
		private @Nullable String epoch;
		private @Nullable String timeUnit;
		private @Nullable String semanticType;
		private @Nullable String description;
		private @Nullable Integer sinceVersion;
		private @Nullable Integer deprecated;

		private FieldBuilder(String name, int id, String type) {
			this.name = name;
			this.id = id;
			this.type = type;
		}

		public FieldBuilder presence(Presence presence) {
			this.presence = presence;
			return this;
		}

		public FieldBuilder valueRef(String valueRef) {
			this.valueRef = valueRef;
			return this;
		}

		public FieldBuilder offset(int offset) {
			this.offset = offset;
			return this;
		}

		public FieldBuilder epoch(String epoch) {
			this.epoch = epoch;
			return this;
		}

		public FieldBuilder timeUnit(String timeUnit) {
			this.timeUnit = timeUnit;
			return this;
		}

		public FieldBuilder semanticType(String semanticType) {
			this.semanticType = semanticType;
			return this;
		}

		public FieldBuilder description(String description) {
			this.description = description;
			return this;
		}

		public FieldBuilder sinceVersion(int sinceVersion) {
			this.sinceVersion = sinceVersion;
			return this;
		}

		public FieldBuilder deprecated(int deprecated) {
			this.deprecated = deprecated;
			return this;
		}

		public Schema.Field build() {
			return new Schema.Field(
					name, id, type, presence, valueRef, offset, epoch, timeUnit, semanticType,
					description, sinceVersion, deprecated
			);
		}
	}

	public static final class RefBuilder implements MemberBuilder {

		private final String name;
		private final String type;
		private @Nullable Integer offset;
		private @Nullable Integer sinceVersion;
		private @Nullable Integer deprecated;

		private RefBuilder(String name, String type) {
			this.name = name;
			this.type = type;
		}

		public RefBuilder offset(int offset) {
			this.offset = offset;
			return this;
		}

		public RefBuilder sinceVersion(int sinceVersion) {
			this.sinceVersion = sinceVersion;
			return this;
		}

		public RefBuilder deprecated(int deprecated) {
			this.deprecated = deprecated;
			return this;
		}

		@Override
		public Schema.Ref build() {
			return new Schema.Ref(name, type, offset, sinceVersion, deprecated);
		}
	}

	public static final class EnumBuilder implements DeclarationBuilder, MemberBuilder {

		private final String name;
		private final String encodingType;
		private final List<Schema.ValidValue> validValues = new ArrayList<>();
		private @Nullable Integer offset;
		private @Nullable String semanticType;
		private @Nullable String description;
		private @Nullable Integer sinceVersion;
		private @Nullable Integer deprecated;

		private EnumBuilder(String name, String encodingType) {
			this.name = name;
			this.encodingType = encodingType;
		}

		public EnumBuilder validValues(ValidValueBuilder... builders) {
			for (ValidValueBuilder builder : builders) {
				validValues.add(builder.build());
			}
			return this;
		}

		public EnumBuilder offset(int offset) {
			this.offset = offset;
			return this;
		}

		public EnumBuilder semanticType(String semanticType) {
			this.semanticType = semanticType;
			return this;
		}

		public EnumBuilder description(String description) {
			this.description = description;
			return this;
		}

		public EnumBuilder sinceVersion(int sinceVersion) {
			this.sinceVersion = sinceVersion;
			return this;
		}

		public EnumBuilder deprecated(int deprecated) {
			this.deprecated = deprecated;
			return this;
		}

		@Override
		public Schema.Enum build() {
			return new Schema.Enum(
					name, encodingType, validValues, offset, semanticType, description, sinceVersion,
					deprecated
			);
		}
	}

	public static final class ValidValueBuilder {

		private final String name;
		private final String value;
		private @Nullable String description;
		private @Nullable Integer sinceVersion;
		private @Nullable Integer deprecated;

		private ValidValueBuilder(String name, String value) {
			this.name = name;
			this.value = value;
		}

		public ValidValueBuilder description(String description) {
			this.description = description;
			return this;
		}

		public ValidValueBuilder sinceVersion(int sinceVersion) {
			this.sinceVersion = sinceVersion;
			return this;
		}

		public ValidValueBuilder deprecated(int deprecated) {
			this.deprecated = deprecated;
			return this;
		}

		public Schema.ValidValue build() {
			return new Schema.ValidValue(name, value, description, sinceVersion, deprecated);
		}
	}

	public static final class SetBuilder implements DeclarationBuilder, MemberBuilder {

		private final String name;
		private final String encodingType;
		private final List<Schema.Choice> choices = new ArrayList<>();
		private @Nullable Integer offset;
		private @Nullable String semanticType;
		private @Nullable String description;
		private @Nullable Integer sinceVersion;
		private @Nullable Integer deprecated;

		private SetBuilder(String name, String encodingType) {
			this.name = name;
			this.encodingType = encodingType;
		}

		public SetBuilder choices(ChoiceBuilder... builders) {
			for (ChoiceBuilder builder : builders) {
				choices.add(builder.build());
			}
			return this;
		}

		public SetBuilder offset(int offset) {
			this.offset = offset;
			return this;
		}

		public SetBuilder semanticType(String semanticType) {
			this.semanticType = semanticType;
			return this;
		}

		public SetBuilder description(String description) {
			this.description = description;
			return this;
		}

		public SetBuilder sinceVersion(int sinceVersion) {
			this.sinceVersion = sinceVersion;
			return this;
		}

		public SetBuilder deprecated(int deprecated) {
			this.deprecated = deprecated;
			return this;
		}

		@Override
		public Schema.Set build() {
			return new Schema.Set(
					name, encodingType, choices, offset, semanticType, description, sinceVersion, deprecated
			);
		}
	}

	public static final class ChoiceBuilder {

		private final String name;
		private final int value;
		private @Nullable String description;
		private @Nullable Integer sinceVersion;
		private @Nullable Integer deprecated;

		private ChoiceBuilder(String name, int value) {
			this.name = name;
			this.value = value;
		}

		public ChoiceBuilder description(String description) {
			this.description = description;
			return this;
		}

		public ChoiceBuilder sinceVersion(int sinceVersion) {
			this.sinceVersion = sinceVersion;
			return this;
		}

		public ChoiceBuilder deprecated(int deprecated) {
			this.deprecated = deprecated;
			return this;
		}

		public Schema.Choice build() {
			return new Schema.Choice(name, value, description, sinceVersion, deprecated);
		}
	}

	public static final class GroupBuilder {

		private final String name;
		private final int id;
		private final List<Schema.Field> fields = new ArrayList<>();
		private final List<Schema.Group> groups = new ArrayList<>();
		private final List<Schema.Data> data = new ArrayList<>();
		private @Nullable String dimensionType;
		private @Nullable Integer blockLength;
		private @Nullable String semanticType;
		private @Nullable String description;
		private @Nullable Integer sinceVersion;
		private @Nullable Integer deprecated;

		private GroupBuilder(String name, int id) {
			this.name = name;
			this.id = id;
		}

		public GroupBuilder fields(FieldBuilder... builders) {
			for (FieldBuilder builder : builders) {
				fields.add(builder.build());
			}
			return this;
		}

		public GroupBuilder groups(GroupBuilder... builders) {
			for (GroupBuilder builder : builders) {
				groups.add(builder.build());
			}
			return this;
		}

		public GroupBuilder data(DataBuilder... builders) {
			for (DataBuilder builder : builders) {
				data.add(builder.build());
			}
			return this;
		}

		public GroupBuilder dimensionType(String dimensionType) {
			this.dimensionType = dimensionType;
			return this;
		}

		public GroupBuilder blockLength(int blockLength) {
			this.blockLength = blockLength;
			return this;
		}

		public GroupBuilder semanticType(String semanticType) {
			this.semanticType = semanticType;
			return this;
		}

		public GroupBuilder description(String description) {
			this.description = description;
			return this;
		}

		public GroupBuilder sinceVersion(int sinceVersion) {
			this.sinceVersion = sinceVersion;
			return this;
		}

		public GroupBuilder deprecated(int deprecated) {
			this.deprecated = deprecated;
			return this;
		}

		public Schema.Group build() {
			return new Schema.Group(
					name, id, fields, groups, data, dimensionType, blockLength, semanticType, description,
					sinceVersion, deprecated
			);
		}
	}

	public static final class DataBuilder {

		private final String name;
		private final int id;
		private final String type;
		private @Nullable Presence presence;
		private @Nullable String valueRef;
		private @Nullable Integer offset;
		private @Nullable String epoch;
		private @Nullable String timeUnit;
		private @Nullable String semanticType;
		private @Nullable String description;
		private @Nullable Integer sinceVersion;
		private @Nullable Integer deprecated;

		private DataBuilder(String name, int id, String type) {
			this.name = name;
			this.id = id;
			this.type = type;
		}

		public DataBuilder presence(Presence presence) {
			this.presence = presence;
			return this;
		}

		public DataBuilder valueRef(String valueRef) {
			this.valueRef = valueRef;
			return this;
		}

		public DataBuilder offset(int offset) {
			this.offset = offset;
			return this;
		}

		public DataBuilder epoch(String epoch) {
			this.epoch = epoch;
			return this;
		}

		public DataBuilder timeUnit(String timeUnit) {
			this.timeUnit = timeUnit;
			return this;
		}

		public DataBuilder semanticType(String semanticType) {
			this.semanticType = semanticType;
			return this;
		}

		public DataBuilder description(String description) {
			this.description = description;
			return this;
		}

		public DataBuilder sinceVersion(int sinceVersion) {
			this.sinceVersion = sinceVersion;
			return this;
		}

		public DataBuilder deprecated(int deprecated) {
			this.deprecated = deprecated;
			return this;
		}

		public Schema.Data build() {
			return new Schema.Data(
					name, id, type, presence, valueRef, offset, epoch, timeUnit, semanticType, description,
					sinceVersion, deprecated
			);
		}
	}

	// ---- the annotated twins
	// ----------------------------------------------------------------------------------

	/**
	 * The api's {@code MessageHeader}, one instance, as every twin refers to it by
	 * identity.
	 */
	public static final Annotated.Composite MESSAGE_HEADER = annotatedComposite("MessageHeader")
			.name("messageHeader")
			.members(
					annotatedType("blockLength", PrimitiveType.UINT16),
					annotatedType("templateId", PrimitiveType.UINT16),
					annotatedType("schemaId", PrimitiveType.UINT16),
					annotatedType("version", PrimitiveType.UINT16)
			)
			.build();

	/** The api's {@code GroupSizeEncoding}, one instance. */
	public static final Annotated.Composite GROUP_SIZE_ENCODING = annotatedComposite("GroupSizeEncoding")
			.name("groupSizeEncoding")
			.members(
					annotatedType("blockLength", PrimitiveType.UINT16),
					annotatedType("numInGroup", PrimitiveType.UINT16)
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

	public static AnnotatedEnumValueBuilder annotatedEnumValue(String javaName, String value) {
		return new AnnotatedEnumValueBuilder(javaName, value);
	}

	public static AnnotatedSetBuilder annotatedSet(String javaName) {
		return new AnnotatedSetBuilder(javaName);
	}

	public static AnnotatedChoiceBuilder annotatedChoice(String javaName, int value) {
		return new AnnotatedChoiceBuilder(javaName, value);
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

	public static Annotated.JavaType declared(AnnotatedDeclarationBuilder declaration) {
		return new Annotated.Declared(declaration.build());
	}

	/** A Java type no mapping knows, named as the problem would name it. */
	public static Annotated.JavaType other(String javaName) {
		return new Annotated.Other(javaName);
	}

	public static Annotated.JavaType listOfRecord() {
		return new Annotated.ListOfRecord();
	}

	public static Annotated.JavaType setOf(AnnotatedDeclarationBuilder set) {
		return new Annotated.SetOf(set.build());
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

		public AnnotatedSchemaBuilder headerType(AnnotatedCompositeBuilder headerType) {
			this.headerType = headerType.build();
			return this;
		}

		public AnnotatedSchemaBuilder semanticVersion(String semanticVersion) {
			this.semanticVersion = semanticVersion;
			return this;
		}

		public AnnotatedSchemaBuilder description(String description) {
			this.description = description;
			return this;
		}

		public AnnotatedSchemaBuilder byteOrder(ByteOrder byteOrder) {
			this.byteOrder = byteOrder;
			return this;
		}

		public AnnotatedSchemaBuilder codecs(boolean codecs) {
			this.codecs = codecs;
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

		public AnnotatedTypeBuilder nullValue(String nullValue) {
			this.nullValue = nullValue;
			return this;
		}

		public AnnotatedTypeBuilder minValue(String minValue) {
			this.minValue = minValue;
			return this;
		}

		public AnnotatedTypeBuilder maxValue(String maxValue) {
			this.maxValue = maxValue;
			return this;
		}

		public AnnotatedTypeBuilder offset(int offset) {
			this.offset = offset;
			return this;
		}

		public AnnotatedTypeBuilder semanticType(String semanticType) {
			this.semanticType = semanticType;
			return this;
		}

		public AnnotatedTypeBuilder description(String description) {
			this.description = description;
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

		@Override
		public Annotated.Type build() {
			if (built == null) {
				built = new Annotated.Type(
						javaName, apiPrimitive(primitiveType), name, value, length, characterEncoding,
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
		private String name = "";
		private int offset;
		private String semanticType = "";
		private String description = "";
		private int sinceVersion;
		private int deprecated;
		private Annotated.@Nullable Composite built;

		private AnnotatedCompositeBuilder(String javaName) {
			this.javaName = javaName;
		}

		public AnnotatedCompositeBuilder members(AnnotatedMemberBuilder... builders) {
			for (AnnotatedMemberBuilder builder : builders) {
				members.add(builder.build());
			}
			return this;
		}

		public AnnotatedCompositeBuilder name(String name) {
			this.name = name;
			return this;
		}

		public AnnotatedCompositeBuilder offset(int offset) {
			this.offset = offset;
			return this;
		}

		public AnnotatedCompositeBuilder semanticType(String semanticType) {
			this.semanticType = semanticType;
			return this;
		}

		public AnnotatedCompositeBuilder description(String description) {
			this.description = description;
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
						javaName, members, name, offset, semanticType, description,
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

		public AnnotatedMessageBuilder name(String name) {
			this.name = name;
			return this;
		}

		public AnnotatedMessageBuilder blockLength(int blockLength) {
			this.blockLength = blockLength;
			return this;
		}

		public AnnotatedMessageBuilder semanticType(String semanticType) {
			this.semanticType = semanticType;
			return this;
		}

		public AnnotatedMessageBuilder description(String description) {
			this.description = description;
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
					javaName, id, components, name, blockLength, semanticType, description,
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

		public AnnotatedFieldBuilder offset(int offset) {
			this.offset = offset;
			return this;
		}

		public AnnotatedFieldBuilder epoch(String epoch) {
			this.epoch = epoch;
			return this;
		}

		public AnnotatedFieldBuilder timeUnit(String timeUnit) {
			this.timeUnit = timeUnit;
			return this;
		}

		public AnnotatedFieldBuilder semanticType(String semanticType) {
			this.semanticType = semanticType;
			return this;
		}

		public AnnotatedFieldBuilder description(String description) {
			this.description = description;
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

		@Override
		public Annotated.Field build() {
			if (built == null) {
				built = new Annotated.Field(
						javaName, javaType, id, type, apiPrimitive(primitiveType), name,
						apiPresence(presence), valueRef,
						offset, epoch, timeUnit, semanticType, description, sinceVersion, deprecated
				);
			}
			return built;
		}
	}

	public static final class AnnotatedGroupBuilder implements AnnotatedComponentBuilder {

		private final String javaName;
		private final int id;
		private final List<Annotated.Component> components = new ArrayList<>();
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
			this.javaType = new Annotated.ListOfRecord();
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

		public AnnotatedGroupBuilder dimensionType(AnnotatedCompositeBuilder dimensionType) {
			this.dimensionType = dimensionType.build();
			return this;
		}

		public AnnotatedGroupBuilder name(String name) {
			this.name = name;
			return this;
		}

		public AnnotatedGroupBuilder blockLength(int blockLength) {
			this.blockLength = blockLength;
			return this;
		}

		public AnnotatedGroupBuilder semanticType(String semanticType) {
			this.semanticType = semanticType;
			return this;
		}

		public AnnotatedGroupBuilder description(String description) {
			this.description = description;
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
						javaName, javaType, id, components, dimensionType, name, blockLength,
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

		public AnnotatedDataBuilder offset(int offset) {
			this.offset = offset;
			return this;
		}

		public AnnotatedDataBuilder semanticType(String semanticType) {
			this.semanticType = semanticType;
			return this;
		}

		public AnnotatedDataBuilder description(String description) {
			this.description = description;
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

		public AnnotatedRefBuilder offset(int offset) {
			this.offset = offset;
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

		public AnnotatedEnumBuilder values(AnnotatedEnumValueBuilder... builders) {
			for (AnnotatedEnumValueBuilder builder : builders) {
				values.add(builder.build());
			}
			return this;
		}

		/** The name code uses for the enum; every twin says it, as discovery does. */
		public AnnotatedEnumBuilder qualifiedName(String qualifiedName) {
			this.qualifiedName = qualifiedName;
			return this;
		}

		public AnnotatedEnumBuilder unknownValue(String unknownValue) {
			this.unknownValue = unknownValue;
			return this;
		}

		public AnnotatedEnumBuilder encodingType(AnnotatedDeclarationBuilder encodingType) {
			this.encodingType = encodingType.build();
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

		public AnnotatedEnumBuilder offset(int offset) {
			this.offset = offset;
			return this;
		}

		public AnnotatedEnumBuilder semanticType(String semanticType) {
			this.semanticType = semanticType;
			return this;
		}

		public AnnotatedEnumBuilder description(String description) {
			this.description = description;
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

	public static final class AnnotatedEnumValueBuilder {

		private final String javaName;
		private final String value;
		private String name = "";
		private String description = "";
		private int sinceVersion;
		private int deprecated;

		private AnnotatedEnumValueBuilder(String javaName, String value) {
			this.javaName = javaName;
			this.value = value;
		}

		public AnnotatedEnumValueBuilder name(String name) {
			this.name = name;
			return this;
		}

		public AnnotatedEnumValueBuilder description(String description) {
			this.description = description;
			return this;
		}

		public AnnotatedEnumValueBuilder sinceVersion(int sinceVersion) {
			this.sinceVersion = sinceVersion;
			return this;
		}

		public AnnotatedEnumValueBuilder deprecated(int deprecated) {
			this.deprecated = deprecated;
			return this;
		}

		Annotated.ValidValue build() {
			return new Annotated.ValidValue(javaName, value, name, description, sinceVersion, deprecated);
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

		public AnnotatedSetBuilder choices(AnnotatedChoiceBuilder... builders) {
			for (AnnotatedChoiceBuilder builder : builders) {
				choices.add(builder.build());
			}
			return this;
		}

		public AnnotatedSetBuilder encodingType(AnnotatedDeclarationBuilder encodingType) {
			this.encodingType = encodingType.build();
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

		public AnnotatedSetBuilder offset(int offset) {
			this.offset = offset;
			return this;
		}

		public AnnotatedSetBuilder semanticType(String semanticType) {
			this.semanticType = semanticType;
			return this;
		}

		public AnnotatedSetBuilder description(String description) {
			this.description = description;
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

	public static final class AnnotatedChoiceBuilder {

		private final String javaName;
		private final int value;
		private String name = "";
		private String description = "";
		private int sinceVersion;
		private int deprecated;

		private AnnotatedChoiceBuilder(String javaName, int value) {
			this.javaName = javaName;
			this.value = value;
		}

		public AnnotatedChoiceBuilder name(String name) {
			this.name = name;
			return this;
		}

		public AnnotatedChoiceBuilder description(String description) {
			this.description = description;
			return this;
		}

		public AnnotatedChoiceBuilder sinceVersion(int sinceVersion) {
			this.sinceVersion = sinceVersion;
			return this;
		}

		public AnnotatedChoiceBuilder deprecated(int deprecated) {
			this.deprecated = deprecated;
			return this;
		}

		Annotated.Choice build() {
			return new Annotated.Choice(javaName, value, name, description, sinceVersion, deprecated);
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
