package net.concini.sbebuddy.generator;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import uk.co.real_logic.sbe.PrimitiveType;
import uk.co.real_logic.sbe.generation.Generators;
import uk.co.real_logic.sbe.generation.java.JavaUtil;
import uk.co.real_logic.sbe.ir.Encoding;
import uk.co.real_logic.sbe.ir.GenerationUtil;
import uk.co.real_logic.sbe.ir.Ir;
import uk.co.real_logic.sbe.ir.Signal;
import uk.co.real_logic.sbe.ir.Token;

import net.concini.sbebuddy.generator.Faces.Absence;
import net.concini.sbebuddy.generator.Faces.Content;
import net.concini.sbebuddy.generator.Faces.Face;
import net.concini.sbebuddy.generator.Faces.Helper;
import net.concini.sbebuddy.generator.Faces.Shape;

/**
 * One message's IR tokens, walked the way {@code JavaGenerator} reads them and
 * joined with its record where one exists: a tree of blocks, groups, var-data
 * and fields, each with its token and its name, and where a component maps it
 * the {@link Faces} leaf it is. Every flyweight member the tree names comes
 * from {@link JavaUtil}. Each token meets its annotation once, by wire name,
 * and there the {@link FaceRules} apply, each problem on its node, whether or
 * not the schema wants codecs; a node whose component breaks a rule is left
 * out, and a construct the codec does not cover yet is a problem on the message
 * only when it does.
 */
final class Join {

	/** The members every header carries, which the message's flyweight writes. */
	private static final Set<String> STANDARD_HEADER_MEMBERS = Set
			.of("blockLength", "templateId", "schemaId", "version");

	/**
	 * One message joined: its header, its body, and the bindings and contexts its
	 * components use, each once, in the order the walk met them.
	 */
	record Message(Header header, Block block, List<Faces.Binding> bindings, List<Faces.Context> contexts) {

		Message {
			bindings = List.copyOf(bindings);
			contexts = List.copyOf(contexts);
		}
	}

	/**
	 * The header as the IR resolved it, the schema's {@code headerType} or the
	 * composite named {@code messageHeader}, joined as a composite is: {@code own}
	 * are its members other than the standard four, and {@code nulls} how each of
	 * them is written when no header is given, a constant needing nothing.
	 */
	record Header(Composite composite, List<Field> own, List<Field> nulls) {

		Header {
			own = List.copyOf(own);
			nulls = List.copyOf(nulls);
		}
	}

	/** A field, a group or var-data: what a block's constructor order holds. */
	sealed interface Node {
	}

	/**
	 * A message's or a group entry's block over its flyweights, named after
	 * {@code name}: its fields, then its groups, then its var-data, each in wire
	 * order; and the nodes the record's components map, in the order they are
	 * declared, which is the canonical constructor's.
	 */
	record Block(
			String encoder,
			String decoder,
			String name,
			List<Field> fields,
			List<Group> groups,
			List<Data> data,
			List<Node> constructorOrder
	) {

		Block {
			fields = List.copyOf(fields);
			groups = List.copyOf(groups);
			data = List.copyOf(data);
			constructorOrder = List.copyOf(constructorOrder);
		}
	}

	/**
	 * A field of a block or a member of a composite, named {@code property} on its
	 * flyweight; {@code type} is its type's token, the field's second, a member's
	 * own. {@code face} is null where no record maps the message, for a constant no
	 * component carries, and for a composite field the codec does not cover;
	 * {@code helpers} are the methods its shape calls, and {@code composite} the
	 * composite its shape goes through, joined once per message however many fields
	 * use it.
	 */
	record Field(
			Token token,
			Token type,
			String property,
			@Nullable Face face,
			List<Helper> helpers,
			@Nullable Composite composite
	) implements Node {

		Field {
			helpers = List.copyOf(helpers);
		}
	}

	/**
	 * A repeating group at {@code path}, which names its methods;
	 * {@code addedSince} is the flyweight's since-version method when the group was
	 * appended above the baseline, or null. {@code component}, the entry
	 * {@code record} and {@code bound} are null where no record maps the message.
	 */
	record Group(
			Token token,
			String property,
			String path,
			Block entry,
			@Nullable String addedSince,
			Annotated.@Nullable Group component,
			@Nullable String record,
			Faces.@Nullable Bound bound
	) implements Node {
	}

	/**
	 * Var-data at {@code path}, which names its methods; {@code addedSince} as on a
	 * group, and {@code lengthEncoder} the flyweight of its encoding, whose length
	 * type holds the maximum. {@code component}, {@code content} and {@code bound}
	 * are null where no record maps the message; {@code charset} is the constant
	 * for text in another encoding, or null; {@code helpers} are the methods its
	 * content calls.
	 */
	record Data(
			Token token,
			String property,
			String path,
			@Nullable String addedSince,
			String lengthEncoder,
			Annotated.@Nullable Data component,
			@Nullable Content content,
			@Nullable String charset,
			Faces.@Nullable Bound bound,
			List<Helper> helpers
	) implements Node {

		Data {
			helpers = List.copyOf(helpers);
		}
	}

	/**
	 * A composite type over its flyweights, named after {@code name}, whose record
	 * is {@code record}: its members in wire order, and those the record's
	 * components map in the order they are declared.
	 */
	record Composite(
			String encoder,
			String decoder,
			String name,
			String record,
			List<Field> members,
			List<Field> constructorOrder
	) {

		Composite {
			members = List.copyOf(members);
			constructorOrder = List.copyOf(constructorOrder);
		}
	}

	private final Ir ir;
	private final Annotated annotated;
	private final Annotated.@Nullable Message message;
	private final String flyweights;
	private final Map<Key, Helper> helpers = new HashMap<>();
	private final Map<String, Composite> composites = new HashMap<>();
	private final Map<String, String> bindings = new LinkedHashMap<>();
	private final Map<String, Faces.Context> contexts = new LinkedHashMap<>();
	private final List<Problem> problems;
	private final FaceRules faces;

	private Join(Ir ir, Annotated annotated, Annotated.@Nullable Message message, List<Problem> problems) {
		this.ir = ir;
		this.annotated = annotated;
		this.message = message;
		this.flyweights = ir.applicableNamespace();
		this.problems = problems;
		this.faces = new FaceRules(problems);
	}

	/**
	 * The message of {@code tokens} joined with {@code message}, its record, or
	 * with nothing where it has none: then every node has its token and its name
	 * and nothing else. Problems are added to {@code problems}, which an error
	 * among them makes the tree incomplete; {@code baseline} is the oldest version
	 * read.
	 */
	static Message join(
			Ir ir, Annotated annotated, int baseline, List<Token> tokens, Annotated.@Nullable Message message,
			List<Problem> problems
	) {
		Join join = new Join(ir, annotated, message, problems);
		Header header = join.header();
		String messageClass = JavaUtil.formatClassName(tokens.get(0).name());
		Owner owner = new Owner(
				join.flyweights + "." + messageClass + "Encoder", join.flyweights + "." + messageClass + "Decoder", "",
				Owner.Kind.MESSAGE, baseline
		);
		Block block = join.block(
				tokens, 1, message == null ? List.of() : message.components(),
				message == null ? List.of() : message.unmapped(), owner, tokens.get(0).name(), messageClass
		);
		List<Faces.Binding> fields = new ArrayList<>();
		join.bindings.forEach((name, type) -> fields.add(new Faces.Binding(type, name)));
		return new Message(header, block, fields, List.copyOf(join.contexts.values()));
	}

	private boolean failed() {
		return problems.stream().anyMatch(Problem::isError);
	}

	private void problem(Object node, String problem) {
		problems.add(new Problem(node, problem));
	}

	/**
	 * A problem of the codec's, on the message: what it lacks, or cannot name.
	 * Reported only when the schema wants codecs, since each says to turn them off,
	 * and only for a message with a record, which alone has a codec.
	 */
	private void codecProblem(String problem) {
		if (annotated.codecs() && message != null) {
			problems.add(new Problem(message, problem));
		}
	}

	/**
	 * Whose flyweights a block's accessors are on: the message's or a group's,
	 * whose decoders guard by version, or a composite's, which never does.
	 * {@code prefix} is the group's path or the composite's class, empty for the
	 * message, and {@code baseline} is the version below which nothing in the block
	 * is read.
	 */
	private record Owner(String encoder, String decoder, String prefix, Kind kind, int baseline) {

		enum Kind {
			MESSAGE, COMPOSITE, GROUP
		}

		/**
		 * A member's path, which names its helpers and its binding's context: a group
		 * entry's member after the group's path and {@code $}, a composite's after its
		 * class and {@code $$}. No SBE name holds a {@code $}, so {@code Fills$Price},
		 * {@code Fills$$Price} and {@code FillsPrice} are three members.
		 */
		String path(String property) {
			String member = Generators.toUpperFirstChar(property);
			return switch (kind) {
				case MESSAGE -> member;
				case GROUP -> prefix + "$" + member;
				case COMPOSITE -> prefix + "$$" + member;
			};
		}
	}

	/**
	 * A helper's identity: what it maps, the declaration's wire name or the path.
	 */
	private record Key(Class<? extends Helper> kind, String name) {
	}

	/**
	 * The helper of the key, made on its first use in the message and the same
	 * helper on every later one, added to what the node being joined uses.
	 */
	private void use(Key key, Supplier<Helper> helper, List<Helper> used) {
		Helper declared = helpers.get(key);
		if (declared == null) {
			declared = helper.get();
			helpers.put(key, declared);
		}
		used.add(declared);
	}

	// ---- the header

	/**
	 * The header, read as a composite is. The standard four are read and never
	 * written; every other member is written from a header or as its null value,
	 * and a constant is neither.
	 */
	private Header header() {
		List<Token> tokens = ir.headerStructure().tokens();
		String headerClass = JavaUtil.formatClassName(tokens.get(0).name());
		Composite composite = compositeBody(tokens, annotated.headerType(), headerClass);
		List<Field> own = new ArrayList<>();
		List<Field> nulls = new ArrayList<>();
		for (Field field : composite.members()) {
			Face face = field.face();
			if (face == null) {
				continue;
			}
			switch (face) {
				case Face.Mapped mapped -> {
					if (!STANDARD_HEADER_MEMBERS.contains(field.property())) {
						own.add(field);
						Shape shape = nullShape(mapped, field.property(), headerClass);
						if (shape != null) {
							nulls.add(
									new Field(
											field.token(), field.type(), field.property(), new Face.Null(shape),
											List.of(), null
									)
							);
						}
					}
				}
				case Face.Null unmapped -> {
					own.add(field);
					nulls.add(field);
				}
			}
		}
		return new Header(composite, own, nulls);
	}

	/**
	 * How a header member is written when no header is given: its null value, an
	 * array's in every element; a constant needs nothing.
	 */
	private @Nullable Shape nullShape(Face.Mapped mapped, String property, String headerClass) {
		return switch (mapped.shape()) {
			case Shape.Scalar scalar -> new Shape.Scalar(null);
			case Shape.Text text -> new Shape.Array(headerClass + "$$" + Generators.toUpperFirstChar(property));
			case Shape.Array array -> array;
			case Shape.Enum enumeration -> enumeration;
			case Shape.Set set -> set;
			case Shape.Constant constant -> null;
			case Shape.Composite composite -> {
				codecProblem(lacking("a composite in a header"));
				yield null;
			}
		};
	}

	// ---- a message's or a group entry's block

	/**
	 * The fields, then the groups, recursing into each, then the var-data, each met
	 * with its component by wire name, a field with none with its {@code unmapped}
	 * entry; a component the schema's {@code block} lacks is a problem. Without a
	 * record every node is met with nothing.
	 */
	private Block block(
			List<Token> tokens, int index, List<Annotated.Component> components, List<Annotated.Field> unmapped,
			Owner owner, String block, String name
	) {
		List<Token> fieldTokens = new ArrayList<>();
		List<Token> groupTokens = new ArrayList<>();
		List<Token> dataTokens = new ArrayList<>();
		int next = GenerationUtil.collectFields(tokens, index, fieldTokens);
		next = GenerationUtil.collectGroups(tokens, next, groupTokens);
		GenerationUtil.collectVarData(tokens, next, dataTokens);
		List<Field> fields = new ArrayList<>();
		List<Group> groups = new ArrayList<>();
		List<Data> data = new ArrayList<>();
		Map<Annotated.Component, Node> byComponent = new IdentityHashMap<>();
		Set<Annotated.Component> matched = Collections.newSetFromMap(new IdentityHashMap<>());
		for (int i = 0; i < fieldTokens.size(); i += fieldTokens.get(i).componentTokenCount()) {
			Token field = fieldTokens.get(i);
			List<Token> typeTokens = fieldTokens.subList(i + 1, i + 1 + fieldTokens.get(i + 1).componentTokenCount());
			Annotated.Field component = component(components, field);
			Field node;
			if (component == null) {
				node = unmapped(field, typeTokens, unmapped, owner);
			} else {
				matched.add(component);
				node = field(field, typeTokens, component, owner);
			}
			if (node != null) {
				fields.add(node);
				if (component != null) {
					byComponent.put(component, node);
				}
			}
		}
		for (int i = 0; i < groupTokens.size(); i += groupTokens.get(i).componentTokenCount()) {
			List<Token> groupOf = groupTokens.subList(i, i + groupTokens.get(i).componentTokenCount());
			Token token = groupOf.get(0);
			Annotated.Group group = group(components, token);
			if (group == null && message != null) {
				throw new IllegalStateException("the schema's " + block + " has a group no component carries");
			}
			if (group != null) {
				matched.add(group);
			}
			Group node = group(groupOf, group, owner, block + "." + token.name());
			if (node != null) {
				groups.add(node);
				if (group != null) {
					byComponent.put(group, node);
				}
			}
		}
		for (int i = 0; i < dataTokens.size(); i += dataTokens.get(i).componentTokenCount()) {
			List<Token> dataOf = dataTokens.subList(i, i + dataTokens.get(i).componentTokenCount());
			Token token = dataOf.get(0);
			Annotated.Data component = data(components, token);
			if (component == null && message != null) {
				throw new IllegalStateException("the schema's " + block + " has var-data no component carries");
			}
			if (component != null) {
				matched.add(component);
			}
			Data node = data(dataOf, component, owner);
			if (node != null) {
				data.add(node);
				if (component != null) {
					byComponent.put(component, node);
				}
			}
		}
		for (Annotated.Component component : components) {
			if (!matched.contains(component)) {
				problem(
						component, "the schema's " + block + " has no " + kind(component) + " named \""
								+ wireName(component) + "\""
				);
			}
		}
		return new Block(
				owner.encoder(), owner.decoder(), name, fields, groups, data, constructorOrder(components, byComponent)
		);
	}

	/**
	 * The canonical constructor takes the components as declared, which the layout
	 * may order differently from the wire; the block is addressed by offset, so
	 * either order reads it.
	 */
	private <C, N> List<N> constructorOrder(List<? extends C> components, Map<C, N> byComponent) {
		List<N> order = new ArrayList<>();
		for (C component : components) {
			N node = byComponent.get(component);
			if (node != null) {
				order.add(node);
			} else if (!failed()) {
				throw new IllegalStateException("a component with no member on the wire");
			}
		}
		return order;
	}

	/**
	 * A field of a message or a group, its binding handed the field's epoch and
	 * time unit beside what its type says.
	 */
	private @Nullable Field field(Token field, List<Token> typeTokens, Annotated.Field component, Owner owner) {
		if (!faces.field(component, typeTokens.get(0), canBeAbsent(field, owner))) {
			return null;
		}
		String name = component.javaName();
		String property = JavaUtil.formatPropertyName(field.name());
		Faces.Bound bound = null;
		Annotated.Binding binding = component.binding();
		if (binding != null) {
			bound = bound(
					binding, owner.path(property), name, typeTokens.get(0),
					field.encoding().epoch(), field.encoding().timeUnit(), field.encoding().presence()
			);
			if (bound == null) {
				return null;
			}
		}
		List<Helper> used = new ArrayList<>();
		Shape shape = shape(field, typeTokens, declarationOf(component), name, owner, used);
		if (shape == null) {
			return null;
		}
		return new Field(
				field, typeTokens.get(0), property,
				new Face.Mapped(
						name, shape, withoutNullValue(absence(field, component.javaType(), owner), shape, field, owner),
						bound
				),
				used, compositeOf(shape, typeTokens)
		);
	}

	/**
	 * A field no component carries, declared under {@code unmapped}: a constant
	 * needs nothing, anything else its null value. Without a record it is met with
	 * nothing.
	 */
	private Field unmapped(Token field, List<Token> typeTokens, List<Annotated.Field> unmapped, Owner owner) {
		String property = JavaUtil.formatPropertyName(field.name());
		if (message == null) {
			return new Field(field, typeTokens.get(0), property, null, List.of(), null);
		}
		Token type = typeTokens.get(0);
		if (type.signal() == Signal.BEGIN_COMPOSITE) {
			codecProblem(lacking("an unmapped field of a composite"));
			return new Field(field, typeTokens.get(0), property, null, List.of(), null);
		}
		if (constant(field)) {
			return new Field(field, typeTokens.get(0), property, null, List.of(), null);
		}
		Annotated.Field declared = unmappedField(unmapped, field);
		if (declared == null) {
			throw new IllegalStateException(field.name() + " is a field no component or unmapped entry carries");
		}
		Shape shape = switch (type.signal()) {
			case ENCODING -> unmappedEncoding(type, owner, property);
			case BEGIN_ENUM -> new Shape.Enum(
					JavaUtil.formatClassName(type.applicableTypeName()), javaName(declared.type())
			);
			case BEGIN_SET -> new Shape.Set(JavaUtil.formatClassName(type.applicableTypeName()));
			default -> throw new IllegalStateException(field.name() + " is a field of " + type.signal());
		};
		return new Field(field, type, property, new Face.Null(shape), List.of(), null);
	}

	/** A primitive takes its null value in one call, an array in every element. */
	private static Shape unmappedEncoding(Token type, Owner owner, String property) {
		return type.arrayLength() <= 1
				? new Shape.Scalar(null)
				: new Shape.Array(owner.path(property));
	}

	private @Nullable Group group(List<Token> tokens, Annotated.@Nullable Group group, Owner owner, String block) {
		Token token = tokens.get(0);
		String property = JavaUtil.formatPropertyName(token.name());
		String path = owner.path(property);
		String groupClass = JavaUtil.formatClassName(token.name());
		Owner entry = new Owner(
				owner.encoder() + "." + groupClass + "Encoder", owner.decoder() + "." + groupClass + "Decoder", path,
				Owner.Kind.GROUP, Math.max(owner.baseline(), token.version())
		);
		// sbe-tool names the group's static methods after its decoder class.
		String addedSince = token.version() > owner.baseline()
				? JavaUtil.formatPropertyName(groupClass + "Decoder") + "SinceVersion"
				: null;
		int index = 1 + tokens.get(1).componentTokenCount();
		if (group == null) {
			return new Group(
					token, property, path, block(tokens, index, List.of(), List.of(), entry, block, groupClass),
					addedSince, null, null, null
			);
		}
		if (!faces.group(group)) {
			return null;
		}
		Faces.Bound bound = null;
		Annotated.Binding binding = group.binding();
		if (binding != null) {
			bound = bound(binding, path, group.javaName(), null, null, null, null);
			if (bound == null) {
				return null;
			}
		}
		return new Group(
				token, property, path,
				block(tokens, index, group.components(), group.unmapped(), entry, block, groupClass), addedSince,
				group, entryRecord(group), bound
		);
	}

	/** Var-data, after the check its text needs. */
	private @Nullable Data data(List<Token> tokens, Annotated.@Nullable Data data, Owner owner) {
		Token token = tokens.get(0);
		String property = JavaUtil.formatPropertyName(token.name());
		String path = owner.path(property);
		String addedSince = token.version() > owner.baseline() ? property + "SinceVersion" : null;
		String lengthEncoder = flyweights + "." + JavaUtil.formatClassName(tokens.get(1).applicableTypeName())
				+ "Encoder";
		if (data == null) {
			return new Data(token, property, path, addedSince, lengthEncoder, null, null, null, null, List.of());
		}
		if (!faces.data(data, tokens.get(1), tokens.get(3))) {
			return null;
		}
		List<Helper> used = new ArrayList<>();
		Content content = content(tokens.get(3), data);
		String charset = null;
		switch (content) {
			case ASCII -> use(new Key(Helper.Ascii.class, ""), Helper.Ascii::new, used);
			case UTF_8 -> use(new Key(Helper.Utf8.class, ""), Helper.Utf8::new, used);
			case BYTES -> use(new Key(Helper.Bytes.class, ""), Helper.Bytes::new, used);
			case ENCODED -> {
				charset = charset(characterEncoding(tokens.get(3), data.javaName()), false, used);
				if (charset == null) {
					return null;
				}
			}
		}
		Faces.Bound bound = null;
		Annotated.Binding binding = data.binding();
		if (binding != null) {
			Token varData = tokens.get(3);
			bound = bound(
					binding, path, data.javaName(), content == Content.BYTES ? null : varData,
					tokens.get(0).encoding().epoch(), tokens.get(0).encoding().timeUnit(), null
			);
			if (bound == null) {
				return null;
			}
		}
		return new Data(token, property, path, addedSince, lengthEncoder, data, content, charset, bound, used);
	}

	/**
	 * Decided by the face, which the rules tied to the varData type: a String is
	 * text in the type's encoding, which the codec checks in ASCII, counts in UTF-8
	 * and encodes to count in any other.
	 */
	private static Content content(Token varData, Annotated.Data data) {
		Annotated.Binding binding = data.binding();
		Annotated.JavaType face = binding == null ? data.javaType() : binding.wire();
		if (!(face instanceof Annotated.Text)) {
			return Content.BYTES;
		}
		String encoding = characterEncoding(varData, data.javaName());
		if (JavaUtil.isAsciiEncoding(encoding)) {
			return Content.ASCII;
		}
		return JavaUtil.isUtf8Encoding(encoding) ? Content.UTF_8 : Content.ENCODED;
	}

	private static String characterEncoding(Token text, String component) {
		String encoding = text.encoding().characterEncoding();
		if (encoding == null) {
			throw new IllegalStateException(component + " is text without an encoding");
		}
		return encoding;
	}

	/**
	 * The constant for text in an encoding other than ASCII and UTF-8, declared
	 * once, with the method that encodes through it. Null, with the problem, for an
	 * encoding the JDK does not know or cannot write, and for a char array one that
	 * writes a zero byte inside a character: sbe-tool's flyweight reads a char
	 * array up to its first zero byte.
	 */
	private @Nullable String charset(String encoding, boolean charArray, List<Helper> used) {
		Charset charset;
		try {
			charset = Charset.forName(encoding);
		} catch (IllegalArgumentException e) {
			// Charset's own exceptions for a name it does not know, or cannot read.
			codecProblem(noCodec("text in " + encoding + ": the JDK knows no such encoding"));
			return null;
		}
		if (!charset.canEncode()) {
			codecProblem(noCodec("text in " + encoding + ": the JDK can read it but not write it"));
			return null;
		}
		if (charArray && containsZero("A".getBytes(charset))) {
			codecProblem(
					noCodec(
							"a char array in " + encoding
									+ ": it writes zero bytes inside a character, and sbe-tool's flyweight ends a char array at its first zero byte"
					)
			);
			return null;
		}
		String constant = charset.name().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "_") + "_CHARSET";
		use(new Key(Helper.Encoded.class, ""), Helper.Encoded::new, used);
		use(
				new Key(Helper.CharsetConstant.class, constant),
				() -> new Helper.CharsetConstant(constant, literal(encoding)), used
		);
		return constant;
	}

	private static boolean containsZero(byte[] bytes) {
		for (byte b : bytes) {
			if (b == 0) {
				return true;
			}
		}
		return false;
	}

	// ---- how a field or member reaches the wire

	/**
	 * An optional field whose face has no null value of its own, a composite, a
	 * set, an array or a string, leaves null to its binding; appended above the
	 * body's baseline, it is still null when the message predates it, since the
	 * flyweight then has nothing to read.
	 */
	private static Absence withoutNullValue(Absence absence, Shape shape, Token field, Owner owner) {
		boolean noNullValue = shape instanceof Shape.Composite || shape instanceof Shape.Set
				|| shape instanceof Shape.Array || shape instanceof Shape.Text;
		if (absence != Absence.OPTIONAL || !noNullValue) {
			return absence;
		}
		return field.version() > owner.baseline() ? Absence.ADDED_NO_NULL_VALUE : Absence.NO_NULL_VALUE;
	}

	/**
	 * Decided by the wire and the component: a constant or a primitive is always
	 * there, the null value makes a field optional, and a field appended above the
	 * body's baseline is absent from older messages. A composite's member is never
	 * newer than its composite, which the mapping sees to.
	 */
	private static Absence absence(Token field, Annotated.JavaType javaType, Owner owner) {
		boolean primitive = javaType instanceof Annotated.Primitive plain && !plain.boxed();
		if (!canBeAbsent(field, owner)) {
			return primitive ? Absence.NONE : Absence.REQUIRED;
		}
		return field.encoding().presence() == Encoding.Presence.OPTIONAL ? Absence.OPTIONAL : Absence.ADDED;
	}

	/**
	 * Whether the wire can leave a field or member absent: optional, or added above
	 * the body's baseline, which a composite's member never is, since a composite
	 * cannot be extended; a constant carries no bytes and is never absent.
	 */
	private static boolean canBeAbsent(Token field, Owner owner) {
		Encoding.Presence presence = field.encoding().presence();
		if (presence == Encoding.Presence.CONSTANT) {
			return false;
		}
		return presence == Encoding.Presence.OPTIONAL
				|| owner.kind() != Owner.Kind.COMPOSITE && field.version() > owner.baseline();
	}

	private @Nullable Shape shape(
			Token field, List<Token> typeTokens, Annotated.@Nullable Declaration declaration, String component,
			Owner owner, List<Helper> used
	) {
		Token type = typeTokens.get(0);
		String property = JavaUtil.formatPropertyName(field.name());
		if (constant(field)) {
			return constant(field, typeTokens, declaration, component, used);
		}
		return switch (type.signal()) {
			case ENCODING -> encoding(type, owner, property, component, used);
			case BEGIN_ENUM -> enumeration(typeTokens, declared(declaration, Annotated.Enum.class, component), used);
			case BEGIN_SET -> set(typeTokens, declared(declaration, Annotated.Set.class, component), used);
			case BEGIN_COMPOSITE -> composite(typeTokens, declared(declaration, Annotated.Composite.class, component));
			default -> throw new IllegalStateException("a field of " + type.signal());
		};
	}

	/** The composite a shape goes through, joined by {@link #composite}. */
	private @Nullable Composite compositeOf(Shape shape, List<Token> typeTokens) {
		return shape instanceof Shape.Composite ? composites.get(typeTokens.get(0).applicableTypeName()) : null;
	}

	/**
	 * A scalar through its accessor, a char string through the flyweight's String
	 * form in ASCII and as encoded bytes in any other encoding, and any other array
	 * through a pair of its own.
	 */
	private @Nullable Shape encoding(Token type, Owner owner, String property, String component, List<Helper> used) {
		PrimitiveType primitive = type.encoding().primitiveType();
		if (type.arrayLength() <= 1) {
			// The box is the one name that neither the IR nor JavaUtil holds.
			return new Shape.Scalar(
					primitive == PrimitiveType.FLOAT ? "Float" : primitive == PrimitiveType.DOUBLE ? "Double" : null
			);
		}
		if (primitive == PrimitiveType.CHAR) {
			String encoding = characterEncoding(type, component);
			String bulk = Generators.toUpperFirstChar(property);
			if (JavaUtil.isAsciiEncoding(encoding)) {
				use(new Key(Helper.Ascii.class, ""), Helper.Ascii::new, used);
				return new Shape.Text(null, bulk);
			}
			String charset = charset(encoding, true, used);
			return charset == null ? null : new Shape.Text(charset, bulk);
		}
		String field = owner.path(property);
		use(
				new Key(Helper.ArrayPair.class, field),
				() -> new Helper.ArrayPair(
						field, owner.encoder(), owner.decoder(), property, Generators.toUpperFirstChar(property),
						component, JavaUtil.javaTypeName(primitive), primitive == PrimitiveType.UINT8
				), used
		);
		return new Shape.Array(field);
	}

	/**
	 * The wire value is read raw and mapped by the valid values' text, never
	 * through the flyweight's enum, so an unknown value is ours to decide.
	 */
	private Shape.Enum enumeration(List<Token> tokens, Annotated.Enum enumeration, List<Helper> used) {
		String enumClass = JavaUtil.formatClassName(tokens.get(0).applicableTypeName());
		PrimitiveType primitive = tokens.get(0).encoding().primitiveType();
		use(new Key(Helper.EnumPair.class, tokens.get(0).applicableTypeName()), () -> {
			List<Helper.EnumValue> values = new ArrayList<>();
			Set<String> wireValues = new HashSet<>();
			for (Token value : tokens) {
				if (value.signal() == Signal.VALID_VALUE) {
					wireValues.add(value.name());
					Annotated.ValidValue constant = validValue(enumeration, value.name());
					if (constant == null) {
						problem(
								enumeration, enumeration.javaName() + " has no constant for the schema's value \""
										+ value.name() + "\""
						);
						continue;
					}
					values.add(
							new Helper.EnumValue(
									constant.javaName(), JavaUtil.formatForJavaKeyword(value.name()),
									JavaUtil.generateLiteral(primitive, value.encoding().constValue().toString())
							)
					);
				}
			}
			for (Annotated.ValidValue value : enumeration.values()) {
				String wireName = wireName(value.name(), value.javaName());
				if (!wireValues.contains(wireName)) {
					problem(
							value, "the schema's " + tokens.get(0).applicableTypeName() + " has no value named \""
									+ wireName + "\""
					);
				}
			}
			return new Helper.EnumPair(
					enumClass, enumeration.qualifiedName(), JavaUtil.javaTypeName(primitive), values,
					enumeration.unknownValue()
			);
		}, used);
		return new Shape.Enum(enumClass, enumeration.qualifiedName());
	}

	private Shape.Set set(List<Token> tokens, Annotated.Set set, List<Helper> used) {
		String setClass = JavaUtil.formatClassName(tokens.get(0).applicableTypeName());
		use(new Key(Helper.SetPair.class, tokens.get(0).applicableTypeName()), () -> {
			List<Helper.Choice> choices = new ArrayList<>();
			Set<String> wireChoices = new HashSet<>();
			for (Token choice : tokens) {
				if (choice.signal() == Signal.CHOICE) {
					wireChoices.add(choice.name());
					Annotated.Choice constant = choice(set, choice.name());
					if (constant == null) {
						problem(
								set, set.javaName() + " has no constant for the schema's choice \"" + choice.name()
										+ "\""
						);
						continue;
					}
					choices.add(
							new Helper.Choice(
									JavaUtil.formatPropertyName(choice.name()), constant.javaName(),
									choice.encoding().constValue().toString()
							)
					);
				}
			}
			for (Annotated.Choice choice : set.choices()) {
				String wireName = wireName(choice.name(), choice.javaName());
				if (!wireChoices.contains(wireName)) {
					problem(
							choice, "the schema's " + tokens.get(0).applicableTypeName() + " has no choice named \""
									+ wireName + "\""
					);
				}
			}
			return new Helper.SetPair(setClass, set.qualifiedName(), choices);
		}, used);
		return new Shape.Set(setClass);
	}

	/**
	 * A composite goes through the pair of its type, joined once per message, the
	 * first time a field uses it.
	 */
	private Shape.Composite composite(List<Token> tokens, Annotated.Composite composite) {
		String compositeClass = JavaUtil.formatClassName(tokens.get(0).applicableTypeName());
		String typeName = tokens.get(0).applicableTypeName();
		if (!composites.containsKey(typeName)) {
			composites.put(typeName, compositeBody(tokens, composite, compositeClass));
		}
		return new Shape.Composite(compositeClass);
	}

	/**
	 * A composite's members with the shapes a field has, over the composite's
	 * flyweights.
	 */
	private Composite compositeBody(List<Token> tokens, Annotated.Composite composite, String compositeClass) {
		Owner owner = new Owner(
				flyweights + "." + compositeClass + "Encoder", flyweights + "." + compositeClass + "Decoder",
				compositeClass, Owner.Kind.COMPOSITE, 0
		);
		List<Field> members = new ArrayList<>();
		Map<Annotated.Member, Field> byMember = new IdentityHashMap<>();
		Set<Annotated.Member> matched = Collections.newSetFromMap(new IdentityHashMap<>());
		for (int i = 1; i < tokens.size() - 1; i += tokens.get(i).componentTokenCount()) {
			Token token = tokens.get(i);
			List<Token> memberTokens = tokens.subList(i, i + token.componentTokenCount());
			String property = JavaUtil.formatPropertyName(token.name());
			Annotated.Member member = member(composite, token.name());
			if (member == null) {
				Face face = constant(token) ? null : new Face.Null(unmappedEncoding(token, owner, property));
				members.add(new Field(token, token, property, face, List.of(), null));
				continue;
			}
			matched.add(member);
			// An inline declaration is its own component's type, so it always fits.
			boolean fits = switch (member) {
				case Annotated.Type type -> faces.member(type, token, canBeAbsent(token, owner));
				case Annotated.Ref ref -> faces.ref(ref, token);
				case Annotated.Enum enumeration -> true;
				case Annotated.Set set -> true;
				case Annotated.Composite nested -> true;
			};
			if (!fits) {
				continue;
			}
			String name = javaName(member);
			Faces.Bound bound = null;
			Annotated.Binding binding = bindingOf(member);
			if (binding != null) {
				// A member has no epoch or time unit: the schema gives them to fields.
				bound = bound(
						binding, owner.path(property), name, token, null, null,
						token.encoding().presence()
				);
				if (bound == null) {
					continue;
				}
			}
			List<Helper> used = new ArrayList<>();
			Shape shape = shape(token, memberTokens, declarationOf(member), name, owner, used);
			if (shape != null) {
				Field field = new Field(
						token, token, property,
						new Face.Mapped(name, shape, absence(token, javaTypeOf(member), owner), bound),
						used, compositeOf(shape, memberTokens)
				);
				members.add(field);
				byMember.put(member, field);
			}
		}
		for (Annotated.Member member : composite.members()) {
			if (!matched.contains(member)) {
				problem(
						member, "the schema's " + tokens.get(0).applicableTypeName() + " has no member named \""
								+ wireName(member) + "\""
				);
			}
		}
		return new Composite(
				owner.encoder(), owner.decoder(), compositeClass, composite.qualifiedName(), members,
				constructorOrder(composite.members(), byMember)
		);
	}

	/**
	 * A constant carries no bytes: it is read as its shape would be, and checked on
	 * the way out against the flyweight's constant, or for an enum against the
	 * record's constant the valueRef names.
	 */
	private @Nullable Shape constant(
			Token field, List<Token> typeTokens, Annotated.@Nullable Declaration declaration, String component,
			List<Helper> used
	) {
		Token type = typeTokens.get(0);
		return switch (type.signal()) {
			case ENCODING -> {
				Encoding encoding = type.encoding();
				boolean text = encoding.primitiveType() == PrimitiveType.CHAR
						&& encoding.constValue().byteArrayValue(PrimitiveType.CHAR).length > 1;
				// A constant is only compared, through the flyweight's String form.
				Shape read = text
						? new Shape.Text(null, Generators.toUpperFirstChar(JavaUtil.formatPropertyName(field.name())))
						: new Shape.Scalar(null);
				yield new Shape.Constant(read, null);
			}
			case BEGIN_ENUM -> {
				Annotated.Enum enumeration = declared(declaration, Annotated.Enum.class, component);
				String reference = field.encoding().constValue().toString();
				String valueName = reference.substring(reference.indexOf('.') + 1);
				Annotated.ValidValue constant = validValue(enumeration, valueName);
				if (constant == null) {
					problem(
							enumeration,
							enumeration.javaName() + " has no constant for the schema's value \"" + valueName
									+ "\""
					);
					yield null;
				}
				yield new Shape.Constant(enumeration(typeTokens, enumeration, used), constant.javaName());
			}
			default -> throw new IllegalStateException("a constant field of " + type.signal());
		};
	}

	/**
	 * The field for the binding, named after the class, {@code priceBinding} for
	 * {@code Price} and declared on first use, and the component's context, named
	 * after its path, {@code legsPriceContext}, from its type's token: the
	 * primitive of a scalar, an array, an enum or a set, none for a composite, and
	 * a {@code char} array's encoding. Null, with the problem, when another class
	 * of the binding's simple name already has its field.
	 */
	private Faces.@Nullable Bound bound(
			Annotated.Binding binding, String path, String component, @Nullable Token type, @Nullable String epoch,
			@Nullable String timeUnit, Encoding.@Nullable Presence presence
	) {
		String simpleName = simpleName(binding.qualifiedName());
		String name = Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
		if (!name.endsWith("Binding")) {
			name += "Binding";
		}
		String declared = bindings.putIfAbsent(name, binding.qualifiedName());
		if (declared != null && !declared.equals(binding.qualifiedName())) {
			codecProblem(
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
				new Faces.Context(
						context, literal(component),
						primitive == null ? "null" : "net.concini.sbebuddy.PrimitiveType." + primitive.name(),
						literal(characterEncoding), literal(epoch), literal(timeUnit),
						presence == null ? "null" : "net.concini.sbebuddy.Presence." + presence.name()
				)
		);
		return new Faces.Bound(name, context);
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

	private static Annotated.@Nullable Group group(List<Annotated.Component> components, Token group) {
		for (Annotated.Component component : components) {
			if (component instanceof Annotated.Group candidate
					&& wireName(candidate.name(), candidate.javaName()).equals(group.name())) {
				return candidate;
			}
		}
		return null;
	}

	private static Annotated.@Nullable Data data(List<Annotated.Component> components, Token data) {
		for (Annotated.Component component : components) {
			if (component instanceof Annotated.Data candidate
					&& wireName(candidate.name(), candidate.javaName()).equals(data.name())) {
				return candidate;
			}
		}
		return null;
	}

	private static Annotated.@Nullable Field unmappedField(List<Annotated.Field> unmapped, Token field) {
		for (Annotated.Field candidate : unmapped) {
			if (wireName(candidate.name(), candidate.javaName()).equals(field.name())) {
				return candidate;
			}
		}
		return null;
	}

	private static String wireName(Annotated.Component component) {
		return switch (component) {
			case Annotated.Field field -> wireName(field.name(), field.javaName());
			case Annotated.Group group -> wireName(group.name(), group.javaName());
			case Annotated.Data data -> wireName(data.name(), data.javaName());
		};
	}

	private static String kind(Annotated.Component component) {
		return switch (component) {
			case Annotated.Field field -> "field";
			case Annotated.Group group -> "group";
			case Annotated.Data data -> "data";
		};
	}

	private static Annotated.@Nullable Member member(Annotated.Composite composite, String wireName) {
		for (Annotated.Member member : composite.members()) {
			if (wireName(member).equals(wireName)) {
				return member;
			}
		}
		return null;
	}

	private static Annotated.@Nullable ValidValue validValue(Annotated.Enum enumeration, String wireName) {
		for (Annotated.ValidValue value : enumeration.values()) {
			if (wireName(value.name(), value.javaName()).equals(wireName)) {
				return value;
			}
		}
		return null;
	}

	private static Annotated.@Nullable Choice choice(Annotated.Set set, String wireName) {
		for (Annotated.Choice choice : set.choices()) {
			if (wireName(choice.name(), choice.javaName()).equals(wireName)) {
				return choice;
			}
		}
		return null;
	}

	// ---- what an annotation is written as

	/**
	 * A field's declaration, named by {@code type} or as the type of what reaches
	 * the wire, the component's own or its binding's; null for a primitive.
	 */
	private static Annotated.@Nullable Declaration declarationOf(Annotated.Field field) {
		if (field.type() != null) {
			return field.type();
		}
		Annotated.Binding binding = field.binding();
		return declarationOf(binding == null ? field.javaType() : binding.wire());
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
			case Annotated.Ref ref -> ref.value() != null
					? ref.value()
					: declarationOf(ref.binding() == null ? ref.javaType() : ref.binding().wire());
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

	/** The name on the wire: the annotation's, or else the Java name. */
	static String wireName(String name, String javaName) {
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

	private static String noCodec(String construct) {
		return "no codec for " + construct + "; set codecs = false on @SbeSchema";
	}
}
