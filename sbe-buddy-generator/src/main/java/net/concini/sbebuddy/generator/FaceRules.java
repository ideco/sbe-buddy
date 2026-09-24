package net.concini.sbebuddy.generator;

import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import uk.co.real_logic.sbe.generation.java.JavaUtil;

import net.concini.sbebuddy.Presence;
import net.concini.sbebuddy.PrimitiveType;

/**
 * The rules between a component's Java type and what the wire hands it, its
 * face: the flyweight's accessor type for a primitive or a type with a length,
 * the enum for an enum, a {@code Set} of the enum for a set, the record for a
 * composite. A component must be its face or bind to it, whatever its kind; one
 * that can be absent must hold null, and a face with no null value cannot be
 * optional; a constant needs a value. Applied to a message's or group's fields
 * and var-data, a composite's inline types and its refs, each problem on the
 * node.
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
	 * {@code named} as a problem names the wire side, and the declaration an enum,
	 * a set or a composite is matched against by identity.
	 */
	private record Face(Kind kind, String javaType, String named, Annotated.@Nullable Declaration declaration) {
	}

	// ---- the four places a face is checked

	/**
	 * A field of a message or a group. {@code baseline} is the version below which
	 * nothing in its body is read.
	 */
	void field(Annotated.Field field, int baseline) {
		Annotated.Declaration declaration = field.type() != null ? field.type() : declarationOf(field.javaType());
		boolean constantType = declaration instanceof Annotated.Type type && type.presence() == Presence.CONSTANT;
		if (field.presence() == Presence.CONSTANT && field.valueRef().isEmpty() && !constantType) {
			// sbe-tool's IR generator crashes on one with neither, past every parser rule.
			problem(field, "a constant field needs a valueRef or a constant type");
		}
		if (field.javaType() instanceof Annotated.Unmapped) {
			return;
		}
		Face face = face(field);
		if (face != null) {
			Annotated.Binding binding = field.binding();
			if (binding != null) {
				binding(field, binding, face);
			} else if (!fits(field.javaType(), face)) {
				problem(field, notTheFieldsFace(field.javaType(), face));
			}
			if (wirePresence(field) == Presence.OPTIONAL) {
				optional(field, face);
			}
		}
		boxing(field, field.javaType(), canBeAbsent(field, baseline), "field");
	}

	/** A composite's inline type, as a field of that type would be. */
	void member(Annotated.Type member) {
		Annotated.JavaType javaType = member.javaType();
		if (javaType == null) {
			return;
		}
		if (javaType instanceof Annotated.Unmapped) {
			if (member.name().isEmpty()) {
				problem(member, "an unmapped member needs a name");
			}
			return;
		}
		if (member.presence() == Presence.CONSTANT && member.value().isEmpty() && member.valueRef().isEmpty()) {
			problem(member, "a constant member needs a value or a valueRef");
		}
		if (member.primitiveType() == PrimitiveType.NONE) {
			return;
		}
		Face face = typeFace(member, wireName(member.name(), member.javaName()));
		Annotated.Binding binding = member.binding();
		if (binding != null) {
			binding(member, binding, face);
		} else if (!fits(javaType, face)) {
			problem(member, notTheFace(javaType, face));
		}
		boolean optional = member.presence() == Presence.OPTIONAL;
		if (optional && length(member) > 1) {
			problem(member, face.named() + " has a length; it cannot be optional");
		}
		boxing(member, javaType, optional, "member");
	}

	/** A composite's ref, whose component is the face of what it refers to. */
	void ref(Annotated.Ref ref) {
		Annotated.Declaration target = ref.value();
		if (target == null && ref.javaType() instanceof Annotated.Declared declared) {
			target = declared.declaration();
		}
		if (target == null || target instanceof Annotated.Type type && type.primitiveType() == PrimitiveType.NONE) {
			return;
		}
		Face face = declarationFace(target, declarationName(target));
		Annotated.Binding binding = ref.binding();
		if (binding != null) {
			binding(ref, binding, face);
		} else if (!fits(ref.javaType(), face)) {
			problem(ref, notTheFace(ref.javaType(), face));
		}
	}

	/**
	 * A message's or group's var-data, whose component is the face of its
	 * encoding's {@code varData}: text for a {@code char}, the bytes otherwise.
	 */
	void data(Annotated.Data data) {
		Annotated.Type varData = varData(data.type());
		if (varData == null || varData.primitiveType() == PrimitiveType.NONE) {
			return; // sbe-tool reports an encoding without one
		}
		String javaType = varData.primitiveType() == PrimitiveType.CHAR ? "String" : "byte[]";
		Face face = new Face(Kind.LENGTH, javaType, wireName(data.type().name(), data.type().javaName()), null);
		Annotated.Binding binding = data.binding();
		if (binding != null) {
			binding(data, binding, face);
		} else if (!fits(data.javaType(), face)) {
			problem(data, notTheFace(data.javaType(), face));
		}
	}

	// ---- the face

	/**
	 * A field's face, from what it is written as: its {@code primitiveType}, else
	 * its declaration, given as {@code type} or as the component's own type; null
	 * where the default mapping makes the component its own face.
	 */
	private static @Nullable Face face(Annotated.Field field) {
		if (field.primitiveType() != PrimitiveType.NONE) {
			return primitiveFace(wire(field.primitiveType()));
		}
		Annotated.Declaration declaration = field.type() != null ? field.type() : declarationOf(field.javaType());
		if (declaration == null) {
			return null;
		}
		if (declaration instanceof Annotated.Type type) {
			if (type.primitiveType() == PrimitiveType.NONE) {
				return null;
			}
			// A type of length 1 is named by its primitive, as a bare primitive is.
			return switch (Integer.signum(length(type) - 1)) {
				case 1 -> typeFace(type, wireName(type.name(), type.javaName()));
				case 0 -> primitiveFace(wire(type.primitiveType()));
				default -> null;
			};
		}
		return declarationFace(declaration, declarationName(declaration));
	}

	private static Face declarationFace(Annotated.Declaration declaration, String named) {
		return switch (declaration) {
			case Annotated.Type type -> typeFace(type, named);
			case Annotated.Enum enumeration -> new Face(Kind.ENUM, named, named, enumeration);
			case Annotated.Set set -> new Face(Kind.SET, "Set<" + named + ">", named, set);
			case Annotated.Composite composite -> new Face(Kind.COMPOSITE, named, named, composite);
		};
	}

	/**
	 * A type's face by its length: sbe-tool reads a {@code char} array as a string,
	 * gives the byte-sized primitives bulk accessors over {@code byte[]}, and every
	 * other array is the array of its element's face; {@code length = 0}, the
	 * variable part of a var-data encoding, is a {@code String} for {@code char}
	 * and {@code byte[]} otherwise.
	 */
	private static Face typeFace(Annotated.Type type, String named) {
		uk.co.real_logic.sbe.PrimitiveType primitive = wire(type.primitiveType());
		if (type.length() == 0) {
			String face = primitive == uk.co.real_logic.sbe.PrimitiveType.CHAR ? "String" : "byte[]";
			return new Face(Kind.LENGTH, face, named, null);
		}
		if (length(type) > 1) {
			String face = switch (primitive) {
				case CHAR -> "String";
				case INT8, UINT8 -> "byte[]";
				default -> JavaUtil.javaTypeName(primitive) + "[]";
			};
			return new Face(Kind.LENGTH, face, named, null);
		}
		return new Face(Kind.PRIMITIVE, JavaUtil.javaTypeName(primitive), named, null);
	}

	/** The flyweight's face for a primitive, {@code int} for {@code uint16}. */
	private static Face primitiveFace(uk.co.real_logic.sbe.PrimitiveType primitive) {
		return new Face(Kind.PRIMITIVE, JavaUtil.javaTypeName(primitive), primitive.primitiveName(), null);
	}

	private static boolean fits(Annotated.JavaType javaType, Face face) {
		return switch (face.kind()) {
			case PRIMITIVE, LENGTH -> javaTypeName(javaType).equals(face.javaType());
			case ENUM, COMPOSITE -> javaType instanceof Annotated.Declared declared
					&& declared.declaration() == face.declaration();
			case SET -> javaType instanceof Annotated.SetOf set && set.declaration() == face.declaration();
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

	/** An optional field of a face that has no null value. */
	private void optional(Annotated.Field field, Face face) {
		switch (face.kind()) {
			case LENGTH -> problem(field, face.named() + " has a length; a field of it cannot be optional");
			case SET -> problem(field, "a set has no null value; a set field cannot be optional");
			case COMPOSITE -> problem(field, face.named() + " is a composite; a field of it cannot be optional");
			case PRIMITIVE, ENUM -> {
			}
		}
	}

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

	/**
	 * Optional, or added above the body's baseline; a constant carries no bytes and
	 * is never absent.
	 */
	private static boolean canBeAbsent(Annotated.Field field, int baseline) {
		Presence presence = wirePresence(field);
		return presence == Presence.OPTIONAL || field.sinceVersion() > baseline && presence != Presence.CONSTANT;
	}

	/**
	 * A field left at the default takes its named type's presence, as sbe-tool
	 * reads the document.
	 */
	private static Presence wirePresence(Annotated.Field field) {
		if (field.presence() == Presence.REQUIRED && field.type() instanceof Annotated.Type named) {
			return named.presence();
		}
		return field.presence();
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

	private static Annotated.@Nullable Type varData(Annotated.Composite encoding) {
		for (Annotated.Member member : encoding.members()) {
			if (member instanceof Annotated.Type type && wireName(type.name(), type.javaName()).equals("varData")) {
				return type;
			}
		}
		return null;
	}

	private static Annotated.@Nullable Declaration declarationOf(Annotated.JavaType javaType) {
		if (javaType instanceof Annotated.Declared declared) {
			return declared.declaration();
		}
		return javaType instanceof Annotated.SetOf set ? set.declaration() : null;
	}

	/**
	 * A type's length as sbe-tool reads it: its {@code length}, or for a constant
	 * {@code char} value longer than one character without one, the value's.
	 */
	private static int length(Annotated.Type type) {
		if (type.length() == 1 && type.presence() == Presence.CONSTANT && type.primitiveType() == PrimitiveType.CHAR) {
			return Math.max(1, type.value().length());
		}
		return type.length();
	}

	private static String wireName(String name, String javaName) {
		return name.isEmpty() ? javaName : name;
	}

	// sbe-tool's PrimitiveType is qualified where it meets the api's, which the
	// annotations hold.
	private static uk.co.real_logic.sbe.PrimitiveType wire(PrimitiveType primitiveType) {
		return uk.co.real_logic.sbe.PrimitiveType.valueOf(primitiveType.name());
	}

	private void problem(Object node, String message) {
		problems.add(new Problem(node, message));
	}
}
