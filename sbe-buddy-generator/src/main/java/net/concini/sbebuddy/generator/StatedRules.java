package net.concini.sbebuddy.generator;

import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import uk.co.real_logic.sbe.PrimitiveValue;
import uk.co.real_logic.sbe.ir.Encoding;
import uk.co.real_logic.sbe.ir.Ir;
import uk.co.real_logic.sbe.ir.Signal;
import uk.co.real_logic.sbe.ir.Token;

import net.concini.sbebuddy.ByteOrder;
import net.concini.sbebuddy.Presence;
import net.concini.sbebuddy.PrimitiveType;

/**
 * What an annotation states that the schema states too, compared where the walk
 * meets the token: a member left at its default lets the schema decide, one
 * written must agree, and a disagreement is a problem on the node naming both.
 * Over a schema sbe-buddy wrote every comparison holds by construction; over
 * one read from a resource they keep the annotations honest. A declared type is
 * compared through its own tokens, since the copy under a field carries the
 * field's presence and the later of the two since-versions; for the same reason
 * a since-version written below the token's is no disagreement.
 */
final class StatedRules {

	private final List<Problem> problems;
	private final Ir ir;

	StatedRules(List<Problem> problems, Ir ir) {
		this.problems = problems;
		this.ir = ir;
	}

	/** The schema's own members, and the header it frames every message in. */
	void schema(Annotated annotated) {
		if (annotated.id() != ir.id()) {
			disagree(annotated, "id", annotated.id(), ir.id());
		}
		if (annotated.version() != ir.version()) {
			disagree(annotated, "version", annotated.version(), ir.version());
		}
		if (annotated.baselineVersion() < 0 || annotated.baselineVersion() > ir.version()) {
			problem(
					annotated, "a baselineVersion is 0 to the schema's version " + ir.version() + ", not "
							+ annotated.baselineVersion()
			);
		}
		text(annotated, "semanticVersion", annotated.semanticVersion(), ir.semanticVersion());
		text(annotated, "description", annotated.description(), ir.description());
		// Qualified because it collides with the api's ByteOrder, which the annotation
		// holds.
		if (annotated.byteOrder() == ByteOrder.BIG_ENDIAN && ir.byteOrder() != java.nio.ByteOrder.BIG_ENDIAN) {
			disagree(annotated, "byteOrder", "bigEndian", "littleEndian");
		}
		String header = ir.headerStructure().tokens().get(0).name();
		Annotated.Composite headerType = annotated.headerType();
		if (!wireName(headerType.name(), headerType.javaName()).equals(header)) {
			problem(annotated, "the schema's header is \"" + header + "\"; declare it as headerType");
		}
	}

	void message(Annotated.Message message, Token token) {
		integer(message, "blockLength", message.blockLength(), token.encodedLength());
		text(message, "semanticType", message.semanticType(), token.encoding().semanticType());
		text(message, "description", message.description(), token.description());
		versions(message, message.sinceVersion(), message.deprecated(), token);
	}

	/** A field against its own token and its type's, the latter declared or not. */
	void field(Annotated.Field field, Token token, Token type) {
		if (field.id() != token.id()) {
			disagree(field, "id", field.id(), token.id());
		}
		presence(field, field.presence(), token.encoding().presence());
		versions(field, field.sinceVersion(), field.deprecated(), token);
		integer(field, "offset", field.offset(), token.offset());
		semanticType(field, field.semanticType(), token, type);
		text(field, "description", field.description(), token.description());
		text(field, "epoch", field.epoch(), token.encoding().epoch());
		text(field, "timeUnit", field.timeUnit(), token.encoding().timeUnit());
		if (type.signal() == Signal.ENCODING) {
			primitive(field, field.primitiveType(), type.encoding().primitiveType());
		}
		Annotated.Declaration declaration = field.type();
		if (declaration != null) {
			declaration(field, declaration, type);
		}
	}

	/** A composite's inline type, whose token is the declaration's own. */
	void member(Annotated.Type member, Token token) {
		type(member, token);
		integer(member, "offset", member.offset(), token.offset());
	}

	void ref(Annotated.Ref ref, Token token) {
		integer(ref, "offset", ref.offset(), token.offset());
		versions(ref, ref.sinceVersion(), ref.deprecated(), token);
		Annotated.Declaration target = ref.value();
		if (target != null) {
			declaration(ref, target, token);
		}
	}

	void group(Annotated.Group group, Token token, Token dimension) {
		if (group.id() != token.id()) {
			disagree(group, "id", group.id(), token.id());
		}
		integer(group, "blockLength", group.blockLength(), token.encodedLength());
		Annotated.Composite dimensionType = group.dimensionType();
		String dimensionName = wireName(dimensionType.name(), dimensionType.javaName());
		if (!dimensionName.equals(dimension.applicableTypeName())) {
			disagree(group, "dimensionType", dimensionName, dimension.applicableTypeName());
		}
		// sbe-tool reads no semanticType off a group, so there is nothing to compare.
		text(group, "description", group.description(), token.description());
		versions(group, group.sinceVersion(), group.deprecated(), token);
	}

	void data(Annotated.Data data, Token token, Token encoding) {
		if (data.id() != token.id()) {
			disagree(data, "id", data.id(), token.id());
		}
		String typeName = wireName(data.type().name(), data.type().javaName());
		if (!typeName.equals(encoding.applicableTypeName())) {
			disagree(data, "type", typeName, encoding.applicableTypeName());
		}
		semanticType(data, data.semanticType(), token, encoding);
		text(data, "description", data.description(), token.description());
		versions(data, data.sinceVersion(), data.deprecated(), token);
	}

	void enumeration(Annotated.Enum enumeration, Token token) {
		Token own = own(token);
		primitive(enumeration, encodingPrimitive(enumeration.encodingType(), enumeration.primitiveType()), own);
		text(enumeration, "semanticType", enumeration.semanticType(), own.encoding().semanticType());
		text(enumeration, "description", enumeration.description(), own.description());
		versions(enumeration, enumeration.sinceVersion(), enumeration.deprecated(), own);
	}

	void validValue(Annotated.ValidValue value, Token token) {
		if (!value.value().isEmpty() && !sameValue(value.value(), token.encoding())) {
			disagree(value, "value", value.value(), token.encoding().constValue());
		}
		text(value, "description", value.description(), token.description());
		versions(value, value.sinceVersion(), value.deprecated(), token);
	}

	void set(Annotated.Set set, Token token) {
		Token own = own(token);
		primitive(set, encodingPrimitive(set.encodingType(), set.primitiveType()), own);
		text(set, "semanticType", set.semanticType(), own.encoding().semanticType());
		text(set, "description", set.description(), own.description());
		versions(set, set.sinceVersion(), set.deprecated(), own);
	}

	void choice(Annotated.Choice choice, Token token) {
		if (choice.value() != token.encoding().constValue().longValue()) {
			disagree(choice, "value", choice.value(), token.encoding().constValue().longValue());
		}
		text(choice, "description", choice.description(), token.description());
		versions(choice, choice.sinceVersion(), choice.deprecated(), token);
	}

	void composite(Annotated.Composite composite, Token token) {
		Token own = own(token);
		text(composite, "semanticType", composite.semanticType(), own.encoding().semanticType());
		text(composite, "description", composite.description(), own.description());
		versions(composite, composite.sinceVersion(), composite.deprecated(), own);
	}

	// ---- declarations

	/**
	 * A declaration named by a field or a ref: its wire name is the token's type,
	 * and a type's members are compared through the declaration's own token.
	 */
	private void declaration(Object node, Annotated.Declaration declaration, Token token) {
		String name = wireName(declaration);
		if (!name.equals(token.applicableTypeName())) {
			disagree(node, "type", name, token.applicableTypeName());
			return;
		}
		if (declaration instanceof Annotated.Type type) {
			type(type, own(token));
		}
	}

	private void type(Annotated.Type type, Token token) {
		if (token.signal() != Signal.ENCODING) {
			return; // the face rules say what it is instead
		}
		Encoding encoding = token.encoding();
		primitive(type, type.primitiveType(), encoding.primitiveType());
		// A constant's length is its value's, which sbe-tool keeps out of the token.
		if (type.length() > 1 && type.presence() != Presence.CONSTANT) {
			integer(type, "length", type.length(), token.arrayLength());
		}
		text(type, "characterEncoding", type.characterEncoding(), encoding.characterEncoding());
		presence(type, type.presence(), encoding.presence());
		if (!type.value().isEmpty() && encoding.presence() == Encoding.Presence.CONSTANT
				&& !sameValue(type.value(), encoding)) {
			disagree(type, "value", type.value(), encoding.constValue());
		}
		text(type, "semanticType", type.semanticType(), encoding.semanticType());
		text(type, "description", type.description(), token.description());
		versions(type, type.sinceVersion(), type.deprecated(), token);
	}

	/**
	 * The declaration's own token where the schema declares it by name, the token
	 * at hand for a type declared inline.
	 */
	private Token own(Token token) {
		List<Token> declared = ir.getType(token.applicableTypeName());
		return declared == null ? token : declared.get(0);
	}

	/** An enum's or set's encoding as stated: its primitive, or its type's. */
	private static PrimitiveType encodingPrimitive(
			Annotated.@Nullable Declaration encodingType, PrimitiveType primitiveType
	) {
		return encodingType instanceof Annotated.Type type ? type.primitiveType() : primitiveType;
	}

	private void primitive(Object node, PrimitiveType stated, Token token) {
		primitive(node, stated, token.encoding().primitiveType());
	}

	/**
	 * A field's or var-data's token carries its type's semanticType where the type
	 * has one, so the field's own is compared only where the type has none.
	 */
	private void semanticType(Object node, String stated, Token token, Token type) {
		if (own(type).encoding().semanticType() == null) {
			text(node, "semanticType", stated, token.encoding().semanticType());
		}
	}

	// ---- the comparisons

	private void integer(Object node, String member, int stated, int schema) {
		if (stated != 0 && stated != schema) {
			disagree(node, member, stated, schema);
		}
	}

	private void text(Object node, String member, String stated, @Nullable String schema) {
		if (!stated.isEmpty() && !stated.equals(schema)) {
			disagree(node, member, stated, schema);
		}
	}

	/**
	 * A token's version is the later of its node's and its type's, so only a
	 * since-version stated above it disagrees; deprecated is the node's own.
	 */
	private void versions(Object node, int sinceVersion, int deprecated, Token token) {
		if (sinceVersion > token.version()) {
			disagree(node, "sinceVersion", sinceVersion, token.version());
		}
		integer(node, "deprecated", deprecated, token.deprecated());
	}

	private void presence(Object node, Presence stated, Encoding.Presence schema) {
		if (stated != Presence.REQUIRED && !stated.name().equals(schema.name())) {
			disagree(
					node, "presence", stated.name().toLowerCase(Locale.ROOT), schema.name().toLowerCase(Locale.ROOT)
			);
		}
	}

	private void primitive(Object node, PrimitiveType stated, uk.co.real_logic.sbe.PrimitiveType schema) {
		if (stated != PrimitiveType.NONE && !stated.name().equals(schema.name())) {
			disagree(node, "primitiveType", stated.name().toLowerCase(Locale.ROOT), schema.primitiveName());
		}
	}

	/**
	 * A stated value against the token's constant, parsed as sbe-tool parsed the
	 * document's text; a char string, which one char's parse refuses, is compared
	 * as the text it is held as.
	 */
	private static boolean sameValue(String stated, Encoding encoding) {
		PrimitiveValue schema = encoding.constValue();
		try {
			return PrimitiveValue.parse(stated, encoding.primitiveType()).equals(schema);
		} catch (IllegalArgumentException e) {
			return stated.equals(schema.toString());
		}
	}

	private void disagree(Object node, String member, Object stated, @Nullable Object schema) {
		problem(node, "the schema has " + member + "=\"" + schema + "\", not \"" + stated + "\"");
	}

	private void problem(Object node, String message) {
		problems.add(new Problem(node, message));
	}

	private static String wireName(Annotated.Declaration declaration) {
		return switch (declaration) {
			case Annotated.Type type -> wireName(type.name(), type.javaName());
			case Annotated.Composite composite -> wireName(composite.name(), composite.javaName());
			case Annotated.Enum enumeration -> wireName(enumeration.name(), enumeration.javaName());
			case Annotated.Set set -> wireName(set.name(), set.javaName());
		};
	}

	private static String wireName(String name, String javaName) {
		return name.isEmpty() ? javaName : name;
	}
}
