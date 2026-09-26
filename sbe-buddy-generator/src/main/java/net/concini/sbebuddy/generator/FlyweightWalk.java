package net.concini.sbebuddy.generator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import uk.co.real_logic.sbe.PrimitiveType;
import uk.co.real_logic.sbe.generation.Generators;
import uk.co.real_logic.sbe.generation.java.JavaUtil;
import uk.co.real_logic.sbe.ir.Ir;
import uk.co.real_logic.sbe.ir.Token;

import net.concini.sbebuddy.generator.FlyweightModel.Accessor;
import net.concini.sbebuddy.generator.FlyweightModel.Parameter;
import net.concini.sbebuddy.generator.FlyweightModel.Part;
import net.concini.sbebuddy.generator.FlyweightModel.Position;
import net.concini.sbebuddy.generator.FlyweightModel.Step;

/**
 * One message's {@link FlyweightModel}: the message {@link Join joined}, then
 * its stages and positions laid out in wire order, each position's step read
 * off the block that holds it: the level's next present group or var-data, else
 * the next entry of the group the level is an entry of, else what follows that
 * group, else nothing, as {@code OtfMessageDecoder} walks a message. A group or
 * var-data added after version 0 is passed over below its version. A group or
 * var-data whose stage would take a name the reader already has, and a field
 * whose accessor would take one of its stage's own, is a problem, on its
 * component where a record maps the message and on the package otherwise.
 */
final class FlyweightWalk {

	private static final String ROOT_BLOCK = "RootBlock";

	private static final String BEFORE_ROOT_BLOCK = "BEFORE_ROOT_BLOCK";

	private static final String END = "END";

	/**
	 * Names a stage cannot take: the reader's own types, and those it uses from
	 * java.lang.
	 */
	private static final List<String> RESERVED = List
			.of("Stage", "Member", "At", "String", "Iterable", "Override", "Appendable", "IllegalStateException");

	/** Methods a stage has whatever its block: a field's accessor cannot be one. */
	private static final List<String> STAGE_METHODS = List
			.of(
					"skip", "bound", "getClass", "hashCode", "toString", "notify", "notifyAll", "wait", "clone",
					"finalize"
			);

	private final String flyweights;
	private final Object node;
	private final List<Problem> problems;
	private final Set<String> classes = new HashSet<>();
	private final Set<String> constants = new HashSet<>();
	private final List<Part> parts = new ArrayList<>();
	private final List<Position> positions = new ArrayList<>();

	private FlyweightWalk(String flyweights, Object node, List<Problem> problems) {
		this.flyweights = flyweights;
		this.node = node;
		this.problems = problems;
	}

	/**
	 * The reader of the message of {@code tokens}, or null with an error among the
	 * problems added to {@code problems}. {@code message} is its record, or null;
	 * {@code schema} is the node a problem lands on where there is none. The join's
	 * own problems are the codec emitter's, which joins every record the same way
	 * and reports them first.
	 */
	static @Nullable FlyweightModel walk(
			Ir ir, Annotated annotated, int baseline, List<Token> tokens, Annotated.@Nullable Message message,
			Object schema, List<Problem> problems
	) {
		Join.Message joined = Join.join(ir, annotated, baseline, tokens, message, new ArrayList<>());
		String messageClass = JavaUtil.formatClassName(tokens.get(0).name());
		String reader = messageClass + "Reader";
		List<Problem> found = new ArrayList<>();
		FlyweightWalk walk = new FlyweightWalk(ir.applicableNamespace(), message == null ? schema : message, found);
		walk.classes.add(reader);
		walk.classes.add(ROOT_BLOCK);
		walk.classes.addAll(RESERVED);
		walk.constants.addAll(List.of(BEFORE_ROOT_BLOCK, constant(ROOT_BLOCK), END));
		Join.Block block = joined.block();
		walk.positions.add(new Position(BEFORE_ROOT_BLOCK, new Step.Arrive("open" + ROOT_BLOCK)));
		walk.positions.add(new Position(constant(ROOT_BLOCK), walk.from(block, 0, new Step.End())));
		List<Accessor> accessors = walk.accessors(
				block, message == null ? List.of() : message.components(),
				message == null ? List.of() : message.unmapped(), ROOT_BLOCK, false
		);
		walk.level(block, "decoder", "", new Step.End());
		walk.positions.add(new Position(END, new Step.End()));
		problems.addAll(found);
		if (found.stream().anyMatch(Problem::isError)) {
			return null;
		}
		String headerClass = JavaUtil.formatClassName(ir.headerStructure().tokens().get(0).name());
		return new FlyweightModel(
				walk.flyweights, tokens.get(0).name(), messageClass, reader, headerClass,
				new FlyweightModel.RootBlock(constant(ROOT_BLOCK), END, accessors), walk.parts, walk.positions
		);
	}

	/**
	 * The positions of a block's groups and var-data, recursing into each group's
	 * entry, and the parts in the order their stages first come; {@code owner} is
	 * the flyweight of the block, {@code end} what comes once the level holds
	 * nothing more. An entry holding var-data has a position past its last one,
	 * where the entry's skip leaves the reader.
	 */
	private void level(Join.Block block, String owner, String prefix, Step end) {
		List<Join.Group> groups = block.groups();
		for (int i = 0; i < groups.size(); i++) {
			Join.Group group = groups.get(i);
			String wireName = group.token().name();
			String name = JavaUtil.formatClassName(wireName);
			String entry = name + "Entry";
			Object node = group.component() == null ? this.node : group.component();
			if (!claim(node, "group", wireName, List.of(name, entry))
					|| !claim(node, "group", wireName, constant(name), constant(entry), after(name))) {
				continue;
			}
			Step rest = new Step.Rest("rest" + name, "hasRest" + name);
			Step following = from(block, i + 1, end);
			parts.add(
					new FlyweightModel.Group(
							prefix + wireName, name, entry, group.property(), group.entry().decoder(), owner,
							constant(name), constant(entry), last(group.entry(), entry), after(name), following,
							accessors(
									group.entry(),
									group.component() == null ? List.of() : group.component().components(),
									group.component() == null ? List.of() : group.component().unmapped(), entry, true
							)
					)
			);
			positions.add(new Position(constant(name), rest));
			positions.add(new Position(constant(entry), from(group.entry(), 0, rest)));
			level(group.entry(), group.property() + "Decoder", prefix + wireName + ".", rest);
			positions.add(new Position(after(name), following));
		}
		List<Join.Data> data = block.data();
		for (int i = 0; i < data.size(); i++) {
			Join.Data one = data.get(i);
			String wireName = one.token().name();
			String name = JavaUtil.formatClassName(wireName);
			Object node = one.component() == null ? this.node : one.component();
			boolean lastOfEntry = end instanceof Step.Rest && i == data.size() - 1;
			String[] taken = lastOfEntry ? new String[]{constant(name), after(name)} : new String[]{constant(name)};
			if (!claim(node, "data", wireName, List.of(name)) || !claim(node, "data", wireName, taken)) {
				continue;
			}
			parts.add(
					new FlyweightModel.Data(
							prefix + wireName, name, one.property(), Generators.toUpperFirstChar(one.property()), owner,
							constant(name)
					)
			);
			positions.add(new Position(constant(name), from(block, groups.size() + i + 1, end)));
			if (lastOfEntry) {
				positions.add(new Position(after(name), end));
			}
		}
	}

	/**
	 * The last position inside an entry: past its last var-data, else past its last
	 * group, else the entry's own.
	 */
	private static String last(Join.Block entry, String entryClass) {
		if (!entry.data().isEmpty()) {
			return after(JavaUtil.formatClassName(entry.data().getLast().token().name()));
		}
		if (!entry.groups().isEmpty()) {
			return after(JavaUtil.formatClassName(entry.groups().getLast().token().name()));
		}
		return constant(entryClass);
	}

	/**
	 * The step to the block's first present group or var-data from {@code index},
	 * its groups counted before its var-data; {@code end} past the last.
	 */
	private Step from(Join.Block block, int index, Step end) {
		int groups = block.groups().size();
		if (index >= groups + block.data().size()) {
			return end;
		}
		Token token;
		String sinceVersion;
		if (index < groups) {
			token = block.groups().get(index).token();
			// sbe-tool names a group's static methods after its decoder class.
			sinceVersion = JavaUtil.formatPropertyName(JavaUtil.formatClassName(token.name()) + "Decoder")
					+ "SinceVersion";
		} else {
			token = block.data().get(index - groups).token();
			sinceVersion = JavaUtil.formatPropertyName(token.name()) + "SinceVersion";
		}
		String method = "open" + JavaUtil.formatClassName(token.name());
		if (token.version() == 0) {
			return new Step.Arrive(method);
		}
		return new Step.ArriveSince(method, block.decoder() + "." + sinceVersion + "()", from(block, index + 1, end));
	}

	/**
	 * Takes the names for a group's or var-data's stages, or reports the first one
	 * the reader already has.
	 */
	private boolean claim(Object node, String kind, String wireName, List<String> names) {
		for (String name : names) {
			if (classes.contains(name)) {
				problems.add(clash(node, kind, wireName, name));
				return false;
			}
		}
		classes.addAll(names);
		return true;
	}

	/** The same for the positions, the reader's {@code At} constants. */
	private boolean claim(Object node, String kind, String wireName, String... names) {
		for (String name : names) {
			if (constants.contains(name)) {
				problems.add(clash(node, kind, wireName, "At." + name));
				return false;
			}
		}
		constants.addAll(List.of(names));
		return true;
	}

	private static Problem clash(Object node, String kind, String wireName, String name) {
		return new Problem(
				node, "the " + kind + " \"" + wireName + "\" clashes with the reader's " + name + "; rename it"
		);
	}

	// ---- sbe-tool's accessors of a block's fields

	/**
	 * Every accessor sbe-tool's decoder has for the block's fields, in wire order,
	 * each field's as {@code JavaGenerator} writes them; a field whose accessor
	 * would be one of the stage's own methods is a problem, on the component or the
	 * unmapped entry that carries it.
	 */
	private List<Accessor> accessors(
			Join.Block block, List<Annotated.Component> components, List<Annotated.Field> unmapped, String stage,
			boolean entry
	) {
		List<Accessor> accessors = new ArrayList<>();
		for (Join.Field field : block.fields()) {
			List<Accessor> own = accessors(field);
			for (Accessor accessor : own) {
				if (STAGE_METHODS.contains(accessor.name()) || entry && accessor.name().equals("index")) {
					problems.add(
							new Problem(
									carrier(field.token().name(), components, unmapped),
									"the field \"" + field.token().name() + "\" clashes with the reader's " + stage
											+ "."
											+ accessor.name() + "(); rename it"
							)
					);
				}
			}
			accessors.addAll(own);
		}
		return accessors;
	}

	/** The component or unmapped entry of the wire name, or the walk's node. */
	private Object carrier(String wireName, List<Annotated.Component> components, List<Annotated.Field> unmapped) {
		List<Annotated.Field> fields = new ArrayList<>(unmapped);
		for (Annotated.Component component : components) {
			if (component instanceof Annotated.Field field) {
				fields.add(field);
			}
		}
		for (Annotated.Field field : fields) {
			if (Join.wireName(field.name(), field.javaName()).equals(wireName)) {
				return field;
			}
		}
		return node;
	}

	private List<Accessor> accessors(Join.Field field) {
		Token type = field.type();
		String property = field.property();
		String bulk = Generators.toUpperFirstChar(property);
		return switch (type.signal()) {
			case ENCODING -> encoding(type, property, bulk);
			case BEGIN_ENUM -> {
				String face = JavaUtil.javaTypeName(type.encoding().primitiveType());
				yield List.of(
						getter(flyweights + "." + JavaUtil.formatClassName(type.applicableTypeName()), property),
						getter(face, property + "Raw")
				);
			}
			// sbe-tool names a set's and a composite's flyweight after the type token's
			// name.
			case BEGIN_SET, BEGIN_COMPOSITE -> List
					.of(getter(flyweights + "." + JavaUtil.formatClassName(type.name()) + "Decoder", property));
			default -> throw new IllegalStateException("a field of " + type.signal());
		};
	}

	/**
	 * A constant through its literal, a scalar through its getter, an array by
	 * index and in bulk, as sbe-tool's primitive decoder has them.
	 */
	private static List<Accessor> encoding(Token type, String property, String bulk) {
		PrimitiveType primitive = type.encoding().primitiveType();
		String face = JavaUtil.javaTypeName(primitive);
		if (type.isConstantEncoding()) {
			if (primitive != PrimitiveType.CHAR) {
				return List.of(getter(face, property));
			}
			boolean text = type.encoding().constValue().byteArrayValue(PrimitiveType.CHAR).length > 1;
			return List.of(
					getter(text ? "String" : "byte", property), getter(face, property, INDEX),
					getter("int", "get" + bulk, DST, DST_OFFSET, LENGTH)
			);
		}
		if (type.arrayLength() == 1) {
			return List.of(getter(face, property));
		}
		if (type.arrayLength() < 1) {
			return List.of();
		}
		if (primitive == PrimitiveType.CHAR) {
			List<Accessor> text = new ArrayList<>(
					List.of(
							getter("String", property), getter(face, property, INDEX),
							getter("int", "get" + bulk, DST, DST_OFFSET)
					)
			);
			if (JavaUtil.isAsciiEncoding(type.encoding().characterEncoding())) {
				text.add(getter("int", "get" + bulk, new Parameter("Appendable", "value")));
			}
			return text;
		}
		if (primitive == PrimitiveType.UINT8) {
			return List.of(
					getter(face, property, INDEX), getter("int", "get" + bulk, DST, DST_OFFSET, LENGTH),
					getter(
							"int", "get" + bulk, new Parameter("org.agrona.MutableDirectBuffer", "dst"), DST_OFFSET,
							LENGTH
					), new Accessor.Wrap("wrap" + bulk, List.of(new Parameter("org.agrona.DirectBuffer", "wrapBuffer")))
			);
		}
		return List.of(getter(face, property, INDEX));
	}

	private static final Parameter INDEX = new Parameter("int", "index");

	private static final Parameter DST = new Parameter("byte[]", "dst");

	private static final Parameter DST_OFFSET = new Parameter("int", "dstOffset");

	private static final Parameter LENGTH = new Parameter("int", "length");

	private static Accessor getter(String type, String name, Parameter... parameters) {
		return new Accessor.Getter(type, name, List.of(parameters));
	}

	// ---- names

	/** A class name as a constant: {@code PartySubIds} is {@code PARTY_SUB_IDS}. */
	static String constant(String className) {
		StringBuilder constant = new StringBuilder();
		for (int i = 0; i < className.length(); i++) {
			char c = className.charAt(i);
			if (i > 0 && Character.isUpperCase(c)
					&& (Character.isLowerCase(className.charAt(i - 1)) || Character.isDigit(className.charAt(i - 1)))) {
				constant.append('_');
			}
			constant.append(c);
		}
		return constant.toString().toUpperCase(Locale.ROOT);
	}

	private static String after(String className) {
		return "AFTER_" + constant(className);
	}
}
