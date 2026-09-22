package net.concini.sbebuddy.generator;

import static net.concini.sbebuddy.generator.Annotated.JavaPrimitive.BYTE;
import static net.concini.sbebuddy.generator.Annotated.JavaPrimitive.CHAR;
import static net.concini.sbebuddy.generator.Annotated.JavaPrimitive.INT;
import static net.concini.sbebuddy.generator.Annotated.JavaPrimitive.LONG;
import static net.concini.sbebuddy.generator.Annotated.JavaPrimitive.SHORT;
import static net.concini.sbebuddy.generator.Fixtures.annotatedEnum;
import static net.concini.sbebuddy.generator.Fixtures.annotatedField;
import static net.concini.sbebuddy.generator.Fixtures.annotatedGroup;
import static net.concini.sbebuddy.generator.Fixtures.annotatedMessage;
import static net.concini.sbebuddy.generator.Fixtures.annotatedSchema;
import static net.concini.sbebuddy.generator.Fixtures.annotatedSet;
import static net.concini.sbebuddy.generator.Fixtures.annotatedType;
import static net.concini.sbebuddy.generator.Fixtures.array;
import static net.concini.sbebuddy.generator.Fixtures.boxed;
import static net.concini.sbebuddy.generator.Fixtures.bytes;
import static net.concini.sbebuddy.generator.Fixtures.declared;
import static net.concini.sbebuddy.generator.Fixtures.primitive;
import static net.concini.sbebuddy.generator.Fixtures.setOf;
import static net.concini.sbebuddy.generator.Fixtures.text;
import static net.concini.sbebuddy.generator.Fixtures.unmapped;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import uk.co.real_logic.sbe.PrimitiveType;
import uk.co.real_logic.sbe.xml.Presence;

/**
 * The rules decidable from one node, each built as the mistake and asserted as
 * the problem and the node it names.
 */
final class MappingTest {

	@Test
	void typeAndPrimitiveTypeTogetherIsAProblem() {
		Fixtures.AnnotatedFieldBuilder field = annotatedField("qty", 1, primitive(INT))
				.type(annotatedType("Quantity", PrimitiveType.UINT32))
				.primitiveType(PrimitiveType.UINT16);

		assertThat(problemsOf(field)).containsExactly(
				new Problem(field.build(), "type and primitiveType both given; give one")
		);
	}

	@Test
	void aComponentTypeWithNoMappingIsAProblem() {
		Fixtures.AnnotatedFieldBuilder field = annotatedField("symbol", 1, text());

		assertThat(problemsOf(field)).containsExactly(
				new Problem(field.build(), "String maps to no SBE type; give type or primitiveType")
		);
	}

	@Test
	void javaCharIsAProblem() {
		Fixtures.AnnotatedFieldBuilder field = annotatedField("initial", 1, primitive(CHAR));

		assertThat(problemsOf(field)).containsExactly(
				new Problem(
						field.build(),
						"char, 16 bits where SBE's char is one byte, maps to no SBE type; give type or primitiveType"
				)
		);
	}

	@Test
	void aComponentThatIsNotTheFaceOfItsWireTypeIsAProblem() {
		Fixtures.AnnotatedFieldBuilder field = annotatedField("quantity", 1, primitive(LONG))
				.primitiveType(PrimitiveType.UINT16);

		assertThat(problemsOf(field)).containsExactly(
				new Problem(field.build(), "long is not the face of uint16, which is int")
		);
	}

	@Test
	void aNamedTypeOfLengthOneHasTheFaceOfItsEncoding() {
		Fixtures.AnnotatedFieldBuilder field = annotatedField("symbol", 1, text())
				.type(annotatedType("Initial", PrimitiveType.CHAR));

		assertThat(problemsOf(field)).containsExactly(
				new Problem(field.build(), "String is not the face of char, which is byte")
		);
	}

	@Test
	void aPrimitiveOnAFieldThatCanBeAbsentIsAProblem() {
		Fixtures.AnnotatedFieldBuilder field = annotatedField("quantity", 1, primitive(INT))
				.presence(Presence.OPTIONAL);

		assertThat(problemsOf(field)).containsExactly(
				new Problem(field.build(), "int cannot hold null, but the field can be absent; use Integer")
		);
	}

	@Test
	void aBoxOnAFieldThatIsNeverAbsentIsAWarning() {
		Fixtures.AnnotatedFieldBuilder field = annotatedField("quantity", 1, boxed(INT));

		assertThat(problemsOf(field)).containsExactly(
				new Problem(
						field.build(), "Integer is boxed although the field is never absent", Problem.Severity.WARNING
				)
		);
	}

	@Test
	void aFieldAddedAboveTheBaselineCanBeAbsent() {
		Fixtures.AnnotatedFieldBuilder field = annotatedField("quantity", 1, primitive(INT)).sinceVersion(2);

		assertThat(problemsOf(field, 2, 1)).containsExactly(
				new Problem(field.build(), "int cannot hold null, but the field can be absent; use Integer")
		);
	}

	@Test
	void aFieldAddedAtTheBaselineIsNeverAbsent() {
		Fixtures.AnnotatedFieldBuilder plain = annotatedField("quantity", 1, primitive(INT)).sinceVersion(1);
		Fixtures.AnnotatedFieldBuilder boxed = annotatedField("price", 2, boxed(LONG)).sinceVersion(1);

		assertThat(problemsOf(plain, 2, 1)).isEmpty();
		assertThat(problemsOf(boxed, 2, 1)).containsExactly(
				new Problem(boxed.build(), "Long is boxed although the field is never absent", Problem.Severity.WARNING)
		);
	}

	@Test
	void aFieldTakesItsNamedTypesPresence() {
		Fixtures.AnnotatedFieldBuilder field = annotatedField("quantity", 1, primitive(LONG))
				.type(annotatedType("Quantity", PrimitiveType.UINT32).presence(Presence.OPTIONAL));

		assertThat(problemsOf(field)).containsExactly(
				new Problem(field.build(), "long cannot hold null, but the field can be absent; use Long")
		);
	}

	@Test
	void aConstantIsNeverAbsent() {
		Fixtures.AnnotatedFieldBuilder field = annotatedField("version", 1, primitive(INT))
				.presence(Presence.CONSTANT)
				.valueRef("Version.CURRENT")
				.sinceVersion(2);

		assertThat(problemsOf(field, 2, 0)).isEmpty();
	}

	@Test
	void anEnumFieldsComponentIsTheEnum() {
		Fixtures.AnnotatedEnumBuilder side = annotatedEnum("Side").primitiveType(PrimitiveType.UINT8);
		Fixtures.AnnotatedFieldBuilder field = annotatedField("side", 1, primitive(SHORT)).type(side);

		assertThat(problemsOf(field)).containsExactly(new Problem(field.build(), "Side is an enum; use Side"));
	}

	@Test
	void aSetFieldsComponentIsASetOfTheEnum() {
		Fixtures.AnnotatedSetBuilder flags = annotatedSet("Flags").primitiveType(PrimitiveType.UINT8);
		Fixtures.AnnotatedFieldBuilder bare = annotatedField("flags", 1, declared(flags));
		Fixtures.AnnotatedFieldBuilder other = annotatedField(
				"flags", 1, setOf(annotatedSet("Other").primitiveType(PrimitiveType.UINT8))
		)
				.type(flags);

		assertThat(problemsOf(bare)).containsExactly(new Problem(bare.build(), "Flags is a set; use Set<Flags>"));
		assertThat(problemsOf(other)).containsExactly(new Problem(other.build(), "Flags is a set; use Set<Flags>"));
	}

	@Test
	void aSetFieldCannotBeOptional() {
		Fixtures.AnnotatedSetBuilder flags = annotatedSet("Flags").primitiveType(PrimitiveType.UINT8);
		Fixtures.AnnotatedFieldBuilder field = annotatedField("flags", 1, setOf(flags)).presence(Presence.OPTIONAL);

		assertThat(problemsOf(field)).containsExactly(
				new Problem(field.build(), "a set has no null value; a set field cannot be optional")
		);
	}

	@Test
	void theLayoutOrdersTheBody() {
		Annotated annotated = annotatedSchema("p", 1, 0)
				.messages(
						annotatedMessage("M", 1)
								.components(
										annotatedField("qty", 2, primitive(INT)),
										annotatedField("orderId", 1, primitive(LONG))
								)
								.unmapped(
										annotatedField("price", 3, unmapped()).name("price")
												.primitiveType(PrimitiveType.INT64)
								)
								.layout("orderId", "price", "qty")
				)
				.build();

		Mapping.Mapped mapped = Mapping.map(annotated);

		assertThat(mapped.problems()).isEmpty();
		assertThat(mapped.schema().messages().get(0).fields()).extracting(Schema.Field::name)
				.containsExactly("orderId", "price", "qty");
	}

	@Test
	void anUnmappedFieldNeedsAName() {
		Fixtures.AnnotatedFieldBuilder price = annotatedField("", 3, unmapped()).primitiveType(PrimitiveType.INT64);
		Fixtures.AnnotatedMessageBuilder message = annotatedMessage("M", 1)
				.components(annotatedField("qty", 2, primitive(INT)))
				.unmapped(price)
				.layout("qty", "");

		assertThat(problemsOf(message)).contains(new Problem(price.build(), "an unmapped field needs a name"));
	}

	@Test
	void anUnmappedFieldNeedsAType() {
		Fixtures.AnnotatedFieldBuilder price = annotatedField("price", 3, unmapped()).name("price");
		Fixtures.AnnotatedMessageBuilder message = annotatedMessage("M", 1)
				.components(annotatedField("qty", 2, primitive(INT)))
				.unmapped(price)
				.layout("qty", "price");

		assertThat(problemsOf(message))
				.containsExactly(new Problem(price.build(), "an unmapped field needs a type or a primitiveType"));
	}

	@Test
	void unmappedFieldsNeedALayout() {
		Fixtures.AnnotatedMessageBuilder message = annotatedMessage("M", 1)
				.components(annotatedField("qty", 2, primitive(INT)))
				.unmapped(annotatedField("price", 3, unmapped()).name("price").primitiveType(PrimitiveType.INT64));

		assertThat(problemsOf(message))
				.containsExactly(new Problem(message.build(), "unmapped fields need a layout to take their place in"));
	}

	@Test
	void theLayoutNamesEverythingOnceAndNothingElse() {
		Fixtures.AnnotatedMessageBuilder twice = annotatedMessage("M", 1)
				.components(annotatedField("qty", 2, primitive(INT)))
				.layout("qty", "qty");
		Fixtures.AnnotatedMessageBuilder nothing = annotatedMessage("M", 1)
				.components(annotatedField("qty", 2, primitive(INT)))
				.layout("qty", "prize");
		Fixtures.AnnotatedMessageBuilder misses = annotatedMessage("M", 1)
				.components(annotatedField("qty", 2, primitive(INT)), annotatedField("orderId", 1, primitive(LONG)))
				.layout("orderId");

		assertThat(problemsOf(twice)).containsExactly(new Problem(twice.build(), "the layout names \"qty\" twice"));
		assertThat(problemsOf(nothing))
				.containsExactly(new Problem(nothing.build(), "the layout names nothing called \"prize\""));
		assertThat(problemsOf(misses)).containsExactly(new Problem(misses.build(), "the layout misses \"qty\""));
	}

	@Test
	void aNameThatIsBothAComponentsAndAnUnmappedFieldsIsAProblem() {
		Fixtures.AnnotatedMessageBuilder message = annotatedMessage("M", 1)
				.components(annotatedField("qty", 2, primitive(INT)))
				.unmapped(annotatedField("qty", 3, unmapped()).name("qty").primitiveType(PrimitiveType.INT64))
				.layout("qty");

		assertThat(problemsOf(message))
				.containsExactly(new Problem(message.build(), "\"qty\" is both a component and an unmapped field"));
	}

	@Test
	void aTypeWithALengthHasTheFaceOfItsArray() {
		Fixtures.AnnotatedFieldBuilder symbol = annotatedField("symbol", 1, bytes())
				.type(annotatedType("Symbol", PrimitiveType.CHAR).length(6));
		Fixtures.AnnotatedFieldBuilder colour = annotatedField("colour", 1, text())
				.type(annotatedType("Rgb", PrimitiveType.UINT8).length(3));
		Fixtures.AnnotatedFieldBuilder samples = annotatedField("samples", 1, array(SHORT))
				.type(annotatedType("Samples", PrimitiveType.INT32).length(4));
		Fixtures.AnnotatedFieldBuilder sizes = annotatedField("sizes", 1, array(LONG))
				.type(annotatedType("Sizes", PrimitiveType.UINT32).length(2));

		assertThat(problemsOf(symbol))
				.containsExactly(new Problem(symbol.build(), "byte[] is not the face of Symbol, which is String"));
		assertThat(problemsOf(colour))
				.containsExactly(new Problem(colour.build(), "String is not the face of Rgb, which is byte[]"));
		assertThat(problemsOf(samples))
				.containsExactly(new Problem(samples.build(), "short[] is not the face of Samples, which is int[]"));
		assertThat(problemsOf(sizes)).isEmpty();
	}

	@Test
	void aConstantCharValueLongerThanOneCharacterIsAString() {
		Fixtures.AnnotatedFieldBuilder currency = annotatedField("currency", 1, primitive(BYTE))
				.type(annotatedType("Currency", PrimitiveType.CHAR).presence(Presence.CONSTANT).value("USD"));

		assertThat(problemsOf(currency))
				.containsExactly(new Problem(currency.build(), "byte is not the face of Currency, which is String"));
	}

	@Test
	void aFieldOfATypeWithALengthCannotBeOptional() {
		Fixtures.AnnotatedFieldBuilder symbol = annotatedField("symbol", 1, text())
				.type(annotatedType("Symbol", PrimitiveType.CHAR).length(6))
				.presence(Presence.OPTIONAL);
		Fixtures.AnnotatedFieldBuilder samples = annotatedField("samples", 1, array(INT))
				.type(annotatedType("Samples", PrimitiveType.INT32).length(4).presence(Presence.OPTIONAL));

		assertThat(problemsOf(symbol))
				.containsExactly(new Problem(symbol.build(), "Symbol has a length; a field of it cannot be optional"));
		assertThat(problemsOf(samples))
				.containsExactly(
						new Problem(samples.build(), "Samples has a length; a field of it cannot be optional")
				);
	}

	@Test
	void aConstantFieldNeedsAValueRefOrAConstantType() {
		Fixtures.AnnotatedFieldBuilder bare = annotatedField("exponent", 1, primitive(BYTE))
				.presence(Presence.CONSTANT);
		Fixtures.AnnotatedFieldBuilder referring = annotatedField("side", 1, primitive(BYTE))
				.primitiveType(PrimitiveType.CHAR)
				.presence(Presence.CONSTANT)
				.valueRef("Side.Buy");
		Fixtures.AnnotatedFieldBuilder typed = annotatedField("exponent", 1, primitive(BYTE))
				.type(annotatedType("Exponent", PrimitiveType.INT8).presence(Presence.CONSTANT).value("-4"));

		assertThat(problemsOf(bare))
				.containsExactly(new Problem(bare.build(), "a constant field needs a valueRef or a constant type"));
		assertThat(problemsOf(referring)).isEmpty();
		assertThat(problemsOf(typed)).isEmpty();
	}

	@Test
	void aBaselineAboveTheSchemasVersionIsAProblem() {
		Annotated annotated = annotatedSchema("p", 1, 1).baselineVersion(2).build();

		assertThat(Mapping.map(annotated).problems()).containsExactly(
				new Problem(annotated, "a baselineVersion is 0 to the schema's version 1, not 2")
		);
	}

	@Test
	void aFieldAfterAGroupIsAProblem() {
		Fixtures.AnnotatedFieldBuilder late = annotatedField("late", 2, primitive(INT));
		Annotated annotated = annotatedSchema("p", 1, 0)
				.messages(annotatedMessage("M", 1).components(annotatedGroup("legs", 1), late))
				.build();

		assertThat(Mapping.map(annotated).problems()).containsExactly(
				new Problem(late.build(), "a field must come before every group and data")
		);
	}

	@Test
	void aGroupOnAnythingButAListOfARecordIsAProblem() {
		Fixtures.AnnotatedGroupBuilder legs = annotatedGroup("legs", 1).javaType(text());
		Annotated annotated = annotatedSchema("p", 1, 0)
				.messages(annotatedMessage("M", 1).components(legs))
				.build();

		assertThat(Mapping.map(annotated).problems()).containsExactly(
				new Problem(legs.build(), "a group must be a List of a record")
		);
	}

	@Test
	void anIdAboveTheXsdsUnsignedShortIsAProblem() {
		Fixtures.AnnotatedFieldBuilder field = annotatedField("qty", 65536, primitive(INT));

		assertThat(problemsOf(field)).containsExactly(
				new Problem(field.build(), "an id is 0 to 65535, not 65536")
		);
	}

	@Test
	void aNameOutsideTheXsdsPatternIsAProblem() {
		Fixtures.AnnotatedFieldBuilder field = annotatedField("qty", 1, primitive(INT)).name("net qty");

		assertThat(problemsOf(field)).containsExactly(
				new Problem(
						field.build(),
						"\"net qty\" is not a name SBE allows: a letter or _, then letters, digits and _"
				)
		);
	}

	@Test
	void aDeclarationWithABadNameIsBlamedOnceHoweverOftenItIsReferred() {
		Fixtures.AnnotatedTypeBuilder quantity = annotatedType("Quantity", PrimitiveType.UINT32).name("net qty");
		Annotated annotated = annotatedSchema("p", 1, 0)
				.types(quantity)
				.messages(
						annotatedMessage("M", 1).components(
								annotatedField("qty", 1, primitive(LONG)).type(quantity),
								annotatedField("shown", 2, primitive(LONG)).type(quantity)
						)
				)
				.build();

		assertThat(Mapping.map(annotated).problems()).containsExactly(
				new Problem(
						quantity.build(),
						"\"net qty\" is not a name SBE allows: a letter or _, then letters, digits and _"
				)
		);
	}

	@Test
	void everySchemaNodeKnowsItsAnnotatedNode() {
		Fixtures.AnnotatedFieldBuilder field = annotatedField("qty", 1, primitive(INT));
		Annotated annotated = annotatedSchema("p", 1, 0)
				.messages(annotatedMessage("M", 1).components(field))
				.build();

		Mapping.Mapped mapped = Mapping.map(annotated);

		assertThat(mapped.origins().get(mapped.schema().messages().get(0).fields().get(0))).isSameAs(field.build());
	}

	private static java.util.List<Problem> problemsOf(Fixtures.AnnotatedFieldBuilder field) {
		return problemsOf(field, 0, 0);
	}

	private static java.util.List<Problem> problemsOf(Fixtures.AnnotatedMessageBuilder message) {
		return Mapping.map(annotatedSchema("p", 1, 0).messages(message).build()).problems();
	}

	private static java.util.List<Problem> problemsOf(Fixtures.AnnotatedFieldBuilder field, int version, int baseline) {
		return Mapping.map(
				annotatedSchema("p", 1, version)
						.baselineVersion(baseline)
						.messages(annotatedMessage("M", 1).components(field))
						.build()
		).problems();
	}
}
