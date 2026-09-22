package net.concini.sbebuddy.generator;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.concini.sbebuddy.ByteOrder;
import net.concini.sbebuddy.Presence;
import net.concini.sbebuddy.PrimitiveType;

/**
 * The api's annotations as data: the {@code @SbeSchema} package as the root
 * record, a nested record per annotation named after its element, one component
 * per member holding the member's value, a default as javac hands it over; plus
 * what an annotation cannot carry: the Java name of the annotated thing, its
 * Java type, and references to declarations by identity rather than by
 * {@code Class}. Nested types are used qualified, {@code Annotated.Field},
 * never imported.
 */
public record Annotated(
		String packageName,
		int id,
		int version,
		Composite headerType,
		List<Declaration> types,
		List<Message> messages,
		String semanticVersion,
		String description,
		ByteOrder byteOrder,
		boolean codecs,
		int baselineVersion
) {

	public Annotated {
		types = List.copyOf(types);
		messages = List.copyOf(messages);
	}

	/**
	 * A class carrying {@code @SbeType}, {@code @SbeComposite}, {@code @SbeEnum} or
	 * {@code @SbeSet}.
	 */
	public sealed interface Declaration {
	}

	/** A composite's component. */
	public sealed interface Member {
	}

	/** A message's or a group's component, in declaration order. */
	public sealed interface Component {
	}

	/** A component's Java type, as far as the mapping needs to know it. */
	public sealed interface JavaType {
	}

	public enum JavaPrimitive {
		BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, CHAR, BOOLEAN
	}

	/** A Java primitive, or its box. */
	public record Primitive(JavaPrimitive kind, boolean boxed) implements JavaType {
	}

	/** {@code String}. */
	public record Text() implements JavaType {
	}

	/** {@code byte[]}. */
	public record Bytes() implements JavaType {
	}

	/** An array of any other Java primitive, {@code int[]}, {@code long[]}. */
	public record Array(JavaPrimitive kind) implements JavaType {
	}

	/** A declared type. */
	public record Declared(Declaration declaration) implements JavaType {
	}

	/** A {@code List} of a record, which is what a group is written on. */
	public record ListOfRecord() implements JavaType {
	}

	/** A {@code Set} of an {@code @SbeSet} enum, which is a set's face. */
	public record SetOf(Declaration declaration) implements JavaType {
	}

	/**
	 * No type at all: the field of an {@code unmapped} entry, which no component
	 * carries.
	 */
	public record Unmapped() implements JavaType {
	}

	/** Anything else, named for the message that rejects it. */
	public record Other(String javaName) implements JavaType {
	}

	/**
	 * {@code @SbeType}; {@code javaType} is the component's when the type is a
	 * composite's member, and null for a declaration on a class.
	 */
	public record Type(
			String javaName,
			@Nullable JavaType javaType,
			PrimitiveType primitiveType,
			String name,
			String value,
			int length,
			String characterEncoding,
			Presence presence,
			String valueRef,
			String nullValue,
			String minValue,
			String maxValue,
			int offset,
			String semanticType,
			String description,
			int sinceVersion,
			int deprecated
	) implements Declaration, Member {
	}

	/**
	 * {@code @SbeComposite}; {@code qualifiedName} is the record's name as code
	 * names it, {@code unmapped} and {@code layout} as on a message.
	 */
	public record Composite(
			String javaName,
			String qualifiedName,
			List<Member> members,
			List<Type> unmapped,
			List<String> layout,
			String name,
			int offset,
			String semanticType,
			String description,
			int sinceVersion,
			int deprecated
	) implements Declaration, Member {

		public Composite {
			members = List.copyOf(members);
			unmapped = List.copyOf(unmapped);
			layout = List.copyOf(layout);
		}
	}

	/**
	 * {@code @SbeRef}; {@code value} is null when the component's own type is the
	 * declaration.
	 */
	public record Ref(
			String javaName,
			JavaType javaType,
			@Nullable Declaration value,
			String name,
			int offset,
			int sinceVersion,
			int deprecated
	) implements Member {
	}

	/**
	 * {@code @SbeEnum}; {@code qualifiedName} is the enum's name as code names it;
	 * {@code unknownValue} is the Java name of the constant carrying
	 * {@code @UnknownValue}, or null; {@code encodingType} is null when unset.
	 */
	public record Enum(
			String javaName,
			String qualifiedName,
			List<ValidValue> values,
			@Nullable String unknownValue,
			@Nullable Declaration encodingType,
			PrimitiveType primitiveType,
			String name,
			int offset,
			String semanticType,
			String description,
			int sinceVersion,
			int deprecated
	) implements Declaration, Member {

		public Enum {
			values = List.copyOf(values);
		}
	}

	/** {@code @SbeEnumValue}. */
	public record ValidValue(
			String javaName,
			String value,
			String name,
			String description,
			int sinceVersion,
			int deprecated
	) {
	}

	/**
	 * {@code @SbeSet}; {@code qualifiedName} is the enum's name as code names it;
	 * {@code encodingType} is null when unset.
	 */
	public record Set(
			String javaName,
			String qualifiedName,
			List<Choice> choices,
			@Nullable Declaration encodingType,
			PrimitiveType primitiveType,
			String name,
			int offset,
			String semanticType,
			String description,
			int sinceVersion,
			int deprecated
	) implements Declaration, Member {

		public Set {
			choices = List.copyOf(choices);
		}
	}

	/** {@code @SbeChoice}. */
	public record Choice(
			String javaName,
			int value,
			String name,
			String description,
			int sinceVersion,
			int deprecated
	) {
	}

	/**
	 * {@code @SbeMessage}; {@code unmapped} are the fields no component carries,
	 * each with {@link Unmapped} for its type and its {@code name} for its Java
	 * name, and {@code layout} the body's order by name, empty for declaration
	 * order.
	 */
	public record Message(
			String javaName,
			int id,
			List<Component> components,
			List<Field> unmapped,
			List<String> layout,
			String name,
			int blockLength,
			String semanticType,
			String description,
			int sinceVersion,
			int deprecated
	) {

		public Message {
			components = List.copyOf(components);
			unmapped = List.copyOf(unmapped);
			layout = List.copyOf(layout);
		}
	}

	/** {@code @SbeField}; {@code type} is null when unset. */
	public record Field(
			String javaName,
			JavaType javaType,
			int id,
			@Nullable Declaration type,
			PrimitiveType primitiveType,
			String name,
			Presence presence,
			String valueRef,
			int offset,
			String epoch,
			String timeUnit,
			String semanticType,
			String description,
			int sinceVersion,
			int deprecated,
			@Nullable Binding binding
	) implements Component {
	}

	/**
	 * A field's {@code binding}: the class code names, and its {@code W}, the Java
	 * type it hands the flyweight, which the face rule compares. Its {@code J} is
	 * the component's type, which only javac can check.
	 */
	public record Binding(String qualifiedName, JavaType wire) {
	}

	/**
	 * {@code @SbeGroup}, with the components of the list's record; {@code unmapped}
	 * and {@code layout} as on a message.
	 */
	public record Group(
			String javaName,
			JavaType javaType,
			int id,
			List<Component> components,
			List<Field> unmapped,
			List<String> layout,
			Composite dimensionType,
			String name,
			int blockLength,
			String semanticType,
			String description,
			int sinceVersion,
			int deprecated
	) implements Component {

		public Group {
			components = List.copyOf(components);
			unmapped = List.copyOf(unmapped);
			layout = List.copyOf(layout);
		}
	}

	/** {@code @SbeData}. */
	public record Data(
			String javaName,
			JavaType javaType,
			int id,
			Composite type,
			String name,
			int offset,
			String semanticType,
			String description,
			int sinceVersion,
			int deprecated
	) implements Component {
	}
}
