package net.concini.sbebuddy.generator;

import static net.concini.sbebuddy.generator.CodecTemplates.*;
import static net.concini.sbebuddy.generator.FaceTemplates.*;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.concini.sbebuddy.generator.CodecModel.Body;
import net.concini.sbebuddy.generator.CodecModel.Helper;
import net.concini.sbebuddy.generator.CodecModel.Member;
import net.concini.sbebuddy.generator.Faces.Content;

/**
 * A {@link CodecModel} as Java source, through {@link CodecTemplates}, each
 * leaf through {@link FaceWriter}. Each question has one switch: how a member's
 * absence wraps its leaf, how a body is written and read, and what each of its
 * methods declares; a binding stands in front of the write and behind the read.
 * A template is filled from the model node it writes, and the names the node
 * does not hold are given beside it.
 */
final class CodecWriter {

	private final CodecModel model;
	private final FaceWriter faces;

	private CodecWriter(CodecModel model) {
		this.model = model;
		this.faces = new FaceWriter(model.flyweights());
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
				nulls.add(faces.writeNull(body.encoder(), unmapped));
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
				case Member.Field field -> write(body, field);
				case Member.Unmapped unmapped -> faces.writeNull(body.encoder(), unmapped);
				case Member.Group group -> ENCODE_CHECKED_FIELD.fill(
						group, "call",
						ENCODE_GROUP_FIELD.fill(
								group, "source", group.binding() == null ? SOURCE.fill(group) : BOUND_SOURCE.fill(group)
						)
				);
				case Member.Data data -> ENCODE_DATA_FIELD.fill(data, "source", nullableSource(data, data.binding()));
			});
		}
		return String.join("\n", writes);
	}

	/**
	 * Decided by the component: an optional field takes the null value for null,
	 * any other reference refuses it, and a primitive is written as it is.
	 */
	private String write(Body body, Member.Field field) {
		String source = field.binding() == null ? SOURCE.fill(field) : BOUND_SOURCE.fill(field);
		return switch (field.absence()) {
			case NONE -> faces.write(body, field, field.shape(), source);
			case REQUIRED, ADDED -> ENCODE_CHECKED_FIELD
					.fill(field, "call", faces.write(body, field, field.shape(), source));
			case NO_NULL_VALUE, ADDED_NO_NULL_VALUE -> field.binding() == null
					? ENCODE_NO_NULL_VALUE_FIELD.fill(field, "call", faces.write(body, field, field.shape(), source))
					: faces.write(body, field, field.shape(), source);
			case OPTIONAL -> faces.writeOptional(body, field, source);
		};
	}

	// ---- reading a body

	/** The constructor's arguments, one per component in declared order. */
	private String arguments(Body body) {
		List<String> arguments = new ArrayList<>();
		for (Member member : body.constructorOrder()) {
			arguments.add(switch (member) {
				case Member.Field field -> read(body, field);
				case Member.Group group -> boundLocal(group, group.binding(), group.addedSince());
				case Member.Data data -> boundLocal(data, data.binding(), data.addedSince());
				case Member.Unmapped unmapped -> throw notRead(unmapped.property());
			});
		}
		return String.join(",\n", arguments);
	}

	/**
	 * Decided by the wire: the null value for an optional field, whatever its
	 * version, since below the acting version the getter returns it; the version
	 * for a field appended above the baseline, which a required field may hold the
	 * null value of.
	 */
	private String read(Body body, Member.Field field) {
		String read = faces.read(field, field.shape());
		if (field.binding() != null) {
			// The null value and the version are decided before the binding is called.
			read = BOUND_READ.fill(field, "read", read);
		}
		return switch (field.absence()) {
			case NONE, REQUIRED, NO_NULL_VALUE -> read;
			case OPTIONAL -> DECODE_OPTIONAL_FIELD.fill("isNull", faces.isNull(body, field), "read", read);
			case ADDED, ADDED_NO_NULL_VALUE -> DECODE_ADDED_FIELD.fill(field, "decoder", body.decoder(), "read", read);
		};
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
					String read = data.content() == Content.BYTES ? DECODE_BYTES.fill(data) : DECODE_TEXT.fill(data);
					yield data.addedSince() == null
							? DECODE_DATA.fill(data, "face", face(data), "read", read)
							: DECODE_ADDED_DATA.fill(data, "face", face(data), "decoder", body.decoder(), "read", read);
				}
				case Member.Field field -> throw notVariable(field.component());
				case Member.Unmapped unmapped -> throw notVariable(unmapped.property());
			});
		}
		return String.join("\n", reads);
	}

	// ---- the helpers

	private String helper(Helper helper) {
		return switch (helper) {
			case Helper.Leaf leaf -> faces.helper(leaf.helper());
			case Helper.CompositePair composite -> String.join(
					"\n\n",
					WRITE_COMPOSITE.fill(
							composite, "encoder", composite.body().encoder(), "members", writes(composite.body())
					),
					READ_COMPOSITE.fill(
							composite, "decoder", composite.body().decoder(), "members", arguments(composite.body())
					)
			);
			case Helper.GroupMethods methods -> groupMethods(methods);
			case Helper.DataMethods methods -> dataMethods(methods);
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

	/**
	 * The length, write and read of one var-data member, each checking what its
	 * content needs.
	 */
	private String dataMethods(Helper.DataMethods methods) {
		Member.Data data = methods.data();
		String length = lengthOf(data.path());
		String count = switch (data.content()) {
			case BYTES -> COUNT_BYTES.fill(methods, "component", data.component());
			case ASCII -> COUNT_ASCII.fill(methods, "component", data.component());
			case UTF_8 -> COUNT_UTF_8.fill(methods, "component", data.component());
			case ENCODED -> COUNT_ENCODED
					.fill(methods, "component", data.component(), "charset", String.valueOf(data.charset()));
		};
		String lengthMethod = DATA_LENGTH.fill(
				methods, "length", length, "face", face(data), "component", data.component(), "property",
				data.property(), "count", count
		);
		if (data.content() == Content.ENCODED) {
			return String.join(
					"\n\n", lengthMethod,
					WRITE_ENCODED_TEXT.fill(
							methods, "path", data.path(), "component", data.component(), "charset",
							String.valueOf(data.charset())
					)
			);
		}
		if (data.content() != Content.BYTES) {
			return String.join(
					"\n\n", lengthMethod,
					WRITE_TEXT.fill(methods, "path", data.path(), "length", length, "property", data.property())
			);
		}
		return String.join(
				"\n\n", lengthMethod,
				WRITE_DATA_BYTES.fill(methods, "path", data.path(), "length", length),
				READ_DATA_BYTES.fill(methods, "path", data.path(), "property", data.property())
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
			case Member.Group group -> term
					.fill(group, "length", lengthOf(group.path()), "source", nullableSource(group, group.binding()));
			case Member.Data data -> term
					.fill(data, "length", lengthOf(data.path()), "source", nullableSource(data, data.binding()));
			case Member.Field field -> throw notVariable(field.component());
			case Member.Unmapped unmapped -> throw notVariable(unmapped.property());
		};
	}

	/**
	 * What a group or var-data member hands its method: the component, or its
	 * binding's view of it, a null component passed on as null for the method to
	 * refuse.
	 */
	private static String nullableSource(Record member, @Nullable String binding) {
		return binding == null ? SOURCE.fill(member) : NULLABLE_BOUND_SOURCE.fill(member);
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

	private static String face(Member.Data data) {
		return data.content() == Content.BYTES ? "byte[]" : "String";
	}

	private static IllegalStateException notVariable(String name) {
		return new IllegalStateException(name + " is in the block, not after it");
	}

	private static IllegalStateException notRead(String name) {
		return new IllegalStateException(name + " is read though no component carries it");
	}
}
