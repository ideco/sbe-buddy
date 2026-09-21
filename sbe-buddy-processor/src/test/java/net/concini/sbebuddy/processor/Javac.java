package net.concini.sbebuddy.processor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.processing.Processor;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import net.concini.sbebuddy.generator.corpus.Corpus;

/**
 * javac in memory: sources as strings, the test classpath so the api is
 * visible, {@code -proc:only}, one processor, and everything the processor
 * wrote collected by path. Public so a test in another package can compile too.
 */
public final class Javac {

	/** The diagnostics javac produced and the files the processor wrote. */
	public record Result(List<Diagnostic<? extends JavaFileObject>> diagnostics, Map<String, String> outputs) {

		public List<Diagnostic<? extends JavaFileObject>> errors() {
			return diagnostics.stream().filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR).toList();
		}
	}

	private Javac() {
	}

	/** A corpus case's two units, compiled with the given processor. */
	public static Result compile(Corpus.Case aCase, Processor processor) {
		String packageInfo = aCase.packageInfo();
		String source = aCase.source();
		if (packageInfo == null || source == null) {
			throw new IllegalArgumentException(aCase.name() + " has no source");
		}
		String directory = aCase.annotated().packageName().replace('.', '/');
		return compile(
				List.of(unit(directory + "/package-info.java", packageInfo), unit(directory + "/Source.java", source)),
				processor
		);
	}

	public static Result compile(List<JavaFileObject> units, Processor processor) {
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
		Map<String, String> outputs = new LinkedHashMap<>();
		try (
				StandardJavaFileManager standard = compiler
						.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
				InMemoryOutput files = new InMemoryOutput(standard, outputs)) {
			List<String> options = List.of("-proc:only", "-classpath", System.getProperty("java.class.path"));
			JavaCompiler.CompilationTask task = compiler.getTask(null, files, diagnostics, options, null, units);
			task.setProcessors(List.of(processor));
			task.call();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		return new Result(new ArrayList<>(diagnostics.getDiagnostics()), outputs);
	}

	public static JavaFileObject unit(String path, String source) {
		return new SimpleJavaFileObject(URI.create("string:///" + path), JavaFileObject.Kind.SOURCE) {

			@Override
			public CharSequence getCharContent(boolean ignoreEncodingErrors) {
				return source;
			}
		};
	}

	/**
	 * Every file javac or the processor writes lands in the map, as text, and a
	 * generated source is readable again, because javac parses it in the next
	 * round.
	 */
	private static final class InMemoryOutput extends ForwardingJavaFileManager<StandardJavaFileManager> {

		private final Map<String, String> outputs;

		InMemoryOutput(StandardJavaFileManager standard, Map<String, String> outputs) {
			super(standard);
			this.outputs = outputs;
		}

		@Override
		public FileObject getFileForOutput(
				Location location, String packageName, String relativeName, FileObject sibling
		) {
			String path = packageName.isEmpty() ? relativeName : packageName.replace('.', '/') + "/" + relativeName;
			return new Written(path, JavaFileObject.Kind.OTHER);
		}

		@Override
		public JavaFileObject getJavaFileForOutput(
				Location location, String className, JavaFileObject.Kind kind, FileObject sibling
		) {
			return new Written(className.replace('.', '/') + kind.extension, kind);
		}

		private final class Written extends SimpleJavaFileObject {

			private final String path;
			private String content = "";

			Written(String path, Kind kind) {
				super(URI.create("memory:///" + path), kind);
				this.path = path;
			}

			@Override
			public OutputStream openOutputStream() {
				return new ByteArrayOutputStream() {

					@Override
					public void close() {
						content = toString(StandardCharsets.UTF_8);
						outputs.put(path, content);
					}
				};
			}

			@Override
			public CharSequence getCharContent(boolean ignoreEncodingErrors) {
				return content;
			}
		}
	}
}
