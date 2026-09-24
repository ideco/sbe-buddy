package net.concini.sbebuddy.generator;

import java.util.List;
import java.util.Locale;
import java.util.function.BiFunction;

import org.jspecify.annotations.Nullable;

import uk.co.real_logic.sbe.PrimitiveType;
import uk.co.real_logic.sbe.generation.java.JavaUtil;
import uk.co.real_logic.sbe.ir.Encoding;
import uk.co.real_logic.sbe.ir.Token;

/**
 * The rules between a component's Java type and what the wire hands it, its
 * face: the flyweight's accessor type for a primitive or a type with a length,
 * the enum for an enum, a {@code Set} of the enum for a set, the record for a
 * composite, a {@code List} of the entry record for a group. A component must
 * be its face or bind to it, whatever its kind, and one that can be absent must
 * hold null. The face is read from the type's token, as the codec's shape is,
 * so the two never disagree; the rules apply where the walk meets each token
 * with its annotation, a message's or group's fields, groups and var-data, a
 * composite's inline types and refs, each problem on the node. Each method says
 * whether the component fits, so the walk builds no shape over one that does
 * not.
 */
final class FaceRules {

	private final List<Problem> problems;

	FaceRules(List<Problem> problems) {
		this.problems = problems;
	}

	private enum Kind {
		PRIMITIVE, LENGTH, ENUM, SET, COMPOSITE
	}

	/**
	 * What the wire hands a component: {@code javaType} as code names it,
	 * {@code named} as a problem names the wire side, and {@code typeName}, the
	 * wire name an enum, a set or a composite is matched by.
	 */
	private record Face(Kind kind, String javaType, String named, String typeName) {
	}

	// ---- the five places a face is checked

	/**
	 * A field of a message or a group, told its type's token and whether the wire
	 * can leave it absent: optional, or added above the body's baseline.
	 */
	boolean field(Annotated.Field field, Token type, boolean canBeAbsent) {
		int errors = errors();
		Face face = face(type, declarationOf(field), null);
		check(field, field.javaType(), field.binding(), face, FaceRules::notTheFieldsFace);
		boxing(field, field.javaType(), canBeAbsent, "field");
		return errors() == errors;
	}

	/** A composite's inline type, as a field of that type would be. */
	boolean member(Annotated.Type member, Token type, boolean canBeAbsent) {
		Annotated.JavaType javaType = member.javaType();
		if (javaType == null) {
			throw new IllegalStateException(member.javaName() + " is a member without a component");
		}
		int errors = errors();
		Face face = face(type, null, type.name());
		check(member, javaType, member.binding(), face, FaceRules::notTheFace);
		boxing(member, javaType, canBeAbsent, "member");
		return errors() == errors;
	}

	/** A composite's ref, whose component is the face of what it refers to. */
	boolean ref(Annotated.Ref ref, Token type) {
		int errors = errors();
		Annotated.Declaration target = ref.value() != null ? ref.value() : declarationOf(ref.javaType());
		Face face = face(type, target, type.applicableTypeName());
		check(ref, ref.javaType(), ref.binding(), face, FaceRules::notTheFace);
		return errors() == errors;
	}

	/** A group, whose face is a {@code List} of its entry record. */
	boolean group(Annotated.Group group) {
		Annotated.Binding binding = group.binding();
		Annotated.JavaType face = binding == null ? group.javaType() : binding.wire();
		if (face instanceof Annotated.ListOfRecord) {
			return true;
		}
		problem(
				group, binding == null
						? "a group must be a List of a record"
						: "a group's binding must bind a List of a record"
		);
		return false;
	}

	/**
	 * A message's or group's var-data, whose component is the face of its
	 * encoding's {@code varData}: text for a {@code char}, the bytes otherwise.
	 */
	boolean data(Annotated.Data data, Token encoding, Token varData) {
		int errors = errors();
		String javaType = varData.encoding().primitiveType() == PrimitiveType.CHAR ? "String" : "byte[]";
		String named = encoding.applicableTypeName();
		check(
				data, data.javaType(), data.binding(), new Face(Kind.LENGTH, javaType, named, named),
				FaceRules::notTheFace
		);
		return errors() == errors;
	}

	// ---- the face

	/**
	 * The face a type's token hands: for an encoding, by its primitive and length,
	 * named {@code encodingName} where given and else by its type where it has a
	 * length and by its primitive otherwise, as a bare primitive is; for an enum, a
	 * set or a composite, the declaration the annotation names where it names one,
	 * else the wire type.
	 */
	private static Face face(Token type, Annotated.@Nullable Declaration declaration, @Nullable String encodingName) {
		String typeName = type.applicableTypeName();
		String declared = declaration == null ? typeName : declarationName(declaration);
		return switch (type.signal()) {
			case ENCODING -> {
				Encoding encoding = type.encoding();
				PrimitiveType primitive = encoding.primitiveType();
				// sbe-tool leaves a constant char's length at 1 however long its value; the
				// value's bytes say whether the flyweight reads it as a string.
				boolean text = primitive == PrimitiveType.CHAR && encoding.presence() == Encoding.Presence.CONSTANT
						&& encoding.constValue().byteArrayValue(PrimitiveType.CHAR).length > 1;
				if (type.arrayLength() > 1 || text) {
					// sbe-tool reads a char array as a string, gives the byte-sized primitives
					// bulk accessors over byte[], and every other array is the array of its
					// element's face.
					String face = switch (primitive) {
						case CHAR -> "String";
						case INT8, UINT8 -> "byte[]";
						default -> JavaUtil.javaTypeName(primitive) + "[]";
					};
					yield new Face(Kind.LENGTH, face, encodingName == null ? type.name() : encodingName, typeName);
				}
				yield new Face(
						Kind.PRIMITIVE, JavaUtil.javaTypeName(primitive),
						encodingName == null ? primitive.primitiveName() : encodingName, typeName
				);
			}
			case BEGIN_ENUM -> new Face(Kind.ENUM, declared, declared, typeName);
			case BEGIN_SET -> new Face(Kind.SET, "Set<" + declared + ">", declared, typeName);
			case BEGIN_COMPOSITE -> new Face(Kind.COMPOSITE, declared, declared, typeName);
			default -> throw new IllegalStateException("a face of " + type.signal());
		};
	}

	/**
	 * A binding stands for the component and must hand the flyweight the face; a
	 * component without one must be the face.
	 */
	private void check(
			Object node, Annotated.JavaType javaType, Annotated.@Nullable Binding binding, Face face,
			BiFunction<Annotated.JavaType, Face, String> mismatch
	) {
		if (binding != null) {
			binding(node, binding, face);
		} else if (!fits(javaType, face)) {
			problem(node, mismatch.apply(javaType, face));
		}
	}

	/**
	 * A primitive or a length by the Java type's name; an enum, a set or a
	 * composite by the wire name of the declaration the component is of.
	 */
	private static boolean fits(Annotated.JavaType javaType, Face face) {
		return switch (face.kind()) {
			case PRIMITIVE, LENGTH -> javaTypeName(javaType).equals(face.javaType());
			case ENUM -> javaType instanceof Annotated.Declared declared
					&& declared.declaration() instanceof Annotated.Enum enumeration
					&& wireName(enumeration).equals(face.typeName());
			case COMPOSITE -> javaType instanceof Annotated.Declared declared
					&& declared.declaration() instanceof Annotated.Composite composite
					&& wireName(composite).equals(face.typeName());
			case SET -> javaType instanceof Annotated.SetOf set && wireName(set.declaration()).equals(face.typeName());
		};
	}

	// ---- what a mismatch says

	private static String notTheFace(Annotated.JavaType javaType, Face face) {
		return javaTypeName(javaType) + " is not the face of " + face.named() + ", which is " + face.javaType();
	}

	/** A field of a declaration is told the declaration's face by its kind. */
	private static String notTheFieldsFace(Annotated.JavaType javaType, Face face) {
		return switch (face.kind()) {
			case PRIMITIVE, LENGTH -> notTheFace(javaType, face);
			case ENUM -> face.named() + " is an enum; use " + face.javaType();
			case SET -> face.named() + " is a set; use " + face.javaType();
			case COMPOSITE -> face.named() + " is a composite; use " + face.javaType();
		};
	}

	/**
	 * A binding stands for the component: it must hand the flyweight the face, a
	 * primitive face through its specialization so nothing is boxed on the way.
	 */
	private void binding(Object node, Annotated.Binding binding, Face face) {
		if (face.kind() == Kind.PRIMITIVE
				? boundName(binding.wire()).equals(face.javaType())
				: fits(binding.wire(), face)) {
			return;
		}
		String implement = face.kind() == Kind.PRIMITIVE
				? "TypeBinding.Of" + (face.javaType().equals("int") ? "Int" : box(face.javaType()))
				: "TypeBinding over " + face.javaType();
		problem(
				node,
				simpleName(binding.qualifiedName()) + " binds the wire as " + boundName(binding.wire())
						+ ", but the face of " + face.named() + " is " + face.javaType() + "; implement " + implement
		);
	}

	// ---- absence

	/**
	 * What can be absent decodes to null, so a primitive component must be its box;
	 * a box on what is never absent may be there for reasons of the user's own, so
	 * it is a warning.
	 */
	private void boxing(Object node, Annotated.JavaType javaType, boolean canBeAbsent, String what) {
		if (!(javaType instanceof Annotated.Primitive primitive)) {
			return;
		}
		String plain = primitive.kind().name().toLowerCase(Locale.ROOT);
		String box = box(plain);
		if (canBeAbsent && !primitive.boxed()) {
			problem(node, plain + " cannot hold null, but the " + what + " can be absent; use " + box);
		}
		if (!canBeAbsent && primitive.boxed()) {
			problems.add(
					new Problem(
							node, box + " is boxed although the " + what + " is never absent", Problem.Severity.WARNING
					)
			);
		}
	}

	// ---- names

	/** A Java type as code names it; a problem quotes it. */
	static String javaTypeName(Annotated.JavaType javaType) {
		return switch (javaType) {
			case Annotated.Primitive primitive -> primitive.kind().name().toLowerCase(Locale.ROOT);
			case Annotated.Text text -> "String";
			case Annotated.Bytes bytes -> "byte[]";
			case Annotated.Array array -> array.kind().name().toLowerCase(Locale.ROOT) + "[]";
			case Annotated.Declared declared -> declarationName(declared.declaration());
			case Annotated.SetOf set -> "Set";
			case Annotated.Unmapped none -> "unmapped";
			case Annotated.ListOfRecord list -> "List";
			case Annotated.Other other -> other.javaName();
		};
	}

	/**
	 * What a binding hands the flyweight, as code names it: a primitive for a
	 * specialization, its box for the generic interface, the reference type
	 * otherwise.
	 */
	private static String boundName(Annotated.JavaType javaType) {
		if (javaType instanceof Annotated.Primitive primitive) {
			String plain = primitive.kind().name().toLowerCase(Locale.ROOT);
			return primitive.boxed() ? box(plain) : plain;
		}
		return javaTypeName(javaType);
	}

	/** The box of a primitive face; anything else is its own. */
	private static String box(String primitive) {
		return switch (primitive) {
			case "byte" -> "Byte";
			case "short" -> "Short";
			case "int" -> "Integer";
			case "long" -> "Long";
			case "float" -> "Float";
			case "double" -> "Double";
			case "char" -> "Character";
			case "boolean" -> "Boolean";
			default -> primitive;
		};
	}

	private static String simpleName(String qualifiedName) {
		return qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1);
	}

	private static String declarationName(Annotated.Declaration declaration) {
		return switch (declaration) {
			case Annotated.Type type -> type.javaName();
			case Annotated.Composite composite -> composite.javaName();
			case Annotated.Enum enumeration -> enumeration.javaName();
			case Annotated.Set set -> set.javaName();
		};
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

	/**
	 * A field's declaration, named by {@code type} or as the component's own type;
	 * null for a primitive.
	 */
	private static Annotated.@Nullable Declaration declarationOf(Annotated.Field field) {
		return field.type() != null ? field.type() : declarationOf(field.javaType());
	}

	private static Annotated.@Nullable Declaration declarationOf(Annotated.JavaType javaType) {
		if (javaType instanceof Annotated.Declared declared) {
			return declared.declaration();
		}
		return javaType instanceof Annotated.SetOf set ? set.declaration() : null;
	}

	private int errors() {
		int errors = 0;
		for (Problem problem : problems) {
			if (problem.isError()) {
				errors++;
			}
		}
		return errors;
	}

	private void problem(Object node, String message) {
		problems.add(new Problem(node, message));
	}
}
