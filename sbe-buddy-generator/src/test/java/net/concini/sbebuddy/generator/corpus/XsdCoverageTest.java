package net.concini.sbebuddy.generator.corpus;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * Every element and attribute sbe.xsd declares occurs in at least one oracle,
 * so completeness is a test.
 */
final class XsdCoverageTest {

	private static final String XS = "http://www.w3.org/2001/XMLSchema";

	@Test
	void everyElementAndAttributeOccursInSomeOracle() throws Exception {
		Set<String> uncovered = declaredByXsd();
		for (Corpus.Case aCase : Corpus.CASES) {
			uncovered.removeAll(usedBy(aCase.oracle()));
		}

		assertThat(uncovered).as("declared by sbe.xsd, used by no oracle").isEmpty();
	}

	/**
	 * {@code element} and {@code element/@attribute} for every declaration in the
	 * XSD.
	 */
	private static Set<String> declaredByXsd() throws Exception {
		Document xsd;
		try (InputStream in = XsdCoverageTest.class.getResourceAsStream("/fpl/sbe.xsd")) {
			xsd = builder().parse(in);
		}
		Map<String, Element> complexTypes = new HashMap<>();
		Map<String, Element> attributeGroups = new HashMap<>();
		for (Element child : children(xsd.getDocumentElement())) {
			if (child.getLocalName().equals("complexType")) {
				complexTypes.put(child.getAttribute("name"), child);
			}
			if (child.getLocalName().equals("attributeGroup")) {
				attributeGroups.put(child.getAttribute("name"), child);
			}
		}
		Set<String> declared = new TreeSet<>();
		NodeList elements = xsd.getElementsByTagNameNS(XS, "element");
		for (int i = 0; i < elements.getLength(); i++) {
			Element element = (Element) elements.item(i);
			String name = element.getAttribute("name");
			if (name.isEmpty()) {
				continue; // a ref to an element declared elsewhere
			}
			declared.add(name);
			Element type = element.hasAttribute("type")
					? complexTypes.get(localName(element.getAttribute("type")))
					: firstChild(element, "complexType");
			if (type != null) {
				collectAttributes(type, name, complexTypes, attributeGroups, declared);
			}
		}
		return declared;
	}

	/**
	 * Attributes of a complex type, following attribute groups and extension bases,
	 * not nested elements.
	 */
	private static void collectAttributes(
			Element node, String element, Map<String, Element> complexTypes,
			Map<String, Element> attributeGroups, Set<String> declared
	) {
		for (Element child : children(node)) {
			switch (child.getLocalName()) {
				case "attribute" -> declared.add(element + "/@" + child.getAttribute("name"));
				case "attributeGroup" -> collectAttributes(
						attributeGroups.get(localName(child.getAttribute("ref"))),
						element, complexTypes, attributeGroups, declared
				);
				case "element" -> {
				}
				case "extension" -> {
					Element base = complexTypes.get(localName(child.getAttribute("base")));
					if (base != null) {
						collectAttributes(base, element, complexTypes, attributeGroups, declared);
					}
					collectAttributes(child, element, complexTypes, attributeGroups, declared);
				}
				default -> collectAttributes(child, element, complexTypes, attributeGroups, declared);
			}
		}
	}

	/**
	 * {@code element} and {@code element/@attribute} for everything an oracle
	 * writes, defaults not filled.
	 */
	private static Set<String> usedBy(String oracle) throws Exception {
		Set<String> used = new TreeSet<>();
		Document document = builder().parse(new InputSource(new StringReader(oracle)));
		NodeList elements = document.getElementsByTagName("*");
		for (int i = 0; i < elements.getLength(); i++) {
			Element element = (Element) elements.item(i);
			String name = element.getLocalName();
			used.add(name);
			NamedNodeMap attributes = element.getAttributes();
			for (int j = 0; j < attributes.getLength(); j++) {
				Attr attribute = (Attr) attributes.item(j);
				if (!attribute.getName().startsWith("xmlns")) {
					used.add(name + "/@" + attribute.getLocalName());
				}
			}
		}
		return used;
	}

	private static javax.xml.parsers.DocumentBuilder builder() throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		return factory.newDocumentBuilder();
	}

	private static java.util.List<Element> children(Element parent) {
		java.util.List<Element> result = new java.util.ArrayList<>();
		for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
			if (node instanceof Element element) {
				result.add(element);
			}
		}
		return result;
	}

	private static Element firstChild(Element parent, String localName) {
		for (Element child : children(parent)) {
			if (child.getLocalName().equals(localName)) {
				return child;
			}
		}
		return null;
	}

	private static String localName(String qualifiedName) {
		return qualifiedName.substring(qualifiedName.indexOf(':') + 1);
	}
}
