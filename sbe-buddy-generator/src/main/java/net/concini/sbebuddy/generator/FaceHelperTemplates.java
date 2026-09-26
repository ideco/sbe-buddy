package net.concini.sbebuddy.generator;

/**
 * The text of the helper methods the leaves call, declared once however many
 * leaves call them: the checks before text or bytes reach a flyweight, the
 * array pairs, and each enum's and set's mapping. {@link FaceWriter} fills them
 * from a {@link Faces.Helper}; a template's names in braces are the only values
 * it takes.
 */
final class FaceHelperTemplates {

	private FaceHelperTemplates() {
	}

	// ---- a char string: the flyweight's own String form, checked first

	/**
	 * The flyweight writes a char above 127 as '?' and throws its own exception
	 * over the length; the codec refuses both first.
	 */
	static final Template ASCII = Template.of("""
			private static String ascii(String value, long length, String field) {
				if (value.length() > length) {
					throw new IllegalArgumentException(field + " is longer than " + length + ": " + value);
				}
				for (int i = 0; i < value.length(); i++) {
					if (value.charAt(i) > 127) {
						throw new IllegalArgumentException(field + " is not ASCII: " + value);
					}
				}
				return value;
			}""");

	static final Template CHARSET_CONSTANT = Template
			.of("private static final java.nio.charset.Charset {constant} = java.nio.charset.Charset.forName({name});");

	/**
	 * A CharsetEncoder reports what String.getBytes would replace: an unmappable
	 * char, a lone surrogate.
	 */
	static final Template ENCODED = Template.of(
			"""
					private static byte[] encoded(String value, java.nio.charset.Charset charset, long maxLength, String field) {
						java.nio.ByteBuffer bytes;
						try {
							bytes = charset.newEncoder().encode(java.nio.CharBuffer.wrap(value));
						} catch (java.nio.charset.CharacterCodingException e) {
							throw new IllegalArgumentException(field + " cannot be written in " + charset.name() + ": " + value, e);
						}
						if (bytes.remaining() > maxLength) {
							throw new IllegalArgumentException(
									field + " is longer than " + maxLength + " bytes in " + charset.name() + ": " + bytes.remaining());
						}
						byte[] encoded = new byte[bytes.remaining()];
						bytes.get(encoded);
						return encoded;
					}

					private static byte[] fixed(String value, java.nio.charset.Charset charset, int length, String field) {
						return java.util.Arrays.copyOf(encoded(value, charset, length, field), length);
					}"""
	);

	// ---- a fixed-length array: a pair per field, over the index accessors

	static final Template WRITE_ARRAY = Template.of("""
			private static void write{field}({face}[] value, {encoder} encoder) {
				if (value.length != {encoder}.{property}Length()) {
					throw new IllegalArgumentException(
							"{component} must be " + {encoder}.{property}Length() + " long, not " + value.length);
				}
				for (int i = 0; i < value.length; i++) {
					encoder.{property}(i, value[i]);
				}
			}""");

	static final Template READ_ARRAY = Template.of("""
			private static {face}[] read{field}({decoder} decoder) {
				{face}[] value = new {face}[{decoder}.{property}Length()];
				for (int i = 0; i < value.length; i++) {
					value[i] = decoder.{property}(i);
				}
				return value;
			}""");

	/**
	 * A uint8 array has bulk accessors over byte[], where its index accessors widen
	 * to short.
	 */
	static final Template WRITE_BYTES = Template.of("""
			private static void write{field}(byte[] value, {encoder} encoder) {
				if (value.length != {encoder}.{property}Length()) {
					throw new IllegalArgumentException(
							"{component} must be " + {encoder}.{property}Length() + " long, not " + value.length);
				}
				encoder.put{bulk}(value, 0, value.length);
			}""");

	static final Template READ_BYTES = Template.of("""
			private static byte[] read{field}({decoder} decoder) {
				byte[] value = new byte[{decoder}.{property}Length()];
				decoder.get{bulk}(value, 0, value.length);
				return value;
			}""");

	// ---- var-data: methods per data member, keyed by its path, over its body's
	// classes; its bytes counted without encoding

	/**
	 * What String.getBytes would write, counted: a lone surrogate, which it would
	 * write as '?', has no UTF-8 form.
	 */
	static final Template UTF_8 = Template.of("""
			private static int utf8(String value, long maxLength, String field) {
				int length = 0;
				for (int i = 0; i < value.length(); i++) {
					char c = value.charAt(i);
					if (c < 0x80) {
						length += 1;
					} else if (c < 0x800) {
						length += 2;
					} else if (!Character.isSurrogate(c)) {
						length += 3;
					} else if (Character.isHighSurrogate(c) && i + 1 < value.length()
							&& Character.isLowSurrogate(value.charAt(i + 1))) {
						length += 4;
						i++;
					} else {
						throw new IllegalArgumentException(field + " has a lone surrogate at index " + i);
					}
				}
				if (length > maxLength) {
					throw new IllegalArgumentException(
							field + " is longer than " + maxLength + " bytes in UTF-8: " + length);
				}
				return length;
			}""");

	static final Template BYTES = Template.of(
			"""
					private static int bytes(byte[] value, long maxLength, String field) {
						if (value.length > maxLength) {
							throw new IllegalArgumentException(field + " is longer than " + maxLength + " bytes: " + value.length);
						}
						return value.length;
					}"""
	);

	// ---- an enum's mapping, one pair per codec

	static final Template ENCODE_ENUM = Template.of("""
			private static {flyweights}.{enumClass} encode{enumClass}({javaEnum} value) {
				return switch (value) {
					{cases}
				};
			}""");

	static final Template ENUM_TO_WIRE = Template.of("case {constant} -> {flyweights}.{enumClass}.{wireConstant};");

	static final Template UNKNOWN_TO_WIRE = Template.of(
			"case {unknownValue} -> throw new IllegalArgumentException(\"{enumClass}.{unknownValue} has no wire form\");"
	);

	static final Template DECODE_ENUM = Template.of("""
			private static {javaEnum} decode{enumClass}({face} raw) {
				return switch (raw) {
					{cases}
				};
			}""");

	static final Template WIRE_TO_ENUM = Template.of("case {literal} -> {javaEnum}.{constant};");

	static final Template WIRE_TO_UNKNOWN = Template.of("default -> {javaEnum}.{unknownValue};");

	static final Template WIRE_TO_NOTHING = Template.of(
			"default -> throw new IllegalArgumentException(\"{enumClass} has no value \" + raw);"
	);

	// ---- a set's mapping, one pair per codec

	static final Template ENCODE_SET = Template.of("""
			private static void encode{setClass}(java.util.Set<{javaEnum}> value, {flyweights}.{setClass}Encoder wire) {
				wire.clear();
				{choices}
			}""");

	static final Template ENCODE_CHOICE = Template.of("wire.{property}(value.contains({javaEnum}.{constant}));");

	/** A bit no choice names has nowhere to go in a Set of the enum. */
	static final Template DECODE_SET = Template.of("""
			private static java.util.Set<{javaEnum}> decode{setClass}({flyweights}.{setClass}Decoder wire) {
				if ((wire.getRaw() & ~({knownBits})) != 0) {
					throw new IllegalArgumentException("{setClass} has a bit no choice names: " + wire.getRaw());
				}
				java.util.Set<{javaEnum}> value = java.util.EnumSet.noneOf({javaEnum}.class);
				{choices}
				return value;
			}""");

	static final Template KNOWN_BIT = Template.of("1L << {bit}");

	static final Template DECODE_CHOICE = Template.of("""
			if (wire.{property}()) {
				value.add({javaEnum}.{constant});
			}""");
}
