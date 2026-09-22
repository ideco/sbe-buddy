package net.concini.sbebuddy.generator;

import static net.concini.sbebuddy.generator.Annotated.JavaPrimitive.INT;
import static net.concini.sbebuddy.generator.Annotated.JavaPrimitive.LONG;
import static net.concini.sbebuddy.generator.Fixtures.annotatedComposite;
import static net.concini.sbebuddy.generator.Fixtures.annotatedData;
import static net.concini.sbebuddy.generator.Fixtures.annotatedField;
import static net.concini.sbebuddy.generator.Fixtures.annotatedGroup;
import static net.concini.sbebuddy.generator.Fixtures.annotatedMessage;
import static net.concini.sbebuddy.generator.Fixtures.annotatedSchema;
import static net.concini.sbebuddy.generator.Fixtures.annotatedType;
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

		List<Problem> problems = Generator.generate(schema, annotatedSchema("p", 1, 0).build(), output);

		assertThat(problems).hasSize(1);
		assertThat(problems.get(0).node()).isSameAs(schema);
		assertThat(problems.get(0).message()).contains("nosuch");
		assertThat(output.getSources()).isEmpty();
	}

	@Test
	void aConstructTheCodecLacksLeavesTheOutputUntouched() {
		// sbe-tool accepts the schema and generates its flyweights; the codec has no
		// var-data yet, so the flyweights must not reach the output either.
		Fixtures.AnnotatedCompositeBuilder varStringEncoding = annotatedVarStringEncoding();
		Annotated annotated = annotatedSchema("p", 1, 0)
				.types(varStringEncoding)
				.messages(
						annotatedMessage("M", 1).components(
								annotatedField("qty", 1, primitive(INT)),
								annotatedData("note", 2, text(), varStringEncoding)
						)
				)
				.build();
		StringWriterOutputManager output = new StringWriterOutputManager();

		assertThat(generate(annotated, output)).containsExactly(
				new Problem(annotated.messages().get(0), "no codec for var-data yet; set codecs = false on @SbeSchema")
		);
		assertThat(output.getSources()).isEmpty();
	}

	@Test
	void varDataInsideAGroupIsAConstructTheCodecLacks() {
		// The message's own body has none; the walk must look inside the group.
		Fixtures.AnnotatedCompositeBuilder varStringEncoding = annotatedVarStringEncoding();
		Annotated annotated = annotatedSchema("p", 1, 0)
				.types(varStringEncoding)
				.messages(
						annotatedMessage("M", 1).components(
								annotatedField("qty", 1, primitive(INT)),
								annotatedGroup("legs", 2).components(
										annotatedField("legId", 3, primitive(INT)),
										annotatedData("note", 4, text(), varStringEncoding)
								)
						)
				)
				.build();
		StringWriterOutputManager output = new StringWriterOutputManager();

		assertThat(generate(annotated, output)).containsExactly(
				new Problem(annotated.messages().get(0), "no codec for var-data yet; set codecs = false on @SbeSchema")
		);
		assertThat(output.getSources()).isEmpty();
	}

	@Test
	void aFieldAddedAboveTheBaselineInsideAGroupIsAConstructTheCodecLacks() {
		// The group's own version is the baseline inside it: a field at version 1 in
		// a group appended in version 1 is never absent, one at version 2 would be.
		Annotated annotated = annotatedSchema("p", 1, 2)
				.messages(
						annotatedMessage("M", 1).components(
								annotatedField("qty", 1, primitive(INT)),
								annotatedGroup("legs", 2).sinceVersion(1).components(
										annotatedField("legId", 3, primitive(INT)).sinceVersion(1),
										annotatedField("ratio", 4, boxed(INT)).sinceVersion(2)
								)
						)
				)
				.build();
		StringWriterOutputManager output = new StringWriterOutputManager();

		assertThat(generate(annotated, output)).containsExactly(
				new Problem(
						annotated.messages().get(0),
						"no codec for a field added above the baseline in a group yet; set codecs = false on @SbeSchema"
				)
		);
		assertThat(output.getSources()).isEmpty();
	}

	@Test
	void aGroupAddedAboveTheBaselineInsideAGroupIsAConstructTheCodecLacks() {
		Annotated annotated = annotatedSchema("p", 1, 1)
				.messages(
						annotatedMessage("M", 1).components(
								annotatedField("qty", 1, primitive(INT)),
								annotatedGroup("legs", 2).components(
										annotatedField("legId", 3, primitive(INT)),
										annotatedGroup("allocations", 4).sinceVersion(1)
												.components(annotatedField("account", 5, primitive(INT)))
								)
						)
				)
				.build();
		StringWriterOutputManager output = new StringWriterOutputManager();

		assertThat(generate(annotated, output)).containsExactly(
				new Problem(
						annotated.messages().get(0),
						"no codec for a group added above the baseline in a group yet; set codecs = false on @SbeSchema"
				)
		);
		assertThat(output.getSources()).isEmpty();
	}

	@Test
	void aStringInAnEncodingThatIsNotAsciiIsAConstructTheCodecLacks() {
		// The flyweight's String form goes through String.getBytes there, and
		// what the codec would check is not settled.
		Fixtures.AnnotatedTypeBuilder name = annotatedType("Name", CHAR).length(8).characterEncoding("UTF-8");
		Annotated annotated = annotatedSchema("p", 1, 0)
				.types(name)
				.messages(annotatedMessage("M", 1).components(annotatedField("name", 1, text()).type(name)))
				.build();
		StringWriterOutputManager output = new StringWriterOutputManager();

		assertThat(generate(annotated, output)).containsExactly(
				new Problem(
						annotated.messages().get(0),
						"no codec for a string in UTF-8 yet; set codecs = false on @SbeSchema"
				)
		);
		assertThat(output.getSources()).isEmpty();
	}

	@Test
	void twoBindingsWithOneSimpleNameInOneCodecIsAProblem() {
		// The codec names its binding field after the class's simple name.
		Annotated annotated = annotatedSchema("p", 1, 0)
				.messages(
						annotatedMessage("M", 1).components(
								annotatedField("fee", 1, other("java.math.BigDecimal")).primitiveType(INT64)
										.binding("a.Cents", primitive(LONG)),
								annotatedField("tax", 2, other("java.math.BigDecimal")).primitiveType(INT64)
										.binding("b.Cents", primitive(LONG))
						)
				)
				.build();
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
		Annotated annotated = annotatedSchema("p", 1, 0)
				.messages(annotatedMessage("M", 1).components(annotatedField("qty", 1, primitive(INT))))
				.build();
		StringWriterOutputManager output = new StringWriterOutputManager();

		assertThat(generate(annotated, output)).isEmpty();
		assertThat(output.getSources().keySet()).containsExactlyInAnyOrder(
				"p.sbe.package-info", "p.sbe.MessageHeaderEncoder", "p.sbe.MessageHeaderDecoder", "p.sbe.MEncoder",
				"p.sbe.MDecoder", "p.sbe.MetaAttribute", "p.MCodec"
		);
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

	private static Fixtures.AnnotatedCompositeBuilder annotatedVarStringEncoding() {
		return annotatedComposite("VarStringEncoding")
				.qualifiedName("p.VarStringEncoding")
				.name("varStringEncoding")
				.members(
						annotatedType("length", UINT16).javaType(primitive(INT)),
						annotatedType("varData", CHAR).length(0).characterEncoding("UTF-8").javaType(text())
				);
	}
}
