package net.concini.sbebuddy.generator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.agrona.generation.DynamicPackageOutputManager;
import org.jspecify.annotations.Nullable;

import uk.co.real_logic.sbe.ir.Ir;
import uk.co.real_logic.sbe.ir.Token;

/**
 * The typed flyweights beside sbe-tool's: a {@code <Message>Reader} for every
 * message of the IR, in template id order, walked by {@link FlyweightWalk} and
 * written by {@link ReaderWriter} into the flyweights' package. A message needs
 * no record to have one. Nothing is written while an error stands.
 */
public final class FlyweightEmitter {

	private FlyweightEmitter() {
	}

	/**
	 * Writes every message's reader through the output under the IR's namespace, or
	 * writes nothing and returns the problems. {@code schema} is the node a problem
	 * lands on where no record maps the message; {@code baseline} is the one the
	 * codecs read from. An output that fails to write is an
	 * {@link UncheckedIOException}.
	 */
	public static List<Problem> emit(
			Ir ir, Annotated annotated, int baseline, Object schema, DynamicPackageOutputManager output
	) {
		List<Problem> problems = new ArrayList<>();
		Map<String, String> sources = new LinkedHashMap<>();
		List<List<Token>> messages = new ArrayList<>(ir.messages());
		messages.sort(Comparator.comparingLong(tokens -> tokens.get(0).id()));
		for (List<Token> tokens : messages) {
			FlyweightModel model = FlyweightWalk
					.walk(ir, annotated, baseline, tokens, record(annotated, tokens.get(0).id()), schema, problems);
			if (model != null) {
				sources.put(model.reader(), ReaderWriter.write(model));
			}
		}
		if (problems.stream().anyMatch(Problem::isError)) {
			return problems;
		}
		for (Map.Entry<String, String> source : sources.entrySet()) {
			// Before every file: Agrona's manager falls back to the first package it
			// was given when a writer closes.
			output.setPackageName(ir.applicableNamespace());
			try (Writer writer = output.createOutput(source.getKey())) {
				writer.write(source.getValue());
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
		return problems;
	}

	private static Annotated.@Nullable Message record(Annotated annotated, int id) {
		for (Annotated.Message message : annotated.messages()) {
			if (message.id() == id) {
				return message;
			}
		}
		return null;
	}
}
