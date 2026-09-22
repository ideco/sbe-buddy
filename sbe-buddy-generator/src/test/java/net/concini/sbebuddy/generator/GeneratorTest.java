package net.concini.sbebuddy.generator;

import static net.concini.sbebuddy.generator.Annotated.JavaPrimitive.INT;
import static net.concini.sbebuddy.generator.Annotated.JavaPrimitive.LONG;
import static net.concini.sbebuddy.generator.Fixtures.annotated;
import static net.concini.sbebuddy.generator.Fixtures.annotatedComposite;
import static net.concini.sbebuddy.generator.Fixtures.annotatedData;
import static net.concini.sbebuddy.generator.Fixtures.annotatedField;
import static net.concini.sbebuddy.generator.Fixtures.annotatedGroup;
import static net.concini.sbebuddy.generator.Fixtures.annotatedMessage;
import static net.concini.sbebuddy.generator.Fixtures.annotatedType;
import static net.concini.sbebuddy.generator.Fixtures.boundField;
import static net.concini.sbebuddy.generator.Fixtures.boxed;
import static net.concini.sbebuddy.generator.Fixtures.composite;
import static net.concini.sbebuddy.generator.Fixtures.data;
import static net.concini.sbebuddy.generator.Fixtures.field;
import static net.concini.sbebuddy.generator.Fixtures.group;
import static net.concini.sbebuddy.generator.Fixtures.message;
import static net.concini.sbebuddy.generator.Fixtures.messageHeader;
import static net.concini.sbebuddy.generator.Fixtures.other;
import static net.concini.sbebuddy.generator.Fixtures.primitive;
import static net.concini.sbebuddy.generator.Fixtures.schema;
import static net.concini.sbebuddy.generator.Fixtures.text;
import static net.concini.sbebuddy.generator.Fixtures.type;
import static org.assertj.core.api.Assertions.assertThat;
import static uk.co.real_logic.sbe.PrimitiveType.CHAR;
import static uk.co.real_logic.sbe.PrimitiveType.INT64;
import static uk.co.real_logic.sbe.PrimitiveType.UINT16;

import java.util.List;

import org.agrona.generation.StringWriterOutputManager;
import org.junit.jupiter.api.Test;

/**
 * The rules that compare nodes, each built as the mistake and asserted as the
 * problem and the node it names; and the pipeline's all-or-nothing rule, that a
 * problem found at any step leaves the output untouched.
 */
final class GeneratorTest {

	@Test
	void twoFieldsWithOneIdIsAProblem() {
		Schema.Field second = field("price", 1, "int64");

		assertThat(Generator.validate(schema(0, message("M", 1, field("qty", 1, "int32"), second))))
				.containsExactly(new Problem(second, "two members have id 1"));
	}

	@Test
	void twoMembersOfOneBlockWithOneNameIsAProblem() {
		Schema.Data note = data("qty", 2, "varStringEncoding");

		assertThat(
				Generator.validate(
						schema(0, message("M", 1, List.of(field("qty", 1, "int32")), List.of(), List.of(note)))
				)
		).containsExactly(new Problem(note, "two members are named \"qty\""));
	}

	@Test
	void twoMessagesWithOneNameIsAProblem() {
		Schema.Message second = message("M", 2);

		assertThat(Generator.validate(schema(0, message("M", 1), second)))
				.containsExactly(new Problem(second, "two messages are named \"M\""));
	}

	@Test
	void twoMessagesWithOneIdIsAProblem() {
		Schema.Message second = message("N", 1);

		assertThat(Generator.validate(schema(0, message("M", 1), second)))
				.containsExactly(new Problem(second, "two messages have id 1"));
	}

	@Test
	void twoDeclarationsWithOneWireNameIsAProblem() {
		Schema.Composite second = composite("Price", type("mantissa", INT64));

		assertThat(Generator.validate(schema(0, List.of(type("Price", INT64), second), List.of())))
				.containsExactly(new Problem(second, "two declarations are named \"Price\""));
	}

	@Test
	void aSinceVersionAboveTheSchemasIsAProblem() {
		Schema.Field late = field("qty", 1, "int32", 2, null);

		assertThat(Generator.validate(schema(1, message("M", 1, late))))
				.containsExactly(new Problem(late, "sinceVersion 2 is above the schema's version 1"));
	}

	@Test
	void aDeprecatedBelowItsSinceVersionIsAProblem() {
		Schema.Field field = field("qty", 1, "int32", 2, 1);

		assertThat(Generator.validate(schema(2, message("M", 1, field))))
				.containsExactly(new Problem(field, "deprecated 1 is below sinceVersion 2"));
	}

	@Test
	void aSiblingAddedBeforeTheOneItFollowsIsAProblem() {
		Schema.Field early = field("price", 2, "int64", 1, null);

		assertThat(Generator.validate(schema(2, message("M", 1, field("qty", 1, "int32", 2, null), early))))
				.containsExactly(new Problem(early, "sinceVersion 1 follows a sibling added in 2"));
	}

	@Test
	void aGroupsOwnBlockIsItsOwnNamespace() {
		Schema.Message message = message(
				"M", 1, List.of(field("qty", 1, "int32")), List.of(group("legs", 2, field("qty", 1, "int32"))),
				List.of()
		);

		assertThat(Generator.validate(schema(0, message))).isEmpty();
	}

	@Test
	void whatSbeToolRejectsIsAProblemNamingTheSchema() {
		// A type no declaration carries: our rules cannot see it from a Schema built
		// by hand, and sbe-tool's parser refuses it.
		Schema schema = schema(0, List.of(messageHeader()), List.of(message("M", 1, field("x", 1, "nosuch"))));
		StringWriterOutputManager output = new StringWriterOutputManager();

		List<Problem> problems = Generator.generate(schema, annotated(0), output);

		assertThat(problems).hasSize(1);
		assertThat(problems.get(0).node()).isSameAs(schema);
		assertThat(problems.get(0).message()).contains("nosuch");
		assertThat(output.getSources()).isEmpty();
	}

	@Test
	void aConstructTheCodecLacksLeavesTheOutputUntouched() {
		// sbe-tool accepts the schema and generates its flyweights; the codec has no
		// var-data yet, so the flyweights must not reach the output either.
		Annotated.Composite varStringEncoding = varStringEncoding();
		Annotated annotated = annotated(
				0, List.of(varStringEncoding),
				annotatedMessage(
						"M", 1, annotatedField("qty", 1, primitive(INT)),
						annotatedData("note", 2, text(), varStringEncoding)
				)
		);

		assertLacks(annotated, "var-data");
	}

	@Test
	void varDataInsideAGroupIsAConstructTheCodecLacks() {
		// The message's own body has none; the walk must look inside the group.
		Annotated.Composite varStringEncoding = varStringEncoding();
		Annotated annotated = annotated(
				0, List.of(varStringEncoding),
				annotatedMessage(
						"M", 1, annotatedField("qty", 1, primitive(INT)),
						annotatedGroup(
								"legs", 2, 0, annotatedField("legId", 3, primitive(INT)),
								annotatedData("note", 4, text(), varStringEncoding)
						)
				)
		);

		assertLacks(annotated, "var-data");
	}

	@Test
	void aFieldAddedAboveTheBaselineInsideAGroupIsAConstructTheCodecLacks() {
		// The group's own version is the baseline inside it: a field at version 1 in
		// a group appended in version 1 is never absent, one at version 2 would be.
		Annotated annotated = annotated(
				2,
				annotatedMessage(
						"M", 1, annotatedField("qty", 1, primitive(INT)),
						annotatedGroup(
								"legs", 2, 1, annotatedField("legId", 3, primitive(INT), 1),
								annotatedField("ratio", 4, boxed(INT), 2)
						)
				)
		);

		assertLacks(annotated, "a field added above the baseline in a group");
	}

	@Test
	void aGroupAddedAboveTheBaselineInsideAGroupIsAConstructTheCodecLacks() {
		Annotated annotated = annotated(
				1,
				annotatedMessage(
						"M", 1, annotatedField("qty", 1, primitive(INT)),
						annotatedGroup(
								"legs", 2, 0, annotatedField("legId", 3, primitive(INT)),
								annotatedGroup("allocations", 4, 1, annotatedField("account", 5, primitive(INT)))
						)
				)
		);

		assertLacks(annotated, "a group added above the baseline in a group");
	}

	@Test
	void aStringInAnEncodingThatIsNotAsciiIsAConstructTheCodecLacks() {
		// The flyweight's String form goes through String.getBytes there, and
		// what the codec would check is not settled.
		Annotated.Type name = annotatedType("Name", CHAR, 8, "UTF-8", null);
		Annotated annotated = annotated(
				0, List.of(name), annotatedMessage("M", 1, annotatedField("name", 1, text(), name))
		);

		assertLacks(annotated, "a string in UTF-8");
	}

	@Test
	void twoBindingsWithOneSimpleNameInOneCodecIsAProblem() {
		// The codec names its binding field after the class's simple name.
		Annotated annotated = annotated(
				0,
				annotatedMessage(
						"M", 1,
						boundField("fee", 1, other("java.math.BigDecimal"), INT64, "a.Cents", primitive(LONG)),
						boundField("tax", 2, other("java.math.BigDecimal"), INT64, "b.Cents", primitive(LONG))
				)
		);
		StringWriterOutputManager output = new StringWriterOutputManager();

		assertThat(generate(annotated, output)).containsExactly(
				new Problem(
						annotated.messages().get(0),
						"two bindings share the simple name Cents in one codec; the second is b.Cents"
				)
		);
		assertThat(output.getSources()).isEmpty();
	}

	@Test
	void aSchemaWithoutAProblemReachesTheOutputWhole() {
		Annotated annotated = annotated(0, annotatedMessage("M", 1, annotatedField("qty", 1, primitive(INT))));
		StringWriterOutputManager output = new StringWriterOutputManager();

		assertThat(generate(annotated, output)).isEmpty();
		assertThat(output.getSources().keySet()).containsExactlyInAnyOrder(
				"p.sbe.package-info", "p.sbe.MessageHeaderEncoder", "p.sbe.MessageHeaderDecoder", "p.sbe.MEncoder",
				"p.sbe.MDecoder", "p.sbe.MetaAttribute", "p.MCodec"
		);
	}

	/**
	 * The one problem naming the construct the codec lacks, and nothing written.
	 */
	private static void assertLacks(Annotated annotated, String construct) {
		StringWriterOutputManager output = new StringWriterOutputManager();

		assertThat(generate(annotated, output)).containsExactly(
				new Problem(
						annotated.messages().get(0),
						"no codec for " + construct + " yet; set codecs = false on @SbeSchema"
				)
		);
		assertThat(output.getSources()).isEmpty();
	}

	/**
	 * The schema the mapping makes of the annotations, generated: the mapping is
	 * the input here, not the expectation, so these tests describe each case once.
	 */
	private static List<Problem> generate(Annotated annotated, StringWriterOutputManager output) {
		Mapping.Mapped mapped = Mapping.map(annotated);
		assertThat(mapped.problems()).as("the mapping of the annotations").isEmpty();
		return Generator.generate(mapped.schema(), annotated, output);
	}

	private static Annotated.Composite varStringEncoding() {
		return annotatedComposite(
				"VarStringEncoding", "p.VarStringEncoding", "varStringEncoding",
				annotatedType("length", UINT16, primitive(INT)), annotatedType("varData", CHAR, 0, "UTF-8", text())
		);
	}
}
