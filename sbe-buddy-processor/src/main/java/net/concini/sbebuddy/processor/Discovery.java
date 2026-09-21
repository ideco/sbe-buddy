package net.concini.sbebuddy.processor;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;

import org.jspecify.annotations.Nullable;

import net.concini.sbebuddy.ByteOrder;
import net.concini.sbebuddy.Presence;
import net.concini.sbebuddy.PrimitiveType;
import net.concini.sbebuddy.SbeChoice;
import net.concini.sbebuddy.SbeComposite;
import net.concini.sbebuddy.SbeData;
import net.concini.sbebuddy.SbeEnum;
import net.concini.sbebuddy.SbeEnumValue;
import net.concini.sbebuddy.SbeField;
import net.concini.sbebuddy.SbeGroup;
import net.concini.sbebuddy.SbeMessage;
import net.concini.sbebuddy.SbeRef;
import net.concini.sbebuddy.SbeSchema;
import net.concini.sbebuddy.SbeSet;
import net.concini.sbebuddy.SbeType;
import net.concini.sbebuddy.UnknownValue;
import net.concini.sbebuddy.generator.Annotated;
import net.concini.sbebuddy.generator.Problem;

/**
 * javac elements to {@link Annotated}: the {@code @SbeSchema} package, the
 * declarations it makes by name, its messages by id, and every declaration a
 * member reaches, one instance however often. Every member is read from the
 * {@link AnnotationMirror} with its defaults filled, so a {@code Class} member
 * is a {@link TypeMirror} and never a class. Rules only javac can see fire here
 * and name the {@link Element}; a package discovered with problems is not for
 * mapping.
 */
public final class Discovery {

	/**
	 * The annotated schema, the problems, and for every annotated node the element
	 * it came from and the annotation on it, by identity.
	 */
	public record Discovered(
			Annotated annotated,
			List<Problem> problems,
			Map<Object, Element> elements,
			Map<Object, AnnotationMirror> mirrors
	) {
	}

	private static final String API_PACKAGE = "net.concini.sbebuddy";

	private final Elements elements;
	private final List<Problem> problems = new ArrayList<>();
	private final Map<Object, Element> origins = new IdentityHashMap<>();
	private final Map<Object, AnnotationMirror> mirrors = new IdentityHashMap<>();
	private final Map<TypeElement, Annotated.Declaration> declarations = new HashMap<>();

	private Discovery(Elements elements) {
		this.elements = elements;
	}

	/** The package must carry {@code @SbeSchema}; the processor checks first. */
	public static Discovered discover(PackageElement schemaPackage, Elements elements) {
		Discovery discovery = new Discovery(elements);
		Annotated annotated = discovery.schema(schemaPackage);
		return new Discovered(
				annotated, List.copyOf(discovery.problems), Collections.unmodifiableMap(discovery.origins),
				Collections.unmodifiableMap(discovery.mirrors)
		);
	}

	/** The annotation of that type on the element, or null. */
	public static @Nullable AnnotationMirror annotation(Element element, Class<? extends Annotation> annotation) {
		for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
			TypeElement type = (TypeElement) mirror.getAnnotationType().asElement();
			if (type.getQualifiedName().contentEquals(annotation.getName())) {
				return mirror;
			}
		}
		return null;
	}

	// ---- the package
	// ----------------------------------------------------------------------------------

	private Annotated schema(PackageElement schemaPackage) {
		Members schema = members(schemaPackage, SbeSchema.class);
		List<TypeElement> declarationTypes = new ArrayList<>();
		List<TypeElement> messageTypes = new ArrayList<>();
		for (TypeElement type : ElementFilter.typesIn(schemaPackage.getEnclosedElements())) {
			walk(type, declarationTypes, messageTypes);
		}
		// javac enters a package's types in an order an incremental build does not
		// keep, so the schema takes its own: messages by id, declarations by name.
		declarationTypes.sort(Comparator.comparing(Discovery::qualifiedName));
		messageTypes.sort(
				Comparator.comparingInt((TypeElement type) -> members(type, SbeMessage.class).integer("id"))
						.thenComparing(Discovery::qualifiedName)
		);
		List<Annotated.Declaration> declared = new ArrayList<>();
		for (TypeElement type : declarationTypes) {
			Annotated.Declaration declaration = declaration(type);
			if (declaration != null) {
				declared.add(declaration);
			}
		}
		List<Annotated.Message> messages = new ArrayList<>();
		for (TypeElement type : messageTypes) {
			messages.add(message(type));
		}
		Annotated annotated = new Annotated(
				schemaPackage.getQualifiedName().toString(),
				schema.integer("id"),
				schema.integer("version"),
				composite(schemaPackage, schema.type("headerType"), "headerType", "MessageHeader"),
				declared,
				messages,
				schema.string("semanticVersion"),
				schema.string("description"),
				schema.enumeration("byteOrder", ByteOrder.class),
				schema.flag("codecs"),
				schema.integer("baselineVersion")
		);
		remember(annotated, schemaPackage, schema.mirror);
		return annotated;
	}

	/**
	 * A message or a declaration, then the types nested in it, except inside a
	 * composite, whose nested types are its inline members.
	 */
	private static void walk(TypeElement type, List<TypeElement> declarations, List<TypeElement> messages) {
		if (has(type, SbeMessage.class)) {
			messages.add(type);
		} else if (isDeclaration(type)) {
			declarations.add(type);
		}
		if (has(type, SbeComposite.class)) {
			return;
		}
		for (TypeElement nested : ElementFilter.typesIn(type.getEnclosedElements())) {
			walk(nested, declarations, messages);
		}
	}

	private static String qualifiedName(TypeElement type) {
		return type.getQualifiedName().toString();
	}

	// ---- declarations
	// ----------------------------------------------------------------------------------

	private static boolean isDeclaration(TypeElement type) {
		return has(type, SbeType.class) || has(type, SbeComposite.class) || has(type, SbeEnum.class)
				|| has(type, SbeSet.class);
	}

	/** The one instance of a declaration, wherever it is reached from. */
	private Annotated.@Nullable Declaration declaration(TypeElement type) {
		Annotated.Declaration known = declarations.get(type);
		if (known != null) {
			return known;
		}
		String javaName = type.getSimpleName().toString();
		Annotated.Declaration declaration;
		if (has(type, SbeType.class)) {
			declaration = type(type, javaName);
		} else if (has(type, SbeComposite.class)) {
			declaration = composite(type, javaName, type);
		} else if (has(type, SbeEnum.class)) {
			declaration = enumeration(type, javaName, type);
		} else if (has(type, SbeSet.class)) {
			declaration = set(type, javaName, type);
		} else {
			return null;
		}
		declarations.put(type, declaration);
		return declaration;
	}

	/** A declaration a member names by class; the problem lands on the member. */
	private Annotated.@Nullable Declaration reference(Element at, TypeElement type) {
		Annotated.Declaration declaration = declaration(type);
		if (declaration == null) {
			problem(at, type.getQualifiedName() + " carries no @SbeType, @SbeComposite, @SbeEnum or @SbeSet");
		}
		return declaration;
	}

	/**
	 * A member that must name a composite: the schema's header, a group's
	 * dimension, a data's type. Anything else is a problem, and the api's built-in
	 * stands in so discovery can go on.
	 */
	private Annotated.Composite composite(Element at, @Nullable TypeElement type, String member, String builtIn) {
		Annotated.Declaration declaration = type == null ? null : declaration(type);
		if (declaration instanceof Annotated.Composite composite) {
			return composite;
		}
		problem(at, member + " must name an @SbeComposite");
		TypeElement standIn = elements.getTypeElement(API_PACKAGE + "." + builtIn);
		if (standIn == null || !(declaration(standIn) instanceof Annotated.Composite composite)) {
			throw new IllegalStateException("the api's " + builtIn + " is not on the classpath");
		}
		return composite;
	}

	private Annotated.Type type(Element element, String javaName) {
		Members type = members(element, SbeType.class);
		Annotated.Type result = new Annotated.Type(
				javaName,
				type.enumeration("primitiveType", PrimitiveType.class),
				type.string("name"),
				type.string("value"),
				type.integer("length"),
				type.string("characterEncoding"),
				type.enumeration("presence", Presence.class),
				type.string("valueRef"),
				type.string("nullValue"),
				type.string("minValue"),
				type.string("maxValue"),
				type.integer("offset"),
				type.string("semanticType"),
				type.string("description"),
				type.integer("sinceVersion"),
				type.integer("deprecated")
		);
		remember(result, element, type.mirror);
		return result;
	}

	private Annotated.Composite composite(TypeElement type, String javaName, Element at) {
		Members composite = members(type, SbeComposite.class);
		if (type.getKind() != ElementKind.RECORD) {
			problem(at, "@SbeComposite goes on a record");
		}
		List<Annotated.Member> members = new ArrayList<>();
		for (RecordComponentElement component : ElementFilter.recordComponentsIn(type.getEnclosedElements())) {
			Annotated.Member member = member(type, component);
			if (member != null) {
				members.add(member);
			}
		}
		Annotated.Composite result = new Annotated.Composite(
				javaName,
				members,
				composite.string("name"),
				composite.integer("offset"),
				composite.string("semanticType"),
				composite.string("description"),
				composite.integer("sinceVersion"),
				composite.integer("deprecated")
		);
		remember(result, at, composite.mirror);
		return result;
	}

	/**
	 * A composite's component: an inline type, a ref, or an inline enum, set or
	 * composite when its type is nested in this composite.
	 */
	private Annotated.@Nullable Member member(TypeElement composite, RecordComponentElement component) {
		String javaName = component.getSimpleName().toString();
		if (has(component, SbeType.class)) {
			return type(component, javaName);
		}
		if (has(component, SbeRef.class)) {
			return ref(component, javaName);
		}
		TypeElement nested = declaredTypeOf(component.asType());
		if (nested != null && composite.equals(nested.getEnclosingElement())) {
			if (has(nested, SbeEnum.class)) {
				return enumeration(nested, javaName, component);
			}
			if (has(nested, SbeSet.class)) {
				return set(nested, javaName, component);
			}
			if (has(nested, SbeComposite.class)) {
				return composite(nested, javaName, component);
			}
		}
		problem(
				component,
				"a composite's component carries @SbeType or @SbeRef, or its type is an @SbeEnum, @SbeSet or @SbeComposite nested in the composite"
		);
		return null;
	}

	private Annotated.Ref ref(RecordComponentElement component, String javaName) {
		Members ref = members(component, SbeRef.class);
		TypeElement valueType = ref.type("value");
		Annotated.Ref result = new Annotated.Ref(
				javaName,
				javaType(component.asType()),
				valueType == null ? null : reference(component, valueType),
				ref.string("name"),
				ref.integer("offset"),
				ref.integer("sinceVersion"),
				ref.integer("deprecated")
		);
		remember(result, component, ref.mirror);
		return result;
	}

	private Annotated.Enum enumeration(TypeElement type, String javaName, Element at) {
		Members enumeration = members(type, SbeEnum.class);
		if (type.getKind() != ElementKind.ENUM) {
			problem(at, "@SbeEnum goes on a Java enum");
		}
		List<Annotated.ValidValue> values = new ArrayList<>();
		String unknownValue = null;
		for (VariableElement constant : constantsOf(type)) {
			if (has(constant, UnknownValue.class)) {
				if (has(constant, SbeEnumValue.class)) {
					problem(constant, "a constant carries @SbeEnumValue or @UnknownValue, not both");
				} else if (unknownValue != null) {
					problem(constant, "an enum designates one unknown value, and " + unknownValue + " already is");
				} else {
					unknownValue = constant.getSimpleName().toString();
				}
				continue;
			}
			if (!has(constant, SbeEnumValue.class)) {
				problem(constant, "a constant of an @SbeEnum carries @SbeEnumValue");
				continue;
			}
			Members value = members(constant, SbeEnumValue.class);
			Annotated.ValidValue validValue = new Annotated.ValidValue(
					constant.getSimpleName().toString(),
					value.string("value"),
					value.string("name"),
					value.string("description"),
					value.integer("sinceVersion"),
					value.integer("deprecated")
			);
			remember(validValue, constant, value.mirror);
			values.add(validValue);
		}
		TypeElement encodingType = enumeration.type("encodingType");
		Annotated.Enum result = new Annotated.Enum(
				javaName,
				type.getQualifiedName().toString(),
				values,
				unknownValue,
				encodingType == null ? null : reference(at, encodingType),
				enumeration.enumeration("primitiveType", PrimitiveType.class),
				enumeration.string("name"),
				enumeration.integer("offset"),
				enumeration.string("semanticType"),
				enumeration.string("description"),
				enumeration.integer("sinceVersion"),
				enumeration.integer("deprecated")
		);
		remember(result, at, enumeration.mirror);
		return result;
	}

	private Annotated.Set set(TypeElement type, String javaName, Element at) {
		Members set = members(type, SbeSet.class);
		if (type.getKind() != ElementKind.ENUM) {
			problem(at, "@SbeSet goes on a Java enum");
		}
		List<Annotated.Choice> choices = new ArrayList<>();
		for (VariableElement constant : constantsOf(type)) {
			if (has(constant, UnknownValue.class)) {
				problem(constant, "@UnknownValue goes on a constant of an @SbeEnum; a set has no unknown value");
			}
			if (!has(constant, SbeChoice.class)) {
				problem(constant, "a constant of an @SbeSet carries @SbeChoice");
				continue;
			}
			Members choice = members(constant, SbeChoice.class);
			Annotated.Choice result = new Annotated.Choice(
					constant.getSimpleName().toString(),
					choice.integer("value"),
					choice.string("name"),
					choice.string("description"),
					choice.integer("sinceVersion"),
					choice.integer("deprecated")
			);
			remember(result, constant, choice.mirror);
			choices.add(result);
		}
		TypeElement encodingType = set.type("encodingType");
		Annotated.Set result = new Annotated.Set(
				javaName,
				type.getQualifiedName().toString(),
				choices,
				encodingType == null ? null : reference(at, encodingType),
				set.enumeration("primitiveType", PrimitiveType.class),
				set.string("name"),
				set.integer("offset"),
				set.string("semanticType"),
				set.string("description"),
				set.integer("sinceVersion"),
				set.integer("deprecated")
		);
		remember(result, at, set.mirror);
		return result;
	}

	private static List<VariableElement> constantsOf(TypeElement type) {
		List<VariableElement> constants = new ArrayList<>();
		for (VariableElement field : ElementFilter.fieldsIn(type.getEnclosedElements())) {
			if (field.getKind() == ElementKind.ENUM_CONSTANT) {
				constants.add(field);
			}
		}
		return constants;
	}

	// ---- messages
	// ----------------------------------------------------------------------------------

	private Annotated.Message message(TypeElement type) {
		Members message = members(type, SbeMessage.class);
		if (type.getKind() != ElementKind.RECORD) {
			problem(type, "@SbeMessage goes on a record");
		}
		Annotated.Message result = new Annotated.Message(
				type.getSimpleName().toString(),
				message.integer("id"),
				components(type),
				message.string("name"),
				message.integer("blockLength"),
				message.string("semanticType"),
				message.string("description"),
				message.integer("sinceVersion"),
				message.integer("deprecated")
		);
		remember(result, type, message.mirror);
		return result;
	}

	/** A message's or a group entry's components, in declaration order. */
	private List<Annotated.Component> components(TypeElement record) {
		List<Annotated.Component> components = new ArrayList<>();
		for (RecordComponentElement component : ElementFilter.recordComponentsIn(record.getEnclosedElements())) {
			if (has(component, SbeField.class)) {
				components.add(field(component));
			} else if (has(component, SbeGroup.class)) {
				components.add(group(component));
			} else if (has(component, SbeData.class)) {
				components.add(data(component));
			} else {
				problem(component, "a component of a message or a group carries @SbeField, @SbeGroup or @SbeData");
			}
		}
		return components;
	}

	private Annotated.Field field(RecordComponentElement component) {
		Members field = members(component, SbeField.class);
		TypeElement type = field.type("type");
		Annotated.Field result = new Annotated.Field(
				component.getSimpleName().toString(),
				javaType(component.asType()),
				field.integer("id"),
				type == null ? null : reference(component, type),
				field.enumeration("primitiveType", PrimitiveType.class),
				field.string("name"),
				field.enumeration("presence", Presence.class),
				field.string("valueRef"),
				field.integer("offset"),
				field.string("epoch"),
				field.string("timeUnit"),
				field.string("semanticType"),
				field.string("description"),
				field.integer("sinceVersion"),
				field.integer("deprecated")
		);
		remember(result, component, field.mirror);
		return result;
	}

	private Annotated.Group group(RecordComponentElement component) {
		Members group = members(component, SbeGroup.class);
		TypeElement entry = listEntryRecord(component.asType());
		Annotated.Group result = new Annotated.Group(
				component.getSimpleName().toString(),
				javaType(component.asType()),
				group.integer("id"),
				entry == null ? List.of() : components(entry),
				composite(component, group.type("dimensionType"), "dimensionType", "GroupSizeEncoding"),
				group.string("name"),
				group.integer("blockLength"),
				group.string("semanticType"),
				group.string("description"),
				group.integer("sinceVersion"),
				group.integer("deprecated")
		);
		remember(result, component, group.mirror);
		return result;
	}

	private Annotated.Data data(RecordComponentElement component) {
		Members data = members(component, SbeData.class);
		Annotated.Data result = new Annotated.Data(
				component.getSimpleName().toString(),
				javaType(component.asType()),
				data.integer("id"),
				composite(component, data.type("type"), "type", "VarDataEncoding"),
				data.string("name"),
				data.integer("offset"),
				data.string("semanticType"),
				data.string("description"),
				data.integer("sinceVersion"),
				data.integer("deprecated")
		);
		remember(result, component, data.mirror);
		return result;
	}

	// ---- Java types
	// ----------------------------------------------------------------------------------

	/** The component's Java type, as far as the mapping needs to know it. */
	private Annotated.JavaType javaType(TypeMirror type) {
		switch (type.getKind()) {
			case BYTE -> {
				return new Annotated.Primitive(Annotated.JavaPrimitive.BYTE, false);
			}
			case SHORT -> {
				return new Annotated.Primitive(Annotated.JavaPrimitive.SHORT, false);
			}
			case INT -> {
				return new Annotated.Primitive(Annotated.JavaPrimitive.INT, false);
			}
			case LONG -> {
				return new Annotated.Primitive(Annotated.JavaPrimitive.LONG, false);
			}
			case FLOAT -> {
				return new Annotated.Primitive(Annotated.JavaPrimitive.FLOAT, false);
			}
			case DOUBLE -> {
				return new Annotated.Primitive(Annotated.JavaPrimitive.DOUBLE, false);
			}
			case CHAR -> {
				return new Annotated.Primitive(Annotated.JavaPrimitive.CHAR, false);
			}
			case BOOLEAN -> {
				return new Annotated.Primitive(Annotated.JavaPrimitive.BOOLEAN, false);
			}
			case ARRAY -> {
				if (((ArrayType) type).getComponentType().getKind() == TypeKind.BYTE) {
					return new Annotated.Bytes();
				}
				return new Annotated.Other(type.toString());
			}
			case DECLARED -> {
				return declaredJavaType(type);
			}
			default -> {
				return new Annotated.Other(type.toString());
			}
		}
	}

	private Annotated.JavaType declaredJavaType(TypeMirror type) {
		TypeElement element = (TypeElement) ((DeclaredType) type).asElement();
		String name = element.getQualifiedName().toString();
		Annotated.JavaPrimitive boxed = switch (name) {
			case "java.lang.Byte" -> Annotated.JavaPrimitive.BYTE;
			case "java.lang.Short" -> Annotated.JavaPrimitive.SHORT;
			case "java.lang.Integer" -> Annotated.JavaPrimitive.INT;
			case "java.lang.Long" -> Annotated.JavaPrimitive.LONG;
			case "java.lang.Float" -> Annotated.JavaPrimitive.FLOAT;
			case "java.lang.Double" -> Annotated.JavaPrimitive.DOUBLE;
			case "java.lang.Character" -> Annotated.JavaPrimitive.CHAR;
			case "java.lang.Boolean" -> Annotated.JavaPrimitive.BOOLEAN;
			default -> null;
		};
		if (boxed != null) {
			return new Annotated.Primitive(boxed, true);
		}
		if (name.equals("java.lang.String")) {
			return new Annotated.Text();
		}
		if (listEntryRecord(type) != null) {
			return new Annotated.ListOfRecord();
		}
		TypeElement setEntry = setEntry(type);
		if (setEntry != null && has(setEntry, SbeSet.class)) {
			Annotated.Declaration set = declaration(setEntry);
			if (set != null) {
				return new Annotated.SetOf(set);
			}
		}
		if (isDeclaration(element)) {
			Annotated.Declaration declaration = declaration(element);
			if (declaration != null) {
				return new Annotated.Declared(declaration);
			}
		}
		return new Annotated.Other(type.toString());
	}

	/** The {@code E} of a {@code Set<E>}, or null for any other type. */
	private static @Nullable TypeElement setEntry(TypeMirror type) {
		if (!(type instanceof DeclaredType declared)) {
			return null;
		}
		TypeElement element = (TypeElement) declared.asElement();
		if (!element.getQualifiedName().contentEquals("java.util.Set") || declared.getTypeArguments().size() != 1) {
			return null;
		}
		return declaredTypeOf(declared.getTypeArguments().get(0));
	}

	/** The record {@code E} of a {@code List<E>}, or null for any other type. */
	private static @Nullable TypeElement listEntryRecord(TypeMirror type) {
		if (!(type instanceof DeclaredType declared)) {
			return null;
		}
		TypeElement element = (TypeElement) declared.asElement();
		if (!element.getQualifiedName().contentEquals("java.util.List") || declared.getTypeArguments().size() != 1) {
			return null;
		}
		TypeElement entry = declaredTypeOf(declared.getTypeArguments().get(0));
		return entry != null && entry.getKind() == ElementKind.RECORD ? entry : null;
	}

	private static @Nullable TypeElement declaredTypeOf(TypeMirror type) {
		return type instanceof DeclaredType declared ? (TypeElement) declared.asElement() : null;
	}

	// ---- reading annotations
	// ----------------------------------------------------------------------------------

	private static boolean has(Element element, Class<? extends Annotation> annotation) {
		return annotation(element, annotation) != null;
	}

	private Members members(Element element, Class<? extends Annotation> annotation) {
		AnnotationMirror mirror = annotation(element, annotation);
		if (mirror == null) {
			throw new IllegalStateException(element + " carries no @" + annotation.getSimpleName());
		}
		return new Members(mirror);
	}

	/** An annotation's members by name, defaults filled. */
	private final class Members {

		final AnnotationMirror mirror;
		private final Map<String, AnnotationValue> values = new HashMap<>();

		Members(AnnotationMirror mirror) {
			this.mirror = mirror;
			elements.getElementValuesWithDefaults(mirror)
					.forEach((member, value) -> values.put(member.getSimpleName().toString(), value));
		}

		int integer(String member) {
			return (Integer) value(member).getValue();
		}

		String string(String member) {
			return (String) value(member).getValue();
		}

		boolean flag(String member) {
			return (Boolean) value(member).getValue();
		}

		<E extends Enum<E>> E enumeration(String member, Class<E> type) {
			VariableElement constant = (VariableElement) value(member).getValue();
			return Enum.valueOf(type, constant.getSimpleName().toString());
		}

		/** A {@code Class} member as the type it names; null for {@code void.class}. */
		@Nullable
		TypeElement type(String member) {
			return declaredTypeOf((TypeMirror) value(member).getValue());
		}

		private AnnotationValue value(String member) {
			AnnotationValue value = values.get(member);
			if (value == null) {
				throw new IllegalStateException(mirror + " has no member " + member);
			}
			return value;
		}
	}

	private void remember(Object node, Element element, AnnotationMirror mirror) {
		origins.put(node, element);
		mirrors.put(node, mirror);
	}

	private void problem(Element element, String message) {
		problems.add(new Problem(element, message));
	}
}
