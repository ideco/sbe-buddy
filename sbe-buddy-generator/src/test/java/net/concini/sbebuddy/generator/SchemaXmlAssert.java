package net.concini.sbebuddy.generator;

import static org.assertj.core.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;

import org.w3c.dom.Document;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xmlunit.assertj3.XmlAssert;
import org.xmlunit.diff.DefaultNodeMatcher;
import org.xmlunit.diff.ElementSelectors;

import uk.co.real_logic.sbe.xml.ParserOptions;
import uk.co.real_logic.sbe.xml.XmlSchemaParser;

/**
 * Compares the XML written for a schema with a hand-written oracle. Both are
 * parsed with sbe.xsd attached, so the XSD's defaults are filled and an absent
 * attribute equals its default; declarations and messages match by name
 * regardless of order, everything else in sequence, because offsets follow
 * declaration order.
 */
public final class SchemaXmlAssert {

	static final Map<String, String> NAMESPACES = Map.of("sbe", SchemaXml.NAMESPACE);

	// Qualified because it collides with our Schema, the subject of every assertion
	// here. The one place it is named, so the collision is explained once.
	private static final javax.xml.validation.Schema XSD;

	static {
		try {
			XSD = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
					.newSchema(new StreamSource(SchemaXmlAssert.class.getResourceAsStream("/fpl/sbe.xsd")));
		} catch (SAXException e) {
			throw new IllegalStateException(e);
		}
	}

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
		XmlAssert.assertThat(parse(xml))
				.and(parse(oracle))
				.ignoreWhitespace()
				.ignoreComments()
				.withNodeMatcher(
						new DefaultNodeMatcher(
								ElementSelectors.conditionalBuilder()
										.whenElementIsNamed("type")
										.thenUse(ElementSelectors.byNameAndAttributes("name"))
										.whenElementIsNamed("composite")
										.thenUse(ElementSelectors.byNameAndAttributes("name"))
										.whenElementIsNamed("enum")
										.thenUse(ElementSelectors.byNameAndAttributes("name"))
										.whenElementIsNamed("set").thenUse(ElementSelectors.byNameAndAttributes("name"))
										.whenElementIsNamed("message")
										.thenUse(ElementSelectors.byNameAndAttributes("name"))
										.elseUse(ElementSelectors.byName)
										.build()
						)
				)
				.areSimilar();
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
	 * Parses with sbe.xsd attached: validated, and the XSD's defaults filled in.
	 */
	static Document parse(String xml) {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);
			factory.setIgnoringComments(true);
			factory.setIgnoringElementContentWhitespace(true);
			factory.setSchema(XSD);
			DocumentBuilder builder = factory.newDocumentBuilder();
			builder.setErrorHandler(new Strict());
			return builder.parse(new InputSource(new StringReader(xml)));
		} catch (Exception e) {
			throw new AssertionError("not a valid SBE schema: " + e.getMessage() + System.lineSeparator() + xml, e);
		}
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

	private static final class Strict implements ErrorHandler {

		@Override
		public void warning(SAXParseException exception) {
		}

		@Override
		public void error(SAXParseException exception) throws SAXException {
			throw exception;
		}

		@Override
		public void fatalError(SAXParseException exception) throws SAXException {
			throw exception;
		}
	}
}
