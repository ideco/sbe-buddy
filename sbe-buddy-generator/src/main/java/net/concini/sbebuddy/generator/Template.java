package net.concini.sbebuddy.generator;

import java.lang.reflect.RecordComponent;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/**
 * A text block with named placeholders, {@code {name}}, and nothing else: no
 * conditionals, no loops; what varies is decided in Java and pasted in. A value
 * that spans lines is indented to its placeholder's column, so a body pasted
 * into a class lands at the right depth; a blank line of it stays blank, and
 * for a placeholder alone on its line that goes for the first line too, so a
 * value may open with a blank line, and an empty value takes the line with it,
 * so a block left out leaves no blank line behind. A name left unfilled or a
 * value never used fails, because a gap in generated code is a bug nobody sees
 * until it compiles. The values may come from a record, a placeholder taking
 * the component of its name, so a template reads a model node as it is.
 */
public final class Template {

	private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z][A-Za-z]*)}");

	private final String text;

	private Template(String text) {
		this.text = text;
	}

	public static Template of(String text) {
		return new Template(text);
	}

	/** Names and values, alternating. */
	public String fill(String... namesAndValues) {
		return fill(null, namesAndValues);
	}

	/**
	 * A placeholder takes the value given for its name, or else the record's
	 * component of that name, which must be a {@code String}. Every value given
	 * must be used; the record's components need not be.
	 */
	public String fill(@Nullable Record source, String... namesAndValues) {
		if (namesAndValues.length % 2 != 0) {
			throw new IllegalArgumentException("names and values alternate");
		}
		Map<String, String> values = new LinkedHashMap<>();
		for (int i = 0; i < namesAndValues.length; i += 2) {
			if (values.put(namesAndValues[i], namesAndValues[i + 1]) != null) {
				throw new IllegalArgumentException(namesAndValues[i] + " is given twice");
			}
		}
		Set<String> unused = new LinkedHashSet<>(values.keySet());
		StringBuilder filled = new StringBuilder();
		Matcher placeholder = PLACEHOLDER.matcher(text);
		int copied = 0;
		while (placeholder.find()) {
			String name = placeholder.group(1);
			String value = values.containsKey(name) ? values.get(name) : component(source, name);
			if (value == null) {
				throw new IllegalArgumentException("{" + name + "} is not filled");
			}
			unused.remove(name);
			if (aloneOnItsLine(placeholder.start(), placeholder.end())) {
				filled.append(text, copied, lineStart(placeholder.start()));
				if (value.isEmpty()) {
					copied = Math.min(placeholder.end() + 1, text.length());
					continue;
				}
				filled.append(indented(value, text.substring(lineStart(placeholder.start()), placeholder.start())));
			} else {
				filled.append(text, copied, placeholder.start());
				filled.append(value);
			}
			copied = placeholder.end();
		}
		filled.append(text, copied, text.length());
		if (!unused.isEmpty()) {
			throw new IllegalArgumentException(unused + " are not placeholders of this template");
		}
		return filled.toString();
	}

	/** The record's text component of the name, or null where it has none. */
	private static @Nullable String component(@Nullable Record source, String name) {
		if (source == null) {
			return null;
		}
		for (RecordComponent component : source.getClass().getRecordComponents()) {
			if (component.getName().equals(name)) {
				Object value;
				try {
					value = component.getAccessor().invoke(source);
				} catch (ReflectiveOperationException e) {
					throw new IllegalStateException(e);
				}
				if (value != null && !(value instanceof String)) {
					throw new IllegalArgumentException(
							"{" + name + "} would take " + source.getClass().getSimpleName() + "." + name
									+ ", which is not text"
					);
				}
				return (String) value;
			}
		}
		return null;
	}

	/**
	 * Every line of the value at the placeholder's indentation, blank ones left
	 * blank.
	 */
	private static String indented(String value, String indentation) {
		StringBuilder result = new StringBuilder();
		String[] lines = value.split("\n", -1);
		for (int i = 0; i < lines.length; i++) {
			if (i > 0) {
				result.append('\n');
			}
			if (!lines[i].isEmpty()) {
				result.append(indentation).append(lines[i]);
			}
		}
		return result.toString();
	}

	private boolean aloneOnItsLine(int start, int end) {
		return text.substring(lineStart(start), start).isBlank() && (end == text.length() || text.charAt(end) == '\n');
	}

	private int lineStart(int position) {
		return text.lastIndexOf('\n', position - 1) + 1;
	}
}
