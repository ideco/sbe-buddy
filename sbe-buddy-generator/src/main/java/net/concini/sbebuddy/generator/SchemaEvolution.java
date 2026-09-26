package net.concini.sbebuddy.generator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.w3c.dom.Element;

import net.concini.sbebuddy.generator.SchemaEquivalence.Difference;
import net.concini.sbebuddy.generator.SchemaEquivalence.Segment;

/**
 * Whether a schema still reads as its baseline, an earlier version of it, under
 * SBE's extension rules. Both documents are parsed with sbe.xsd attached, as
 * {@link SchemaEquivalence} parses them, and nothing is matched by name:
 * messages by id, fields, groups and var-data by position, types by what they
 * are. Names, descriptions and deprecation are free, and so is presence between
 * required and optional; everything else is the wire or what it means, and
 * stays. Each difference's path is in the schema's names, empty for the schema
 * itself.
 */
public final class SchemaEvolution {

	/** Never compared: labels, and the version a node was retired in. */
	private static final Set<String> FREE = Set.of("name", "description", "deprecated");

	/**
	 * Compared where a node is used, as a field or a composite's member, rather
	 * than as part of what its type is.
	 */
	private static final List<String> PLACEMENT = List.of("offset", "sinceVersion");

	/**
	 * Compared by a type's own rules: its placement where it is used, its encoding
	 * as a type, its presence as a constant or not.
	 */
	private static final Set<String> TYPE_HANDLED = Set.of("offset", "sinceVersion", "encodingType", "presence");

	private static final List<String> PRIMITIVES = List
			.of("char", "int8", "int16", "int32", "int64", "uint8", "uint16", "uint32", "uint64", "float", "double");

	private final List<Difference> differences = new ArrayList<>();
	private final List<Segment> path = new ArrayList<>();
	private final Map<String, Element> ours;
	private final Map<String, Element> theirs;
	private final Map<String, Element> primitives;
	private final int version;

	private SchemaEvolution(Element schema, Element baseline, Map<String, Element> primitives) {
		this.ours = declarations(schema);
		this.theirs = declarations(baseline);
		this.primitives = primitives;
		this.version = version(baseline);
	}

	/**
	 * The differences that break {@code baseline} in {@code schema}, none when the
	 * schema reads every message the baseline reads; a document sbe.xsd rejects is
	 * an {@link IllegalArgumentException}.
	 */
	public static List<Difference> differences(String schema, String baseline) {
		Element ours = SchemaEquivalence.parse(schema).getDocumentElement();
		Element theirs = SchemaEquivalence.parse(baseline).getDocumentElement();
		SchemaEvolution evolution = new SchemaEvolution(ours, theirs, primitives());
		evolution.root(ours, theirs);
		return List.copyOf(evolution.differences);
	}

	/** The version of a document sbe.xsd accepts. */
	public static int version(String document) {
		return version(SchemaEquivalence.parse(document).getDocumentElement());
	}

	private static int version(Element root) {
		return Integer.parseInt(root.getAttribute("version"));
	}

	/**
	 * A primitive named as a type, as sbe-tool reads one: a {@code type} of length
	 * one, required, with the XSD's defaults filled like any other.
	 */
	private static Map<String, Element> primitives() {
		StringBuilder types = new StringBuilder();
		for (String primitive : PRIMITIVES) {
			types.append("<type name=\"").append(primitive).append("\" primitiveType=\"").append(primitive)
					.append("\"/>");
		}
		Element root = SchemaEquivalence.parse(
				"<sbe:messageSchema xmlns:sbe=\"http://fixprotocol.io/2016/sbe\" version=\"0\"><types>" + types
						+ "</types><sbe:message name=\"m\" id=\"1\"/></sbe:messageSchema>"
		).getDocumentElement();
		return declarations(root);
	}

	private static Map<String, Element> declarations(Element root) {
		Map<String, Element> declarations = new LinkedHashMap<>();
		for (Element types : SchemaEquivalence.children(root, "types")) {
			for (Element declaration : SchemaEquivalence.children(types, null)) {
				declarations.put(declaration.getAttribute("name"), declaration);
			}
		}
		return declarations;
	}

	// ---- the schema and its messages

	private void root(Element schema, Element baseline) {
		if (!schema.getAttribute("id").equals(baseline.getAttribute("id"))) {
			differ(
					"the baseline is schema " + baseline.getAttribute("id") + ", not " + schema.getAttribute("id")
							+ "; a schema keeps its id"
			);
		}
		if (version > version(schema)) {
			differ("the baseline is version " + version + ", above the schema's " + version(schema));
		}
		if (!schema.getAttribute("byteOrder").equals(baseline.getAttribute("byteOrder"))) {
			differ(
					"the baseline is " + baseline.getAttribute("byteOrder") + ", not "
							+ schema.getAttribute("byteOrder") + "; the byte order never changes"
			);
		}
		String header = schema.getAttribute("headerType");
		String reason = type(header, baseline.getAttribute("headerType"));
		if (reason != null) {
			under(
					new Segment("composite", header),
					() -> differ("the header differs from the baseline's: " + reason + "; the header never changes")
			);
		}
		Map<String, Element> messages = byId(schema);
		Map<String, Element> released = byId(baseline);
		for (Map.Entry<String, Element> message : released.entrySet()) {
			if (!messages.containsKey(message.getKey())) {
				differ(
						"the baseline has a message \"" + message.getValue().getAttribute("name") + "\" (id "
								+ message.getKey()
								+ ") and the schema none with that id; a message stays, deprecated if it is retired"
				);
			}
		}
		for (Map.Entry<String, Element> message : messages.entrySet()) {
			Element other = released.get(message.getKey());
			under(segment(message.getValue()), () -> {
				if (other == null) {
					added(message.getValue());
				} else {
					block(message.getValue(), other);
				}
			});
		}
	}

	private static Map<String, Element> byId(Element root) {
		Map<String, Element> messages = new LinkedHashMap<>();
		for (Element message : SchemaEquivalence.children(root, "message")) {
			messages.put(message.getAttribute("id"), message);
		}
		return messages;
	}

	/**
	 * A message's or a group's body: the baseline's fields, groups and var-data
	 * first, in order, then whatever was appended since.
	 */
	private void block(Element ours, Element theirs) {
		attributes(ours, theirs, Set.of("id", "blockLength", "dimensionType"));
		blockLength(ours, theirs);
		if (ours.getLocalName().equals("group")) {
			id(ours, theirs);
			String reason = type(ours.getAttribute("dimensionType"), theirs.getAttribute("dimensionType"));
			if (reason != null) {
				differ("the dimensions differ from the baseline's: " + reason + "; a group keeps its dimensions");
			}
		}
		for (String element : List.of("field", "group", "data")) {
			List<Element> members = SchemaEquivalence.children(ours, element);
			List<Element> released = SchemaEquivalence.children(theirs, element);
			for (int i = released.size(); i < members.size(); i++) {
				Element member = members.get(i);
				under(segment(member), () -> added(member));
			}
			for (int i = 0; i < Math.min(members.size(), released.size()); i++) {
				Element member = members.get(i);
				Element other = released.get(i);
				under(segment(member), () -> {
					if (element.equals("group")) {
						block(member, other);
					} else {
						field(member, other);
					}
				});
			}
			for (Element removed : released.subList(Math.min(members.size(), released.size()), released.size())) {
				differ(
						"the baseline's " + theirs.getAttribute("name") + " has " + noun(element, true) + " \""
								+ removed.getAttribute("name") + "\" (id " + removed.getAttribute("id")
								+ ") the schema lacks; " + (element.equals("field")
										? "a field stays, unmapped if the record retires it"
										: noun(element, true) + " stays")
				);
			}
		}
	}

	/** A field or var-data at the position the baseline has one. */
	private void field(Element ours, Element theirs) {
		id(ours, theirs);
		attributes(ours, theirs, Set.of("id", "type", "presence", "valueRef"));
		presence(ours, theirs);
		String constant = constant(ours, this.ours);
		String released = constant(theirs, this.theirs);
		if (!Objects.equals(constant, released)) {
			differ(
					"the baseline's constant here is " + quoted(released) + ", not " + quoted(constant)
							+ "; a constant keeps its value"
			);
		}
		String reason = type(ours.getAttribute("type"), theirs.getAttribute("type"));
		if (reason != null) {
			differ("the type differs from the baseline's: " + reason);
		}
	}

	private void id(Element ours, Element theirs) {
		if (!ours.getAttribute("id").equals(theirs.getAttribute("id"))) {
			String element = ours.getLocalName();
			differ(
					"the baseline has " + noun(element, true) + " \"" + theirs.getAttribute("name") + "\" (id "
							+ theirs.getAttribute("id") + ") here, not id " + ours.getAttribute("id")
							+ "; the id is its identity, and " + noun(element, true)
							+ " that means something else is appended as a new one"
			);
		}
	}

	/** A block length the baseline states may grow, and stays stated. */
	private void blockLength(Element ours, Element theirs) {
		String length = ours.getAttribute("blockLength");
		String released = theirs.getAttribute("blockLength");
		if (length.isEmpty() || released.isEmpty()) {
			if (!length.equals(released)) {
				differ(
						"the baseline has " + stated("blockLength", released) + " here, not " + value(length)
								+ "; a block length stays as stated"
				);
			}
		} else if (Long.parseLong(length) < Long.parseLong(released)) {
			differ(
					"the baseline has blockLength=\"" + released + "\", above \"" + length
							+ "\"; a block only grows"
			);
		}
	}

	private void presence(Element ours, Element theirs) {
		String presence = ours.getAttribute("presence");
		String released = theirs.getAttribute("presence");
		if (!presence.equals(released) && (presence.equals("constant") || released.equals("constant"))) {
			differ(
					"the baseline has presence=\"" + released + "\" here, not \"" + presence
							+ "\"; a constant takes no space in the block"
			);
		}
	}

	/** Something the baseline lacks, which states the version it was added in. */
	private void added(Element element) {
		if (sinceVersion(element) <= version) {
			differ(
					noun(element.getLocalName(), true)
							+ " the baseline lacks needs a sinceVersion above the baseline's version "
							+ version
			);
		}
	}

	/**
	 * Every attribute either side has, except the free ones and those the caller
	 * compares its own way.
	 */
	private void attributes(Element ours, Element theirs, Set<String> handled) {
		Mismatch mismatch = mismatch(ours, theirs, handled);
		if (mismatch != null) {
			differ("the baseline has " + mismatch.released() + " here, not " + mismatch.value());
		}
	}

	// ---- types, as what they are

	/**
	 * Why two types, each named in its own document, differ on the wire or in
	 * meaning, or null where they are one: a primitive by its name, a declaration
	 * by its content.
	 */
	private @Nullable String type(String ours, String theirs) {
		return type(resolve(ours, this.ours), resolve(theirs, this.theirs));
	}

	private Element resolve(String name, Map<String, Element> declarations) {
		Element declaration = declarations.get(name);
		if (declaration != null) {
			return declaration;
		}
		Element primitive = primitives.get(name);
		if (primitive == null) {
			// sbe.xsd lets the name through; sbe-tool refuses it before any comparison.
			throw new IllegalArgumentException("no type named " + name);
		}
		return primitive;
	}

	private @Nullable String type(Element ours, Element theirs) {
		String kind = theirs.getLocalName();
		boolean primitive = primitives.containsValue(ours) && primitives.containsValue(theirs);
		if (!ours.getLocalName().equals(kind) || primitive && ours != theirs) {
			return describe(theirs) + ", not " + describe(ours);
		}
		Mismatch mismatch = mismatch(ours, theirs, TYPE_HANDLED);
		if (mismatch != null) {
			return describe(theirs) + " has " + mismatch.released() + ", not " + mismatch.value();
		}
		return switch (kind) {
			case "type" -> encoded(ours, theirs);
			case "composite" -> composite(ours, theirs);
			case "enum" -> values(ours, theirs, "validValue", "value");
			case "set" -> values(ours, theirs, "choice", "choice");
			default -> throw new IllegalStateException("a type of " + kind);
		};
	}

	private @Nullable String encoded(Element ours, Element theirs) {
		String presence = ours.getAttribute("presence");
		String released = theirs.getAttribute("presence");
		if (!presence.equals(released) && (presence.equals("constant") || released.equals("constant"))) {
			return describe(theirs) + " has presence=\"" + released + "\", not \"" + presence + "\"";
		}
		String constant = ours.getTextContent().trim();
		String releasedConstant = theirs.getTextContent().trim();
		if (!constant.equals(releasedConstant)) {
			return describe(theirs) + " has the constant " + quoted(releasedConstant) + ", not "
					+ quoted(constant);
		}
		return null;
	}

	/** A composite never grows: its members, in order, each what it was. */
	private @Nullable String composite(Element ours, Element theirs) {
		List<Element> members = SchemaEquivalence.children(ours, null);
		List<Element> released = SchemaEquivalence.children(theirs, null);
		if (members.size() != released.size()) {
			return describe(theirs) + " has " + released.size() + " members, not " + members.size()
					+ "; a composite keeps its members";
		}
		for (int i = 0; i < members.size(); i++) {
			Element member = members.get(i);
			Element other = released.get(i);
			String reason = placement(member, other);
			if (reason == null) {
				reason = type(target(member, this.ours), target(other, this.theirs));
			}
			if (reason != null) {
				return describe(theirs) + " at \"" + other.getAttribute("name") + "\": " + reason;
			}
		}
		return null;
	}

	/** A member's offset and version, which belong to it rather than its type. */
	private @Nullable String placement(Element ours, Element theirs) {
		for (String attribute : PLACEMENT) {
			String value = ours.getAttribute(attribute);
			String released = theirs.getAttribute(attribute);
			if (!value.equals(released)) {
				return stated(attribute, released) + ", not " + value(value);
			}
		}
		return null;
	}

	private Element target(Element member, Map<String, Element> declarations) {
		return member.getLocalName().equals("ref") ? resolve(member.getAttribute("type"), declarations) : member;
	}

	/**
	 * An enum's valid values or a set's choices, matched by value or bit: one the
	 * baseline has stays, at its version, and one it lacks states a later one.
	 */
	private @Nullable String values(Element ours, Element theirs, String element, String noun) {
		String encoding = type(ours.getAttribute("encodingType"), theirs.getAttribute("encodingType"));
		if (encoding != null) {
			return describe(theirs) + " is encoded as " + encoding;
		}
		Map<String, Element> values = byValue(ours, element);
		Map<String, Element> released = byValue(theirs, element);
		for (Map.Entry<String, Element> value : released.entrySet()) {
			Element other = values.get(value.getKey());
			String name = value.getValue().getAttribute("name");
			if (other == null) {
				return describe(theirs) + " has the " + noun + " \"" + value.getKey() + "\" (" + name
						+ "), which the schema's lacks; " + noun + "s stay, deprecated if they are retired";
			}
			if (sinceVersion(other) != sinceVersion(value.getValue())) {
				return describe(theirs) + " has the " + noun + " \"" + value.getKey() + "\" (" + name
						+ ") since version " + sinceVersion(value.getValue()) + ", not " + sinceVersion(other);
			}
		}
		for (Map.Entry<String, Element> value : values.entrySet()) {
			if (!released.containsKey(value.getKey()) && sinceVersion(value.getValue()) <= version) {
				return describe(theirs) + " lacks the " + noun + " \"" + value.getKey() + "\" ("
						+ value.getValue().getAttribute("name")
						+ "), which needs a sinceVersion above the baseline's version "
						+ version;
			}
		}
		return null;
	}

	private static Map<String, Element> byValue(Element parent, String element) {
		Map<String, Element> values = new LinkedHashMap<>();
		for (Element value : SchemaEquivalence.children(parent, element)) {
			values.put(value.getTextContent().trim(), value);
		}
		return values;
	}

	/**
	 * The value a constant field's {@code valueRef} names, or null for a field
	 * without one.
	 */
	private static @Nullable String constant(Element field, Map<String, Element> declarations) {
		String valueRef = field.getAttribute("valueRef");
		if (valueRef.isEmpty()) {
			return null;
		}
		int dot = valueRef.lastIndexOf('.');
		Element enumeration = declarations.get(valueRef.substring(0, dot));
		if (enumeration != null) {
			for (Element value : SchemaEquivalence.children(enumeration, "validValue")) {
				if (value.getAttribute("name").equals(valueRef.substring(dot + 1))) {
					return value.getTextContent().trim();
				}
			}
		}
		// sbe-tool refuses a reference to nothing; name it as it is written.
		return valueRef;
	}

	// ---- attributes

	/** An attribute as the baseline states it, and the schema's value. */
	private record Mismatch(String released, String value) {
	}

	/**
	 * The first attribute either side has, the XSD's defaults among them, that
	 * differs; the namespace declarations, the free attributes and {@code handled}
	 * aside.
	 */
	private static @Nullable Mismatch mismatch(Element ours, Element theirs, Set<String> handled) {
		Set<String> names = new LinkedHashSet<>(SchemaEquivalence.attributes(theirs).keySet());
		names.addAll(SchemaEquivalence.attributes(ours).keySet());
		for (String name : names) {
			if (FREE.contains(name) || handled.contains(name)) {
				continue;
			}
			String value = ours.getAttribute(name);
			String released = theirs.getAttribute(name);
			if (!value.equals(released)) {
				return new Mismatch(stated(name, released), value(value));
			}
		}
		return null;
	}

	private static String stated(String attribute, String value) {
		return value.isEmpty() ? "no " + attribute : attribute + "=\"" + value + "\"";
	}

	private static String value(String value) {
		return value.isEmpty() ? "none" : "\"" + value + "\"";
	}

	private static String quoted(@Nullable String value) {
		return value == null ? "none" : "\"" + value + "\"";
	}

	private String describe(Element type) {
		return primitives.containsValue(type)
				? type.getAttribute("name")
				: type.getLocalName() + " \"" + type.getAttribute("name") + "\"";
	}

	private static int sinceVersion(Element element) {
		String since = element.getAttribute("sinceVersion");
		return since.isEmpty() ? 0 : Integer.parseInt(since);
	}

	/**
	 * What an element is called in a sentence, with its article where
	 * {@code article} asks for one.
	 */
	private static String noun(String element, boolean article) {
		return switch (element) {
			case "field" -> article ? "a field" : "field";
			case "group" -> article ? "a group" : "group";
			case "data" -> "var-data";
			case "message" -> article ? "a message" : "message";
			default -> throw new IllegalStateException("a member " + element);
		};
	}

	private static Segment segment(Element element) {
		return new Segment(element.getLocalName(), element.getAttribute("name"));
	}

	private void differ(String message) {
		differences.add(new Difference(path, message));
	}

	private void under(Segment segment, Runnable comparison) {
		path.add(segment);
		try {
			comparison.run();
		} finally {
			path.remove(path.size() - 1);
		}
	}
}
