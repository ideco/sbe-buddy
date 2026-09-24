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

/**
 * javac in memory: sources as strings, the test classpath so the api is
 * visible, {@code -proc:only}, one processor, and everything the processor
 * wrote collected by path. Public so a test in another package can compile too.
 */
public final class Javac {

	/** The diagnostics javac produced and the files the processor wrote. */
	public record Result(List<Diagnostic<? extends JavaFileObject>> diagnostics, Map<String, String> outputs) {

		public List<Diagnostic<? extends JavaFileObject>> errors() {
			return ofKind(Diagnostic.Kind.ERROR);
		}

		public List<Diagnostic<? extends JavaFileObject>> warnings() {
			return ofKind(Diagnostic.Kind.WARNING);
		}

		private List<Diagnostic<? extends JavaFileObject>> ofKind(Diagnostic.Kind kind) {
			return diagnostics.stream().filter(diagnostic -> diagnostic.getKind() == kind).toList();
		}
	}

	private Javac() {
	}

	public static Result compile(List<JavaFileObject> units, Processor processor) {
		return compile(units, processor, List.of("-proc:only"));
	}

	/**
	 * With options of the test's own in place of {@code -proc:only}, for what javac
	 * reports only when it compiles, such as a deprecated member's use.
	 */
	public static Result compile(List<JavaFileObject> units, Processor processor, List<String> compilerOptions) {
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
		Map<String, String> outputs = new LinkedHashMap<>();
		try (
				StandardJavaFileManager standard = compiler
						.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
				InMemoryOutput files = new InMemoryOutput(standard, outputs)) {
			List<String> options = new ArrayList<>(compilerOptions);
			options.addAll(List.of("-classpath", System.getProperty("java.class.path")));
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
