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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

import net.concini.sbebuddy.generator.SchemaXmlAssert;

/**
 * Every schema in the repository, the corpus and the example, goes round in
 * both directions: the records compiled code-first write their schema, and the
 * same records compiled schema-first over that schema generate the same
 * flyweights and codecs, byte for byte; a package that is schema-first in the
 * repository is compiled code-first with its resource spliced out and writes
 * its resource back, equivalent. The switch is the one member spliced into each
 * {@code package-info}, and the schema each package wrote is served from a
 * class-path directory, as a checked-in resource would be. A partial package
 * reads its resource in both runs, and generates the same sources in both.
 */
final class SchemaRoundTripTest {

	/** The modules' schema sources and, beside each, the module's resources. */
	private static final Map<Path, Path> ROOTS = Map.of(
			Path.of("../sbe-buddy-tests/src/main/java"), Path.of("../sbe-buddy-tests/src/main/resources"),
			Path.of("../sbe-buddy-example/src/main/java"), Path.of("../sbe-buddy-example/src/main/resources")
	);

	@TempDir
	Path directory;

	@Test
	void everySchemaGoesRoundInBothDirections() throws IOException {
		List<Path> codeFirst = new ArrayList<>();
		Map<String, Path> frozen = new TreeMap<>();
		for (Map.Entry<Path, Path> root : ROOTS.entrySet()) {
			try (Stream<Path> files = Files.walk(root.getKey())) {
				for (Path source : files.filter(file -> file.toString().endsWith(".java")).toList()) {
					codeFirst.add(
							source.endsWith("package-info.java") ? writingItsSchema(source, root, frozen) : source
					);
				}
			}
		}
		// The modules' resources on the class path, where a baseline is read in both
		// runs, as a build puts them there.
		List<Path> classpath = ROOTS.values().stream().filter(Files::isDirectory).toList();
		Compilation written = compile(directory.resolve("code-first"), codeFirst, classpath);
		assertThat(written.errors()).isEmpty();
		Map<String, String> schemas = written.schemas();
		assertThat(schemas).as("schemas written code-first").hasSizeGreaterThan(20);
		for (Map.Entry<String, Path> resource : frozen.entrySet()) {
			String schema = schemas.get(resource.getKey());
			assertThat(schema).as("the schema written for " + resource.getKey()).isNotNull();
			SchemaXmlAssert.assertThat(schema).matches(Files.readString(resource.getValue()));
		}

		Path resources = directory.resolve("resources");
		schemas.forEach((path, schema) -> write(resources, path, schema));
		List<Path> schemaFirst = new ArrayList<>();
		for (Path source : codeFirst) {
			schemaFirst.add(source.endsWith("package-info.java") ? readingItsSchema(source) : source);
		}
		List<Path> readable = new ArrayList<>(List.of(resources));
		readable.addAll(classpath);
		Compilation again = compile(directory.resolve("schema-first"), schemaFirst, readable);

		assertThat(again.errors()).isEmpty();
		assertThat(again.schemas()).as("nothing written schema-first").isEmpty();
		assertThat(again.generated().keySet()).isEqualTo(written.generated().keySet());
		written.generated()
				.forEach((path, source) -> assertThat(again.generated().get(path)).as(path).isEqualTo(source));
	}

	/**
	 * The package-info as it is, or with its {@code resource} spliced out of its
	 * {@code @SbeSchema}, that resource noted under the path its schema is written
	 * to, so the schema written code-first can be held against it. A partial
	 * package stays as it is: its records cannot write the whole resource.
	 */
	private Path writingItsSchema(Path packageInfo, Map.Entry<Path, Path> root, Map<String, Path> frozen) {
		String source = read(packageInfo);
		Matcher resource = RESOURCE.matcher(source);
		if (!resource.find() || source.contains(PARTIAL)) {
			return packageInfo;
		}
		Path packageDirectory = root.getKey().relativize(packageInfo.getParent());
		frozen.put(
				packageDirectory.toString().replace(File.separatorChar, '/') + "/schema.xml",
				root.getValue().resolve(packageDirectory).resolve(resource.group(2))
		);
		// A member in the middle of the list keeps one of its two commas.
		String stripped = resource.replaceFirst(resource.group(1) != null && resource.group(3) != null ? ", " : "");
		return write(directory.resolve("code-first-sources"), packageDirectory.resolve("package-info.java"), stripped);
	}

	/**
	 * The package-info with {@code resource = "schema.xml"} spliced into its
	 * {@code @SbeSchema}, written under the temporary directory; a partial package
	 * as it is, reading its own resource in both runs.
	 */
	private Path readingItsSchema(Path packageInfo) {
		String source = read(packageInfo);
		if (source.contains(PARTIAL)) {
			return packageInfo;
		}
		String switched = source.replace("@SbeSchema(", "@SbeSchema(resource = \"schema.xml\", ");
		assertThat(switched).as(packageInfo.toString()).isNotEqualTo(source);
		return write(directory.resolve("schema-first-sources"), packageInfo.toString().replace("..", "up"), switched);
	}

	private static final String PARTIAL = "partial = true";

	/** {@code resource = "…"} in a member list, with the comma either side. */
	private static final Pattern RESOURCE = Pattern.compile("(, )?resource = \"([^\"]+)\"(, )?");

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

	private static String read(Path file) {
		try {
			return Files.readString(file);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private static Path write(Path root, Path relative, String content) {
		return write(root, relative.toString(), content);
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
