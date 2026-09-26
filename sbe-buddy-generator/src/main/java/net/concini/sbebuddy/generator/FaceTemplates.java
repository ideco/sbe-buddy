package net.concini.sbebuddy.generator;

/**
 * The text of one leaf, grouped by the shape it writes: how a field or member
 * is read, written, written as its null value and guarded for its absence, and
 * how a binding stands in front of it. {@link FaceWriter} fills most of them
 * from a {@link Faces} shape; a template's names in braces are the only values
 * it takes. {@code flyweight} is the variable holding the flyweight,
 * {@code value} the expression of the component's value, {@code qualifier} what
 * a static helper is called through, empty in a codec and the class with its
 * dot in a reader or writer, whose stages may declare a method of a helper's
 * name, and {@code instance} the same for a composite's pair, which may call a
 * binding: the class's {@code this} in a reader or writer.
 */
final class FaceTemplates {

	private FaceTemplates() {
	}

	// ---- a primitive field

	static final Template ENCODE_FIELD = Template.of("{flyweight}.{property}({source});");

	static final Template ENCODE_OPTIONAL_FIELD = Template.of(
			"{flyweight}.{property}({value} == null ? {encoder}.{property}NullValue() : {source});"
	);

	static final Template DECODE_FIELD = Template.of("{flyweight}.{property}()");

	static final Template IS_NULL = Template.of("{flyweight}.{property}() == {decoder}.{property}NullValue()");

	/** The null value of the floats is NaN, which == never matches. */
	static final Template IS_NULL_FLOATING = Template.of(
			"{floatBox}.compare({flyweight}.{property}(), {decoder}.{property}NullValue()) == 0"
	);

	// ---- a field of an enum

	static final Template ENCODE_ENUM_FIELD = Template
			.of("{flyweight}.{property}({qualifier}encode{enumClass}({source}));");

	static final Template ENCODE_OPTIONAL_ENUM_FIELD = Template.of(
			"{flyweight}.{property}({value} == null ? {flyweights}.{enumClass}.NULL_VAL : {qualifier}encode{enumClass}({source}));"
	);

	static final Template DECODE_ENUM_FIELD = Template.of("{qualifier}decode{enumClass}({flyweight}.{property}Raw())");

	static final Template IS_NULL_ENUM = Template.of(
			"{flyweight}.{property}Raw() == {flyweights}.{enumClass}.NULL_VAL.value()"
	);

	// ---- a field of a set

	static final Template ENCODE_SET_FIELD = Template
			.of("{qualifier}encode{setClass}({source}, {flyweight}.{property}());");

	static final Template DECODE_SET_FIELD = Template.of("{qualifier}decode{setClass}({flyweight}.{property}())");

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
			"{flyweight}.{property}({qualifier}ascii({source}, {encoder}.{property}Length(), \"{component}\"));"
	);

	/**
	 * In another encoding, the flyweight's String form would write an unmappable
	 * char as '?' and read it back; the codec encodes it, refusing what the
	 * encoding cannot hold, and writes the bytes, padded with zeros.
	 */
	static final Template ENCODE_ENCODED_STRING_FIELD = Template.of(
			"{flyweight}.put{bulk}({qualifier}fixed({source}, {charset}, {encoder}.{property}Length(), \"{component}\"), 0);"
	);

	// ---- a fixed-length array: a pair per field, over the index accessors

	static final Template ENCODE_ARRAY_FIELD = Template.of("{qualifier}write{field}({source}, {flyweight});");

	static final Template DECODE_ARRAY_FIELD = Template.of("{qualifier}read{field}({flyweight})");

	// ---- a constant: no bytes; the record must agree with the schema

	static final Template CHECK_CONSTANT_FIELD = Template.of("""
			if ({source} != {flyweight}.{property}()) {
				throw new IllegalArgumentException("{component} is the constant " + {flyweight}.{property}());
			}""");

	static final Template CHECK_CONSTANT_STRING_FIELD = Template.of("""
			if (!{flyweight}.{property}().equals({source})) {
				throw new IllegalArgumentException("{component} is the constant " + {flyweight}.{property}());
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

	/** A codec's value of the component: the record's accessor. */
	static final Template SOURCE = Template.of("value.{component}()");

	/** What the flyweight is handed: the binding's view of the component. */
	static final Template BOUND_SOURCE = Template.of("{binding}.toWire({value}, {context})");

	static final Template BOUND_READ = Template.of("{binding}.fromWire({read}, {context})");

	// ---- a composite: a pair per composite type, over its own flyweights

	static final Template ENCODE_COMPOSITE_FIELD = Template
			.of("{instance}write{compositeClass}({source}, {flyweight}.{property}());");

	static final Template DECODE_COMPOSITE_FIELD = Template
			.of("{instance}read{compositeClass}({flyweight}.{property}())");

	// ---- the shapes over them

	/** A component that may be null on a required field: null has no wire form. */
	static final Template ENCODE_CHECKED_FIELD = Template.of("""
			if ({value} == null) {
				throw new IllegalArgumentException("{component} is required");
			}
			{call}""");

	/**
	 * An optional field on a face with no null value, without a binding to write
	 * one.
	 */
	static final Template ENCODE_NO_NULL_VALUE_FIELD = Template.of(
			"""
					if ({value} == null) {
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
			"{flyweight}.actingVersion() < {decoder}.{property}SinceVersion() ? null : {read}"
	);

	// ---- var-data: through its methods, which count and check it

	static final Template ENCODE_DATA_FIELD = Template.of("{qualifier}write{path}({source}, {flyweight});");

	/**
	 * A group's or var-data's view, whose null the method it goes to refuses: the
	 * binding is never handed null.
	 */
	static final Template NULLABLE_BOUND_SOURCE = Template
			.of("{value} == null ? null : {binding}.toWire({value}, {context})");

	static final Template DECODE_TEXT = Template.of("{flyweight}.{property}()");

	static final Template DECODE_BYTES = Template.of("{qualifier}read{path}({flyweight})");
}
