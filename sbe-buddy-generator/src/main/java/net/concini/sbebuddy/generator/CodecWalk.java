package net.concini.sbebuddy.generator;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import uk.co.real_logic.sbe.PrimitiveType;
import uk.co.real_logic.sbe.generation.Generators;
import uk.co.real_logic.sbe.generation.java.JavaUtil;
import uk.co.real_logic.sbe.ir.Encoding;
import uk.co.real_logic.sbe.ir.GenerationUtil;
import uk.co.real_logic.sbe.ir.Ir;
import uk.co.real_logic.sbe.ir.Signal;
import uk.co.real_logic.sbe.ir.Token;

import net.concini.sbebuddy.generator.CodecModel.Absence;
import net.concini.sbebuddy.generator.CodecModel.Body;
import net.concini.sbebuddy.generator.CodecModel.Content;
import net.concini.sbebuddy.generator.CodecModel.Helper;
import net.concini.sbebuddy.generator.CodecModel.Member;
import net.concini.sbebuddy.generator.CodecModel.Shape;

/**
 * One message's {@link CodecModel}, from the IR the way {@code JavaGenerator}
 * reads it and from {@link Annotated} for the Java side. Every flyweight member
 * the model names comes from {@link JavaUtil}, so the codec calls what was
 * generated. Each token meets its annotation once, by wire name, and each
 * helper is registered once, after the helpers it uses. A construct the codec
 * does not cover yet is a problem on the message, and the message gets no
 * model.
 */
final class CodecWalk {

	/** The members every header carries, which the message's flyweight writes. */
	private static final Set<String> STANDARD_HEADER_MEMBERS = Set
			.of("blockLength", "templateId", "schemaId", "version");

	private final Ir ir;
	private final Annotated annotated;
	private final String flyweights;
	private final Map<Object, Helper> helpers = new LinkedHashMap<>();
	private final Map<String, String> bindings = new LinkedHashMap<>();
	private final Map<String, CodecModel.Context> contexts = new LinkedHashMap<>();
	private final Set<String> problems = new LinkedHashSet<>();

	private CodecWalk(Ir ir, Annotated annotated) {
		this.ir = ir;
		this.annotated = annotated;
		this.flyweights = ir.applicableNamespace();
	}

	/**
	 * The message's model, or null with its problems added to {@code problems}.
	 */
	static @Nullable CodecModel walk(Ir ir, Annotated annotated, Annotated.Message message, List<Problem> problems) {
		CodecWalk walk = new CodecWalk(ir, annotated);
		CodecModel model = walk.model(message);
		for (String problem : walk.problems) {
			problems.add(new Problem(message, problem));
		}
		return walk.problems.isEmpty() ? model : null;
	}

	/**
	 * Whose flyweights a body's accessors are on: the message's or a group's, whose
	 * decoders guard by version, or a composite's, which never does. {@code prefix}
	 * keeps a member's or an entry's helpers apart from a field's of the same name,
	 * and {@code baseline} is the version below which nothing in the body is read.
	 */
	private record Owner(String encoder, String decoder, String prefix, Kind kind, int baseline) {

		enum Kind {
			MESSAGE, COMPOSITE, GROUP
		}
	}

	/**
	 * A helper's identity: what it maps, the declaration's wire name or the path.
	 */
	private record Key(Class<? extends Helper> kind, String name) {
	}

	/** A bound component's binding field and context constant. */
	private record Bound(String binding, String context) {
	}

	private CodecModel model(Annotated.Message message) {
		CodecModel.Header header = header();
		List<Token> tokens = ir.getMessage(message.id());
		String messageClass = JavaUtil.formatClassName(tokens.get(0).name());
		Owner owner = new Owner(
				flyweights + "." + messageClass + "Encoder", flyweights + "." + messageClass + "Decoder", "",
				Owner.Kind.MESSAGE, annotated.baselineVersion()
		);
		Body body = body(tokens, 1, message.components(), message.unmapped(), owner);
		List<CodecModel.Binding> fields = new ArrayList<>();
		bindings.forEach((name, type) -> fields.add(new CodecModel.Binding(type, name)));
		return new CodecModel(
				annotated.packageName(), message.javaName() + "Codec",
				message.qualifiedName(),
				flyweights, header, messageClass, annotated.baselineVersion(), fields, List.copyOf(contexts.values()),
				body, List.copyOf(helpers.values())
		);
	}

	// ---- the header

	/**
	 * The header as the IR resolved it, the schema's {@code headerType} or the
	 * composite named {@code messageHeader}, read as a composite is. The standard
	 * four are read and never written; every other member is written from a header
	 * or as its null value, and a constant is neither.
	 */
	private CodecModel.Header header() {
		List<Token> tokens = ir.headerStructure().tokens();
		String headerClass = JavaUtil.formatClassName(tokens.get(0).name());
		Annotated.Composite composite = annotated.headerType();
		Body body = compositeBody(tokens, composite, headerClass);
		List<Member> own = new ArrayList<>();
		List<Member.Unmapped> nulls = new ArrayList<>();
		for (Member member : body.wireOrder()) {
			switch (member) {
				case Member.Field field -> {
					if (!STANDARD_HEADER_MEMBERS.contains(field.property())) {
						own.add(field);
						Shape shape = nullShape(field, headerClass);
						if (shape != null) {
							nulls.add(new Member.Unmapped(field.property(), shape));
						}
					}
				}
				case Member.Unmapped unmapped -> {
					own.add(unmapped);
					nulls.add(unmapped);
				}
				case Member.Group group -> throw new IllegalStateException("a header holds no group");
				case Member.Data data -> throw new IllegalStateException("a header holds no var-data");
			}
		}
		return new CodecModel.Header(
				headerClass, composite.qualifiedName(),
				new Body(body.encoder(), body.decoder(), own, body.constructorOrder()), nulls
		);
	}

	/**
	 * How a header member is written when no header is given: its null value, an
	 * array's in every element; a constant needs nothing.
	 */
	private @Nullable Shape nullShape(Member.Field field, String headerClass) {
		return switch (field.shape()) {
			case Shape.Scalar scalar -> new Shape.Scalar(null);
			case Shape.Text text -> new Shape.Array(headerClass + Generators.toUpperFirstChar(field.property()));
			case Shape.Array array -> array;
			case Shape.Enum enumeration -> enumeration;
			case Shape.Set set -> set;
			case Shape.Constant constant -> null;
			case Shape.Composite composite -> {
				problems.add(lacking("a composite in a header"));
				yield null;
			}
		};
	}

	// ---- a message's or a group entry's body

	/** The fields, then the groups, recursing into each, then the var-data. */
	private Body body(
			List<Token> tokens, int index, List<Annotated.Component> components, List<Annotated.Field> unmapped,
			Owner owner
	) {
		List<Token> fields = new ArrayList<>();
		List<Token> groups = new ArrayList<>();
		List<Token> varData = new ArrayList<>();
		int next = GenerationUtil.collectFields(tokens, index, fields);
		next = GenerationUtil.collectGroups(tokens, next, groups);
		GenerationUtil.collectVarData(tokens, next, varData);
		List<Member> wireOrder = new ArrayList<>();
		Map<Annotated.Component, Member> byComponent = new IdentityHashMap<>();
		for (int i = 0; i < fields.size(); i += fields.get(i).componentTokenCount()) {
			Token field = fields.get(i);
			List<Token> typeTokens = fields.subList(i + 1, i + 1 + fields.get(i + 1).componentTokenCount());
			Annotated.Field component = component(components, field);
			Member member = component == null
					? unmapped(field, typeTokens, unmapped, owner)
					: field(field, typeTokens, component, owner);
			if (member != null) {
				wireOrder.add(member);
				if (component != null) {
					byComponent.put(component, member);
				}
			}
		}
		for (int i = 0; i < groups.size(); i += groups.get(i).componentTokenCount()) {
			List<Token> groupTokens = groups.subList(i, i + groups.get(i).componentTokenCount());
			Annotated.Group group = group(components, groupTokens.get(0));
			Member.Group member = group(groupTokens, group, owner);
			if (member != null) {
				wireOrder.add(member);
				byComponent.put(group, member);
			}
		}
		for (int i = 0; i < varData.size(); i += varData.get(i).componentTokenCount()) {
			List<Token> dataTokens = varData.subList(i, i + varData.get(i).componentTokenCount());
			Annotated.Data data = data(components, dataTokens.get(0));
			Member.Data member = data(dataTokens, data, owner);
			if (member != null) {
				wireOrder.add(member);
				byComponent.put(data, member);
			}
		}
		return new Body(owner.encoder(), owner.decoder(), wireOrder, constructorOrder(components, byComponent));
	}

	/**
	 * The canonical constructor takes the components as declared, which the layout
	 * may order differently from the wire; the block is addressed by offset, so
	 * either order reads it.
	 */
	private <C> List<Member> constructorOrder(List<? extends C> components, Map<C, Member> byComponent) {
		List<Member> order = new ArrayList<>();
		for (C component : components) {
			Member member = byComponent.get(component);
			if (member != null) {
				order.add(member);
			} else if (problems.isEmpty()) {
				throw new IllegalStateException("a component with no member on the wire");
			}
		}
		return order;
	}

	/**
	 * A field of a message or a group, its binding handed the field's epoch and
	 * time unit beside what its type says.
	 */
	private @Nullable Member field(Token field, List<Token> typeTokens, Annotated.Field component, Owner owner) {
		String name = component.javaName();
		String property = JavaUtil.formatPropertyName(field.name());
		Bound bound = null;
		Annotated.Binding binding = component.binding();
		if (binding != null) {
			bound = bound(
					binding, owner.prefix() + Generators.toUpperFirstChar(property), name, typeTokens.get(0),
					field.encoding().epoch(), field.encoding().timeUnit()
			);
			if (bound == null) {
				return null;
			}
		}
		Shape shape = shape(field, typeTokens, declarationOf(component), name, owner);
		if (shape == null) {
			return null;
		}
		return new Member.Field(
				name, property, shape, absence(field, component.javaType(), owner),
				bound == null ? null : bound.binding(), bound == null ? null : bound.context()
		);
	}

	/**
	 * A field no component carries: a constant needs nothing, anything else its
	 * null value.
	 */
	private @Nullable Member unmapped(
			Token field, List<Token> typeTokens, List<Annotated.Field> unmapped, Owner owner
	) {
		Token type = typeTokens.get(0);
		if (type.signal() == Signal.BEGIN_COMPOSITE) {
			problems.add(lacking("an unmapped field of a composite"));
			return null;
		}
		if (constant(field)) {
			return null;
		}
		Annotated.Field declared = unmappedField(unmapped, field);
		String property = JavaUtil.formatPropertyName(field.name());
		Shape shape = switch (type.signal()) {
			case ENCODING -> unmappedEncoding(type, owner, property);
			case BEGIN_ENUM -> new Shape.Enum(
					JavaUtil.formatClassName(type.applicableTypeName()), javaName(declared.type())
			);
			case BEGIN_SET -> new Shape.Set(JavaUtil.formatClassName(type.applicableTypeName()));
			default -> throw new IllegalStateException(declared.name() + " is a field of " + type.signal());
		};
		return new Member.Unmapped(property, shape);
	}

	/** A primitive takes its null value in one call, an array in every element. */
	private static Shape unmappedEncoding(Token type, Owner owner, String property) {
		return type.arrayLength() <= 1
				? new Shape.Scalar(null)
				: new Shape.Array(owner.prefix() + Generators.toUpperFirstChar(property));
	}

	private Member.@Nullable Group group(List<Token> tokens, Annotated.Group group, Owner owner) {
		Token token = tokens.get(0);
		String property = JavaUtil.formatPropertyName(token.name());
		String path = owner.prefix() + Generators.toUpperFirstChar(property);
		Bound bound = null;
		Annotated.Binding binding = group.binding();
		if (binding != null) {
			bound = bound(binding, path, group.javaName(), null, null, null);
			if (bound == null) {
				return null;
			}
		}
		boolean added = token.version() > owner.baseline();
		String groupClass = JavaUtil.formatClassName(token.name());
		Owner entry = new Owner(
				owner.encoder() + "." + groupClass + "Encoder", owner.decoder() + "." + groupClass + "Decoder", path,
				Owner.Kind.GROUP, Math.max(owner.baseline(), token.version())
		);
		// sbe-tool names the group's static methods after its decoder class.
		Member.Group member = new Member.Group(
				group.javaName(), property, path, entryRecord(group),
				body(tokens, 1 + tokens.get(1).componentTokenCount(), group.components(), group.unmapped(), entry),
				added ? JavaUtil.formatPropertyName(groupClass + "Decoder") + "SinceVersion" : null,
				bound == null ? null : bound.binding(), bound == null ? null : bound.context()
		);
		helpers.put(new Key(Helper.GroupMethods.class, path), new Helper.GroupMethods(member, owner.encoder()));
		return member;
	}

	/**
	 * Var-data through methods of its own, keyed by its path, after the check its
	 * text needs.
	 */
	private Member.@Nullable Data data(List<Token> tokens, Annotated.Data data, Owner owner) {
		Token token = tokens.get(0);
		String property = JavaUtil.formatPropertyName(token.name());
		boolean added = token.version() > owner.baseline();
		Content content = content(tokens.get(3), data);
		if (content == null) {
			return null;
		}
		switch (content) {
			case ASCII -> helpers.putIfAbsent(new Key(Helper.Ascii.class, ""), new Helper.Ascii());
			case UTF_8 -> helpers.putIfAbsent(new Key(Helper.Utf8.class, ""), new Helper.Utf8());
			case BYTES -> helpers.putIfAbsent(new Key(Helper.Bytes.class, ""), new Helper.Bytes());
		}
		String path = owner.prefix() + Generators.toUpperFirstChar(property);
		Bound bound = null;
		Annotated.Binding binding = data.binding();
		if (binding != null) {
			Token varData = tokens.get(3);
			bound = bound(
					binding, path, data.javaName(), content == Content.BYTES ? null : varData,
					tokens.get(0).encoding().epoch(), tokens.get(0).encoding().timeUnit()
			);
			if (bound == null) {
				return null;
			}
		}
		Member.Data member = new Member.Data(
				data.javaName(), property, path, content, added ? property + "SinceVersion" : null,
				bound == null ? null : bound.binding(), bound == null ? null : bound.context()
		);
		helpers.put(
				new Key(Helper.DataMethods.class, path),
				new Helper.DataMethods(
						member, owner.encoder(), owner.decoder(), Generators.toUpperFirstChar(property),
						flyweights + "." + JavaUtil.formatClassName(tokens.get(1).applicableTypeName()) + "Encoder"
				)
		);
		return member;
	}

	/**
	 * Decided by the component, whose face the rules tied to the varData type: a
	 * String is text in the type's encoding, which the codec checks in ASCII and
	 * counts in UTF-8.
	 */
	private @Nullable Content content(Token varData, Annotated.Data data) {
		Annotated.Binding binding = data.binding();
		Annotated.JavaType face = binding == null ? data.javaType() : binding.wire();
		if (!(face instanceof Annotated.Text)) {
			return Content.BYTES;
		}
		String encoding = varData.encoding().characterEncoding();
		if (encoding == null) {
			throw new IllegalStateException(data.javaName() + " is text over a varData without an encoding");
		}
		if (JavaUtil.isAsciiEncoding(encoding)) {
			return Content.ASCII;
		}
		if (JavaUtil.isUtf8Encoding(encoding)) {
			return Content.UTF_8;
		}
		problems.add(lacking("text data in " + encoding));
		return null;
	}

	// ---- how a field or member reaches the wire

	/**
	 * Decided by the wire and the component: a constant or a primitive is always
	 * there, the null value makes a field optional, and a field appended above the
	 * body's baseline is absent from older messages. A composite's member is never
	 * newer than its composite, which the mapping sees to.
	 */
	private Absence absence(Token field, Annotated.JavaType javaType, Owner owner) {
		boolean primitive = javaType instanceof Annotated.Primitive plain && !plain.boxed();
		if (constant(field)) {
			return primitive ? Absence.NONE : Absence.REQUIRED;
		}
		if (field.encoding().presence() == Encoding.Presence.OPTIONAL) {
			return Absence.OPTIONAL;
		}
		if (owner.kind() != Owner.Kind.COMPOSITE && field.version() > owner.baseline()) {
			return Absence.ADDED;
		}
		return primitive ? Absence.NONE : Absence.REQUIRED;
	}

	private @Nullable Shape shape(
			Token field, List<Token> typeTokens, Annotated.@Nullable Declaration declaration, String component,
			Owner owner
	) {
		Token type = typeTokens.get(0);
		String property = JavaUtil.formatPropertyName(field.name());
		if (constant(field)) {
			return constant(field, typeTokens, declaration, component);
		}
		return switch (type.signal()) {
			case ENCODING -> encoding(type, owner, property, component);
			case BEGIN_ENUM -> enumeration(typeTokens, declared(declaration, Annotated.Enum.class, component));
			case BEGIN_SET -> set(typeTokens, declared(declaration, Annotated.Set.class, component));
			case BEGIN_COMPOSITE -> composite(typeTokens, declared(declaration, Annotated.Composite.class, component));
			default -> throw new IllegalStateException("a field of " + type.signal());
		};
	}

	/**
	 * A scalar through its accessor, a char string through the flyweight's String
	 * form in ASCII, and any other array through a pair of its own.
	 */
	private @Nullable Shape encoding(Token type, Owner owner, String property, String component) {
		PrimitiveType primitive = type.encoding().primitiveType();
		if (type.arrayLength() <= 1) {
			// The box is the one name that neither the IR nor JavaUtil holds.
			return new Shape.Scalar(
					primitive == PrimitiveType.FLOAT ? "Float" : primitive == PrimitiveType.DOUBLE ? "Double" : null
			);
		}
		if (primitive == PrimitiveType.CHAR) {
			String encoding = type.encoding().characterEncoding();
			if (!JavaUtil.isAsciiEncoding(encoding)) {
				problems.add(lacking("a string in " + encoding));
				return null;
			}
			helpers.putIfAbsent(new Key(Helper.Ascii.class, ""), new Helper.Ascii());
			return new Shape.Text();
		}
		String field = owner.prefix() + Generators.toUpperFirstChar(property);
		helpers.putIfAbsent(
				new Key(Helper.ArrayPair.class, field),
				new Helper.ArrayPair(
						field, owner.encoder(), owner.decoder(), property, Generators.toUpperFirstChar(property),
						component, JavaUtil.javaTypeName(primitive), primitive == PrimitiveType.UINT8
				)
		);
		return new Shape.Array(field);
	}

	/**
	 * The wire value is read raw and mapped by the valid values' text, never
	 * through the flyweight's enum, so an unknown value is ours to decide.
	 */
	private Shape.Enum enumeration(List<Token> tokens, Annotated.Enum enumeration) {
		String enumClass = JavaUtil.formatClassName(tokens.get(0).applicableTypeName());
		PrimitiveType primitive = tokens.get(0).encoding().primitiveType();
		helpers.computeIfAbsent(new Key(Helper.EnumPair.class, tokens.get(0).applicableTypeName()), key -> {
			List<Helper.EnumValue> values = new ArrayList<>();
			for (Token value : tokens) {
				if (value.signal() == Signal.VALID_VALUE) {
					values.add(
							new Helper.EnumValue(
									validValue(enumeration, value.name()).javaName(),
									JavaUtil.formatForJavaKeyword(value.name()),
									JavaUtil.generateLiteral(primitive, value.encoding().constValue().toString())
							)
					);
				}
			}
			return new Helper.EnumPair(
					enumClass, enumeration.qualifiedName(), JavaUtil.javaTypeName(primitive), values,
					enumeration.unknownValue()
			);
		});
		return new Shape.Enum(enumClass, enumeration.qualifiedName());
	}

	private Shape.Set set(List<Token> tokens, Annotated.Set set) {
		String setClass = JavaUtil.formatClassName(tokens.get(0).applicableTypeName());
		helpers.computeIfAbsent(new Key(Helper.SetPair.class, tokens.get(0).applicableTypeName()), key -> {
			List<Helper.Choice> choices = new ArrayList<>();
			for (Token choice : tokens) {
				if (choice.signal() == Signal.CHOICE) {
					choices.add(
							new Helper.Choice(
									JavaUtil.formatPropertyName(choice.name()), choice(set, choice.name()).javaName(),
									choice.encoding().constValue().toString()
							)
					);
				}
			}
			return new Helper.SetPair(setClass, set.qualifiedName(), choices);
		});
		return new Shape.Set(setClass);
	}

	/**
	 * A composite goes through the pair of its type, registered after the pairs its
	 * members use, which register while its body is built.
	 */
	private Shape.Composite composite(List<Token> tokens, Annotated.Composite composite) {
		String compositeClass = JavaUtil.formatClassName(tokens.get(0).applicableTypeName());
		Key key = new Key(Helper.CompositePair.class, tokens.get(0).applicableTypeName());
		if (!helpers.containsKey(key)) {
			Body body = compositeBody(tokens, composite, compositeClass);
			helpers.put(key, new Helper.CompositePair(compositeClass, composite.qualifiedName(), body));
		}
		return new Shape.Composite(compositeClass);
	}

	/**
	 * A composite's members with the shapes a field has, over the composite's
	 * flyweights.
	 */
	private Body compositeBody(List<Token> tokens, Annotated.Composite composite, String compositeClass) {
		Owner owner = new Owner(
				flyweights + "." + compositeClass + "Encoder", flyweights + "." + compositeClass + "Decoder",
				compositeClass, Owner.Kind.COMPOSITE, 0
		);
		List<Member> wireOrder = new ArrayList<>();
		Map<Annotated.Member, Member> byMember = new IdentityHashMap<>();
		for (int i = 1; i < tokens.size() - 1; i += tokens.get(i).componentTokenCount()) {
			Token token = tokens.get(i);
			List<Token> memberTokens = tokens.subList(i, i + token.componentTokenCount());
			String property = JavaUtil.formatPropertyName(token.name());
			Annotated.Member member = member(composite, token.name());
			if (member == null) {
				if (!constant(token)) {
					wireOrder.add(new Member.Unmapped(property, unmappedEncoding(token, owner, property)));
				}
				continue;
			}
			String name = javaName(member);
			Bound bound = null;
			Annotated.Binding binding = bindingOf(member);
			if (binding != null) {
				// A member has no epoch or time unit: the schema gives them to fields.
				bound = bound(
						binding, owner.prefix() + Generators.toUpperFirstChar(property), name, token, null, null
				);
				if (bound == null) {
					continue;
				}
			}
			Shape shape = shape(token, memberTokens, declarationOf(member), name, owner);
			if (shape != null) {
				Member field = new Member.Field(
						name, property, shape, absence(token, javaTypeOf(member), owner),
						bound == null ? null : bound.binding(), bound == null ? null : bound.context()
				);
				wireOrder.add(field);
				byMember.put(member, field);
			}
		}
		return new Body(owner.encoder(), owner.decoder(), wireOrder, constructorOrder(composite.members(), byMember));
	}

	/**
	 * A constant carries no bytes: it is read as its shape would be, and checked on
	 * the way out against the flyweight's constant, or for an enum against the
	 * record's constant the valueRef names.
	 */
	private Shape constant(
			Token field, List<Token> typeTokens, Annotated.@Nullable Declaration declaration, String component
	) {
		Token type = typeTokens.get(0);
		return switch (type.signal()) {
			case ENCODING -> {
				Encoding encoding = type.encoding();
				boolean text = encoding.primitiveType() == PrimitiveType.CHAR
						&& encoding.constValue().byteArrayValue(PrimitiveType.CHAR).length > 1;
				yield new Shape.Constant(text ? new Shape.Text() : new Shape.Scalar(null), null);
			}
			case BEGIN_ENUM -> {
				Annotated.Enum enumeration = declared(declaration, Annotated.Enum.class, component);
				String reference = field.encoding().constValue().toString();
				String constant = validValue(enumeration, reference.substring(reference.indexOf('.') + 1)).javaName();
				yield new Shape.Constant(enumeration(typeTokens, enumeration), constant);
			}
			default -> throw new IllegalStateException("a constant field of " + type.signal());
		};
	}

	/**
	 * The codec's field for the binding, named after the class,
	 * {@code priceBinding} for {@code Price} and declared on first use, and the
	 * component's context, named after its path, {@code legsPriceContext}, from its
	 * type's token: the primitive of a scalar, an array, an enum or a set, none for
	 * a composite, and a {@code char} array's encoding. Null, with the problem,
	 * when another class of the binding's simple name already has its field.
	 */
	private @Nullable Bound bound(
			Annotated.Binding binding, String path, String component, @Nullable Token type, @Nullable String epoch,
			@Nullable String timeUnit
	) {
		String simpleName = simpleName(binding.qualifiedName());
		String name = Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
		if (!name.endsWith("Binding")) {
			name += "Binding";
		}
		String declared = bindings.putIfAbsent(name, binding.qualifiedName());
		if (declared != null && !declared.equals(binding.qualifiedName())) {
			problems.add(
					"two bindings share the simple name " + simpleName + " in one codec; the second is "
							+ binding.qualifiedName()
			);
			return null;
		}
		PrimitiveType primitive = type == null || type.signal() == Signal.BEGIN_COMPOSITE
				? null
				: type.encoding().primitiveType();
		String characterEncoding = type != null && primitive == PrimitiveType.CHAR && type.arrayLength() != 1
				? type.encoding().characterEncoding()
				: null;
		String context = Character.toLowerCase(path.charAt(0)) + path.substring(1) + "Context";
		contexts.putIfAbsent(
				context,
				new CodecModel.Context(
						context, literal(component),
						primitive == null ? "null" : "net.concini.sbebuddy.PrimitiveType." + primitive.name(),
						literal(characterEncoding), literal(epoch), literal(timeUnit)
				)
		);
		return new Bound(name, context);
	}

	/** Text as a Java string literal, or {@code null}. */
	private static String literal(@Nullable String text) {
		if (text == null) {
			return "null";
		}
		StringBuilder literal = new StringBuilder("\"");
		for (char c : text.toCharArray()) {
			if (c == '"' || c == '\\') {
				literal.append('\\').append(c);
			} else if (c < 0x20 || c > 0x7e) {
				literal.append(String.format("\\u%04x", (int) c));
			} else {
				literal.append(c);
			}
		}
		return literal.append('"').toString();
	}

	// ---- the annotation a token came from, by wire name

	private static Annotated.@Nullable Field component(List<Annotated.Component> components, Token field) {
		for (Annotated.Component component : components) {
			if (component instanceof Annotated.Field candidate
					&& wireName(candidate.name(), candidate.javaName()).equals(field.name())) {
				return candidate;
			}
		}
		return null;
	}

	private static Annotated.Group group(List<Annotated.Component> components, Token group) {
		for (Annotated.Component component : components) {
			if (component instanceof Annotated.Group candidate
					&& wireName(candidate.name(), candidate.javaName()).equals(group.name())) {
				return candidate;
			}
		}
		throw new IllegalStateException("no component for group " + group.name());
	}

	private static Annotated.Data data(List<Annotated.Component> components, Token data) {
		for (Annotated.Component component : components) {
			if (component instanceof Annotated.Data candidate
					&& wireName(candidate.name(), candidate.javaName()).equals(data.name())) {
				return candidate;
			}
		}
		throw new IllegalStateException("no component for data " + data.name());
	}

	private static Annotated.Field unmappedField(List<Annotated.Field> unmapped, Token field) {
		for (Annotated.Field candidate : unmapped) {
			if (wireName(candidate.name(), candidate.javaName()).equals(field.name())) {
				return candidate;
			}
		}
		throw new IllegalStateException("no component for field " + field.name());
	}

	private static Annotated.@Nullable Member member(Annotated.Composite composite, String wireName) {
		for (Annotated.Member member : composite.members()) {
			if (wireName(member).equals(wireName)) {
				return member;
			}
		}
		return null;
	}

	private static Annotated.ValidValue validValue(Annotated.Enum enumeration, String wireName) {
		for (Annotated.ValidValue value : enumeration.values()) {
			if (wireName(value.name(), value.javaName()).equals(wireName)) {
				return value;
			}
		}
		throw new IllegalStateException(enumeration.javaName() + " has no value " + wireName);
	}

	private static Annotated.Choice choice(Annotated.Set set, String wireName) {
		for (Annotated.Choice choice : set.choices()) {
			if (wireName(choice.name(), choice.javaName()).equals(wireName)) {
				return choice;
			}
		}
		throw new IllegalStateException(set.javaName() + " has no choice " + wireName);
	}

	// ---- what an annotation is written as

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
		return javaType instanceof Annotated.SetOf setOf ? setOf.declaration() : null;
	}

	/**
	 * A member's declaration: an inline one is its own, a ref's is what it names.
	 */
	private static Annotated.@Nullable Declaration declarationOf(Annotated.Member member) {
		return switch (member) {
			case Annotated.Type type -> null;
			case Annotated.Ref ref -> ref.value() != null ? ref.value() : declarationOf(ref.javaType());
			case Annotated.Enum enumeration -> enumeration;
			case Annotated.Set set -> set;
			case Annotated.Composite composite -> composite;
		};
	}

	/**
	 * A member's binding: an inline type's or a ref's; an inline enum, set or
	 * composite carries no annotation to name one.
	 */
	private static Annotated.@Nullable Binding bindingOf(Annotated.Member member) {
		return switch (member) {
			case Annotated.Type type -> type.binding();
			case Annotated.Ref ref -> ref.binding();
			case Annotated.Enum enumeration -> null;
			case Annotated.Set set -> null;
			case Annotated.Composite composite -> null;
		};
	}

	/** The component's type behind a member: an inline declaration is its own. */
	private static Annotated.JavaType javaTypeOf(Annotated.Member member) {
		return switch (member) {
			case Annotated.Type type -> {
				if (type.javaType() == null) {
					throw new IllegalStateException(type.javaName() + " is a member without a component");
				}
				yield type.javaType();
			}
			case Annotated.Ref ref -> ref.javaType();
			case Annotated.Enum enumeration -> new Annotated.Declared(enumeration);
			case Annotated.Set set -> new Annotated.SetOf(set);
			case Annotated.Composite composite -> new Annotated.Declared(composite);
		};
	}

	/** The declaration the mapping saw to, in the shape the token says. */
	private static <D extends Annotated.Declaration> D declared(
			Annotated.@Nullable Declaration declaration, Class<D> shape, String component
	) {
		if (shape.isInstance(declaration)) {
			return shape.cast(declaration);
		}
		throw new IllegalStateException(component + " is not a field of " + shape.getSimpleName());
	}

	/** The entry record's name as code names it; the mapping saw to the shape. */
	private static String entryRecord(Annotated.Group group) {
		Annotated.Binding binding = group.binding();
		Annotated.JavaType face = binding == null ? group.javaType() : binding.wire();
		if (face instanceof Annotated.ListOfRecord list) {
			return list.qualifiedName();
		}
		throw new IllegalStateException(group.javaName() + " is a group that is not a List of a record");
	}

	private static String javaName(Annotated.Member member) {
		return switch (member) {
			case Annotated.Type type -> type.javaName();
			case Annotated.Ref ref -> ref.javaName();
			case Annotated.Enum enumeration -> enumeration.javaName();
			case Annotated.Set set -> set.javaName();
			case Annotated.Composite composite -> composite.javaName();
		};
	}

	/** The qualified name of an unmapped enum field's declaration. */
	private static String javaName(Annotated.@Nullable Declaration declaration) {
		if (declaration instanceof Annotated.Enum enumeration) {
			return enumeration.qualifiedName();
		}
		throw new IllegalStateException("an unmapped enum field without its enum");
	}

	private static String wireName(Annotated.Member member) {
		return switch (member) {
			case Annotated.Type type -> wireName(type.name(), type.javaName());
			case Annotated.Ref ref -> wireName(ref.name(), ref.javaName());
			case Annotated.Enum enumeration -> wireName(enumeration.name(), enumeration.javaName());
			case Annotated.Set set -> wireName(set.name(), set.javaName());
			case Annotated.Composite composite -> wireName(composite.name(), composite.javaName());
		};
	}

	private static String wireName(String name, String javaName) {
		return name.isEmpty() ? javaName : name;
	}

	private static String simpleName(String qualifiedName) {
		return qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1);
	}

	private static boolean constant(Token field) {
		return field.encoding().presence() == Encoding.Presence.CONSTANT;
	}

	private static String lacking(String construct) {
		return "no codec for " + construct + " yet; set codecs = false on @SbeSchema";
	}
}
