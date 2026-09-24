package net.concini.sbebuddy.processor;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

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
import net.concini.sbebuddy.SbeUnion;
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
	private final Types types;
	private final List<Problem> problems = new ArrayList<>();
	private final Map<Object, Element> origins = new IdentityHashMap<>();
	private final Map<Object, AnnotationMirror> mirrors = new IdentityHashMap<>();
	private final Map<TypeElement, Annotated.Declaration> declarations = new HashMap<>();
	private final Set<TypeElement> misfits = new HashSet<>();

	private Discovery(Elements elements, Types types) {
		this.elements = elements;
		this.types = types;
	}

	/** The package must carry {@code @SbeSchema}; the processor checks first. */
	public static Discovered discover(PackageElement schemaPackage, Elements elements, Types types) {
		Discovery discovery = new Discovery(elements, types);
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
		List<TypeElement> unionTypes = new ArrayList<>();
		for (TypeElement type : ElementFilter.typesIn(schemaPackage.getEnclosedElements())) {
			walk(type, declarationTypes, messageTypes, unionTypes);
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
		Map<TypeElement, Annotated.Message> messagesByType = new HashMap<>();
		for (TypeElement type : messageTypes) {
			Annotated.Message message = message(type);
			messages.add(message);
			messagesByType.put(type, message);
		}
		unionTypes.sort(Comparator.comparing(Discovery::qualifiedName));
		boolean codecs = schema.flag("codecs");
		List<Annotated.Union> unions = new ArrayList<>();
		for (TypeElement type : unionTypes) {
			Annotated.Union union = union(type, messagesByType, codecs);
			if (union != null) {
				unions.add(union);
			}
		}
		Annotated annotated = new Annotated(
				schemaPackage.getQualifiedName().toString(),
				schema.integer("id"),
				schema.integer("version"),
				header(schemaPackage, schema),
				declared,
				messages,
				unions,
				schema.string("semanticVersion"),
				schema.string("description"),
				schema.enumeration("byteOrder", ByteOrder.class),
				codecs,
				schema.integer("baselineVersion")
		);
		remember(annotated, schemaPackage, schema.mirror);
		return annotated;
	}

	/**
	 * A message, a declaration or a union, then the types nested in it, except
	 * inside a composite, whose nested types are its inline members.
	 */
	private static void walk(
			TypeElement type, List<TypeElement> declarations, List<TypeElement> messages, List<TypeElement> unions
	) {
		if (has(type, SbeMessage.class)) {
			messages.add(type);
		} else if (isDeclaration(type)) {
			declarations.add(type);
		}
		if (has(type, SbeUnion.class)) {
			unions.add(type);
		}
		if (has(type, SbeComposite.class)) {
			return;
		}
		for (TypeElement nested : ElementFilter.typesIn(type.getEnclosedElements())) {
			walk(nested, declarations, messages, unions);
		}
	}

	// ---- unions
	// ----------------------------------------------------------------------------------

	/**
	 * A sealed interface over messages of this schema: the hierarchy beneath it
	 * flattened to its messages, each once, in template id order. Null where the
	 * interface or a subtype breaks a rule.
	 */
	private Annotated.@Nullable Union union(
			TypeElement type, Map<TypeElement, Annotated.Message> messagesByType, boolean codecs
	) {
		if (!isSealedInterface(type)) {
			problem(type, "@SbeUnion goes on a sealed interface");
			return null;
		}
		int before = problems.size();
		if (!type.getTypeParameters().isEmpty()) {
			problem(type, "a union is not generic: its codec decodes to one type");
		}
		if (!codecs) {
			problem(type, "a union needs codecs, and codecs = false on @SbeSchema generates none");
		}
		Map<TypeElement, Annotated.Message> members = new LinkedHashMap<>();
		boolean clean = flatten(type, type, messagesByType, members);
		if (!clean || problems.size() > before) {
			return null;
		}
		List<Annotated.Message> ordered = new ArrayList<>(members.values());
		ordered.sort(Comparator.comparingInt(Annotated.Message::id));
		Annotated.Union union = new Annotated.Union(type.getSimpleName().toString(), qualifiedName(type), ordered);
		remember(union, type, members(type, SbeUnion.class).mirror);
		return union;
	}

	/**
	 * The messages beneath a sealed interface into {@code members}; false where a
	 * subtype is neither a message of this schema nor a sealed interface. Such a
	 * subtype is reported once, whichever union reaches it first.
	 */
	private boolean flatten(
			TypeElement union, TypeElement type, Map<TypeElement, Annotated.Message> messagesByType,
			Map<TypeElement, Annotated.Message> members
	) {
		boolean clean = true;
		for (TypeMirror permitted : type.getPermittedSubclasses()) {
			TypeElement subtype = (TypeElement) types.asElement(permitted);
			Annotated.Message message = messagesByType.get(subtype);
			if (message != null) {
				members.put(subtype, message);
			} else if (isSealedInterface(subtype)) {
				clean &= flatten(union, subtype, messagesByType, members);
			} else {
				clean = false;
				if (misfits.add(subtype)) {
					problem(subtype, misfit(subtype, union));
				}
			}
		}
		return clean;
	}

	private static String misfit(TypeElement subtype, TypeElement union) {
		String name = subtype.getSimpleName() + " is in the union " + union.getSimpleName();
		if (has(subtype, SbeMessage.class)) {
			return name + " but is a message of another schema";
		}
		if (subtype.getModifiers().contains(Modifier.NON_SEALED)) {
			return name + " and non-sealed, which leaves the union open to types its codec cannot know";
		}
		if (subtype.getKind() == ElementKind.RECORD) {
			return name + " but carries no @SbeMessage";
		}
		return name + " but is neither an @SbeMessage record nor a sealed interface";
	}

	private static boolean isSealedInterface(TypeElement type) {
		return type.getKind() == ElementKind.INTERFACE && type.getModifiers().contains(Modifier.SEALED);
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
		if (isDeclaration(type) && toWire(type) != null) {
			problem(
					type,
					type.getQualifiedName()
							+ " declares a type and implements TypeBinding; a binding is a class of its own"
			);
		}
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
		return builtIn(builtIn);
	}

	/**
	 * The schema's header. Its member is typed to take only a
	 * {@code MessageHeader}, so a value without a type is one javac refused and
	 * reported; the built-in stands in for it.
	 */
	private Annotated.Composite header(PackageElement schemaPackage, Members schema) {
		TypeElement type = schema.type("headerType");
		return type == null
				? builtIn("DefaultMessageHeader")
				: composite(schemaPackage, type, "headerType", "DefaultMessageHeader");
	}

	private Annotated.Composite builtIn(String builtIn) {
		TypeElement type = elements.getTypeElement(API_PACKAGE + "." + builtIn);
		if (type == null || !(declaration(type) instanceof Annotated.Composite composite)) {
			throw new IllegalStateException("the api's " + builtIn + " is not on the classpath");
		}
		return composite;
	}

	/**
	 * A type on a class, without a Java type, or on a composite's component, with
	 * the component's.
	 */
	private Annotated.Type type(Element element, String javaName) {
		Annotated.JavaType javaType = element instanceof RecordComponentElement component
				? javaType(component.asType())
				: null;
		return type(members(element, SbeType.class), javaName, javaType, element);
	}

	private Annotated.Type type(Members type, String javaName, Annotated.@Nullable JavaType javaType, Element at) {
		Annotated.Binding binding = null;
		if (javaType == null) {
			if (type.type("binding") != null) {
				problem(at, "@SbeType on a class declares a type; a binding goes on a component that uses it");
			}
		} else {
			binding = binding(type, javaType, at, "member");
		}
		Annotated.Type result = new Annotated.Type(
				javaName,
				javaType,
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
				type.integer("deprecated"),
				binding
		);
		remember(result, at, type.mirror);
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
		List<Annotated.Type> unmapped = new ArrayList<>();
		for (AnnotationMirror mirror : composite.annotations("unmapped")) {
			Members member = new Members(mirror);
			unmapped.add(type(member, member.string("name"), new Annotated.Unmapped(), at));
		}
		Annotated.Composite result = new Annotated.Composite(
				javaName,
				type.getQualifiedName().toString(),
				members,
				unmapped,
				composite.strings("layout"),
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
				// The set's face is a Set of its enum, as a field's is.
				problem(component, nested.getSimpleName() + " is a set; use Set<" + nested.getSimpleName() + ">");
				return null;
			}
			if (has(nested, SbeComposite.class)) {
				return composite(nested, javaName, component);
			}
		}
		TypeElement setEntry = setEntry(component.asType());
		if (setEntry != null && composite.equals(setEntry.getEnclosingElement()) && has(setEntry, SbeSet.class)) {
			return set(setEntry, javaName, component);
		}
		problem(
				component,
				"a composite's component carries @SbeType or @SbeRef, or its type is an @SbeEnum or @SbeComposite nested in the composite, or a Set of an @SbeSet nested in it"
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
				ref.integer("deprecated"),
				binding(ref, javaType(component.asType()), component, "member")
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
				qualifiedName(type),
				message.integer("id"),
				components(type),
				unmapped(message, type),
				message.strings("layout"),
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

	/**
	 * The fields no component carries, each read from its own {@code @SbeField}
	 * inside the message's or group's annotation and placed on the element that
	 * carries it.
	 */
	private List<Annotated.Field> unmapped(Members body, Element at) {
		List<Annotated.Field> unmapped = new ArrayList<>();
		for (AnnotationMirror mirror : body.annotations("unmapped")) {
			Members field = new Members(mirror);
			unmapped.add(field(field, field.string("name"), new Annotated.Unmapped(), at));
		}
		return unmapped;
	}

	private Annotated.Field field(RecordComponentElement component) {
		return field(
				members(component, SbeField.class), component.getSimpleName().toString(),
				javaType(component.asType()), component
		);
	}

	private Annotated.Field field(Members field, String javaName, Annotated.JavaType javaType, Element at) {
		TypeElement type = field.type("type");
		Annotated.Field result = new Annotated.Field(
				javaName,
				javaType,
				field.integer("id"),
				type == null ? null : reference(at, type),
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
				field.integer("deprecated"),
				binding(field, javaType, at, "field")
		);
		remember(result, at, field.mirror);
		return result;
	}

	/**
	 * A component's binding, checked as far as javac can: a class of its own, not a
	 * declaration, concrete, constructible without arguments from the schema
	 * package, a {@code TypeBinding} or one of its specializations whose {@code J}
	 * is the component's type, where a primitive component matches its box. The
	 * face it binds goes down as a Java type for the face rule, a primitive unboxed
	 * for a specialization. {@code what} names the component for an unmapped one,
	 * which has nothing to bind.
	 */
	private Annotated.@Nullable Binding binding(
			Members annotation, Annotated.JavaType javaType, Element at, String what
	) {
		TypeElement type = annotation.type("binding");
		if (type == null) {
			return null;
		}
		String name = type.getQualifiedName().toString();
		if (javaType instanceof Annotated.Unmapped) {
			problem(at, "an unmapped " + what + " has no component to bind");
			return null;
		}
		if (isDeclaration(type)) {
			problem(at, name + " is a declaration; a binding is a class of its own");
			return null;
		}
		ExecutableType toWire = toWire(type);
		if (toWire == null) {
			problem(at, name + " implements neither TypeBinding nor one of its specializations");
			return null;
		}
		if (type.getModifiers().contains(Modifier.ABSTRACT)) {
			problem(at, name + " is abstract");
		}
		if (!constructible(type, at)) {
			problem(at, name + " needs a no-arg constructor the schema package can call");
		}
		TypeMirror bound = toWire.getParameterTypes().get(0);
		TypeMirror component = at.asType();
		// javac's PrimitiveType, qualified beside the api's.
		boolean boxed = component.getKind().isPrimitive()
				&& types.isSameType(bound, types.boxedClass((javax.lang.model.type.PrimitiveType) component).asType());
		if (!types.isSameType(bound, component) && !boxed) {
			problem(at, name + " binds " + bound + ", not " + component);
		}
		return new Annotated.Binding(name, javaType(toWire.getReturnType()));
	}

	private static final List<String> BINDING_INTERFACES = List.of(
			"TypeBinding", "TypeBinding.OfByte", "TypeBinding.OfShort", "TypeBinding.OfInt", "TypeBinding.OfLong",
			"TypeBinding.OfFloat", "TypeBinding.OfDouble"
	);

	/**
	 * {@code toWire} as the class implements it, from whichever of
	 * {@code TypeBinding} and its specializations the class implements, its type
	 * arguments substituted: the parameter is {@code J}, the return type the face
	 * it binds; null for a class that implements none.
	 */
	private @Nullable ExecutableType toWire(TypeElement type) {
		for (String name : BINDING_INTERFACES) {
			TypeElement binding = elements.getTypeElement(API_PACKAGE + "." + name);
			if (binding == null) {
				throw new IllegalStateException("the api's " + name + " is not on the classpath");
			}
			if (!types.isSubtype(types.erasure(type.asType()), types.erasure(binding.asType()))) {
				continue;
			}
			for (ExecutableElement method : ElementFilter.methodsIn(binding.getEnclosedElements())) {
				if (method.getSimpleName().contentEquals("toWire")) {
					return (ExecutableType) types.asMemberOf((DeclaredType) type.asType(), method);
				}
			}
			throw new IllegalStateException("the api's " + name + " has no toWire");
		}
		return null;
	}

	/**
	 * The codec, in the schema package, says {@code new X()}: a no-arg constructor
	 * that is not private, and public with its class when the class lives
	 * elsewhere.
	 */
	private boolean constructible(TypeElement type, Element at) {
		boolean samePackage = elements.getPackageOf(type).equals(elements.getPackageOf(at));
		if (!samePackage && !type.getModifiers().contains(Modifier.PUBLIC)) {
			return false;
		}
		for (ExecutableElement constructor : ElementFilter.constructorsIn(type.getEnclosedElements())) {
			if (!constructor.getParameters().isEmpty() || constructor.getModifiers().contains(Modifier.PRIVATE)) {
				continue;
			}
			if (samePackage || constructor.getModifiers().contains(Modifier.PUBLIC)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * A group, whose entry record is the {@code List}'s the component is, or with a
	 * binding the one it binds.
	 */
	private Annotated.Group group(RecordComponentElement component) {
		Members group = members(component, SbeGroup.class);
		Annotated.JavaType javaType = javaType(component.asType());
		Annotated.Binding binding = binding(group, javaType, component, "group");
		TypeMirror face = component.asType();
		TypeElement bindingType = group.type("binding");
		if (binding != null && bindingType != null) {
			ExecutableType toWire = toWire(bindingType);
			if (toWire != null) {
				face = toWire.getReturnType();
			}
		}
		TypeElement entry = listEntryRecord(face);
		Annotated.Group result = new Annotated.Group(
				component.getSimpleName().toString(),
				javaType,
				group.integer("id"),
				entry == null ? List.of() : components(entry),
				unmapped(group, component),
				group.strings("layout"),
				composite(component, group.type("dimensionType"), "dimensionType", "GroupSizeEncoding"),
				group.string("name"),
				group.integer("blockLength"),
				group.string("semanticType"),
				group.string("description"),
				group.integer("sinceVersion"),
				group.integer("deprecated"),
				binding
		);
		remember(result, component, group.mirror);
		return result;
	}

	private Annotated.Data data(RecordComponentElement component) {
		Members data = members(component, SbeData.class);
		Annotated.JavaType javaType = javaType(component.asType());
		Annotated.Data result = new Annotated.Data(
				component.getSimpleName().toString(),
				javaType,
				data.integer("id"),
				composite(component, data.type("type"), "type", "VarDataEncoding"),
				data.string("name"),
				data.integer("offset"),
				data.string("semanticType"),
				data.string("description"),
				data.integer("sinceVersion"),
				data.integer("deprecated"),
				binding(data, javaType, component, "data")
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
				return switch (((ArrayType) type).getComponentType().getKind()) {
					case BYTE -> new Annotated.Bytes();
					case SHORT -> new Annotated.Array(Annotated.JavaPrimitive.SHORT);
					case INT -> new Annotated.Array(Annotated.JavaPrimitive.INT);
					case LONG -> new Annotated.Array(Annotated.JavaPrimitive.LONG);
					case FLOAT -> new Annotated.Array(Annotated.JavaPrimitive.FLOAT);
					case DOUBLE -> new Annotated.Array(Annotated.JavaPrimitive.DOUBLE);
					case CHAR -> new Annotated.Array(Annotated.JavaPrimitive.CHAR);
					case BOOLEAN -> new Annotated.Array(Annotated.JavaPrimitive.BOOLEAN);
					default -> new Annotated.Other(type.toString());
				};
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
		TypeElement listEntry = listEntryRecord(type);
		if (listEntry != null) {
			return new Annotated.ListOfRecord(listEntry.getQualifiedName().toString());
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

		/**
		 * A {@code Class} member as the type it names; null for {@code void.class}, and
		 * for a value javac refused, which it reports itself.
		 */
		@Nullable
		TypeElement type(String member) {
			return value(member).getValue() instanceof TypeMirror type ? declaredTypeOf(type) : null;
		}

		List<String> strings(String member) {
			List<String> strings = new ArrayList<>();
			for (AnnotationValue element : array(member)) {
				strings.add((String) element.getValue());
			}
			return strings;
		}

		/** An array of annotations, each a mirror of its own. */
		List<AnnotationMirror> annotations(String member) {
			List<AnnotationMirror> mirrors = new ArrayList<>();
			for (AnnotationValue element : array(member)) {
				mirrors.add((AnnotationMirror) element.getValue());
			}
			return mirrors;
		}

		@SuppressWarnings("unchecked")
		private List<? extends AnnotationValue> array(String member) {
			return (List<? extends AnnotationValue>) value(member).getValue();
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
