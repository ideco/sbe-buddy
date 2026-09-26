package net.concini.sbebuddy.generator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import uk.co.real_logic.sbe.ir.Ir;
import uk.co.real_logic.sbe.ir.Token;

import net.concini.sbebuddy.generator.CodecModel.Block;
import net.concini.sbebuddy.generator.CodecModel.Call;
import net.concini.sbebuddy.generator.CodecModel.Component;
import net.concini.sbebuddy.generator.CodecModel.Variable;
import net.concini.sbebuddy.generator.Faces.Face;

/**
 * One message's {@link CodecModel}: the message {@link Join joined} with its
 * record, then laid over its reader and writer. Each block is read through the
 * reader's stages, groups and var-data in wire order before the constructor,
 * and written through the writer's bound chain, followed stage by stage: a hop
 * to the twin where the next component is taken there, the required components
 * in wire order, then those the block complete takes, then each group and
 * var-data. The header's leaves and the helpers they call are the codec's own.
 */
final class CodecWalk {

	private final Ir ir;
	private final String packageName;
	private final String reader;
	private final String writer;
	private final Map<String, FlyweightModel.Part> parts = new HashMap<>();
	private final Map<String, WriterModel.Stage> stages = new HashMap<>();
	private final List<CodecModel.Group> groups = new ArrayList<>();
	private final Set<Faces.Helper> helpers = new LinkedHashSet<>();
	private final Set<String> bindings = new HashSet<>();
	private final Set<String> contexts = new HashSet<>();

	private CodecWalk(Ir ir, String packageName, FlyweightModel reader, WriterModel.Message writer) {
		this.ir = ir;
		this.packageName = packageName;
		this.reader = reader.reader();
		this.writer = writer.writer();
		for (FlyweightModel.Part part : reader.parts()) {
			parts.put(
					switch (part) {
						case FlyweightModel.Group group -> group.path();
						case FlyweightModel.Data data -> data.path();
					}, part
			);
		}
		for (WriterModel.Stage stage : writer.stages()) {
			stages.put(stage.name(), stage);
		}
	}

	/**
	 * The problems of the message's join with its record, added to
	 * {@code problems}, and whether it joined without an error; a warning is added
	 * and stops nothing. {@code baseline} is the oldest version the codec reads.
	 */
	static boolean check(Ir ir, Annotated annotated, int baseline, Annotated.Message message, List<Problem> problems) {
		List<Problem> found = new ArrayList<>();
		join(ir, annotated, baseline, message, found);
		problems.addAll(found);
		return found.stream().noneMatch(Problem::isError);
	}

	/**
	 * The model of a message whose join {@link #check} found clean, over its
	 * reader's and writer's models.
	 */
	static CodecModel walk(
			Ir ir, Annotated annotated, int baseline, Annotated.Message message, FlyweightModel reader,
			WriterModel.Message writer
	) {
		Join.Message joined = join(ir, annotated, baseline, message, new ArrayList<>());
		if (joined == null) {
			throw new IllegalStateException(message.javaName() + " is walked though its join failed");
		}
		CodecWalk walk = new CodecWalk(ir, annotated.packageName(), reader, writer);
		CodecModel.Header header = walk.header(joined.header());
		FlyweightModel.Bound bound = reader.rootBlock().bound();
		Block block = walk.block(
				joined.block(), "", walk.readerClass("RootBlock"),
				bound == null ? null : walk.readerClass(bound.name()),
				writer.first(), true
		);
		return new CodecModel(
				annotated.packageName(), message.javaName() + "Codec", message.qualifiedName(),
				ir.applicableNamespace(), header, writer.message(), walk.qualified(walk.reader),
				walk.qualified(walk.writer), walk.writerClass(writer.first()), baseline,
				joined.bindings().stream().filter(binding -> walk.bindings.contains(binding.name())).toList(),
				joined.contexts().stream().filter(context -> walk.contexts.contains(context.name())).toList(), block,
				walk.groups, List.copyOf(walk.helpers)
		);
	}

	/** Null, with the problem, for a record whose id names no message. */
	private static Join.@Nullable Message join(
			Ir ir, Annotated annotated, int baseline, Annotated.Message message, List<Problem> problems
	) {
		List<Token> tokens = ir.getMessage(message.id());
		if (tokens == null) {
			problems.add(new Problem(message, "the schema has no message with id " + message.id()));
			return null;
		}
		String wireName = Join.wireName(message.name(), message.javaName());
		if (!tokens.get(0).name().equals(wireName)) {
			problems.add(
					new Problem(
							message, "the schema's message with id " + message.id() + " is named \""
									+ tokens.get(0).name() + "\", not \"" + wireName + "\""
					)
			);
		}
		Join.Message joined = Join.join(ir, annotated, baseline, tokens, message, problems);
		return problems.stream().anyMatch(Problem::isError) ? null : joined;
	}

	/**
	 * The header's own members written and every component read, through the
	 * helpers, bindings and contexts they call.
	 */
	private CodecModel.Header header(Join.Header header) {
		Join.Composite composite = header.composite();
		for (Join.Field field : composite.members()) {
			if (field.face() instanceof Face.Mapped leaf) {
				use(field.helpers(), leaf.binding(), leaf.context());
			}
		}
		return new CodecModel.Header(
				composite.name(), composite.record(), composite.encoder(), composite.decoder(), leaves(header.own()),
				leaves(composite.constructorOrder())
		);
	}

	private static List<Face.Mapped> leaves(List<Join.Field> fields) {
		List<Face.Mapped> leaves = new ArrayList<>();
		for (Join.Field field : fields) {
			if (!(field.face() instanceof Face.Mapped leaf)) {
				throw new IllegalStateException(field.property() + " is a header component with no leaf");
			}
			leaves.add(leaf);
		}
		return leaves;
	}

	/**
	 * A block read through the reader's {@code stage} and {@code bound}, and
	 * written from the writer's stage {@code start}; {@code prefix} is the wire
	 * path of the group whose entry it is and its dot, empty for the message.
	 * {@code root} ends the chain with the length.
	 */
	private Block block(
			Join.Block block, String prefix, String stage, @Nullable String bound, String start, boolean root
	) {
		Map<Join.Node, Variable> variables = new HashMap<>();
		List<Variable> variable = new ArrayList<>();
		List<Variable.Group> checks = new ArrayList<>();
		List<Call> chain = new ArrayList<>();
		String at = start;
		List<Face.Mapped> late = new ArrayList<>();
		for (Join.Field field : block.fields()) {
			if (field.face() instanceof Face.Mapped leaf) {
				switch (WriterWalk.place(ir, field)) {
					case REQUIRED -> at = take(at, leaf.component(), chain);
					case OPTIONAL, CHECKED -> late.add(leaf);
				}
			}
		}
		for (Face.Mapped leaf : late) {
			at = take(at, leaf.component(), chain);
		}
		for (Join.Group group : block.groups()) {
			String path = prefix + group.token().name();
			if (!(parts.get(path) instanceof FlyweightModel.Group part)) {
				throw new IllegalStateException(path + " is a group the reader has not");
			}
			Faces.Bound binding = group.bound();
			if (binding != null) {
				use(binding.binding(), binding.context());
			}
			Variable.Group member = new Variable.Group(
					mapped(group.component(), path).javaName(), group.path(), mapped(group.record(), path),
					group.entry().encoder(), group.addedSince(), block.decoder(),
					binding == null ? null : binding.binding(), binding == null ? null : binding.context()
			);
			String opened = returns(at, group.property(), 0);
			FlyweightModel.Bound entryBound = part.bound();
			Block entry = block(
					group.entry(), path + ".", readerClass(part.entry()),
					entryBound == null ? null : readerClass(entryBound.name()), returns(opened, "entry", 0), false
			);
			String after = returns(opened, "end", 0);
			groups.add(
					new CodecModel.Group(
							member, entry, writerClass(opened), writerClass(after), readerClass(part.name())
					)
			);
			chain.add(new Call.Open(member, group.property(), writerClass(opened), group.property()));
			checks.add(member);
			variable.add(member);
			variables.put(group, member);
			at = after;
		}
		for (Join.Data data : block.data()) {
			String path = prefix + data.token().name();
			if (!(parts.get(path) instanceof FlyweightModel.Data part) || part.bound() == null) {
				throw new IllegalStateException(path + " is var-data the reader has no bound stage of");
			}
			Faces.Helper.Data leaf = mapped(data.leaf(), path);
			Faces.Bound binding = data.bound();
			if (binding != null) {
				use(binding.binding(), binding.context());
			}
			String component = mapped(data.component(), path).javaName();
			Variable.Data member = new Variable.Data(
					component, readerClass(part.name()), readerClass(part.bound().name()), mapped(data.type(), path),
					qualified(writer) + "." + leaf.length(), data.addedSince(), block.decoder(),
					binding == null ? null : binding.binding(), binding == null ? null : binding.context()
			);
			at = take(at, component, chain);
			variable.add(member);
			variables.put(data, member);
		}
		if (root && !returns(at, "length", 0).equals("int")) {
			throw new IllegalStateException(at + " has a length() that is no int");
		}
		List<Component> constructorOrder = new ArrayList<>();
		for (Join.Node node : block.constructorOrder()) {
			constructorOrder.add(switch (node) {
				case Join.Field field -> {
					if (!(field.face() instanceof Face.Mapped leaf)) {
						throw new IllegalStateException(field.property() + " is a component with no leaf");
					}
					yield new Component.Field(leaf.component());
				}
				case Join.Group group -> variables.get(group);
				case Join.Data data -> variables.get(data);
			});
		}
		return new Block(stage, bound, variable, constructorOrder, checks, chain);
	}

	/**
	 * The step taking {@code component} from the stage {@code at}, through the hop
	 * to its twin unless the chain stands on one; the stage the step returns.
	 */
	private String take(String at, String component, List<Call> chain) {
		String twin = at;
		if (stage(at).kind() != WriterModel.Stage.Kind.BOUND) {
			chain.add(new Call.Hop());
			twin = returns(at, "bound", 0);
		}
		chain.add(new Call.Take(component));
		return returns(twin, component, 1);
	}

	/** What the stage's method of the name and arity returns. */
	private String returns(String at, String method, int arity) {
		for (WriterModel.Signature signature : stage(at).methods()) {
			if (signature.name().equals(method) && signature.parameters().size() == arity) {
				return signature.returns();
			}
		}
		throw new IllegalStateException(at + " has no " + method + " of " + arity + " parameters");
	}

	private WriterModel.Stage stage(String name) {
		WriterModel.Stage stage = stages.get(name);
		if (stage == null) {
			throw new IllegalStateException(name + " is not a stage of " + writer);
		}
		return stage;
	}

	/**
	 * What the codec reads and writes through of its own: the helpers its header
	 * calls, and the bindings and contexts of those and of its groups and var-data,
	 * the members of a composite included.
	 */
	private void use(List<Faces.Helper> used, @Nullable String binding, @Nullable String context) {
		helpers.addAll(used);
		use(binding, context);
		for (Faces.Helper helper : used) {
			if (helper instanceof Faces.Helper.CompositePair pair) {
				for (Face.Mapped member : pair.constructorOrder()) {
					use(member.binding(), member.context());
				}
			}
		}
	}

	private void use(@Nullable String binding, @Nullable String context) {
		if (binding != null) {
			bindings.add(binding);
		}
		if (context != null) {
			contexts.add(context);
		}
	}

	private String readerClass(String name) {
		return qualified(reader) + "." + name;
	}

	private String writerClass(String name) {
		return qualified(writer) + "." + name;
	}

	private String qualified(String name) {
		return packageName + "." + name;
	}

	/**
	 * What only a record gives a node, which every node of a codec's message has.
	 */
	private static <T> T mapped(@Nullable T value, String path) {
		if (value == null) {
			throw new IllegalStateException(path + " is joined without a record");
		}
		return value;
	}
}
