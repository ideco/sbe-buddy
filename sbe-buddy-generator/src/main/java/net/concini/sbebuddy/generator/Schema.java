package net.concini.sbebuddy.generator;

import java.nio.ByteOrder;
import java.util.List;

import org.jspecify.annotations.Nullable;

import uk.co.real_logic.sbe.PrimitiveType;
import uk.co.real_logic.sbe.xml.Presence;

/**
 * The schema model: the {@code messageSchema} element, with one nested record
 * per {@code sbe.xsd} element named after it, one component per XSD attribute
 * with the attribute's name, and {@code value} for element text. An attribute
 * the XSD makes optional is nullable; nothing carries a default. Nested types
 * are used qualified, {@code Schema.Field}, never imported.
 */
public record Schema(
		String packageName, // the XSD's "package", a Java keyword
		int id,
		int version,
		List<Declaration> types,
		List<Message> messages,
		@Nullable String semanticVersion,
		@Nullable String description,
		@Nullable ByteOrder byteOrder,
		@Nullable String headerType
) {

	public Schema {
		types = List.copyOf(types);
		messages = List.copyOf(messages);
	}

	/** What the {@code types} element holds. */
	public sealed interface Declaration {
	}

	/** What a {@code composite} element holds. */
	public sealed interface Member {
	}

	/** The {@code type} element; {@code value} is its text, the constant. */
	public record Type(
			String name,
			PrimitiveType primitiveType,
			@Nullable String value,
			@Nullable Integer length,
			@Nullable String characterEncoding,
			@Nullable Presence presence,
			@Nullable String valueRef,
			@Nullable String nullValue,
			@Nullable String minValue,
			@Nullable String maxValue,
			@Nullable Integer offset,
			@Nullable String semanticType,
			@Nullable String description,
			@Nullable Integer sinceVersion,
			@Nullable Integer deprecated
	) implements Declaration, Member {
	}

	/** The {@code composite} element. */
	public record Composite(
			String name,
			List<Member> members,
			@Nullable Integer offset,
			@Nullable String semanticType,
			@Nullable String description,
			@Nullable Integer sinceVersion,
			@Nullable Integer deprecated
	) implements Declaration, Member {

		public Composite {
			members = List.copyOf(members);
		}
	}

	/** The {@code ref} element. */
	public record Ref(
			String name,
			String type,
			@Nullable Integer offset,
			@Nullable Integer sinceVersion,
			@Nullable Integer deprecated
	) implements Member {
	}

	/** The {@code enum} element. */
	public record Enum(
			String name,
			String encodingType,
			List<ValidValue> validValues,
			@Nullable Integer offset,
			@Nullable String semanticType,
			@Nullable String description,
			@Nullable Integer sinceVersion,
			@Nullable Integer deprecated
	) implements Declaration, Member {

		public Enum {
			validValues = List.copyOf(validValues);
		}
	}

	/** The {@code validValue} element; {@code value} is its text. */
	public record ValidValue(
			String name,
			String value,
			@Nullable String description,
			@Nullable Integer sinceVersion,
			@Nullable Integer deprecated
	) {
	}

	/** The {@code set} element. */
	public record Set(
			String name,
			String encodingType,
			List<Choice> choices,
			@Nullable Integer offset,
			@Nullable String semanticType,
			@Nullable String description,
			@Nullable Integer sinceVersion,
			@Nullable Integer deprecated
	) implements Declaration, Member {

		public Set {
			choices = List.copyOf(choices);
		}
	}

	/** The {@code choice} element; {@code value} is its text, the bit. */
	public record Choice(
			String name,
			int value,
			@Nullable String description,
			@Nullable Integer sinceVersion,
			@Nullable Integer deprecated
	) {
	}

	/** The {@code message} element. */
	public record Message(
			String name,
			int id,
			List<Field> fields,
			List<Group> groups,
			List<Data> data,
			@Nullable Integer blockLength,
			@Nullable String semanticType,
			@Nullable String description,
			@Nullable Integer sinceVersion,
			@Nullable Integer deprecated
	) {

		public Message {
			fields = List.copyOf(fields);
			groups = List.copyOf(groups);
			data = List.copyOf(data);
		}
	}

	/** The {@code field} element. */
	public record Field(
			String name,
			int id,
			String type,
			@Nullable Presence presence,
			@Nullable String valueRef,
			@Nullable Integer offset,
			@Nullable String epoch,
			@Nullable String timeUnit,
			@Nullable String semanticType,
			@Nullable String description,
			@Nullable Integer sinceVersion,
			@Nullable Integer deprecated
	) {
	}

	/** The {@code group} element. */
	public record Group(
			String name,
			int id,
			List<Field> fields,
			List<Group> groups,
			List<Data> data,
			@Nullable String dimensionType,
			@Nullable Integer blockLength,
			@Nullable String semanticType,
			@Nullable String description,
			@Nullable Integer sinceVersion,
			@Nullable Integer deprecated
	) {

		public Group {
			fields = List.copyOf(fields);
			groups = List.copyOf(groups);
			data = List.copyOf(data);
		}
	}

	/** The {@code data} element; the XSD gives it {@code field}'s attributes. */
	public record Data(
			String name,
			int id,
			String type,
			@Nullable Presence presence,
			@Nullable String valueRef,
			@Nullable Integer offset,
			@Nullable String epoch,
			@Nullable String timeUnit,
			@Nullable String semanticType,
			@Nullable String description,
			@Nullable Integer sinceVersion,
			@Nullable Integer deprecated
	) {
	}
}
