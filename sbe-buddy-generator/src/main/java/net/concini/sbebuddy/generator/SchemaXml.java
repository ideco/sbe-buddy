package net.concini.sbebuddy.generator;

import java.io.IOException;
import java.io.Writer;
import java.nio.ByteOrder;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.jspecify.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import uk.co.real_logic.sbe.xml.Presence;

/**
 * Writes a {@link Schema} as the XML document sbe-tool reads: one element per
 * node, in the XSD's order, with only the attributes that are set. A default is
 * never written.
 */
public final class SchemaXml {

	static final String NAMESPACE = "http://fixprotocol.io/2016/sbe";

	private SchemaXml() {
	}

	public static Document of(Schema schema) {
		Document document = newDocument();
		Element root = document.createElementNS(NAMESPACE, "sbe:messageSchema");
		root.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns:sbe", NAMESPACE);
		document.appendChild(root);
		attribute(root, "package", schema.packageName());
		attribute(root, "id", schema.id());
		attribute(root, "version", schema.version());
		attribute(root, "semanticVersion", schema.semanticVersion());
		attribute(root, "description", schema.description());
		attribute(root, "byteOrder", byteOrder(schema.byteOrder()));
		attribute(root, "headerType", schema.headerType());
		Element types = child(root, "types");
		for (Schema.Declaration declaration : schema.types()) {
			declaration(types, declaration);
		}
		for (Schema.Message message : schema.messages()) {
			message(root, message);
		}
		return document;
	}

	public static void write(Schema schema, Writer writer) throws IOException {
		try {
			Transformer transformer = TransformerFactory.newInstance().newTransformer();
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
			transformer.transform(new DOMSource(of(schema)), new StreamResult(writer));
		} catch (TransformerException e) {
			throw new IOException(e);
		}
	}

	private static void declaration(Element parent, Schema.Declaration declaration) {
		switch (declaration) {
			case Schema.Type type -> type(parent, type);
			case Schema.Composite composite -> composite(parent, composite);
			case Schema.Enum enumeration -> enumeration(parent, enumeration);
			case Schema.Set set -> set(parent, set);
		}
	}

	private static void member(Element parent, Schema.Member member) {
		switch (member) {
			case Schema.Type type -> type(parent, type);
			case Schema.Ref ref -> ref(parent, ref);
			case Schema.Enum enumeration -> enumeration(parent, enumeration);
			case Schema.Set set -> set(parent, set);
			case Schema.Composite composite -> composite(parent, composite);
		}
	}

	private static void type(Element parent, Schema.Type type) {
		Element element = child(parent, "type");
		attribute(element, "name", type.name());
		attribute(element, "primitiveType", type.primitiveType().primitiveName());
		attribute(element, "length", type.length());
		attribute(element, "characterEncoding", type.characterEncoding());
		attribute(element, "presence", presence(type.presence()));
		attribute(element, "valueRef", type.valueRef());
		attribute(element, "nullValue", type.nullValue());
		attribute(element, "minValue", type.minValue());
		attribute(element, "maxValue", type.maxValue());
		attribute(element, "offset", type.offset());
		attribute(element, "semanticType", type.semanticType());
		attribute(element, "description", type.description());
		attribute(element, "sinceVersion", type.sinceVersion());
		attribute(element, "deprecated", type.deprecated());
		if (type.value() != null) {
			element.setTextContent(type.value());
		}
	}

	private static void composite(Element parent, Schema.Composite composite) {
		Element element = child(parent, "composite");
		attribute(element, "name", composite.name());
		attribute(element, "offset", composite.offset());
		attribute(element, "semanticType", composite.semanticType());
		attribute(element, "description", composite.description());
		attribute(element, "sinceVersion", composite.sinceVersion());
		attribute(element, "deprecated", composite.deprecated());
		for (Schema.Member member : composite.members()) {
			member(element, member);
		}
	}

	private static void ref(Element parent, Schema.Ref ref) {
		Element element = child(parent, "ref");
		attribute(element, "name", ref.name());
		attribute(element, "type", ref.type());
		attribute(element, "offset", ref.offset());
		attribute(element, "sinceVersion", ref.sinceVersion());
		attribute(element, "deprecated", ref.deprecated());
	}

	private static void enumeration(Element parent, Schema.Enum enumeration) {
		Element element = child(parent, "enum");
		attribute(element, "name", enumeration.name());
		attribute(element, "encodingType", enumeration.encodingType());
		attribute(element, "offset", enumeration.offset());
		attribute(element, "semanticType", enumeration.semanticType());
		attribute(element, "description", enumeration.description());
		attribute(element, "sinceVersion", enumeration.sinceVersion());
		attribute(element, "deprecated", enumeration.deprecated());
		for (Schema.ValidValue validValue : enumeration.validValues()) {
			Element valueElement = child(element, "validValue");
			attribute(valueElement, "name", validValue.name());
			attribute(valueElement, "description", validValue.description());
			attribute(valueElement, "sinceVersion", validValue.sinceVersion());
			attribute(valueElement, "deprecated", validValue.deprecated());
			valueElement.setTextContent(validValue.value());
		}
	}

	private static void set(Element parent, Schema.Set set) {
		Element element = child(parent, "set");
		attribute(element, "name", set.name());
		attribute(element, "encodingType", set.encodingType());
		attribute(element, "offset", set.offset());
		attribute(element, "semanticType", set.semanticType());
		attribute(element, "description", set.description());
		attribute(element, "sinceVersion", set.sinceVersion());
		attribute(element, "deprecated", set.deprecated());
		for (Schema.Choice choice : set.choices()) {
			Element choiceElement = child(element, "choice");
			attribute(choiceElement, "name", choice.name());
			attribute(choiceElement, "description", choice.description());
			attribute(choiceElement, "sinceVersion", choice.sinceVersion());
			attribute(choiceElement, "deprecated", choice.deprecated());
			choiceElement.setTextContent(Integer.toString(choice.value()));
		}
	}

	private static void message(Element parent, Schema.Message message) {
		// The XSD declares message at top level, like messageSchema, so it is
		// qualified; every other element is local.
		Element element = parent.getOwnerDocument().createElementNS(NAMESPACE, "sbe:message");
		parent.appendChild(element);
		attribute(element, "name", message.name());
		attribute(element, "id", message.id());
		attribute(element, "blockLength", message.blockLength());
		attribute(element, "semanticType", message.semanticType());
		attribute(element, "description", message.description());
		attribute(element, "sinceVersion", message.sinceVersion());
		attribute(element, "deprecated", message.deprecated());
		body(element, message.fields(), message.groups(), message.data());
	}

	private static void body(
			Element element, List<Schema.Field> fields, List<Schema.Group> groups,
			List<Schema.Data> data
	) {
		for (Schema.Field field : fields) {
			field(element, field);
		}
		for (Schema.Group group : groups) {
			group(element, group);
		}
		for (Schema.Data datum : data) {
			data(element, datum);
		}
	}

	private static void field(Element parent, Schema.Field field) {
		Element element = child(parent, "field");
		attribute(element, "name", field.name());
		attribute(element, "id", field.id());
		attribute(element, "type", field.type());
		attribute(element, "epoch", field.epoch());
		attribute(element, "timeUnit", field.timeUnit());
		attribute(element, "offset", field.offset());
		attribute(element, "presence", presence(field.presence()));
		attribute(element, "valueRef", field.valueRef());
		attribute(element, "semanticType", field.semanticType());
		attribute(element, "description", field.description());
		attribute(element, "sinceVersion", field.sinceVersion());
		attribute(element, "deprecated", field.deprecated());
	}

	private static void group(Element parent, Schema.Group group) {
		Element element = child(parent, "group");
		attribute(element, "name", group.name());
		attribute(element, "id", group.id());
		attribute(element, "dimensionType", group.dimensionType());
		attribute(element, "blockLength", group.blockLength());
		attribute(element, "semanticType", group.semanticType());
		attribute(element, "description", group.description());
		attribute(element, "sinceVersion", group.sinceVersion());
		attribute(element, "deprecated", group.deprecated());
		body(element, group.fields(), group.groups(), group.data());
	}

	private static void data(Element parent, Schema.Data data) {
		Element element = child(parent, "data");
		attribute(element, "name", data.name());
		attribute(element, "id", data.id());
		attribute(element, "type", data.type());
		attribute(element, "epoch", data.epoch());
		attribute(element, "timeUnit", data.timeUnit());
		attribute(element, "offset", data.offset());
		attribute(element, "presence", presence(data.presence()));
		attribute(element, "valueRef", data.valueRef());
		attribute(element, "semanticType", data.semanticType());
		attribute(element, "description", data.description());
		attribute(element, "sinceVersion", data.sinceVersion());
		attribute(element, "deprecated", data.deprecated());
	}

	private static Element child(Element parent, String name) {
		Element element = parent.getOwnerDocument().createElement(name);
		parent.appendChild(element);
		return element;
	}

	private static void attribute(Element element, String name, @Nullable String value) {
		if (value != null) {
			element.setAttribute(name, value);
		}
	}

	private static void attribute(Element element, String name, @Nullable Integer value) {
		if (value != null) {
			element.setAttribute(name, Integer.toString(value));
		}
	}

	private static @Nullable String presence(@Nullable Presence presence) {
		if (presence == null) {
			return null;
		}
		return switch (presence) {
			case REQUIRED -> "required";
			case OPTIONAL -> "optional";
			case CONSTANT -> "constant";
		};
	}

	private static @Nullable String byteOrder(@Nullable ByteOrder byteOrder) {
		if (byteOrder == null) {
			return null;
		}
		return byteOrder == ByteOrder.BIG_ENDIAN ? "bigEndian" : "littleEndian";
	}

	private static Document newDocument() {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);
			return factory.newDocumentBuilder().newDocument();
		} catch (ParserConfigurationException e) {
			throw new IllegalStateException(e);
		}
	}
}
