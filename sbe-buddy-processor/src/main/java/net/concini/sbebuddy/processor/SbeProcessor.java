package net.concini.sbebuddy.processor;

import java.io.IOException;
import java.io.Writer;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.StandardLocation;

import org.jspecify.annotations.Nullable;

import net.concini.sbebuddy.SbeSchema;
import net.concini.sbebuddy.generator.Generator;
import net.concini.sbebuddy.generator.Mapping;
import net.concini.sbebuddy.generator.Problem;
import net.concini.sbebuddy.generator.Schema;
import net.concini.sbebuddy.generator.SchemaXml;

/**
 * The javac front-end: any annotated element in a round makes its package the
 * unit of work, handled once, in the round that first shows it. Per package:
 * discover, map, validate; every problem is an error on the element it names; a
 * package with none gets its {@code schema.xml} in the class output.
 */
@SupportedAnnotationTypes("net.concini.sbebuddy.*")
public final class SbeProcessor extends AbstractProcessor {

	private final Set<String> handled = new HashSet<>();

	@Override
	public SourceVersion getSupportedSourceVersion() {
		return SourceVersion.latest();
	}

	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
		if (round.processingOver()) {
			return false;
		}
		// Each package with the element that first showed it, where the missing
		// @SbeSchema is reported.
		Map<PackageElement, Element> packages = new LinkedHashMap<>();
		for (TypeElement annotation : annotations) {
			for (Element element : round.getElementsAnnotatedWith(annotation)) {
				packages.putIfAbsent(processingEnv.getElementUtils().getPackageOf(element), element);
			}
		}
		packages.forEach((schemaPackage, first) -> {
			if (handled.add(schemaPackage.getQualifiedName().toString())) {
				schema(schemaPackage, first);
			}
		});
		return false;
	}

	private void schema(PackageElement schemaPackage, Element first) {
		if (Discovery.annotation(schemaPackage, SbeSchema.class) == null) {
			error("package " + schemaPackage.getQualifiedName() + " carries no @SbeSchema", first, null);
			return;
		}
		Discovery.Discovered discovered = Discovery.discover(schemaPackage, processingEnv.getElementUtils());
		if (!discovered.problems().isEmpty()) {
			report(discovered.problems(), discovered, null, schemaPackage);
			return;
		}
		Mapping.Mapped mapped = Mapping.map(discovered.annotated());
		List<Problem> problems = mapped.problems().isEmpty() ? Generator.validate(mapped.schema()) : mapped.problems();
		if (!problems.isEmpty()) {
			report(problems, discovered, mapped, schemaPackage);
			return;
		}
		write(schemaPackage, mapped.schema());
	}

	/**
	 * A problem names an element, an annotated node or a schema node; the node is
	 * resolved in at most two lookups, and a node that resolves to nothing, which
	 * cannot happen, lands on the package.
	 */
	private void report(
			List<Problem> problems, Discovery.Discovered discovered, Mapping.@Nullable Mapped mapped,
			PackageElement schemaPackage
	) {
		for (Problem problem : problems) {
			Object node = problem.node();
			if (mapped != null) {
				Object origin = mapped.origins().get(node);
				node = origin == null ? node : origin;
			}
			Element element = node instanceof Element direct ? direct : discovered.elements().get(node);
			error(problem.message(), element == null ? schemaPackage : element, discovered.mirrors().get(node));
		}
	}

	private void error(String message, Element element, @Nullable AnnotationMirror mirror) {
		if (mirror == null) {
			processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, element);
		} else {
			processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, element, mirror);
		}
	}

	private void write(PackageElement schemaPackage, Schema schema) {
		try (
				Writer writer = processingEnv.getFiler()
						.createResource(
								StandardLocation.CLASS_OUTPUT, schemaPackage.getQualifiedName(), "schema.xml",
								schemaPackage
						)
						.openWriter()) {
			SchemaXml.write(schema, writer);
		} catch (IOException e) {
			error("could not write schema.xml: " + e.getMessage(), schemaPackage, null);
		}
	}
}
