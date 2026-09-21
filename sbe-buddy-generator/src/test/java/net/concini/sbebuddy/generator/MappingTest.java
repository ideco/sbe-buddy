package net.concini.sbebuddy.generator;

import static net.concini.sbebuddy.generator.Annotated.JavaPrimitive.CHAR;
import static net.concini.sbebuddy.generator.Annotated.JavaPrimitive.INT;
import static net.concini.sbebuddy.generator.Fixtures.annotatedField;
import static net.concini.sbebuddy.generator.Fixtures.annotatedGroup;
import static net.concini.sbebuddy.generator.Fixtures.annotatedMessage;
import static net.concini.sbebuddy.generator.Fixtures.annotatedSchema;
import static net.concini.sbebuddy.generator.Fixtures.annotatedType;
import static net.concini.sbebuddy.generator.Fixtures.primitive;
import static net.concini.sbebuddy.generator.Fixtures.text;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import uk.co.real_logic.sbe.PrimitiveType;

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
	void everySchemaNodeKnowsItsAnnotatedNode() {
		Fixtures.AnnotatedFieldBuilder field = annotatedField("qty", 1, primitive(INT));
		Annotated annotated = annotatedSchema("p", 1, 0)
				.messages(annotatedMessage("M", 1).components(field))
				.build();

		Mapping.Mapped mapped = Mapping.map(annotated);

		assertThat(mapped.origins().get(mapped.schema().messages().get(0).fields().get(0))).isSameAs(field.build());
	}

	private static java.util.List<Problem> problemsOf(Fixtures.AnnotatedFieldBuilder field) {
		return Mapping.map(
				annotatedSchema("p", 1, 0)
						.messages(annotatedMessage("M", 1).components(field))
						.build()
		).problems();
	}
}
