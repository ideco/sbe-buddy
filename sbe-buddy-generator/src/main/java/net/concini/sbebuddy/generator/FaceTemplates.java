package net.concini.sbebuddy.generator;

/**
 * The text of one leaf, grouped by the shape it writes: how a field or member
 * is read, written, written as its null value and guarded for its absence, and
 * how a binding stands in front of it. {@link FaceWriter} fills most of them
 * from a {@link Faces} shape; a template's names in braces are the only values
 * it takes.
 */
final class FaceTemplates {

	private FaceTemplates() {
	}

	// ---- a primitive field

	static final Template ENCODE_FIELD = Template.of("encoder.{property}({source});");

	static final Template ENCODE_OPTIONAL_FIELD = Template.of(
			"encoder.{property}(value.{component}() == null ? {encoder}.{property}NullValue() : {source});"
	);

	static final Template DECODE_FIELD = Template.of("decoder.{property}()");

	static final Template IS_NULL = Template.of("decoder.{property}() == {decoder}.{property}NullValue()");

	/** The null value of the floats is NaN, which == never matches. */
	static final Template IS_NULL_FLOATING = Template.of(
			"{floatBox}.compare(decoder.{property}(), {decoder}.{property}NullValue()) == 0"
	);

	// ---- a field of an enum

	static final Template ENCODE_ENUM_FIELD = Template.of("encoder.{property}(encode{enumClass}({source}));");

	static final Template ENCODE_OPTIONAL_ENUM_FIELD = Template.of(
			"encoder.{property}(value.{component}() == null ? {flyweights}.{enumClass}.NULL_VAL : encode{enumClass}({source}));"
	);

	static final Template DECODE_ENUM_FIELD = Template.of("decode{enumClass}(decoder.{property}Raw())");

	static final Template IS_NULL_ENUM = Template.of(
			"decoder.{property}Raw() == {flyweights}.{enumClass}.NULL_VAL.value()"
	);

	// ---- a field of a set

	static final Template ENCODE_SET_FIELD = Template.of("encode{setClass}({source}, encoder.{property}());");

	static final Template DECODE_SET_FIELD = Template.of("decode{setClass}(decoder.{property}())");

	// ---- a field no component carries: the null value out, nothing back

	static final Template ENCODE_UNMAPPED_FIELD = Template
			.of("encoder.{property}({encoder}.{property}NullValue());");

	/** Any array, a char string in any encoding included, element by element. */
	static final Template ENCODE_UNMAPPED_ARRAY_FIELD = Template.of("""
			for (int i = 0; i < {encoder}.{property}Length(); i++) {
				encoder.{property}(i, {encoder}.{property}NullValue());
			}""");

	static final Template ENCODE_UNMAPPED_ENUM_FIELD = Template
			.of("encoder.{property}({flyweights}.{enumClass}.NULL_VAL);");

	static final Template ENCODE_UNMAPPED_SET_FIELD = Template.of("encoder.{property}().clear();");

	/**
	 * Member by member, through the writer's {@code nulls} for the composite's
	 * flyweight.
	 */
	static final Template ENCODE_UNMAPPED_COMPOSITE_FIELD = Template.of("nulls(encoder.{property}());");

	// ---- a char string: the flyweight's own String form, checked first

	static final Template ENCODE_STRING_FIELD = Template.of(
			"encoder.{property}(ascii({source}, {encoder}.{property}Length(), \"{component}\"));"
	);

	/**
	 * In another encoding, the flyweight's String form would write an unmappable
	 * char as '?' and read it back; the codec encodes it, refusing what the
	 * encoding cannot hold, and writes the bytes, padded with zeros.
	 */
	static final Template ENCODE_ENCODED_STRING_FIELD = Template.of(
			"encoder.put{bulk}(fixed({source}, {charset}, {encoder}.{property}Length(), \"{component}\"), 0);"
	);

	// ---- a fixed-length array: a pair per field, over the index accessors

	static final Template ENCODE_ARRAY_FIELD = Template.of("write{field}({source}, encoder);");

	static final Template DECODE_ARRAY_FIELD = Template.of("read{field}(decoder)");

	// ---- a constant: no bytes; the record must agree with the schema

	static final Template CHECK_CONSTANT_FIELD = Template.of("""
			if ({source} != encoder.{property}()) {
				throw new IllegalArgumentException("{component} is the constant " + encoder.{property}());
			}""");

	static final Template CHECK_CONSTANT_STRING_FIELD = Template.of("""
			if (!encoder.{property}().equals({source})) {
				throw new IllegalArgumentException("{component} is the constant " + encoder.{property}());
			}""");

	static final Template CHECK_CONSTANT_ENUM_FIELD = Template.of("""
			if ({source} != {javaEnum}.{constant}) {
				throw new IllegalArgumentException("{component} is the constant {constant}");
			}""");

	// ---- a binding: the component mapped before the flyweight and after it,
	// handed the component's context

	static final Template BINDING_FIELD = Template.of("private final {type} {name} = new {type}();");

	static final Template CONTEXT_FIELD = Template.of(
			"private static final net.concini.sbebuddy.BindingContext {name} = new net.concini.sbebuddy.BindingContext({component}, {primitiveType}, {characterEncoding}, {epoch}, {timeUnit}, {presence});"
	);

	/** What the flyweight is handed: the component, or the binding's view of it. */
	static final Template SOURCE = Template.of("value.{component}()");

	static final Template BOUND_SOURCE = Template.of("{binding}.toWire(value.{component}(), {context})");

	static final Template BOUND_READ = Template.of("{binding}.fromWire({read}, {context})");

	// ---- a composite: a pair per composite type, over its own flyweights

	static final Template ENCODE_COMPOSITE_FIELD = Template
			.of("write{compositeClass}({source}, encoder.{property}());");

	static final Template DECODE_COMPOSITE_FIELD = Template.of("read{compositeClass}(decoder.{property}())");

	// ---- the shapes over them

	/** A component that may be null on a required field: null has no wire form. */
	static final Template ENCODE_CHECKED_FIELD = Template.of("""
			if (value.{component}() == null) {
				throw new IllegalArgumentException("{component} is required");
			}
			{call}""");

	/**
	 * An optional field on a face with no null value, without a binding to write
	 * one.
	 */
	static final Template ENCODE_NO_NULL_VALUE_FIELD = Template.of(
			"""
					if (value.{component}() == null) {
						throw new IllegalArgumentException("{component} has no null value on the wire; a binding may write one");
					}
					{call}"""
	);

	static final Template DECODE_OPTIONAL_FIELD = Template.of("{isNull} ? null : {read}");

	/**
	 * Absent below the acting version, decided on the version and never on the null
	 * value, which a required field may legitimately hold.
	 */
	static final Template DECODE_ADDED_FIELD = Template.of(
			"decoder.actingVersion() < {decoder}.{property}SinceVersion() ? null : {read}"
	);
}
