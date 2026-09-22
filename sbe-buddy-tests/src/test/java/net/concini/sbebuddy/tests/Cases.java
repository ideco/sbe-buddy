package net.concini.sbebuddy.tests;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Every {@link SchemaCase} compiled into this module's tests, found by walking
 * the class files beside this class, so a new schema needs no registration.
 * Ordered by class name, so a run is the same run every time.
 */
final class Cases {

	private Cases() {
	}

	static List<SchemaCase> discover() {
		Path root = testClasses();
		try (Stream<Path> files = Files.walk(root)) {
			return files.filter(file -> file.toString().endsWith(".class"))
					.map(file -> className(root, file))
					.filter(name -> !name.endsWith("package-info"))
					.sorted()
					.map(Cases::load)
					.filter(
							type -> SchemaCase.class.isAssignableFrom(type) && !Modifier.isAbstract(type.getModifiers())
					)
					.map(Cases::instantiate)
					.toList();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static Path testClasses() {
		try {
			return Path.of(SchemaCase.class.getProtectionDomain().getCodeSource().getLocation().toURI());
		} catch (URISyntaxException e) {
			throw new IllegalStateException(e);
		}
	}

	private static String className(Path root, Path file) {
		String relative = root.relativize(file).toString();
		return relative.substring(0, relative.length() - ".class".length())
				.replace(root.getFileSystem().getSeparator(), ".");
	}

	private static Class<?> load(String name) {
		try {
			return Class.forName(name, false, Cases.class.getClassLoader());
		} catch (ClassNotFoundException e) {
			throw new IllegalStateException(name + " is on the classpath but does not load", e);
		}
	}

	/** A case may be package-private, as test classes are; its constructor too. */
	private static SchemaCase instantiate(Class<?> type) {
		try {
			Constructor<?> constructor = type.getDeclaredConstructor();
			constructor.setAccessible(true);
			return (SchemaCase) constructor.newInstance();
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(type.getName() + " needs a no-arg constructor", e);
		}
	}
}
