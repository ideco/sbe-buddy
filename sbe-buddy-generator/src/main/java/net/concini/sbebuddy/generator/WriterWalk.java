package net.concini.sbebuddy.generator;

import static net.concini.sbebuddy.generator.FlyweightWalk.constant;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import uk.co.real_logic.sbe.PrimitiveType;
import uk.co.real_logic.sbe.generation.Generators;
import uk.co.real_logic.sbe.generation.java.JavaUtil;
import uk.co.real_logic.sbe.ir.Encoding;
import uk.co.real_logic.sbe.ir.Ir;
import uk.co.real_logic.sbe.ir.Signal;
import uk.co.real_logic.sbe.ir.Token;

import net.concini.sbebuddy.generator.FlyweightModel.Parameter;
import net.concini.sbebuddy.generator.WriterModel.Field;
import net.concini.sbebuddy.generator.WriterModel.Guard;
import net.concini.sbebuddy.generator.WriterModel.Implementation;
import net.concini.sbebuddy.generator.WriterModel.Method;
import net.concini.sbebuddy.generator.WriterModel.NullWrite;
import net.concini.sbebuddy.generator.WriterModel.Nulls;
import net.concini.sbebuddy.generator.WriterModel.Signature;
import net.concini.sbebuddy.generator.WriterModel.Site;
import net.concini.sbebuddy.generator.WriterModel.Stage;
import net.concini.sbebuddy.generator.WriterModel.Then;

/**
 * The writers of a schema as {@link WriterModel}s: a message's typestate, laid
 * out from its {@link Join} in wire order, and the sub-chain of each composite
 * and set a field opens. A block's required fields are a stage each, in wire
 * order; its optional fields and its first group or var-data are the stage
 * where the block is complete; each group is a stage with {@code entry()} and
 * {@code end()}, and what follows a group or var-data is a stage named after
 * it, down to {@code length()}. An entry complete returns to its group. A name
 * the writer would take twice is a problem on the node that takes it second.
 */
final class WriterWalk {

	private static final String ROOT_BLOCK = "RootBlock";

	private static final String BEFORE_ROOT_BLOCK = "BEFORE_ROOT_BLOCK";

	/** Names a message's writer declares or uses from java.lang. */
	private static final List<String> RESERVED = List
			.of("At", "String", "CharSequence", "Override", "IllegalStateException");

	/**
	 * Names a sub-chain declares or uses: its type parameter, its chain,
	 * java.lang's.
	 */
	private static final List<String> SUB_RESERVED = List
			.of("N", "Chain", "String", "CharSequence", "Override", "IllegalStateException");

	private static final Parameter VALUE_STRING = new Parameter("String", "value");

	private static final Parameter VALUE_CHARS = new Parameter("CharSequence", "value");

	private static final Parameter SRC_OFFSET = new Parameter("int", "srcOffset");

	private static final Parameter LENGTH = new Parameter("int", "length");

	private final Ir ir;
	private final String packageName;
	private final String flyweights;
	private final String writer;
	private final Object node;
	private final List<Problem> problems;
	private final Set<String> composites;
	private final Set<String> sets;
	private final Set<String> classes = new HashSet<>();
	private final Set<String> constants = new HashSet<>();
	private final Map<String, Declared> stages = new LinkedHashMap<>();
	private final Map<String, List<String>> parents = new HashMap<>();
	private final List<String> positions = new ArrayList<>();
	private final List<Builder> implementations = new ArrayList<>();
	private final List<Field> encoders = new ArrayList<>();
	private final Map<String, Nulls> nulls = new LinkedHashMap<>();

	private WriterWalk(
			Ir ir, String packageName, String writer, Object node, List<Problem> problems, Set<String> composites,
			Set<String> sets
	) {
		this.ir = ir;
		this.packageName = packageName;
		this.flyweights = ir.applicableNamespace();
		this.writer = writer;
		this.node = node;
		this.problems = problems;
		this.composites = composites;
		this.sets = sets;
	}

	/**
	 * The writer of the message of {@code tokens}, or null with an error among the
	 * problems added to {@code problems}. {@code message} is its record, or null;
	 * {@code schema} is the node a problem lands on where there is none. The type
	 * names of the composites and sets its steps open are added to
	 * {@code composites} and {@code sets}, in the order met.
	 */
	static WriterModel.@Nullable Message walk(
			Ir ir, Annotated annotated, int baseline, List<Token> tokens, Annotated.@Nullable Message message,
			Object schema, List<Problem> problems, Set<String> composites, Set<String> sets
	) {
		Join.Message joined = Join.join(ir, annotated, baseline, tokens, message, new ArrayList<>());
		String messageClass = JavaUtil.formatClassName(tokens.get(0).name());
		List<Problem> found = new ArrayList<>();
		WriterWalk walk = new WriterWalk(
				ir, annotated.packageName(), messageClass + "Writer", message == null ? schema : message, found,
				composites, sets
		);
		walk.classes.add(walk.writer);
		walk.classes.addAll(RESERVED);
		walk.classes.addAll(List.of(ROOT_BLOCK, ROOT_BLOCK + "Stage"));
		walk.constants.addAll(List.of(BEFORE_ROOT_BLOCK, constant(ROOT_BLOCK)));
		walk.positions.add(BEFORE_ROOT_BLOCK);
		Level root = new Level(
				ROOT_BLOCK, "", constant(ROOT_BLOCK), ROOT_BLOCK + "Stage", "rootBlockStage", "encoder",
				joined.block().encoder(), null
		);
		Target first = walk.level(
				joined.block(), root, message == null ? List.of() : message.components(),
				message == null ? List.of() : message.unmapped()
		);
		List<NullWrite> header = walk.header();
		problems.addAll(found);
		if (found.stream().anyMatch(Problem::isError)) {
			return null;
		}
		List<Stage> stages = new ArrayList<>();
		walk.stages.forEach(
				(name, declared) -> stages.add(
						new Stage(
								declared.kind(), declared.subject(), name, walk.parents.getOrDefault(name, List.of()),
								declared.methods()
						)
				)
		);
		return new WriterModel.Message(
				walk.packageName, walk.flyweights, tokens.get(0).name(), messageClass, walk.writer,
				JavaUtil.formatClassName(ir.headerStructure().tokens().get(0).name()), first.stage(), stages,
				walk.positions, walk.implementations.stream().map(Builder::build).toList(), walk.encoders, header,
				List.copyOf(walk.nulls.values())
		);
	}

	/**
	 * A block of the message, the root block or a group's entry: {@code name}
	 * prefixes its stages and names it in a refusal, {@code path} is the group's
	 * wire path, empty for the root block, {@code position} is where the writer
	 * stands inside it, {@code field} holds its object of class
	 * {@code implementation}, and {@code flyweight} its encoder, of the class
	 * {@code encoder}. An entry's {@code group} is where it returns.
	 */
	private record Level(
			String name,
			String path,
			String position,
			String implementation,
			String field,
			String flyweight,
			String encoder,
			@Nullable Parent group
	) {
	}

	/**
	 * The group an entry returns to: its stage, the field holding its object, its
	 * position, and what its {@code end()} leads to.
	 */
	private record Parent(String stage, String field, String position, Target after) {
	}

	/** A stage and the field holding the object behind it. */
	private record Target(String stage, String field) {
	}

	/**
	 * Lays out a block's stages, its groups' recursively, and returns the stage the
	 * block starts at. The block's object implements its fields' stages, the stage
	 * where it is complete and the stages after each of its groups and var-data;
	 * each group has an object of its own.
	 */
	private Target level(
			Join.Block block, Level level, List<Annotated.Component> components, List<Annotated.Field> unmapped
	) {
		Builder implementation = new Builder(level.implementation(), level.field());
		implementations.add(implementation);
		positions.add(level.position());
		List<Join.Field> required = new ArrayList<>();
		List<Join.Field> optional = new ArrayList<>();
		List<NullWrite> writes = new ArrayList<>();
		for (Join.Field field : block.fields()) {
			if (isConstant(field.token())) {
				continue;
			}
			NullWrite write = nullWrite(field.type(), field.property());
			if (write == null) {
				continue;
			}
			writes.add(write);
			if (!writable(field.type())) {
				continue;
			}
			if (field.token().encoding().presence() == Encoding.Presence.OPTIONAL) {
				optional.add(field);
			} else {
				required.add(field);
			}
		}
		nulls.put(level.encoder(), new Nulls(level.encoder(), writes));
		int followers = block.groups().size() + block.data().size();
		Parent group = level.group();
		boolean complete = group == null || !optional.isEmpty() || followers > 0;
		List<String> names = new ArrayList<>();
		for (Join.Field field : required) {
			String name = level.name() + JavaUtil.formatClassName(field.token().name());
			claimClass(carrier(field, components, unmapped), "field", field.token().name(), name);
			names.add(name);
			implementation.stages.add(name);
		}
		// Past the required fields: the block complete, else the group it returns to.
		Target filled = complete || group == null
				? new Target(level.name(), level.field())
				: new Target(group.stage(), group.field());
		Target first = names.isEmpty() ? filled : new Target(names.get(0), level.field());
		Guard guard = new Guard.Current(level.position(), level.position(), level.name());
		for (int i = 0; i < required.size(); i++) {
			Join.Field field = required.get(i);
			Target next = i + 1 < required.size() ? new Target(names.get(i + 1), level.field()) : filled;
			stage(names.get(i), Stage.Kind.FIELD, path(level.path(), field.token().name()));
			steps(
					implementation, names.get(i), field.type(), field.property(), level.flyweight(), level.encoder(),
					next.stage(), guard, new Then.Return(object(next.field(), implementation)), false,
					carrier(field, components, unmapped), "field", field.token().name()
			);
		}
		if (!complete) {
			return first;
		}
		stage(level.name(), group == null ? Stage.Kind.ROOT_BLOCK : Stage.Kind.ENTRY, level.path());
		implementation.stages.add(level.name());
		if (group != null && followers == 0) {
			parents.put(level.name(), List.of(group.stage()));
			add(
					implementation, null, new Method.Delegate(signature(first.stage(), "entry"), group.field()), node,
					"", ""
			);
			add(
					implementation, null, new Method.Delegate(signature(group.after().stage(), "end"), group.field()),
					node, "", ""
			);
		}
		for (Join.Field field : optional) {
			steps(
					implementation, level.name(), field.type(), field.property(), level.flyweight(),
					level.encoder(), level.name(), guard, new Then.Return("this"), false,
					carrier(field, components, unmapped), "field", field.token().name()
			);
		}
		String stage = level.name();
		String position = level.position();
		int index = 0;
		for (Join.Group one : block.groups()) {
			index++;
			After after = after(
					one.component() == null ? node : one.component(), "group", one.token().name(), index == followers,
					level, implementation
			);
			group(one, level, implementation, stage, position, after);
			stage = after.target().stage();
			position = after.position();
		}
		for (Join.Data one : block.data()) {
			index++;
			After after = after(
					one.component() == null ? node : one.component(), "data", one.token().name(), index == followers,
					level, implementation
			);
			data(one, level, implementation, stage, position, after);
			stage = after.target().stage();
			position = after.position();
		}
		if (group == null) {
			add(
					implementation, stage,
					new Method.Length(signature("int", "length"), new Guard.Current(position, position, stage)),
					node, "", ""
			);
		}
		return first;
	}

	/**
	 * What follows a group or var-data: its target, the writer's position there,
	 * and whether it is a stage of its own, declared once the group's entries are.
	 */
	private record After(Target target, String position, boolean own, String subject) {
	}

	/**
	 * The stage after a group or var-data: named after it, on the block's object,
	 * unless it is the last of an entry, whose group then takes over.
	 */
	private After after(Object node, String kind, String wireName, boolean last, Level level, Builder implementation) {
		Parent group = level.group();
		String subject = path(level.path(), wireName);
		if (last && group != null) {
			return new After(new Target(group.stage(), group.field()), group.position(), false, subject);
		}
		String name = "After" + JavaUtil.formatClassName(wireName);
		claimClass(node, kind, wireName, name);
		claimConstant(node, kind, wireName, constant(name));
		implementation.stages.add(name);
		return new After(new Target(name, level.field()), constant(name), true, subject);
	}

	/**
	 * A group opened from the stage {@code stage} at {@code position}: its own
	 * stage and object, then its entry as a block of its own, then the stage after
	 * it, declared in that order.
	 */
	private void group(Join.Group group, Level level, Builder from, String stage, String position, After after) {
		String wireName = group.token().name();
		Object node = group.component() == null ? this.node : group.component();
		String name = JavaUtil.formatClassName(wireName);
		String entry = name + "Entry";
		claimClass(node, "group", wireName, name);
		claimClass(node, "group", wireName, entry);
		claimClass(node, "group", wireName, name + "Stage");
		claimClass(node, "group", wireName, entry + "Stage");
		claimConstant(node, "group", wireName, constant(name));
		claimConstant(node, "group", wireName, constant(entry));
		String field = group.property() + "Stage";
		String encoder = group.property() + "Encoder";
		String encoderClass = group.entry().encoder();
		encoders.add(new Field(encoderClass, encoder));
		add(
				from, stage,
				new Method.Open(
						signature(name, group.property()), new Guard.Current(position, position, stage),
						level.flyweight(), group.property(), encoderClass, encoder, new Then.Move(constant(name), field)
				), node, "group", wireName
		);
		positions.add(constant(name));
		String path = path(level.path(), wireName);
		stage(name, Stage.Kind.GROUP, path);
		Builder implementation = new Builder(name + "Stage", field);
		implementation.stages.add(name);
		implementations.add(implementation);
		Join.Block block = group.entry();
		Target first = level(
				block,
				new Level(
						entry, path, constant(entry), entry + "Stage", group.property() + "EntryStage", encoder,
						encoderClass, new Parent(name, field, constant(name), after.target())
				),
				group.component() == null ? List.of() : group.component().components(),
				group.component() == null ? List.of() : group.component().unmapped()
		);
		boolean followed = !block.groups().isEmpty() || !block.data().isEmpty();
		Guard guard = new Guard.Current(constant(name), followed ? constant(name) : constant(entry), name);
		add(
				implementation, name,
				new Method.Entry(
						signature(first.stage(), "entry"), guard, encoder,
						new Then.Move(constant(entry), object(first.field(), implementation))
				), node, "group", wireName
		);
		add(
				implementation, name,
				new Method.End(
						signature(after.target().stage(), "end"), guard, encoder,
						new Then.Move(after.position(), object(after.target().field(), implementation))
				), node, "group", wireName
		);
		afterStage(after);
	}

	/**
	 * Var-data written from the stage {@code stage} at {@code position}, by
	 * sbe-tool's setters for it, then the stage after it.
	 */
	private void data(Join.Data data, Level level, Builder from, String stage, String position, After after) {
		String wireName = data.token().name();
		Object node = data.component() == null ? this.node : data.component();
		String bulk = "put" + Generators.toUpperFirstChar(data.property());
		String encoding = data.varData().encoding().characterEncoding();
		String returns = after.target().stage();
		List<Signature> forms = new ArrayList<>();
		if (encoding != null) {
			forms.add(signature(returns, data.property(), VALUE_STRING));
			if (JavaUtil.isAsciiEncoding(encoding)) {
				forms.add(signature(returns, data.property(), VALUE_CHARS));
			}
		}
		forms.add(signature(returns, bulk, new Parameter("org.agrona.DirectBuffer", "src"), SRC_OFFSET, LENGTH));
		forms.add(signature(returns, bulk, new Parameter("byte[]", "src"), SRC_OFFSET, LENGTH));
		Guard guard = new Guard.Current(position, position, stage);
		Then then = new Then.Move(after.position(), object(after.target().field(), from));
		for (Signature form : forms) {
			add(from, stage, new Method.Put(form, guard, level.flyweight(), then), node, "data", wireName);
		}
		afterStage(after);
	}

	/**
	 * Declares the stage after a group or var-data, unless its group takes over.
	 */
	private void afterStage(After after) {
		if (after.own()) {
			positions.add(after.position());
			stage(after.target().stage(), Stage.Kind.AFTER, after.subject());
		}
	}

	/**
	 * A method of {@code implementation}, declared on {@code stage} unless it is
	 * inherited, or a problem where the class has one of its signature already.
	 */
	private void add(
			Builder implementation, @Nullable String stage, Method method, Object node, String kind, String wireName
	) {
		if (implementation.add(method, node, kind, wireName) && stage != null) {
			methods(stage).add(method.signature());
		}
	}

	private List<Signature> methods(String stage) {
		return declared(stage).methods();
	}

	private Declared declared(String stage) {
		Declared declared = stages.get(stage);
		if (declared == null) {
			throw new IllegalStateException(stage + " is declared after its methods");
		}
		return declared;
	}

	/** A stage as it is declared, its methods added as they are laid out. */
	private record Declared(Stage.Kind kind, String subject, List<Signature> methods) {
	}

	private void stage(String name, Stage.Kind kind, String subject) {
		stages.putIfAbsent(name, new Declared(kind, subject, new ArrayList<>()));
	}

	private static String path(String prefix, String wireName) {
		return prefix.isEmpty() ? wireName : prefix + "." + wireName;
	}

	/** The object as a step returns it: {@code this} from its own class. */
	private static String object(String field, Builder implementation) {
		return field.equals(implementation.field) ? "this" : field;
	}

	/**
	 * The header's own members, every one but the standard four and the constants,
	 * as their null values.
	 */
	private List<NullWrite> header() {
		List<Token> tokens = ir.headerStructure().tokens();
		List<NullWrite> writes = new ArrayList<>();
		for (int i = 1; i < tokens.size() - 1; i += tokens.get(i).componentTokenCount()) {
			Token member = tokens.get(i);
			String property = JavaUtil.formatPropertyName(member.name());
			if (Join.STANDARD_HEADER_MEMBERS.contains(property) || isConstant(member)) {
				continue;
			}
			NullWrite write = nullWrite(member, property);
			if (write != null) {
				writes.add(write);
			}
		}
		return writes;
	}

	// ---- one field's or member's step

	/**
	 * The step of a field or member of {@code type}, every form sbe-tool's encoder
	 * has for writing it whole, on the stage {@code stage}, each returning
	 * {@code returns}; {@code nullable} adds {@code <property>Null()}, for a
	 * composite's optional member. A set or a composite opens its sub-chain, held
	 * by the step's object.
	 */
	private void steps(
			Builder implementation, String stage, Token type, String property, String flyweight, String encoder,
			String returns, Guard guard, Then then, boolean nullable, Object node, String kind, String wireName
	) {
		List<Method> methods = new ArrayList<>();
		String bulk = "put" + Generators.toUpperFirstChar(property);
		switch (type.signal()) {
			case ENCODING -> {
				PrimitiveType primitive = type.encoding().primitiveType();
				String face = JavaUtil.javaTypeName(primitive);
				if (type.arrayLength() == 1) {
					methods.add(put(returns, property, guard, flyweight, then, new Parameter(face, "value")));
				} else if (primitive == PrimitiveType.CHAR) {
					methods.add(put(returns, property, guard, flyweight, then, VALUE_STRING));
					if (JavaUtil.isAsciiEncoding(type.encoding().characterEncoding())) {
						methods.add(put(returns, property, guard, flyweight, then, VALUE_CHARS));
					}
					methods.add(put(returns, bulk, guard, flyweight, then, new Parameter("byte[]", "src"), SRC_OFFSET));
				} else {
					array(methods, type, face, returns, property, bulk, guard, flyweight, encoder, then);
				}
			}
			case BEGIN_ENUM -> methods.add(
					put(
							returns, property, guard, flyweight, then,
							new Parameter(
									flyweights + "." + JavaUtil.formatClassName(type.applicableTypeName()), "value"
							)
					)
			);
			case BEGIN_SET, BEGIN_COMPOSITE -> {
				String typeName = type.applicableTypeName();
				(type.signal() == Signal.BEGIN_SET ? sets : composites).add(typeName);
				String sub = packageName + "." + JavaUtil.formatClassName(typeName) + "Writer";
				String site = property + "Writer";
				implementation.sites.add(new Site(sub, returns, site));
				methods.add(
						new Method.Sub(
								signature(sub + "<" + returns + ">", property), guard, site, flyweight, property, then
						)
				);
			}
			default -> throw new IllegalStateException("a field of " + type.signal());
		}
		if (nullable && type.signal() != Signal.BEGIN_SET && type.signal() != Signal.BEGIN_COMPOSITE) {
			NullWrite write = nullWrite(type, property);
			if (write == null) {
				throw new IllegalStateException(property + " has no null value to write");
			}
			methods.add(new Method.PutNull(signature(returns, property + "Null"), guard, encoder, write, then));
		}
		for (Method method : methods) {
			add(implementation, stage, method, node, kind, wireName);
		}
	}

	/**
	 * An array other than a char string: sbe-tool's setter taking every element
	 * where it has one, for two to four, and in bulk for {@code uint8}; element by
	 * element from an array otherwise.
	 */
	private static void array(
			List<Method> methods, Token type, String face, String returns, String property, String bulk, Guard guard,
			String flyweight, String encoder, Then then
	) {
		int length = type.arrayLength();
		if (length >= 2 && length <= 4) {
			List<Parameter> values = new ArrayList<>();
			for (int i = 0; i < length; i++) {
				values.add(new Parameter(face, "value" + i));
			}
			methods.add(new Method.Put(new Signature(returns, bulk, values), guard, flyweight, then));
		}
		if (type.encoding().primitiveType() == PrimitiveType.UINT8) {
			methods.add(put(returns, bulk, guard, flyweight, then, new Parameter("byte[]", "src"), SRC_OFFSET, LENGTH));
			methods.add(
					put(
							returns, bulk, guard, flyweight, then, new Parameter("org.agrona.DirectBuffer", "src"),
							SRC_OFFSET, LENGTH
					)
			);
		}
		if (methods.isEmpty()) {
			methods.add(
					new Method.PutEach(
							signature(returns, bulk, new Parameter(face + "[]", "src"), SRC_OFFSET), guard, flyweight,
							encoder, property, then
					)
			);
		}
	}

	private static Method put(
			String returns, String name, Guard guard, String flyweight, Then then, Parameter... parameters
	) {
		return new Method.Put(signature(returns, name, parameters), guard, flyweight, then);
	}

	private static Signature signature(String returns, String name, Parameter... parameters) {
		return new Signature(returns, name, List.of(parameters));
	}

	// ---- null values

	/**
	 * How a field or member of {@code type} is written as its null value, or null
	 * where it has no bytes: an array of length 0. A composite's null values are
	 * its members', declared once.
	 */
	private @Nullable NullWrite nullWrite(Token type, String property) {
		return switch (type.signal()) {
			case ENCODING -> {
				if (type.arrayLength() < 1) {
					yield null;
				}
				yield new NullWrite(
						property,
						type.arrayLength() == 1 ? new Faces.Shape.Scalar(null) : new Faces.Shape.Array(property)
				);
			}
			// A null write names no record's enum: the flyweight's stands in.
			case BEGIN_ENUM -> {
				String enumClass = JavaUtil.formatClassName(type.applicableTypeName());
				yield new NullWrite(property, new Faces.Shape.Enum(enumClass, flyweights + "." + enumClass));
			}
			case BEGIN_SET -> new NullWrite(
					property, new Faces.Shape.Set(JavaUtil.formatClassName(type.applicableTypeName()))
			);
			case BEGIN_COMPOSITE -> {
				compositeNulls(type.applicableTypeName());
				yield new NullWrite(
						property, new Faces.Shape.Composite(JavaUtil.formatClassName(type.applicableTypeName()))
				);
			}
			default -> throw new IllegalStateException("a field of " + type.signal());
		};
	}

	private void compositeNulls(String typeName) {
		String encoder = flyweights + "." + JavaUtil.formatClassName(typeName) + "Encoder";
		if (nulls.containsKey(encoder)) {
			return;
		}
		// Claimed before the members, so a composite holding itself cannot recurse.
		nulls.put(encoder, new Nulls(encoder, List.of()));
		List<NullWrite> writes = new ArrayList<>();
		for (Token member : members(ir, typeName)) {
			if (isConstant(member)) {
				continue;
			}
			NullWrite write = nullWrite(member, JavaUtil.formatPropertyName(member.name()));
			if (write != null) {
				writes.add(write);
			}
		}
		nulls.put(encoder, new Nulls(encoder, writes));
	}

	// ---- the sub-chains

	/**
	 * The sub-chain of the composite {@code typeName}: its first member's step on
	 * the class, each later one a stage, the last returning to the caller. A
	 * problem lands on its declaration where one maps it, else on {@code schema}.
	 * The composites and sets its members open are added to {@code composites} and
	 * {@code sets}.
	 */
	static WriterModel.@Nullable Composite composite(
			Ir ir, Annotated annotated, String typeName, Object schema, List<Problem> problems,
			Set<String> composites, Set<String> sets
	) {
		String compositeClass = JavaUtil.formatClassName(typeName);
		String writer = compositeClass + "Writer";
		List<Problem> found = new ArrayList<>();
		WriterWalk walk = new WriterWalk(
				ir, annotated.packageName(), writer, declaration(annotated, typeName, schema), found, composites, sets
		);
		walk.classes.add(writer);
		walk.classes.addAll(SUB_RESERVED);
		String encoder = walk.flyweights + "." + compositeClass + "Encoder";
		List<Token> members = new ArrayList<>();
		for (Token member : members(ir, typeName)) {
			if (!isConstant(member) && walk.writable(member)) {
				members.add(member);
			}
		}
		List<String> names = new ArrayList<>();
		for (int i = 1; i < members.size(); i++) {
			String name = JavaUtil.formatClassName(members.get(i).name());
			walk.claimClass(walk.node, "member", members.get(i).name(), name);
			names.add(name);
		}
		Builder head = walk.new Builder(writer, "");
		Builder chain = walk.new Builder("Chain", "chain");
		Guard guard = new Guard.Open(writer);
		for (int i = 0; i < members.size(); i++) {
			Token member = members.get(i);
			boolean last = i == members.size() - 1;
			String returns = last ? "N" : names.get(i) + "<N>";
			String stage = i == 0 ? writer : names.get(i - 1);
			walk.stage(stage, Stage.Kind.MEMBER, typeName + "." + member.name());
			walk.steps(
					i == 0 ? head : chain, stage, member, JavaUtil.formatPropertyName(member.name()), "encoder",
					encoder, returns, guard, last ? new Then.Release() : new Then.Return("chain"),
					member.encoding().presence() == Encoding.Presence.OPTIONAL, walk.node, "member", member.name()
			);
		}
		problems.addAll(found);
		if (found.stream().anyMatch(Problem::isError)) {
			return null;
		}
		List<Stage> stages = new ArrayList<>();
		for (String name : names) {
			Declared declared = walk.declared(name);
			stages.add(new Stage(Stage.Kind.MEMBER, declared.subject(), name, List.of(), declared.methods()));
		}
		List<Site> sites = new ArrayList<>(head.sites);
		sites.addAll(chain.sites);
		return new WriterModel.Composite(
				walk.packageName, walk.flyweights, typeName, writer, encoder, stages, sites, head.methods,
				names.stream().map(name -> name + "<N>").toList(), chain.methods
		);
	}

	/**
	 * The sub-chain of the set {@code typeName}: its choices, then {@code end()}.
	 */
	static WriterModel.Set set(Ir ir, String packageName, String typeName) {
		String setClass = JavaUtil.formatClassName(typeName);
		List<String> choices = new ArrayList<>();
		for (Token token : ir.getType(typeName)) {
			if (token.signal() == Signal.CHOICE) {
				choices.add(JavaUtil.formatPropertyName(token.name()));
			}
		}
		return new WriterModel.Set(
				packageName, ir.applicableNamespace(), typeName, setClass + "Writer",
				ir.applicableNamespace() + "." + setClass + "Encoder", choices
		);
	}

	/**
	 * Whether a field or member has a step: a constant has none, nor an array of
	 * length 0, nor a composite of no member that has one.
	 */
	private boolean writable(Token type) {
		return switch (type.signal()) {
			case ENCODING -> type.arrayLength() >= 1;
			case BEGIN_COMPOSITE -> members(ir, type.applicableTypeName()).stream()
					.anyMatch(member -> !isConstant(member) && writable(member));
			default -> true;
		};
	}

	/**
	 * A composite's members as sbe-tool generates its flyweight from them: the
	 * type's tokens by name, as the IR captured them from the messages.
	 */
	private static List<Token> members(Ir ir, String typeName) {
		List<Token> tokens = ir.getType(typeName);
		List<Token> members = new ArrayList<>();
		for (int i = 1; i < tokens.size() - 1; i += tokens.get(i).componentTokenCount()) {
			members.add(tokens.get(i));
		}
		return members;
	}

	private static Object declaration(Annotated annotated, String typeName, Object schema) {
		for (Annotated.Declaration declaration : annotated.types()) {
			if (declaration instanceof Annotated.Composite composite
					&& Join.wireName(composite.name(), composite.javaName()).equals(typeName)) {
				return composite;
			}
		}
		return schema;
	}

	private static boolean isConstant(Token token) {
		return token.encoding().presence() == Encoding.Presence.CONSTANT;
	}

	private Object carrier(Join.Field field, List<Annotated.Component> components, List<Annotated.Field> unmapped) {
		return FlyweightWalk.carrier(node, field.token().name(), components, unmapped);
	}

	// ---- names

	private boolean claimClass(Object node, String kind, String wireName, String name) {
		if (classes.add(name)) {
			return true;
		}
		problems.add(clash(node, kind, wireName, name));
		return false;
	}

	private void claimConstant(Object node, String kind, String wireName, String name) {
		if (!constants.add(name)) {
			problems.add(clash(node, kind, wireName, "At." + name));
		}
	}

	private Problem clash(Object node, String kind, String wireName, String name) {
		return new Problem(
				node, "the " + kind + " \"" + wireName + "\" clashes with the writer's " + name + "; rename it"
		);
	}

	/**
	 * What brought a method: the node, the kind and the wire name of a field,
	 * group, var-data or member, or an empty kind for the writer's own.
	 */
	private record Claim(String method, Object node, String kind, String wireName) {
	}

	/**
	 * An object's class as it is laid out: the stages it implements, the sub-chains
	 * it holds and its methods, no two of one signature.
	 */
	private final class Builder {

		private final String name;
		private final String field;
		private final List<String> stages = new ArrayList<>();
		private final List<Site> sites = new ArrayList<>();
		private final List<Method> methods = new ArrayList<>();
		private final Map<String, Claim> signatures = new HashMap<>();

		private Builder(String name, String field) {
			this.name = name;
			this.field = field;
		}

		/**
		 * Adds the method, or reports that one of its signature is there already on the
		 * node that brought one of them, a schema's node before the writer's.
		 */
		boolean add(Method method, Object node, String kind, String wireName) {
			Signature signature = method.signature();
			String key = signature.name() + signature.parameters().stream().map(Parameter::type).toList();
			Claim claim = new Claim(name + "." + signature.name() + "()", node, kind, wireName);
			Claim taken = signatures.putIfAbsent(key, claim);
			if (taken == null) {
				methods.add(method);
				return true;
			}
			Claim reported = kind.isEmpty() ? taken : claim;
			Claim other = reported == claim ? taken : claim;
			problems.add(clash(reported.node(), reported.kind(), reported.wireName(), other.method()));
			return false;
		}

		Implementation build() {
			return new Implementation(name, field, stages, sites, methods);
		}
	}
}
