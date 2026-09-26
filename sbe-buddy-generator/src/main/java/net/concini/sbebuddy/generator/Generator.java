package net.concini.sbebuddy.generator;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;

import org.agrona.generation.DynamicPackageOutputManager;
import org.agrona.generation.StringWriterOutputManager;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.Document;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import uk.co.real_logic.sbe.generation.java.JavaGenerator;
import uk.co.real_logic.sbe.ir.Ir;
import uk.co.real_logic.sbe.xml.IrGenerator;
import uk.co.real_logic.sbe.xml.MessageSchema;
import uk.co.real_logic.sbe.xml.ParserOptions;
import uk.co.real_logic.sbe.xml.XmlSchemaParser;

/**
 * The rules of ours that compare nodes, which no single node can decide: names
 * and ids that collide, and versions that do not line up with the schema's or
 * with the siblings they follow, each problem naming the {@link Schema} node it
 * was decided from; and the pipeline from a schema through sbe-tool's own
 * toolchain to the flyweights, sbe-tool as the backstop behind those rules.
 */
public final class Generator {

	// Qualified because it collides with our Schema, the subject of this class.
	private static final javax.xml.validation.Schema XSD;

	static {
		try {
			XSD = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
					.newSchema(new StreamSource(Generator.class.getResourceAsStream("/fpl/sbe.xsd")));
		} catch (SAXException e) {
			throw new IllegalStateException(e);
		}
	}

	/** An XML document read from the class path, with the URI it was read from. */
	public record Resource(String text, String systemId) {
	}

	private final List<Problem> problems = new ArrayList<>();
	private final int version;

	private Generator(int version) {
		this.version = version;
	}

	public static List<Problem> validate(Schema schema) {
		Generator generator = new Generator(schema.version());
		generator.schema(schema);
		return List.copyOf(generator.problems);
	}

	/**
	 * Steps 3 to 7 of the pipeline: the document, validated against sbe.xsd and
	 * parsed by sbe-tool, then the IR under the schema package's {@code .sbe}
	 * namespace and the flyweights with SbeTool's defaults, then the join of the IR
	 * with the annotations, which applies the face rules and, unless the schema
	 * turned them off, writes the codecs under the schema package. Generation is
	 * all or nothing: every source is built in memory first and reaches the output
	 * only when there is no error; warnings come back with everything written.
	 * Whatever sbe-tool reports, warning or error, is a problem naming the schema
	 * with sbe-tool's text verbatim; a rule the join finds broken is a problem
	 * naming its node. An output that fails to write is an
	 * {@link UncheckedIOException}.
	 *
	 * <p>
	 * With a {@code baseline}, the document is held against it by
	 * {@link SchemaEvolution} once sbe-tool accepts it, each difference a problem
	 * on the node it is on, and the codecs read from the baseline's version; a
	 * baseline the XML parser or sbe.xsd refuses is a problem naming the schema.
	 * Without one, they read from version 0.
	 * </p>
	 */
	public static List<Problem> generate(
			Schema schema, Annotated annotated, @Nullable Resource baseline, DynamicPackageOutputManager output
	) {
		String document = document(schema);
		return generate(parse(document, schema, schema.packageName()), document, schema, baseline, annotated, output);
	}

	/**
	 * Steps 3 to 7 over a schema read from a resource: the document rendered from
	 * {@code schema} is compared with the resource, its XIncludes resolved against
	 * its URI, by {@link SchemaEquivalence}, and each difference is a problem on
	 * the schema node it is on, or on the schema where no node corresponds; with
	 * none, the resource is the document the rest of the pipeline takes, and the
	 * one held against the baseline. A partial package compares only what its
	 * records map. A resource the XML parser or sbe.xsd refuses is a problem naming
	 * the schema.
	 */
	public static List<Problem> generate(
			Schema schema, Resource resource, @Nullable Resource baseline, Annotated annotated,
			DynamicPackageOutputManager output
	) {
		String included;
		try {
			included = include(resource.text(), resource.systemId());
		} catch (SAXException e) {
			return List.of(new Problem(schema, String.valueOf(e.getMessage())));
		}
		List<SchemaEquivalence.Difference> differences;
		try {
			differences = SchemaEquivalence.differences(document(schema), included, annotated.partial());
		} catch (IllegalArgumentException e) {
			return List.of(new Problem(schema, "sbe.xsd: " + e.getMessage()));
		}
		if (!differences.isEmpty()) {
			return problems(schema, differences);
		}
		return generate(parse(included, schema, schema.packageName()), included, schema, baseline, annotated, output);
	}

	private static List<Problem> problems(Schema schema, List<SchemaEquivalence.Difference> differences) {
		List<Problem> problems = new ArrayList<>();
		for (SchemaEquivalence.Difference difference : differences) {
			problems.add(new Problem(node(schema, difference.path()), difference.message()));
		}
		return problems;
	}

	/** The version the codecs read from, or the problems that stop them. */
	private record Evolution(int baseline, List<Problem> problems) {
	}

	/**
	 * The document sbe-tool accepted held against the baseline, whose version the
	 * codecs then read from; what sbe.xsd refuses here is the baseline. A
	 * difference names the node its path reaches in {@code schema}, or the schema
	 * where the path leaves what the records map.
	 */
	private static Evolution evolution(String document, Schema schema, @Nullable Resource baseline) {
		if (baseline == null) {
			return new Evolution(0, List.of());
		}
		String included;
		List<SchemaEquivalence.Difference> differences;
		try {
			included = include(baseline.text(), baseline.systemId());
			differences = SchemaEvolution.differences(document, included);
		} catch (SAXException | IllegalArgumentException e) {
			return new Evolution(0, List.of(new Problem(schema, "the baseline: " + e.getMessage())));
		}
		return new Evolution(SchemaEvolution.version(included), problems(schema, differences));
	}

	/**
	 * The node a difference's path names, or the nearest one on the path that
	 * exists: a message the resource has and the schema lacks lands on the schema.
	 */
	private static Object node(Schema schema, List<SchemaEquivalence.Segment> path) {
		Object node = schema;
		for (SchemaEquivalence.Segment segment : path) {
			Object next = child(node, segment.element(), segment.name());
			if (next == null) {
				return node;
			}
			node = next;
		}
		return node;
	}

	private static @Nullable Object child(Object node, String element, String name) {
		return switch (node) {
			case Schema schema -> element.equals("message")
					? first(schema.messages(), message -> message.name().equals(name))
					: first(schema.types(), declaration -> wireName(declaration).equals(name));
			case Schema.Message message -> member(message.fields(), message.groups(), message.data(), name);
			case Schema.Group group -> member(group.fields(), group.groups(), group.data(), name);
			case Schema.Composite composite -> first(composite.members(), member -> memberName(member).equals(name));
			case Schema.Enum enumeration -> first(enumeration.validValues(), value -> value.name().equals(name));
			case Schema.Set set -> first(set.choices(), choice -> choice.name().equals(name));
			default -> null;
		};
	}

	private static @Nullable Object member(
			List<Schema.Field> fields, List<Schema.Group> groups, List<Schema.Data> data, String name
	) {
		Object member = first(fields, field -> field.name().equals(name));
		if (member == null) {
			member = first(groups, group -> group.name().equals(name));
		}
		return member == null ? first(data, datum -> datum.name().equals(name)) : member;
	}

	private static <T> @Nullable T first(List<T> nodes, Predicate<T> matches) {
		for (T node : nodes) {
			if (matches.test(node)) {
				return node;
			}
		}
		return null;
	}

	/**
	 * The document with its XIncludes resolved, as text again for the one path
	 * every schema takes; the fixups off, as sbe-tool has them, so nothing is added
	 * that sbe.xsd does not know.
	 */
	private static String include(String document, String systemId) throws SAXException {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		factory.setXIncludeAware(true);
		try {
			factory.setFeature("http://apache.org/xml/features/xinclude/fixup-base-uris", false);
			factory.setFeature("http://apache.org/xml/features/xinclude/fixup-language", false);
			DocumentBuilder builder = factory.newDocumentBuilder();
			builder.setErrorHandler(new Refusing());
			InputSource source = new InputSource(new StringReader(document));
			source.setSystemId(systemId);
			Document included = builder.parse(source);
			StringWriter writer = new StringWriter();
			TransformerFactory.newInstance().newTransformer()
					.transform(new DOMSource(included), new StreamResult(writer));
			return writer.toString();
		} catch (ParserConfigurationException | TransformerException e) {
			throw new IllegalStateException(e);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** Every report of the parser is a refusal; the default prints and goes on. */
	private static final class Refusing implements ErrorHandler {

		@Override
		public void warning(SAXParseException e) throws SAXException {
			throw e;
		}

		@Override
		public void error(SAXParseException e) throws SAXException {
			throw e;
		}

		@Override
		public void fatalError(SAXParseException e) throws SAXException {
			throw e;
		}
	}

	private static List<Problem> generate(
			Parsed parsed, String document, Schema schema, @Nullable Resource baseline, Annotated annotated,
			DynamicPackageOutputManager output
	) {
		Ir ir = parsed.ir();
		if (ir == null) {
			return parsed.problems();
		}
		Evolution evolution = evolution(document, schema, baseline);
		if (!evolution.problems().isEmpty()) {
			return evolution.problems();
		}
		StringWriterOutputManager staged = new StringWriterOutputManager();
		staged.setPackageName(ir.applicableNamespace());
		try {
			// The buffer types by name, as SbeTool passes them: naming the classes
			// would load them, and a user's javac must never load an Agrona buffer.
			new JavaGenerator(
					ir, "org.agrona.MutableDirectBuffer", "org.agrona.DirectBuffer", false, false, false, staged
			)
					.generate();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		List<Problem> problems = CodecEmitter.emit(ir, annotated, evolution.baseline(), staged);
		if (problems.stream().anyMatch(Problem::isError)) {
			return problems;
		}
		for (Map.Entry<String, CharSequence> source : staged.getSources().entrySet()) {
			// Keyed by qualified class name; the header flyweight, which sbe-tool opens
			// twice with one content, is one entry here and so one file there.
			int dot = source.getKey().lastIndexOf('.');
			output.setPackageName(source.getKey().substring(0, dot));
			try (Writer writer = output.createOutput(source.getKey().substring(dot + 1))) {
				writer.write(source.getValue().toString());
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
		return problems;
	}

	/**
	 * The IR of a schema sbe-tool accepts, for a test that feeds the codec emitter
	 * directly; one it rejects is an {@link IllegalArgumentException}.
	 */
	public static Ir ir(Schema schema) {
		Parsed parsed = parse(document(schema), schema, schema.packageName());
		Ir ir = parsed.ir();
		if (ir == null) {
			throw new IllegalArgumentException("sbe-tool rejects the schema: " + parsed.problems());
		}
		return ir;
	}

	/** The IR, or the problems that stopped sbe-tool short of it. */
	private record Parsed(@Nullable Ir ir, List<Problem> problems) {
	}

	/**
	 * Steps 4 and 5 over a document: sbe-tool's problems name {@code node}, and the
	 * IR's namespace is the schema package's {@code .sbe}.
	 */
	private static Parsed parse(String document, Object node, String packageName) {
		try {
			XSD.newValidator().validate(new StreamSource(new StringReader(document)));
		} catch (SAXException e) {
			return new Parsed(null, List.of(new Problem(node, "sbe.xsd: " + e.getMessage())));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		ByteArrayOutputStream reported = new ByteArrayOutputStream();
		ParserOptions options = ParserOptions.builder()
				.stopOnError(true)
				.warningsFatal(true)
				.suppressOutput(false)
				.errorPrintStream(new PrintStream(reported, true, StandardCharsets.UTF_8))
				.build();
		MessageSchema parsed;
		try {
			parsed = XmlSchemaParser
					.parse(new ByteArrayInputStream(document.getBytes(StandardCharsets.UTF_8)), options);
		} catch (Exception e) {
			// sbe-tool's parse declares Exception; every line it printed is a rule
			// it applied, and the exception repeats the first without its prefix.
			List<Problem> problems = new ArrayList<>();
			for (String line : reported.toString(StandardCharsets.UTF_8).split("\\R")) {
				if (!line.isBlank()) {
					problems.add(new Problem(node, line));
				}
			}
			return new Parsed(
					null, problems.isEmpty() ? List.of(new Problem(node, String.valueOf(e.getMessage()))) : problems
			);
		}
		return new Parsed(new IrGenerator().generate(parsed, packageName + ".sbe"), List.of());
	}

	private static String document(Schema schema) {
		StringWriter writer = new StringWriter();
		try {
			SchemaXml.write(schema, writer);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return writer.toString();
	}

	private void schema(Schema schema) {
		Set<String> names = new HashSet<>();
		for (Schema.Declaration declaration : schema.types()) {
			String name = wireName(declaration);
			if (!names.add(name)) {
				problem(declaration, "two declarations are named \"" + name + "\"");
			}
			declaration(declaration);
		}
		Set<String> messageNames = new HashSet<>();
		Set<Integer> messageIds = new HashSet<>();
		for (Schema.Message message : schema.messages()) {
			if (!messageNames.add(message.name())) {
				problem(message, "two messages are named \"" + message.name() + "\"");
			}
			if (!messageIds.add(message.id())) {
				problem(message, "two messages have id " + message.id());
			}
			versions(message, message.sinceVersion(), message.deprecated());
			block(message.fields(), message.groups(), message.data());
		}
	}

	private void declaration(Schema.Declaration declaration) {
		switch (declaration) {
			case Schema.Type type -> versions(type, type.sinceVersion(), type.deprecated());
			case Schema.Composite composite -> composite(composite);
			case Schema.Enum enumeration -> enumeration(enumeration);
			case Schema.Set set -> set(set);
		}
	}

	private void composite(Schema.Composite composite) {
		versions(composite, composite.sinceVersion(), composite.deprecated());
		appendOnly(composite.members(), member -> since(memberSinceVersion(member)));
		for (Schema.Member member : composite.members()) {
			switch (member) {
				case Schema.Type type -> versions(type, type.sinceVersion(), type.deprecated());
				case Schema.Ref ref -> versions(ref, ref.sinceVersion(), ref.deprecated());
				case Schema.Composite nested -> composite(nested);
				case Schema.Enum enumeration -> enumeration(enumeration);
				case Schema.Set set -> set(set);
			}
		}
	}

	private void enumeration(Schema.Enum enumeration) {
		versions(enumeration, enumeration.sinceVersion(), enumeration.deprecated());
		for (Schema.ValidValue value : enumeration.validValues()) {
			versions(value, value.sinceVersion(), value.deprecated());
		}
	}

	private void set(Schema.Set set) {
		versions(set, set.sinceVersion(), set.deprecated());
		for (Schema.Choice choice : set.choices()) {
			versions(choice, choice.sinceVersion(), choice.deprecated());
		}
	}

	/**
	 * A message's or a group's body: one namespace over the three lists, and each
	 * list appended to in version order, because each lays out in its own order.
	 */
	private void block(List<Schema.Field> fields, List<Schema.Group> groups, List<Schema.Data> data) {
		Set<String> names = new HashSet<>();
		Set<Integer> ids = new HashSet<>();
		for (Schema.Field field : fields) {
			member(field, field.name(), field.id(), names, ids);
			versions(field, field.sinceVersion(), field.deprecated());
		}
		for (Schema.Group group : groups) {
			member(group, group.name(), group.id(), names, ids);
			versions(group, group.sinceVersion(), group.deprecated());
			block(group.fields(), group.groups(), group.data());
		}
		for (Schema.Data datum : data) {
			member(datum, datum.name(), datum.id(), names, ids);
			versions(datum, datum.sinceVersion(), datum.deprecated());
		}
		appendOnly(fields, field -> since(field.sinceVersion()));
		appendOnly(groups, group -> since(group.sinceVersion()));
		appendOnly(data, datum -> since(datum.sinceVersion()));
	}

	private void member(Object node, String name, int id, Set<String> names, Set<Integer> ids) {
		if (!names.add(name)) {
			problem(node, "two members are named \"" + name + "\"");
		}
		if (!ids.add(id)) {
			problem(node, "two members have id " + id);
		}
	}

	private void versions(Object node, @Nullable Integer sinceVersion, @Nullable Integer deprecated) {
		int since = since(sinceVersion);
		if (since > version) {
			problem(node, "sinceVersion " + since + " is above the schema's version " + version);
		}
		if (deprecated != null && deprecated < since) {
			problem(node, "deprecated " + deprecated + " is below sinceVersion " + since);
		}
	}

	private <T> void appendOnly(List<T> siblings, ToIntFunction<T> sinceVersion) {
		int highest = 0;
		for (T sibling : siblings) {
			int since = sinceVersion.applyAsInt(sibling);
			if (since < highest) {
				problem(sibling, "sinceVersion " + since + " follows a sibling added in " + highest);
			}
			highest = Math.max(highest, since);
		}
	}

	private void problem(Object node, String message) {
		problems.add(new Problem(node, message));
	}

	private static int since(@Nullable Integer sinceVersion) {
		return sinceVersion == null ? 0 : sinceVersion;
	}

	private static String wireName(Schema.Declaration declaration) {
		return switch (declaration) {
			case Schema.Type type -> type.name();
			case Schema.Composite composite -> composite.name();
			case Schema.Enum enumeration -> enumeration.name();
			case Schema.Set set -> set.name();
		};
	}

	private static String memberName(Schema.Member member) {
		return switch (member) {
			case Schema.Type type -> type.name();
			case Schema.Ref ref -> ref.name();
			case Schema.Composite composite -> composite.name();
			case Schema.Enum enumeration -> enumeration.name();
			case Schema.Set set -> set.name();
		};
	}

	private static @Nullable Integer memberSinceVersion(Schema.Member member) {
		return switch (member) {
			case Schema.Type type -> type.sinceVersion();
			case Schema.Ref ref -> ref.sinceVersion();
			case Schema.Composite composite -> composite.sinceVersion();
			case Schema.Enum enumeration -> enumeration.sinceVersion();
			case Schema.Set set -> set.sinceVersion();
		};
	}
}
