package net.concini.sbebuddy.processor;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The processor takes the package of any annotated element as its unit of work,
 * so an incremental build that recompiles one record must regenerate the whole
 * package, reading the rest from the class files of the previous build. Proven
 * with a real javac writing real files: the package compiled whole, then one
 * record alone against the first compilation's class output.
 */
final class IncrementalCompilationTest {

	private static final String PACKAGE_INFO = """
			@SbeSchema(id = 7, version = 0)
			package incremental;

			import net.concini.sbebuddy.SbeSchema;
			""";

	private static final String QUOTE = """
			package incremental;

			import net.concini.sbebuddy.SbeField;
			import net.concini.sbebuddy.SbeMessage;

			@SbeMessage(id = 1)
			public record Quote(
					@SbeField(id = 1) long instrumentId,
					@SbeField(id = 2) long bid,
					@SbeField(id = 3) long ask
			) {
			}
			""";

	private static final String TRADE = """
			package incremental;

			import net.concini.sbebuddy.SbeField;
			import net.concini.sbebuddy.SbeMessage;

			@SbeMessage(id = 2)
			public record Trade(
					@SbeField(id = 1) long instrumentId,
					@SbeField(id = 2) int quantity
			) {
			}
			""";

	@TempDir
	Path directory;

	@Test
	void recompilingOneRecordRegeneratesTheWholePackage() throws IOException {
		Path sources = directory.resolve("src");
		Path packageInfo = write(sources, "incremental/package-info.java", PACKAGE_INFO);
		Path quote = write(sources, "incremental/Quote.java", QUOTE);
		Path trade = write(sources, "incremental/Trade.java", TRADE);

		Compilation whole = compile(directory.resolve("whole"), List.of(packageInfo, quote, trade), List.of());

		assertThat(whole.errors()).isEmpty();
		assertThat(whole.generated()).containsKeys(
				"incremental/QuoteCodec.java", "incremental/TradeCodec.java", "incremental/sbe/QuoteEncoder.java",
				"incremental/sbe/TradeDecoder.java", "incremental/sbe/MessageHeaderEncoder.java"
		);
		assertThat(whole.schema()).contains("name=\"Quote\"", "name=\"Trade\"");

		// Only Trade.java changed: package-info and Quote come from the class files.
		Compilation one = compile(directory.resolve("one"), List.of(trade), List.of(whole.classes()));

		assertThat(one.errors()).isEmpty();
		assertThat(one.generated().keySet()).isEqualTo(whole.generated().keySet());
		whole.generated().forEach((path, source) -> assertThat(one.generated().get(path)).as(path).isEqualTo(source));
		assertThat(one.schema()).isEqualTo(whole.schema());
		// And the generated sources compiled, against Quote from its class file.
		assertThat(one.classes().resolve("incremental/QuoteCodec.class")).exists();
		assertThat(one.classes().resolve("incremental/sbe/QuoteEncoder.class")).exists();
	}

	/**
	 * What a compilation left behind: the diagnostics, the class output, and the
	 * generated sources by path relative to the source output.
	 */
	private record Compilation(
			List<Diagnostic<? extends JavaFileObject>> diagnostics, Path classes, Map<String, String> generated
	) {

		List<Diagnostic<? extends JavaFileObject>> errors() {
			return diagnostics.stream().filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR).toList();
		}

		String schema() throws IOException {
			return Files.readString(classes.resolve("incremental/schema.xml"));
		}
	}

	/**
	 * A full compilation, classes and generated sources written under the
	 * directory, with the test classpath and the given class directories visible.
	 */
	private static Compilation compile(Path directory, List<Path> units, List<Path> classpath) throws IOException {
		Path classes = Files.createDirectories(directory.resolve("classes"));
		Path generated = Files.createDirectories(directory.resolve("generated"));
		List<Path> fullClasspath = new ArrayList<>(classpath);
		for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
			fullClasspath.add(Path.of(entry));
		}
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
		try (
				StandardJavaFileManager files = compiler
						.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
			files.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(classes));
			files.setLocationFromPaths(StandardLocation.SOURCE_OUTPUT, List.of(generated));
			files.setLocationFromPaths(StandardLocation.CLASS_PATH, fullClasspath);
			JavaCompiler.CompilationTask task = compiler
					.getTask(null, files, diagnostics, List.of(), null, files.getJavaFileObjectsFromPaths(units));
			task.setProcessors(List.of(new SbeProcessor()));
			task.call();
		}
		return new Compilation(diagnostics.getDiagnostics(), classes, sourcesUnder(generated));
	}

	private static Map<String, String> sourcesUnder(Path root) throws IOException {
		Map<String, String> sources = new TreeMap<>();
		try (Stream<Path> paths = Files.walk(root)) {
			for (Path path : paths.filter(Files::isRegularFile).toList()) {
				sources.put(root.relativize(path).toString().replace(File.separatorChar, '/'), Files.readString(path));
			}
		}
		return sources;
	}

	private static Path write(Path root, String relative, String content) throws IOException {
		Path path = root.resolve(relative);
		Files.createDirectories(path.getParent());
		return Files.writeString(path, content);
	}
}
