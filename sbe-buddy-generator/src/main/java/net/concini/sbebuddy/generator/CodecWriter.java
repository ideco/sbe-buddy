package net.concini.sbebuddy.generator;

import static net.concini.sbebuddy.generator.CodecTemplates.*;
import static net.concini.sbebuddy.generator.FaceTemplates.BINDING_FIELD;
import static net.concini.sbebuddy.generator.FaceTemplates.CONTEXT_FIELD;
import static net.concini.sbebuddy.generator.FaceTemplates.SOURCE;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.concini.sbebuddy.generator.CodecModel.Block;
import net.concini.sbebuddy.generator.CodecModel.Call;
import net.concini.sbebuddy.generator.CodecModel.Component;
import net.concini.sbebuddy.generator.CodecModel.Variable;

/**
 * A {@link CodecModel} as Java source, through {@link CodecTemplates}: a block
 * read through its reader's stages into the record's constructor, and written
 * by its writer's chain; the header's leaves through {@link FaceWriter}. Each
 * question has one switch: how a block is read and written, and what each of
 * its groups declares. A template is filled from the model node it writes, and
 * the names the node does not hold are given beside it.
 */
final class CodecWriter {

	private final CodecModel model;
	private final FaceWriter faces;

	private CodecWriter(CodecModel model) {
		this.model = model;
		this.faces = new FaceWriter(model.flyweights(), "", "");
	}

	static String write(CodecModel model) {
		return new CodecWriter(model).codec();
	}

	private String codec() {
		Block block = model.block();
		CodecModel.Header header = model.header();
		List<String> bindings = new ArrayList<>();
		for (Faces.Binding binding : model.bindings()) {
			bindings.add(BINDING_FIELD.fill(binding));
		}
		List<String> contexts = new ArrayList<>();
		for (Faces.Context context : model.contexts()) {
			contexts.add(CONTEXT_FIELD.fill(context));
		}
		List<String> variableLengths = new ArrayList<>();
		for (Variable variable : block.variable()) {
			variableLengths.add(lengthTerm(LENGTH_TERM, variable));
		}
		List<String> groupMethods = new ArrayList<>();
		for (CodecModel.Group group : model.groups()) {
			groupMethods.add(groupMethods(group));
		}
		List<String> helpers = new ArrayList<>();
		for (Faces.Helper helper : model.helpers()) {
			helpers.add(faces.helper(helper));
		}
		return CODEC.fill(
				model,
				"headerClass", header.headerClass(),
				"headerRecord", header.record(),
				"bindings", String.join("\n", bindings),
				"contexts", String.join("\n", contexts),
				"variableLengths", String.join("", variableLengths),
				"writeHeader", header.own().isEmpty() ? "" : WRITE_HEADER_CALL.fill(),
				"encodeBlock", writes(block, MESSAGE_START.fill(), true),
				"atBaseline", model.baseline() == 0
						? ""
						: AT_BASELINE.fill("baseline", String.valueOf(model.baseline())),
				"refuseBelowBaseline", model.baseline() == 0
						? ""
						: REFUSE_BELOW_BASELINE.fill(model, "baseline", String.valueOf(model.baseline())),
				"readBlock", block.bound() == null ? PASS_ROOT_BLOCK.fill() : READ_ROOT_BLOCK.fill(block),
				"decodeVariable", variableReads(block),
				"decodeFields", arguments(block, "block"),
				"headerMethods", headerMethods(header),
				"groupMethods", groupMethods.isEmpty() ? "" : "\n" + String.join("\n\n", groupMethods),
				"helpers", helpers.isEmpty() ? "" : "\n" + String.join("\n\n", helpers)
		);
	}

	/**
	 * The header read whole, and, where it has members of its own, written from a
	 * header.
	 */
	private String headerMethods(CodecModel.Header header) {
		List<String> reads = new ArrayList<>();
		for (Faces.Face.Mapped leaf : header.constructorOrder()) {
			reads.add(faces.read(leaf, "decoder", header.decoder()));
		}
		List<String> methods = new ArrayList<>();
		methods.add(READ_HEADER.fill(header, "members", String.join(",\n", reads)));
		if (!header.own().isEmpty()) {
			List<String> writes = new ArrayList<>();
			for (Faces.Face.Mapped leaf : header.own()) {
				writes.add(faces.write(leaf, "encoder", header.encoder(), SOURCE.fill(leaf)));
			}
			methods.add(WRITE_HEADER.fill(header, "members", String.join("\n", writes)));
		}
		return String.join("\n\n", methods);
	}

	// ---- writing a block

	/**
	 * The block's groups refused as null, then its chain from {@code start}: a
	 * statement up to each group it opens, whose entries' method it goes on from,
	 * and the last, which returns the message's length, or leaves an entry to its
	 * group.
	 */
	private static String writes(Block block, String start, boolean message) {
		List<String> statements = new ArrayList<>();
		for (Variable.Group group : block.checks()) {
			statements.add(CHECK_GROUP.fill(group));
		}
		String from = start;
		List<Call> calls = new ArrayList<>();
		for (Call call : block.chain()) {
			if (!(call instanceof Call.Open open)) {
				calls.add(call);
				continue;
			}
			statements.add(
					calls.isEmpty()
							? OPEN_GROUP_AT_START.fill(open, "start", from)
							: OPEN_GROUP.fill(open, "start", from, "calls", calls(calls, false))
			);
			calls.clear();
			from = WRITE_GROUP_CALL.fill(open.group(), "source", source(open.group()), "local", open.local());
		}
		if (message) {
			statements.add(
					calls.isEmpty()
							? LENGTH_AT_START.fill("start", from)
							: CHAIN_LENGTH.fill("start", from, "calls", calls(calls, false))
			);
		} else {
			statements.add(
					calls.isEmpty()
							? END_AT_START.fill("start", from)
							: CHAIN_END.fill("start", from, "calls", calls(calls, true))
			);
		}
		return String.join("\n", statements);
	}

	/**
	 * The calls between two statements' ends, one a line; {@code last} where the
	 * final one, a step, ends the statement.
	 */
	private static String calls(List<Call> calls, boolean last) {
		List<String> lines = new ArrayList<>();
		for (int i = 0; i < calls.size(); i++) {
			boolean ending = last && i == calls.size() - 1;
			lines.add(switch (calls.get(i)) {
				case Call.Hop hop -> HOP.fill();
				case Call.Take take -> ending ? LAST_TAKE.fill(take) : TAKE.fill(take);
				case Call.Open open -> throw new IllegalStateException(open.local() + " is opened inside a statement");
			});
		}
		return String.join("\n", lines);
	}

	/** A group's entries as the writer takes them: through its binding, if any. */
	private static String source(Variable.Group group) {
		return group.binding() == null ? SOURCE.fill(group) : BOUND_GROUP_SOURCE.fill(group);
	}

	// ---- reading a block

	/**
	 * The constructor's arguments, one per component in declared order, a field's
	 * through the bound stage held in {@code variable}.
	 */
	private static String arguments(Block block, String variable) {
		List<String> arguments = new ArrayList<>();
		for (Component component : block.constructorOrder()) {
			arguments.add(switch (component) {
				case Component.Field field -> READ_FIELD.fill(field, "variable", variable);
				case Variable.Group group -> boundLocal(group, group.binding(), group.addedSince());
				case Variable.Data data -> LOCAL.fill(data);
			});
		}
		return String.join(",\n", arguments);
	}

	/**
	 * The block's groups and var-data read into locals, in wire order, before the
	 * constructor: the reader hands them out one after another.
	 */
	private static String variableReads(Block block) {
		List<String> reads = new ArrayList<>();
		for (Variable variable : block.variable()) {
			reads.add(switch (variable) {
				case Variable.Group group -> group.addedSince() == null
						? READ_GROUP_LOCAL.fill(group)
						: READ_ADDED_GROUP_LOCAL.fill(group);
				case Variable.Data data -> data.addedSince() == null
						? READ_DATA_LOCAL.fill(data)
						: READ_ADDED_DATA_LOCAL.fill(data);
			});
		}
		return String.join("\n", reads);
	}

	// ---- a group's methods

	/**
	 * The entries written with the entry's chain, read into a list sized by the
	 * count, and summed without encoding, each nested group and var-data through
	 * its own length.
	 */
	private static String groupMethods(CodecModel.Group group) {
		Variable.Group variable = group.group();
		Block entry = group.entry();
		List<String> terms = new ArrayList<>();
		for (Variable nested : entry.variable()) {
			terms.add(lengthTerm(NESTED_LENGTH_TERM, nested));
		}
		String length = terms.isEmpty()
				? GROUP_LENGTH.fill(variable, "length", lengthOf(variable.path()))
				: NESTED_GROUP_LENGTH.fill(
						variable, "length", lengthOf(variable.path()), "terms", String.join("\n", terms)
				);
		return String.join(
				"\n\n",
				WRITE_GROUP.fill(
						variable, "after", group.after(), "stage", group.stage(), "body",
						writes(entry, ENTRY_START.fill(), false)
				),
				READ_GROUP.fill(
						variable, "header", group.header(), "entry",
						entry.bound() == null ? PASS_ENTRY.fill() : READ_ENTRY.fill(entry), "reads",
						variableReads(entry), "arguments", arguments(entry, "entry")
				),
				length
		);
	}

	/**
	 * A group's or a data member's share of the length, through its method, as a
	 * term of the message's sum, or a statement in an entry's.
	 */
	private static String lengthTerm(Template term, Variable variable) {
		return switch (variable) {
			case Variable.Group group -> term.fill(
					group, "length", lengthOf(group.path()), "source",
					nullableSource(group, group.binding(), group.context())
			);
			case Variable.Data data -> term
					.fill(data, "source", nullableSource(data, data.binding(), data.context()));
		};
	}

	/**
	 * What a group or var-data member hands its length: the component, or its
	 * binding's view of it, a null component passed on as null for the length to
	 * refuse.
	 */
	private static String nullableSource(Variable variable, @Nullable String binding, @Nullable String context) {
		return binding == null
				? SOURCE.fill("component", variable.component())
				: NULLABLE_BOUND_COMPONENT.fill(
						"component", variable.component(), "binding", binding, "context", String.valueOf(context)
				);
	}

	/**
	 * A group's constructor argument: the local it was read into, or that local
	 * through its binding, an absent one staying null.
	 */
	private static String boundLocal(Record member, @Nullable String binding, @Nullable String addedSince) {
		if (binding == null) {
			return LOCAL.fill(member);
		}
		return addedSince == null ? BOUND_LOCAL.fill(member) : BOUND_ADDED_LOCAL.fill(member);
	}

	/** {@code legsLength} for the path {@code Legs}. */
	private static String lengthOf(String path) {
		return Character.toLowerCase(path.charAt(0)) + path.substring(1) + "Length";
	}
}
