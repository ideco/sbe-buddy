package net.concini.sbebuddy.generator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.agrona.generation.DynamicPackageOutputManager;
import org.jspecify.annotations.Nullable;

import uk.co.real_logic.sbe.ir.Ir;

/**
 * Step 7 of the pipeline, the join of the IR with the annotations: every
 * message walked by {@link CodecWalk}, which applies the face rules to each
 * token it meets, into a {@link CodecModel} written by {@link CodecWriter} as
 * {@code <Msg>Codec}, then one {@code <Union>Codec} per union, composed from
 * its members' codecs and written by {@link UnionWriter}. The walk runs whether
 * or not the schema wants codecs, for the rules; the codecs are written only
 * when it does. A construct the codec does not cover yet is a problem naming
 * the message; nothing is skipped silently, and nothing is written while an
 * error stands.
 */
public final class CodecEmitter {

	private CodecEmitter() {
	}

	/**
	 * Walks every message and, when the schema wants codecs, writes each message's
	 * and union's codec through the output under the schema package; or writes
	 * nothing and returns the problems: every rule broken in any message is
	 * collected, never a partial write. Warnings come back with everything written.
	 * An output that fails to write is an {@link UncheckedIOException}.
	 */
	public static List<Problem> emit(Ir ir, Annotated annotated, DynamicPackageOutputManager output) {
		Problems problems = new Problems();
		List<Problem> schema = new ArrayList<>();
		new StatedRules(schema, ir).schema(annotated);
		problems.addAll(schema);
		Map<String, String> sources = new LinkedHashMap<>();
		Map<Annotated.Message, CodecModel> models = new IdentityHashMap<>();
		for (Annotated.Message message : annotated.messages()) {
			List<Problem> walked = new ArrayList<>();
			CodecModel model = CodecWalk.walk(ir, annotated, message, walked);
			problems.addAll(walked);
			if (model != null) {
				models.put(message, model);
				sources.put(model.codec(), CodecWriter.write(model));
			}
		}
		if (!annotated.codecs()) {
			return problems.list();
		}
		problems.addAll(duplicateCodecs(annotated));
		for (Annotated.Union union : annotated.unions()) {
			List<Problem> unionProblems = new ArrayList<>();
			UnionModel model = union(annotated, union, models, unionProblems);
			problems.addAll(unionProblems);
			if (model != null) {
				sources.put(model.codec(), UnionWriter.write(model));
			}
		}
		if (problems.list().stream().anyMatch(Problem::isError)) {
			return problems.list();
		}
		for (Map.Entry<String, String> source : sources.entrySet()) {
			// Before every file: Agrona's manager falls back to the first package it
			// was given when a writer closes.
			output.setPackageName(annotated.packageName());
			try (Writer writer = output.createOutput(source.getKey())) {
				writer.write(source.getValue());
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
		return problems.list();
	}

	/**
	 * The problems of every message, each once: a composite, the header included,
	 * is walked by every message that uses it, and a rule its member breaks would
	 * otherwise be reported once per message. Nodes are told apart by identity,
	 * since two messages may hold fields of equal content.
	 */
	private static final class Problems {

		private final List<Problem> list = new ArrayList<>();
		private final Map<Object, Set<String>> seen = new IdentityHashMap<>();

		void addAll(List<Problem> problems) {
			for (Problem problem : problems) {
				if (seen.computeIfAbsent(problem.node(), node -> new HashSet<>())
						.add(problem.severity() + " " + problem.message())) {
					list.add(problem);
				}
			}
		}

		List<Problem> list() {
			return List.copyOf(list);
		}
	}

	/**
	 * A union's codec, composed from its members' models; null with a problem
	 * naming the union when a member has no codec.
	 */
	private static @Nullable UnionModel union(
			Annotated annotated, Annotated.Union union, Map<Annotated.Message, CodecModel> models,
			List<Problem> problems
	) {
		List<UnionModel.Case> cases = new ArrayList<>();
		CodecModel any = null;
		for (Annotated.Message member : union.members()) {
			CodecModel model = models.get(member);
			if (model == null) {
				problems.add(
						new Problem(
								union, "no codec for " + union.javaName() + ": its message " + member.javaName()
										+ " has none"
						)
				);
				continue;
			}
			any = model;
			cases.add(
					new UnionModel.Case(
							member.qualifiedName(), annotated.packageName() + "." + model.codec(),
							Character.toLowerCase(model.codec().charAt(0)) + model.codec().substring(1),
							model.flyweights() + "." + model.message() + "Decoder"
					)
			);
		}
		if (any == null || cases.size() < union.members().size()) {
			return null;
		}
		return new UnionModel(
				annotated.packageName(), union.javaName() + "Codec", union.qualifiedName(), union.javaName(),
				any.flyweights(), any.header(), cases
		);
	}

	/**
	 * Codecs are named by simple name in the schema package, so a union and a
	 * message, or two messages, nested in different types may both claim one.
	 */
	private static List<Problem> duplicateCodecs(Annotated annotated) {
		Map<String, List<Object>> claims = new LinkedHashMap<>();
		Map<Object, String> descriptions = new IdentityHashMap<>();
		for (Annotated.Message message : annotated.messages()) {
			claims.computeIfAbsent(message.javaName() + "Codec", name -> new ArrayList<>()).add(message);
			descriptions.put(message, "the message " + message.qualifiedName());
		}
		for (Annotated.Union union : annotated.unions()) {
			claims.computeIfAbsent(union.javaName() + "Codec", name -> new ArrayList<>()).add(union);
			descriptions.put(union, "the union " + union.qualifiedName());
		}
		List<Problem> problems = new ArrayList<>();
		for (Map.Entry<String, List<Object>> claim : claims.entrySet()) {
			if (claim.getValue().size() > 1) {
				List<String> sources = claim.getValue().stream().map(descriptions::get).toList();
				for (Object node : claim.getValue()) {
					problems.add(
							new Problem(
									node,
									claim.getKey() + " would be generated twice: for " + String.join(" and ", sources)
							)
					);
				}
			}
		}
		return problems;
	}
}
