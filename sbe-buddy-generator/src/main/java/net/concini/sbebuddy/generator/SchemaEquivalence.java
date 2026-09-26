package net.concini.sbebuddy.generator;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;

import org.jspecify.annotations.Nullable;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * Whether two SBE documents are one schema. Both are parsed with sbe.xsd
 * attached, so the XSD's defaults are filled and an absent attribute equals its
 * default; declarations and messages match by name regardless of order,
 * everything else in sequence, because offsets follow declaration order. The
 * differences are phrased from the first document's side, the one the
 * annotations render: "the schema" is the second, the resource or the oracle. A
 * partial comparison lets the second hold messages and declarations the first
 * lacks, and the first hold no message at all.
 */
public final class SchemaEquivalence {

	/**
	 * One place the two documents differ: {@code path} names the node from the root
	 * down, empty for the schema itself.
	 */
	public record Difference(List<Segment> path, String message) {

		public Difference {
			path = List.copyOf(path);
		}

		@Override
		public String toString() {
			if (path.isEmpty()) {
				return message;
			}
			List<String> steps = new ArrayList<>();
			for (Segment segment : path) {
				steps.add(segment.element() + " " + segment.name());
			}
			return String.join(", ", steps) + ": " + message;
		}
	}

	/** A node on the path, by its element and its {@code name}. */
	public record Segment(String element, String name) {
	}

	// Qualified because it collides with our Schema, whose documents are compared.
	private static final javax.xml.validation.Schema XSD;

	/**
	 * sbe.xsd with a schema's messages optional, for the document rendered from a
	 * partial package, which may map none; everything else as sbe.xsd has it.
	 */
	private static final javax.xml.validation.Schema WITHOUT_MESSAGES;

	private static final String MESSAGES = "<xs:element ref=\"sbe:message\" maxOccurs=\"unbounded\"/>";

	static {
		try (InputStream xsd = SchemaEquivalence.class.getResourceAsStream("/fpl/sbe.xsd")) {
			// sbe.xsd opens with a byte order mark, which a reader of text passes on.
			String text = new String(Objects.requireNonNull(xsd, "sbe.xsd").readAllBytes(), StandardCharsets.UTF_8)
					.replaceFirst("^\uFEFF", "");
			if (!text.contains(MESSAGES)) {
				throw new IllegalStateException("sbe.xsd no longer declares a schema's messages as " + MESSAGES);
			}
			SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
			XSD = factory.newSchema(new StreamSource(new StringReader(text)));
			WITHOUT_MESSAGES = factory.newSchema(
					new StreamSource(
							new StringReader(
									text.replace(
											MESSAGES,
											"<xs:element ref=\"sbe:message\" minOccurs=\"0\" maxOccurs=\"unbounded\"/>"
									)
							)
					)
			);
		} catch (SAXException e) {
			throw new IllegalStateException(e);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private final List<Difference> differences = new ArrayList<>();
	private final List<Segment> path = new ArrayList<>();
	private final boolean partial;

	private SchemaEquivalence(boolean partial) {
		this.partial = partial;
	}

	/**
	 * The differences between {@code rendered} and {@code schema}, none when they
	 * are one schema; a document sbe.xsd rejects is an
	 * {@link IllegalArgumentException}.
	 */
	public static List<Difference> differences(String rendered, String schema) {
		return differences(rendered, schema, false);
	}

	/**
	 * The differences, and with {@code partial} none for a message or a declaration
	 * only {@code schema} has: the rendered document is then a part of the schema,
	 * which may hold no message.
	 */
	public static List<Difference> differences(String rendered, String schema, boolean partial) {
		SchemaEquivalence equivalence = new SchemaEquivalence(partial);
		equivalence.root(
				parse(rendered, partial ? WITHOUT_MESSAGES : XSD).getDocumentElement(),
				parse(schema).getDocumentElement()
		);
		return List.copyOf(equivalence.differences);
	}

	/**
	 * Parsed with sbe.xsd attached: validated, and the XSD's defaults filled in.
	 */
	static Document parse(String xml) {
		return parse(xml, XSD);
	}

	private static Document parse(String xml, javax.xml.validation.Schema xsd) {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		factory.setIgnoringComments(true);
		factory.setIgnoringElementContentWhitespace(true);
		factory.setSchema(xsd);
		try {
			DocumentBuilder builder = factory.newDocumentBuilder();
			builder.setErrorHandler(new Strict());
			return builder.parse(new InputSource(new StringReader(xml)));
		} catch (ParserConfigurationException e) {
			throw new IllegalStateException(e);
		} catch (SAXException e) {
			throw new IllegalArgumentException("not a valid SBE schema: " + e.getMessage(), e);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private void root(Element rendered, Element schema) {
		attributes(rendered, schema);
		Map<Segment, Element> renderedTypes = new LinkedHashMap<>();
		Map<Segment, Element> schemaTypes = new LinkedHashMap<>();
		for (Element types : children(rendered, "types")) {
			renderedTypes.putAll(byName(types));
		}
		for (Element types : children(schema, "types")) {
			schemaTypes.putAll(byName(types));
		}
		for (Segment segment : renderedTypes.keySet()) {
			if (!schemaTypes.containsKey(segment)) {
				differ(segment, "the schema has no " + segment.element() + " named \"" + segment.name() + "\"");
			}
		}
		for (Segment segment : schemaTypes.keySet()) {
			if (!partial && !renderedTypes.containsKey(segment)) {
				differ(
						segment, "the schema has " + article(segment.element()) + " " + segment.element() + " \""
								+ segment.name() + "\" and no declaration maps it"
				);
			}
		}
		for (Map.Entry<Segment, Element> type : renderedTypes.entrySet()) {
			Element other = schemaTypes.get(type.getKey());
			if (other != null) {
				declaration(type.getKey(), type.getValue(), other);
			}
		}
		Map<Segment, Element> renderedMessages = byName(rendered, "message");
		Map<Segment, Element> schemaMessages = byName(schema, "message");
		for (Segment segment : renderedMessages.keySet()) {
			if (!schemaMessages.containsKey(segment)) {
				differ(segment, "the schema has no message named \"" + segment.name() + "\"");
			}
		}
		for (Map.Entry<Segment, Element> message : schemaMessages.entrySet()) {
			if (!partial && !renderedMessages.containsKey(message.getKey())) {
				differ(
						message.getKey(), "the schema has a message \"" + message.getKey().name() + "\" (id "
								+ message.getValue().getAttribute("id")
								+ ") and no record maps it; add one, or declare the package partial"
				);
			}
		}
		for (Map.Entry<Segment, Element> message : renderedMessages.entrySet()) {
			Element other = schemaMessages.get(message.getKey());
			if (other != null) {
				under(message.getKey(), () -> block(message.getValue(), other));
			}
		}
	}

	private void declaration(Segment segment, Element rendered, Element schema) {
		under(segment, () -> {
			attributes(rendered, schema);
			switch (segment.element()) {
				case "type" -> text(rendered, schema);
				case "composite" -> members(rendered, schema);
				case "enum" -> values(rendered, schema, "validValue", "value", "constant");
				case "set" -> values(rendered, schema, "choice", "choice", "constant");
				default -> throw new IllegalStateException("a declaration of " + segment.element());
			}
		});
	}

	/** A composite's members: each a component or an unmapped member. */
	private void members(Element rendered, Element schema) {
		Map<Segment, Element> ours = byName(rendered);
		Map<Segment, Element> theirs = byName(schema);
		String owner = "the schema's " + rendered.getAttribute("name");
		for (Segment segment : ours.keySet()) {
			if (!theirs.containsKey(segment)) {
				differ(segment, owner + " has no member named \"" + segment.name() + "\"");
			}
		}
		for (Segment segment : theirs.keySet()) {
			if (!ours.containsKey(segment)) {
				differ(
						segment, owner + " has a member \"" + segment.name()
								+ "\" no component carries; add it, or declare it unmapped"
				);
			}
		}
		if (!sameOrder(ours, theirs, owner, "members")) {
			return;
		}
		for (Map.Entry<Segment, Element> member : ours.entrySet()) {
			Element other = matching(theirs, member.getKey());
			under(member.getKey(), () -> {
				attributes(member.getValue(), other);
				switch (member.getKey().element()) {
					case "type" -> text(member.getValue(), other);
					case "composite" -> members(member.getValue(), other);
					case "enum" -> values(member.getValue(), other, "validValue", "value", "constant");
					case "set" -> values(member.getValue(), other, "choice", "choice", "constant");
					case "ref" -> {
					}
					default -> throw new IllegalStateException("a member of " + member.getKey().element());
				}
			});
		}
	}

	/**
	 * An enum's valid values or a set's choices, each a constant of the Java type.
	 */
	private void values(Element rendered, Element schema, String element, String noun, String mapsTo) {
		Map<Segment, Element> ours = byName(rendered);
		Map<Segment, Element> theirs = byName(schema);
		String owner = "the schema's " + rendered.getAttribute("name");
		for (Segment segment : ours.keySet()) {
			if (!theirs.containsKey(segment)) {
				differ(segment, owner + " has no " + noun + " named \"" + segment.name() + "\"");
			}
		}
		for (Segment segment : theirs.keySet()) {
			if (!ours.containsKey(segment)) {
				differ(
						segment, owner + " has a " + noun + " named \"" + segment.name() + "\" and no " + mapsTo
								+ " maps it"
				);
			}
		}
		if (!sameOrder(ours, theirs, owner, element + "s")) {
			return;
		}
		for (Map.Entry<Segment, Element> value : ours.entrySet()) {
			Element other = matching(theirs, value.getKey());
			under(value.getKey(), () -> {
				attributes(value.getValue(), other);
				text(value.getValue(), other);
			});
		}
	}

	/** A message's or a group's fields, groups and var-data. */
	private void block(Element rendered, Element schema) {
		attributes(rendered, schema);
		Map<Segment, Element> ours = byName(rendered);
		Map<Segment, Element> theirs = byName(schema);
		String owner = "the schema's " + rendered.getAttribute("name");
		for (Segment segment : ours.keySet()) {
			if (!theirs.containsKey(segment)) {
				differ(segment, owner + " has no " + segment.element() + " named \"" + segment.name() + "\"");
			}
		}
		for (Segment segment : theirs.keySet()) {
			if (!ours.containsKey(segment)) {
				String hint = segment.element().equals("field") ? "; add it, or declare it unmapped" : "";
				differ(
						segment, owner + " has " + article(segment.element()) + " " + segment.element() + " \""
								+ segment.name() + "\" no component carries" + hint
				);
			}
		}
		if (!sameOrder(ours, theirs, owner, "members")) {
			return;
		}
		for (Map.Entry<Segment, Element> member : ours.entrySet()) {
			Element other = matching(theirs, member.getKey());
			under(member.getKey(), () -> {
				if (member.getKey().element().equals("group")) {
					block(member.getValue(), other);
				} else {
					attributes(member.getValue(), other);
				}
			});
		}
	}

	/**
	 * Whether the two hold the same names in the same order; a difference in the
	 * order is reported once, on the owner, and ends the comparison beneath it.
	 */
	private boolean sameOrder(Map<Segment, Element> ours, Map<Segment, Element> theirs, String owner, String what) {
		if (!ours.keySet().equals(theirs.keySet())) {
			return false;
		}
		List<String> ourOrder = ours.keySet().stream().map(Segment::name).toList();
		List<String> theirOrder = theirs.keySet().stream().map(Segment::name).toList();
		if (!ourOrder.equals(theirOrder)) {
			differences.add(
					new Difference(
							path, owner + " has its " + what + " in the order " + theirOrder + ", not " + ourOrder
					)
			);
			return false;
		}
		return true;
	}

	/**
	 * Every attribute either side has, the XSD's defaults among them, except the
	 * namespace declarations.
	 */
	private void attributes(Element rendered, Element schema) {
		Map<String, String> ours = attributes(rendered);
		Map<String, String> theirs = attributes(schema);
		for (Map.Entry<String, String> attribute : ours.entrySet()) {
			String other = theirs.get(attribute.getKey());
			if (other == null) {
				differences.add(
						new Difference(
								path, "the schema has no " + attribute.getKey() + " where the annotations have \""
										+ attribute.getValue() + "\""
						)
				);
			} else if (!other.equals(attribute.getValue())) {
				differences.add(
						new Difference(
								path, "the schema has " + attribute.getKey() + "=\"" + other + "\", not \""
										+ attribute.getValue() + "\""
						)
				);
			}
		}
		for (Map.Entry<String, String> attribute : theirs.entrySet()) {
			if (!ours.containsKey(attribute.getKey())) {
				differences.add(
						new Difference(
								path, "the schema has " + attribute.getKey() + "=\"" + attribute.getValue()
										+ "\" where the annotations have none"
						)
				);
			}
		}
	}

	/**
	 * The counterpart of a node whose name both sides hold, as sameOrder saw to.
	 */
	private static Element matching(Map<Segment, Element> theirs, Segment segment) {
		Element element = theirs.get(segment);
		if (element == null) {
			throw new IllegalStateException("no " + segment.element() + " named " + segment.name());
		}
		return element;
	}

	static Map<String, String> attributes(Element element) {
		Map<String, String> attributes = new LinkedHashMap<>();
		NamedNodeMap nodes = element.getAttributes();
		for (int i = 0; i < nodes.getLength(); i++) {
			Attr attribute = (Attr) nodes.item(i);
			if (!XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(attribute.getNamespaceURI())) {
				attributes.put(attribute.getLocalName(), attribute.getValue());
			}
		}
		return attributes;
	}

	/** The element's text, a constant's or a valid value's. */
	private void text(Element rendered, Element schema) {
		String ours = rendered.getTextContent().trim();
		String theirs = schema.getTextContent().trim();
		if (!ours.equals(theirs)) {
			differences.add(new Difference(path, "the schema has the value \"" + theirs + "\", not \"" + ours + "\""));
		}
	}

	/** The named children of an element, keyed by element and name, in order. */
	private static Map<Segment, Element> byName(Element parent) {
		Map<Segment, Element> children = new LinkedHashMap<>();
		for (Element child : children(parent, null)) {
			children.put(new Segment(child.getLocalName(), child.getAttribute("name")), child);
		}
		return children;
	}

	private static Map<Segment, Element> byName(Element parent, String element) {
		Map<Segment, Element> children = new LinkedHashMap<>();
		for (Element child : children(parent, element)) {
			children.put(new Segment(child.getLocalName(), child.getAttribute("name")), child);
		}
		return children;
	}

	/** The element children, of the given local name or all of them. */
	static List<Element> children(Element parent, @Nullable String element) {
		List<Element> children = new ArrayList<>();
		NodeList nodes = parent.getChildNodes();
		for (int i = 0; i < nodes.getLength(); i++) {
			if (nodes.item(i) instanceof Element child
					&& (element == null || element.equals(child.getLocalName()))) {
				children.add(child);
			}
		}
		return children;
	}

	private void differ(Segment segment, String message) {
		under(segment, () -> differences.add(new Difference(path, message)));
	}

	private void under(Segment segment, Runnable comparison) {
		path.add(segment);
		try {
			comparison.run();
		} finally {
			path.remove(path.size() - 1);
		}
	}

	private static String article(String element) {
		return element.equals("enum") ? "an" : "a";
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
