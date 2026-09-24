package net.concini.sbebuddy.generator;

import static org.assertj.core.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import org.w3c.dom.Document;

import uk.co.real_logic.sbe.xml.ParserOptions;
import uk.co.real_logic.sbe.xml.XmlSchemaParser;

/**
 * Compares the XML written for a schema with a hand-written oracle, by the
 * equivalence the compiler applies between a package and its resource,
 * {@link SchemaEquivalence}: the XSD's defaults filled, declarations and
 * messages matched by name regardless of order, everything else in sequence.
 */
public final class SchemaXmlAssert {

	private final String xml;

	private SchemaXmlAssert(String xml) {
		this.xml = xml;
	}

	public static SchemaXmlAssert assertThat(Schema schema) {
		return new SchemaXmlAssert(text(schema));
	}

	/** The XML as written, by the processor or anything else. */
	public static SchemaXmlAssert assertThat(String xml) {
		return new SchemaXmlAssert(xml);
	}

	/**
	 * The written XML is equivalent to the oracle, and sbe-tool accepts the oracle
	 * without an error or a warning.
	 */
	public void matches(String oracle) {
		acceptedBySbeTool(oracle);
		// Qualified because this class's own assertThat shadows AssertJ's.
		org.assertj.core.api.Assertions.assertThat(SchemaEquivalence.differences(xml, oracle))
				.as("differences from the oracle")
				.isEmpty();
	}

	static String text(Schema schema) {
		StringWriter writer = new StringWriter();
		try {
			SchemaXml.write(schema, writer);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		return writer.toString();
	}

	/**
	 * Parsed with sbe.xsd attached: validated, and the XSD's defaults filled in.
	 */
	static Document parse(String xml) {
		return SchemaEquivalence.parse(xml);
	}

	private static void acceptedBySbeTool(String oracle) {
		ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();
		ParserOptions options = ParserOptions.builder()
				.stopOnError(true)
				.warningsFatal(true)
				.suppressOutput(false)
				.errorPrintStream(new PrintStream(diagnostics, true, StandardCharsets.UTF_8))
				.build();
		try {
			XmlSchemaParser.parse(new ByteArrayInputStream(oracle.getBytes(StandardCharsets.UTF_8)), options);
		} catch (Exception e) {
			fail("sbe-tool rejects the oracle: %s%n%s", e.getMessage(), diagnostics.toString(StandardCharsets.UTF_8));
		}
	}
}
