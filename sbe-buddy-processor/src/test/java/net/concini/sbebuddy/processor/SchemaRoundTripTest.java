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
 * Every schema in the repository, the corpus and the example, goes round: the
 * records compiled code-first write their schema, and the same records compiled
 * schema-first over that schema generate the same flyweights and codecs, byte
 * for byte. The switch from one to the other is the one member spliced into
 * each {@code package-info}, and the schema each package wrote is served from a
 * class-path directory, as a checked-in resource would be.
 */
final class SchemaRoundTripTest {

	/** The modules' schema sources, relative to this module. */
	private static final List<Path> SOURCE_ROOTS = List
			.of(Path.of("../sbe-buddy-tests/src/main/java"), Path.of("../sbe-buddy-example/src/main/java"));

	/**
	 * What the modules' own schema-first packages read from the class path: the
	 * corpus's resources and the example's oracles.
	 */
	private static final List<Path> RESOURCE_ROOTS = List
			.of(Path.of("../sbe-buddy-tests/src/main/resources"), Path.of("../sbe-buddy-example/src/main/sbe"));

	@TempDir
	Path directory;

	@Test
	void everySchemaWrittenCodeFirstMapsTheSameRecordsSchemaFirst() throws IOException {
		List<Path> sources = new ArrayList<>();
		for (Path root : SOURCE_ROOTS) {
			try (Stream<Path> files = Files.walk(root)) {
				sources.addAll(files.filter(file -> file.toString().endsWith(".java")).toList());
			}
		}
		Compilation codeFirst = compile(directory.resolve("code-first"), sources, RESOURCE_ROOTS);
		assertThat(codeFirst.errors()).isEmpty();
		Map<String, String> schemas = codeFirst.schemas();
		assertThat(schemas).as("schemas written code-first").hasSizeGreaterThan(20);

		Path resources = directory.resolve("resources");
		schemas.forEach((path, schema) -> write(resources, path, schema));
		List<Path> schemaFirst = new ArrayList<>();
		for (Path source : sources) {
			schemaFirst.add(source.endsWith("package-info.java") ? readingItsSchema(source) : source);
		}
		List<Path> classpath = new ArrayList<>(RESOURCE_ROOTS);
		classpath.add(resources);
		Compilation again = compile(directory.resolve("schema-first"), schemaFirst, classpath);

		assertThat(again.errors()).isEmpty();
		assertThat(again.schemas()).as("nothing written schema-first").isEmpty();
		assertThat(again.generated().keySet()).isEqualTo(codeFirst.generated().keySet());
		codeFirst.generated()
				.forEach((path, source) -> assertThat(again.generated().get(path)).as(path).isEqualTo(source));
	}

	/**
	 * The package-info with {@code resource = "schema.xml"} spliced into its
	 * {@code @SbeSchema}, written under the temporary directory; one that names a
	 * resource already, the example's client, as it is.
	 */
	private Path readingItsSchema(Path packageInfo) throws IOException {
		String source = Files.readString(packageInfo);
		if (source.contains("resource =")) {
			return packageInfo;
		}
		String switched = source.replace("@SbeSchema(", "@SbeSchema(resource = \"schema.xml\", ");
		assertThat(switched).as(packageInfo.toString()).isNotEqualTo(source);
		return write(directory.resolve("switched"), packageInfo.toString().replace("..", "up"), switched);
	}

	/**
	 * What a compilation left behind: the diagnostics, the class output with the
	 * schemas in it, and the generated sources by path relative to the source
	 * output.
	 */
	private record Compilation(
			List<Diagnostic<? extends JavaFileObject>> diagnostics, Path classes, Map<String, String> generated
	) {

		List<Diagnostic<? extends JavaFileObject>> errors() {
			return diagnostics.stream().filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR).toList();
		}

		/** Every {@code schema.xml} written, by its path under the class output. */
		Map<String, String> schemas() throws IOException {
			return filesUnder(classes);
		}
	}

	/**
	 * Annotation processing only, the schemas written under the directory and the
	 * generated sources beside them, with the test classpath and the given
	 * directories visible.
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
			JavaCompiler.CompilationTask task = compiler.getTask(
					null, files, diagnostics, List.of("-proc:only"), null, files.getJavaFileObjectsFromPaths(units)
			);
			task.setProcessors(List.of(new SbeProcessor()));
			task.call();
		}
		return new Compilation(diagnostics.getDiagnostics(), classes, filesUnder(generated));
	}

	private static Map<String, String> filesUnder(Path root) throws IOException {
		Map<String, String> files = new TreeMap<>();
		try (Stream<Path> paths = Files.walk(root)) {
			for (Path path : paths.filter(Files::isRegularFile).toList()) {
				files.put(root.relativize(path).toString().replace(File.separatorChar, '/'), Files.readString(path));
			}
		}
		return files;
	}

	private static Path write(Path root, String relative, String content) {
		Path path = root.resolve(relative);
		try {
			Files.createDirectories(path.getParent());
			return Files.writeString(path, content);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}
}
