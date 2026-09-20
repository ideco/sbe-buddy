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

	public static MessageBuilder message(String name, int id) {
		return new MessageBuilder(name, id);
	}

	public static FieldBuilder field(String name, int id, String type) {
		return new FieldBuilder(name, id, type);
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
}
