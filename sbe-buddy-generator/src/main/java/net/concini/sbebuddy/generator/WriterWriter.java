package net.concini.sbebuddy.generator;

import static net.concini.sbebuddy.generator.SubWriterTemplates.*;
import static net.concini.sbebuddy.generator.WriterStepTemplates.*;
import static net.concini.sbebuddy.generator.WriterTemplates.*;

import java.util.ArrayList;
import java.util.List;

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
 * A {@link WriterModel} as Java source, through {@link WriterTemplates},
 * {@link WriterStepTemplates} and {@link SubWriterTemplates}: one switch per
 * question, what a method does, how it checks, and how it hands on. Null values
 * are written through {@link FaceWriter}'s null writers, as the codec writes an
 * unmapped field.
 */
final class WriterWriter {

	private final String flyweights;
	private final String writer;
	private final String headerClass;
	private final FaceWriter faces;

	private WriterWriter(String flyweights, String writer, String headerClass) {
		this.flyweights = flyweights;
		this.writer = writer;
		this.headerClass = headerClass;
		this.faces = new FaceWriter(flyweights);
	}

	static String write(WriterModel.Message model) {
		return new WriterWriter(model.flyweights(), model.writer(), model.headerClass()).message(model);
	}

	static String write(WriterModel.Composite model) {
		return new WriterWriter(model.flyweights(), model.writer(), "").composite(model);
	}

	static String write(WriterModel.Set model) {
		List<String> choices = new ArrayList<>();
		for (String choice : model.choices()) {
			choices.add(CHOICE.fill("writer", model.writer(), "choice", choice));
		}
		return SET.fill(model, "choices", String.join("\n", choices));
	}

	private String message(WriterModel.Message model) {
		List<String> fields = new ArrayList<>();
		List<String> implementations = new ArrayList<>();
		for (Implementation implementation : model.implementations()) {
			fields.add(OBJECT_FIELD.fill(implementation));
			implementations.add(
					IMPLEMENTATION.fill(
							implementation, "stages", String.join(", ", implementation.stages()), "sites",
							sites(implementation.sites()), "methods", methods(implementation.methods(), true)
					)
			);
		}
		for (Field encoder : model.encoders()) {
			fields.add(ENCODER_FIELD.fill(encoder));
		}
		List<String> positions = new ArrayList<>();
		for (String position : model.positions()) {
			positions.add(POSITION.fill("name", position));
		}
		List<String> nulls = new ArrayList<>();
		for (Nulls one : model.nulls()) {
			nulls.add(NULLS.fill("encoder", one.encoder(), "writes", writes(one.encoder(), one.writes())));
		}
		return WRITER.fill(
				model,
				"stages", stages(model.stages(), false),
				"positions", String.join("\n", positions),
				"fields", String.join("\n", fields),
				"header", writes(model.flyweights() + "." + model.headerClass() + "Encoder", model.header()),
				"nulls", String.join("\n", nulls),
				"implementations", String.join("\n\n", implementations)
		);
	}

	private String composite(WriterModel.Composite model) {
		if (model.chain().isEmpty()) {
			return SHORT_COMPOSITE.fill(
					model, "sites", sites(model.sites()), "methods", methods(model.methods(), false)
			);
		}
		return COMPOSITE.fill(
				model,
				"stages", stages(model.stages(), true),
				"sites", sites(model.sites()),
				"methods", methods(model.methods(), false),
				"chain", String.join(", ", model.chain()),
				"chainMethods", methods(model.chainMethods(), true)
		);
	}

	private String stages(List<Stage> stages, boolean generic) {
		List<String> written = new ArrayList<>();
		for (Stage stage : stages) {
			List<String> methods = new ArrayList<>();
			for (Signature signature : stage.methods()) {
				methods.add(DECLARATION.fill(signature, "parameters", parameters(signature.parameters())));
			}
			Template doc = switch (stage.kind()) {
				case FIELD -> FIELD_DOC;
				case ROOT_BLOCK -> ROOT_BLOCK_DOC;
				case ENTRY -> ENTRY_DOC;
				case GROUP -> GROUP_DOC;
				case AFTER -> AFTER_DOC;
				case MEMBER -> MEMBER_DOC;
			};
			String javadoc = stage.kind() == Stage.Kind.ROOT_BLOCK ? doc.fill() : doc.fill("subject", stage.subject());
			Template template = generic
					? SubWriterTemplates.STAGE
					: stage.parents().isEmpty() ? WriterTemplates.STAGE : EXTENDING_STAGE;
			written.add(
					stage.parents().isEmpty()
							? template.fill(
									"javadoc", javadoc, "name", stage.name(), "methods", String.join("\n", methods)
							)
							: template.fill(
									"javadoc", javadoc, "name", stage.name(), "parents",
									String.join(", ", stage.parents()), "methods", String.join("\n", methods)
							)
			);
		}
		return String.join("\n\n", written);
	}

	private static String sites(List<Site> sites) {
		List<String> written = new ArrayList<>();
		for (Site site : sites) {
			written.add(SITE_FIELD.fill(site));
		}
		return String.join("\n", written);
	}

	private String writes(String encoder, List<NullWrite> writes) {
		List<String> written = new ArrayList<>();
		for (NullWrite write : writes) {
			written.add(faces.writeNull(encoder, write.property(), write.shape()));
		}
		return String.join("\n", written);
	}

	// ---- the methods

	private String methods(List<Method> methods, boolean overriding) {
		List<String> written = new ArrayList<>();
		for (Method method : methods) {
			written.add(method(method, overriding ? OVERRIDE.fill() : ""));
		}
		return String.join("\n", written);
	}

	/** One method: its guard, what it does, and how it hands on. */
	private String method(Method method, String annotation) {
		Signature signature = method.signature();
		String guard = "";
		String body = "";
		String then = "";
		switch (method) {
			case Method.Put put -> {
				guard = guard(put.guard());
				body = PUT.fill(
						"flyweight", put.flyweight(), "name", signature.name(), "arguments",
						String.join(", ", signature.parameters().stream().map(Parameter::name).toList())
				);
				then = then(put.then());
			}
			case Method.PutEach each -> {
				guard = guard(each.guard());
				body = PUT_EACH.fill(each);
				then = then(each.then());
			}
			case Method.PutNull putNull -> {
				guard = guard(putNull.guard());
				body = faces.writeNull(putNull.encoder(), putNull.write().property(), putNull.write().shape());
				then = then(putNull.then());
			}
			case Method.Sub sub -> {
				guard = guard(sub.guard());
				then = switch (sub.then()) {
					case Then.Return next -> SUB.fill(sub, "object", next.object());
					case Then.Move move -> throw new IllegalStateException("a sub-chain moves the writer nowhere");
					case Then.Release release -> SUB_RELEASE.fill(sub);
				};
			}
			case Method.Open open -> {
				guard = guard(open.guard());
				body = OPEN_GROUP.fill(open);
				then = then(open.then());
			}
			case Method.Entry entry -> {
				guard = guard(entry.guard());
				body = ENTRY.fill("encoder", entry.encoder(), "writer", writer);
				then = then(entry.then());
			}
			case Method.End end -> {
				guard = guard(end.guard());
				body = END.fill("encoder", end.encoder());
				then = then(end.then());
			}
			case Method.Length length -> {
				guard = guard(length.guard());
				then = LENGTH.fill("flyweights", flyweights, "headerClass", headerClass);
			}
			case Method.Delegate delegate ->
				then = DELEGATE.fill("object", delegate.object(), "name", signature.name());
		}
		return METHOD.fill(
				signature, "annotation", annotation, "parameters", parameters(signature.parameters()), "guard", guard,
				"body", body, "then", then
		);
	}

	private String guard(Guard guard) {
		return switch (guard) {
			case Guard.Current current -> CURRENT.fill(current, "writer", writer);
			case Guard.Open open -> OPEN.fill(open);
		};
	}

	private static String then(Then then) {
		return switch (then) {
			case Then.Return next -> RETURN.fill(next);
			case Then.Move move -> MOVE.fill(move);
			case Then.Release release -> RELEASE.fill();
		};
	}

	private static String parameters(List<Parameter> parameters) {
		List<String> written = new ArrayList<>();
		for (Parameter parameter : parameters) {
			written.add(WriterTemplates.PARAMETER.fill(parameter));
		}
		return String.join(", ", written);
	}
}
