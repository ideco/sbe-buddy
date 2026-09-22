package net.concini.sbebuddy.generator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.agrona.generation.DynamicPackageOutputManager;

import uk.co.real_logic.sbe.ir.Ir;

/**
 * Step 7 of the pipeline: one {@code <Msg>Codec} per message, each walked into
 * a {@link CodecModel} by {@link CodecWalk} and written by {@link CodecWriter}.
 * A construct the codec does not cover yet is a problem naming the message;
 * nothing is skipped silently, and nothing is written while any problem stands.
 */
public final class CodecEmitter {

	private CodecEmitter() {
	}

	/**
	 * Writes each message's codec through the output under the schema package, or
	 * writes nothing and returns the problems: a construct the codec lacks in any
	 * message is collected, never a partial write. An output that fails to write is
	 * an {@link UncheckedIOException}.
	 */
	public static List<Problem> emit(Ir ir, Annotated annotated, DynamicPackageOutputManager output) {
		List<Problem> problems = new ArrayList<>();
		Map<String, String> sources = new LinkedHashMap<>();
		for (Annotated.Message message : annotated.messages()) {
			CodecModel model = CodecWalk.walk(ir, annotated, message, problems);
			if (model != null) {
				sources.put(model.codec(), CodecWriter.write(model));
			}
		}
		if (!problems.isEmpty()) {
			return List.copyOf(problems);
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
		return List.of();
	}
}
