package net.concini.sbebuddy.generator;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.ToIntFunction;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;

import org.agrona.generation.DynamicPackageOutputManager;
import org.jspecify.annotations.Nullable;
import org.xml.sax.SAXException;

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
	 * Steps 3 to 6 of the pipeline: the document, validated against sbe.xsd and
	 * parsed by sbe-tool, then the IR under the schema package's {@code .sbe}
	 * namespace and the flyweights through the output, with SbeTool's defaults.
	 * Whatever sbe-tool reports, warning or error, is a problem naming the schema
	 * with sbe-tool's text verbatim, and nothing is generated. An output that fails
	 * to write is an {@link UncheckedIOException}.
	 */
	public static List<Problem> generate(Schema schema, DynamicPackageOutputManager output) {
		String document = document(schema);
		try {
			XSD.newValidator().validate(new StreamSource(new StringReader(document)));
		} catch (SAXException e) {
			return List.of(new Problem(schema, "sbe.xsd: " + e.getMessage()));
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
					problems.add(new Problem(schema, line));
				}
			}
			return problems.isEmpty() ? List.of(new Problem(schema, String.valueOf(e.getMessage()))) : problems;
		}
		Ir ir = new IrGenerator().generate(parsed, schema.packageName() + ".sbe");
		output.setPackageName(ir.applicableNamespace());
		try {
			// The buffer types by name, as SbeTool passes them: naming the classes
			// would load them, and a user's javac must never load an Agrona buffer.
			new JavaGenerator(
					ir, "org.agrona.MutableDirectBuffer", "org.agrona.DirectBuffer", false, false, false, output
			)
					.generate();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return List.of();
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
