package net.concini.sbebuddy.generator;

import static net.concini.sbebuddy.generator.CodecTemplates.*;
import static net.concini.sbebuddy.generator.FaceTemplates.*;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.concini.sbebuddy.generator.CodecModel.Body;
import net.concini.sbebuddy.generator.CodecModel.Helper;
import net.concini.sbebuddy.generator.CodecModel.Member;

/**
 * A {@link CodecModel} as Java source, through {@link CodecTemplates}, each
 * leaf, its absence and its binding through {@link FaceWriter}. Each question
 * has one switch: how a body is written and read, and what each of its methods
 * declares. A template is filled from the model node it writes, and the names
 * the node does not hold are given beside it.
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
		Body body = model.body();
		CodecModel.Header header = model.header();
		String headerClass = header.headerClass();
		List<String> bindings = new ArrayList<>();
		for (Faces.Binding binding : model.bindings()) {
			bindings.add(BINDING_FIELD.fill(binding));
		}
		List<String> contexts = new ArrayList<>();
		for (Faces.Context context : model.contexts()) {
			contexts.add(CONTEXT_FIELD.fill(context));
		}
		List<String> variableLengths = new ArrayList<>();
		for (Member member : variable(body)) {
			variableLengths.add(lengthTerm(LENGTH_TERM, member));
		}
		List<String> helpers = new ArrayList<>();
		for (Helper helper : model.helpers()) {
			helpers.add(helper(helper));
		}
		return CODEC.fill(
				model,
				"headerClass", headerClass,
				"headerRecord", header.record(),
				"bindings", String.join("\n", bindings),
				"contexts", String.join("\n", contexts),
				"variableLengths", String.join("", variableLengths),
				"writeNullHeader", header.nulls().isEmpty() ? "" : WRITE_NULL_HEADER_CALL.fill(),
				"writeHeader", header.body().wireOrder().isEmpty() ? "" : WRITE_HEADER_CALL.fill(),
				"encodeFields", writes(body),
				"atBaseline", model.baseline() == 0
						? ""
						: AT_BASELINE.fill("baseline", String.valueOf(model.baseline())),
				"refuseBelowBaseline", model.baseline() == 0
						? ""
						: REFUSE_BELOW_BASELINE.fill(model, "baseline", String.valueOf(model.baseline())),
				"decodeVariable", variableReads(body),
				"decodeFields", arguments(body),
				"decodedLength", variable(body).isEmpty()
						? BLOCK_DECODED_LENGTH.fill(model, "headerClass", headerClass)
						: WALKED_DECODED_LENGTH.fill(model, "headerClass", headerClass),
				"headerMethods", headerMethods(header),
				"helpers", helpers.isEmpty() ? "" : "\n" + String.join("\n\n", helpers)
		);
	}

	/**
	 * The header read whole, and, where it has members of its own, written from a
	 * header and as null.
	 */
	private String headerMethods(CodecModel.Header header) {
		Body body = header.body();
		List<String> methods = new ArrayList<>();
		methods.add(READ_HEADER.fill(header, "decoder", body.decoder(), "members", arguments(body)));
		if (!body.wireOrder().isEmpty()) {
			methods.add(WRITE_HEADER.fill(header, "encoder", body.encoder(), "members", writes(body)));
		}
		if (!header.nulls().isEmpty()) {
			List<String> nulls = new ArrayList<>();
			for (Member.Unmapped unmapped : header.nulls()) {
				nulls.add(faces.writeNull(body.encoder(), unmapped.property(), unmapped.shape()));
			}
			methods.add(WRITE_NULL_HEADER.fill("encoder", body.encoder(), "members", String.join("\n", nulls)));
		}
		return String.join("\n\n", methods);
	}

	// ---- writing a body

	/** The body's members in wire order, as encode statements. */
	private String writes(Body body) {
		List<String> writes = new ArrayList<>();
		for (Member member : body.wireOrder()) {
			writes.add(switch (member) {
				case Member.Field field -> faces
						.write(field.leaf(), "encoder", body.encoder(), SOURCE.fill(field.leaf()));
				case Member.Unmapped unmapped -> faces.writeNull(body.encoder(), unmapped.property(), unmapped.shape());
				case Member.Group group -> ENCODE_CHECKED_FIELD.fill(
						group, "value", SOURCE.fill(group), "call",
						ENCODE_GROUP_FIELD.fill(
								group, "source",
								group.binding() == null
										? SOURCE.fill(group)
										: BOUND_SOURCE.fill(group, "value", SOURCE.fill(group))
						)
				);
				case Member.Data data -> faces
						.writeData(data.leaf(), data.binding(), data.context(), "encoder", SOURCE.fill(data));
			});
		}
		return String.join("\n", writes);
	}

	// ---- reading a body

	/** The constructor's arguments, one per component in declared order. */
	private String arguments(Body body) {
		List<String> arguments = new ArrayList<>();
		for (Member member : body.constructorOrder()) {
			arguments.add(switch (member) {
				case Member.Field field -> faces.read(field.leaf(), "decoder", body.decoder());
				case Member.Group group -> boundLocal(group, group.binding(), group.addedSince());
				case Member.Data data -> boundLocal(data, data.binding(), data.addedSince());
				case Member.Unmapped unmapped -> throw notRead(unmapped.property());
			});
		}
		return String.join(",\n", arguments);
	}

	/**
	 * The body's groups and var-data read into locals, in wire order, before the
	 * constructor: the flyweight reads them one after another.
	 */
	private String variableReads(Body body) {
		List<String> reads = new ArrayList<>();
		for (Member member : variable(body)) {
			reads.add(switch (member) {
				case Member.Group group -> group.addedSince() == null
						? DECODE_GROUP.fill(group)
						: DECODE_ADDED_GROUP.fill(group, "decoder", body.decoder());
				case Member.Data data -> {
					String read = faces.readData(data.leaf(), "decoder");
					String face = FaceWriter.face(data.leaf().content());
					yield data.addedSince() == null
							? DECODE_DATA.fill(data, "face", face, "read", read)
							: DECODE_ADDED_DATA.fill(data, "face", face, "decoder", body.decoder(), "read", read);
				}
				case Member.Field field -> throw notVariable(field.leaf().component());
				case Member.Unmapped unmapped -> throw notVariable(unmapped.property());
			});
		}
		return String.join("\n", reads);
	}

	// ---- the helpers

	private String helper(Helper helper) {
		return switch (helper) {
			case Helper.Leaf leaf -> faces.helper(leaf.helper());
			case Helper.GroupMethods methods -> groupMethods(methods);
		};
	}

	/**
	 * The entries written with the entry's encode statements, read into a list
	 * sized by the count, and summed without encoding, each nested group through
	 * its own length.
	 */
	private String groupMethods(Helper.GroupMethods methods) {
		Member.Group group = methods.group();
		Body entry = group.entry();
		List<String> terms = new ArrayList<>();
		for (Member member : variable(entry)) {
			terms.add(lengthTerm(NESTED_LENGTH_TERM, member));
		}
		String length = terms.isEmpty()
				? GROUP_LENGTH.fill(group, "length", lengthOf(group.path()), "encoder", entry.encoder())
				: NESTED_GROUP_LENGTH.fill(
						group, "length", lengthOf(group.path()), "encoder", entry.encoder(), "terms",
						String.join("\n", terms)
				);
		return String.join(
				"\n\n",
				WRITE_GROUP.fill(group, "encoder", entry.encoder(), "parent", methods.parent(), "body", writes(entry)),
				READ_GROUP.fill(
						group, "decoder", entry.decoder(), "reads", variableReads(entry), "arguments", arguments(entry)
				),
				length
		);
	}

	/** The members that follow the block, groups and var-data, in wire order. */
	private static List<Member> variable(Body body) {
		List<Member> variable = new ArrayList<>();
		for (Member member : body.wireOrder()) {
			if (member instanceof Member.Group || member instanceof Member.Data) {
				variable.add(member);
			}
		}
		return variable;
	}

	/**
	 * A group's or a data member's share of the length, through its method, as a
	 * term of the message's sum, or a statement in an entry's.
	 */
	private static String lengthTerm(Template term, Member member) {
		return switch (member) {
			case Member.Group group -> term.fill(
					group, "length", lengthOf(group.path()), "source",
					nullableSource(group, group.binding(), group.context())
			);
			case Member.Data data -> term.fill(
					data, "length", data.leaf().length(), "source", nullableSource(data, data.binding(), data.context())
			);
			case Member.Field field -> throw notVariable(field.leaf().component());
			case Member.Unmapped unmapped -> throw notVariable(unmapped.property());
		};
	}

	/**
	 * What a group or var-data member hands its method: the component, or its
	 * binding's view of it, a null component passed on as null for the method to
	 * refuse.
	 */
	private static String nullableSource(Record member, @Nullable String binding, @Nullable String context) {
		return binding == null
				? SOURCE.fill(member)
				: NULLABLE_BOUND_SOURCE
						.fill("value", SOURCE.fill(member), "binding", binding, "context", String.valueOf(context));
	}

	/**
	 * A group's or var-data's constructor argument: the local it was read into, or
	 * that local through its binding, an absent one staying null.
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

	private static IllegalStateException notVariable(String name) {
		return new IllegalStateException(name + " is in the block, not after it");
	}

	private static IllegalStateException notRead(String name) {
		return new IllegalStateException(name + " is read though no component carries it");
	}
}
