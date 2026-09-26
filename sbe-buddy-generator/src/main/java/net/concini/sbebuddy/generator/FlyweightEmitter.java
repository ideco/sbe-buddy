package net.concini.sbebuddy.generator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.agrona.generation.DynamicPackageOutputManager;
import org.jspecify.annotations.Nullable;

import uk.co.real_logic.sbe.ir.Ir;
import uk.co.real_logic.sbe.ir.Token;

/**
 * The typed flyweights over sbe-tool's: a {@code <Message>Reader} and a
 * {@code <Message>Writer} for every message of the IR, in template id order,
 * walked by {@link FlyweightWalk} and {@link WriterWalk} and written by
 * {@link ReaderWriter} and {@link WriterWriter} into the schema's package,
 * beside the codecs and the records their bound stages name, then a sub-chain
 * for every composite and set the writers open. A message needs no record to
 * have them. Nothing is written while an error stands.
 */
public final class FlyweightEmitter {

	private FlyweightEmitter() {
	}

	/**
	 * Writes every message's reader and writer, and the sub-chains, through the
	 * output under the IR's namespace, or writes nothing and returns the problems.
	 * {@code schema} is the node a problem lands on where no record maps the
	 * message; {@code baseline} is the one the codecs read from. An output that
	 * fails to write is an {@link UncheckedIOException}.
	 */
	public static List<Problem> emit(
			Ir ir, Annotated annotated, int baseline, Object schema, DynamicPackageOutputManager output
	) {
		List<Problem> problems = new ArrayList<>();
		Map<String, String> sources = new LinkedHashMap<>();
		List<List<Token>> messages = new ArrayList<>(ir.messages());
		messages.sort(Comparator.comparingLong(tokens -> tokens.get(0).id()));
		Set<String> composites = new LinkedHashSet<>();
		Set<String> sets = new LinkedHashSet<>();
		for (List<Token> tokens : messages) {
			Annotated.Message record = record(annotated, tokens.get(0).id());
			FlyweightModel model = FlyweightWalk.walk(ir, annotated, baseline, tokens, record, schema, problems);
			if (model == null) {
				continue;
			}
			sources.put(model.reader(), ReaderWriter.write(model));
			WriterModel.Message writer = WriterWalk
					.walk(ir, annotated, baseline, tokens, record, schema, problems, composites, sets);
			if (writer != null) {
				sources.put(writer.writer(), WriterWriter.write(writer));
			}
		}
		// A composite's members may open further composites, which join the end.
		Set<String> walked = new HashSet<>();
		while (walked.size() < composites.size()) {
			for (String typeName : List.copyOf(composites)) {
				if (walked.add(typeName)) {
					WriterModel.Composite composite = WriterWalk
							.composite(ir, annotated, typeName, schema, problems, composites, sets);
					if (composite != null) {
						sources.put(composite.writer(), WriterWriter.write(composite));
					}
				}
			}
		}
		for (String typeName : sets) {
			WriterModel.Set set = WriterWalk.set(ir, annotated.packageName(), typeName);
			sources.put(set.writer(), WriterWriter.write(set));
		}
		if (problems.stream().anyMatch(Problem::isError)) {
			return problems;
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
