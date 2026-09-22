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
import static net.concini.sbebuddy.generator.Fixtures.groupSizeEncoding;
import static net.concini.sbebuddy.generator.Fixtures.message;
import static net.concini.sbebuddy.generator.Fixtures.messageHeader;
import static net.concini.sbebuddy.generator.Fixtures.messageSchema;
import static net.concini.sbebuddy.generator.Fixtures.other;
import static net.concini.sbebuddy.generator.Fixtures.primitive;
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
		Fixtures.FieldBuilder second = field("price", 1, "int64");

		assertThat(
				validate(
						messageSchema("p", 1, 0).messages(
								message("M", 1).fields(field("qty", 1, "int32"), second)
						)
				)
		).containsExactly(new Problem(second.build(), "two members have id 1"));
	}

	@Test
	void twoMembersOfOneBlockWithOneNameIsAProblem() {
		Fixtures.DataBuilder note = data("qty", 2, "varStringEncoding");

		assertThat(
				validate(
						messageSchema("p", 1, 0).messages(
								message("M", 1).fields(field("qty", 1, "int32")).data(note)
						)
				)
		).containsExactly(new Problem(note.build(), "two members are named \"qty\""));
	}

	@Test
	void twoMessagesWithOneNameIsAProblem() {
		Fixtures.MessageBuilder second = message("M", 2);

		assertThat(validate(messageSchema("p", 1, 0).messages(message("M", 1), second)))
				.containsExactly(new Problem(second.build(), "two messages are named \"M\""));
	}

	@Test
	void twoMessagesWithOneIdIsAProblem() {
		Fixtures.MessageBuilder second = message("N", 1);

		assertThat(validate(messageSchema("p", 1, 0).messages(message("M", 1), second)))
				.containsExactly(new Problem(second.build(), "two messages have id 1"));
	}

	@Test
	void twoDeclarationsWithOneWireNameIsAProblem() {
		Fixtures.CompositeBuilder second = composite("Price").members(type("mantissa", INT64));

		assertThat(validate(messageSchema("p", 1, 0).types(type("Price", INT64), second)))
				.containsExactly(new Problem(second.build(), "two declarations are named \"Price\""));
	}

	@Test
	void aSinceVersionAboveTheSchemasIsAProblem() {
		Fixtures.FieldBuilder late = field("qty", 1, "int32").sinceVersion(2);

		assertThat(validate(messageSchema("p", 1, 1).messages(message("M", 1).fields(late))))
				.containsExactly(new Problem(late.build(), "sinceVersion 2 is above the schema's version 1"));
	}

	@Test
	void aDeprecatedBelowItsSinceVersionIsAProblem() {
		Fixtures.FieldBuilder field = field("qty", 1, "int32").sinceVersion(2).deprecated(1);

		assertThat(validate(messageSchema("p", 1, 2).messages(message("M", 1).fields(field))))
				.containsExactly(new Problem(field.build(), "deprecated 1 is below sinceVersion 2"));
	}

	@Test
	void aSiblingAddedBeforeTheOneItFollowsIsAProblem() {
		Fixtures.FieldBuilder early = field("price", 2, "int64").sinceVersion(1);

		assertThat(
				validate(
						messageSchema("p", 1, 2).messages(
								message("M", 1).fields(field("qty", 1, "int32").sinceVersion(2), early)
						)
				)
		).containsExactly(new Problem(early.build(), "sinceVersion 1 follows a sibling added in 2"));
	}

	@Test
	void aGroupsOwnBlockIsItsOwnNamespace() {
		assertThat(
				validate(
						messageSchema("p", 1, 0).messages(
								message("M", 1)
										.fields(field("qty", 1, "int32"))
										.groups(group("legs", 2).fields(field("qty", 1, "int32")))
						)
				)
		).isEmpty();
	}

	private static List<Problem> validate(Fixtures.SchemaBuilder schema) {
		return Generator.validate(schema.build());
	}

	@Test
	void whatSbeToolRejectsIsAProblemNamingTheSchema() {
		// A type no declaration carries: our rules cannot see it from a Schema built
		// by hand, and sbe-tool's parser refuses it.
		Schema schema = messageSchema("p", 1, 0)
				.types(messageHeader())
				.messages(message("M", 1).fields(field("x", 1, "nosuch")))
				.build();
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
		Fixtures.CompositeBuilder varStringEncoding = composite("varStringEncoding").members(
				type("length", UINT16),
				type("varData", CHAR).length(0).characterEncoding("UTF-8")
		);
		Schema schema = messageSchema("p", 1, 0)
				.types(messageHeader(), varStringEncoding)
				.messages(
						message("M", 1)
								.fields(field("qty", 1, "int32"))
								.data(data("note", 2, "varStringEncoding"))
				)
				.build();
		Annotated annotated = annotatedSchema("p", 1, 0)
				.types(annotatedVarStringEncoding())
				.messages(
						annotatedMessage("M", 1).components(
								annotatedField("qty", 1, primitive(INT)),
								annotatedData("note", 2, text(), annotatedVarStringEncoding())
						)
				)
				.build();
		StringWriterOutputManager output = new StringWriterOutputManager();

		List<Problem> problems = Generator.generate(schema, annotated, output);

		assertThat(problems).containsExactly(
				new Problem(annotated.messages().get(0), "no codec for var-data yet; set codecs = false on @SbeSchema")
		);
		assertThat(output.getSources()).isEmpty();
	}

	@Test
	void varDataInsideAGroupIsAConstructTheCodecLacks() {
		// The message's own body has none; the walk must look inside the group.
		Fixtures.CompositeBuilder varStringEncoding = composite("varStringEncoding").members(
				type("length", UINT16),
				type("varData", CHAR).length(0).characterEncoding("UTF-8")
		);
		Schema schema = messageSchema("p", 1, 0)
				.types(messageHeader(), varStringEncoding, groupSizeEncoding())
				.messages(
						message("M", 1)
								.fields(field("qty", 1, "int32"))
								.groups(
										group("legs", 2)
												.fields(field("legId", 3, "int32"))
												.data(data("note", 4, "varStringEncoding"))
								)
				)
				.build();
		Annotated annotated = annotatedSchema("p", 1, 0)
				.types(annotatedVarStringEncoding())
				.messages(
						annotatedMessage("M", 1).components(
								annotatedField("qty", 1, primitive(INT)),
								annotatedGroup("legs", 2).components(
										annotatedField("legId", 3, primitive(INT)),
										annotatedData("note", 4, text(), annotatedVarStringEncoding())
								)
						)
				)
				.build();
		StringWriterOutputManager output = new StringWriterOutputManager();

		List<Problem> problems = Generator.generate(schema, annotated, output);

		assertThat(problems).containsExactly(
				new Problem(annotated.messages().get(0), "no codec for var-data yet; set codecs = false on @SbeSchema")
		);
		assertThat(output.getSources()).isEmpty();
	}

	@Test
	void aFieldAddedAboveTheBaselineInsideAGroupIsAConstructTheCodecLacks() {
		// The group's own version is the baseline inside it: a field at version 1 in
		// a group appended in version 1 is never absent, one at version 2 would be.
		Schema schema = messageSchema("p", 1, 2)
				.types(messageHeader(), groupSizeEncoding())
				.messages(
						message("M", 1)
								.fields(field("qty", 1, "int32"))
								.groups(
										group("legs", 2)
												.sinceVersion(1)
												.fields(
														field("legId", 3, "int32").sinceVersion(1),
														field("ratio", 4, "int32").sinceVersion(2)
												)
								)
				)
				.build();
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

		List<Problem> problems = Generator.generate(schema, annotated, output);

		assertThat(problems).containsExactly(
				new Problem(
						annotated.messages().get(0),
						"no codec for a field added above the baseline in a group yet; set codecs = false on @SbeSchema"
				)
		);
		assertThat(output.getSources()).isEmpty();
	}

	@Test
	void aGroupAddedAboveTheBaselineInsideAGroupIsAConstructTheCodecLacks() {
		Schema schema = messageSchema("p", 1, 1)
				.types(messageHeader(), groupSizeEncoding())
				.messages(
						message("M", 1)
								.fields(field("qty", 1, "int32"))
								.groups(
										group("legs", 2)
												.fields(field("legId", 3, "int32"))
												.groups(
														group("allocations", 4)
																.sinceVersion(1)
																.fields(field("account", 5, "int32"))
												)
								)
				)
				.build();
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

		List<Problem> problems = Generator.generate(schema, annotated, output);

		assertThat(problems).containsExactly(
				new Problem(
						annotated.messages().get(0),
						"no codec for a group added above the baseline in a group yet; set codecs = false on @SbeSchema"
				)
		);
		assertThat(output.getSources()).isEmpty();
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

	@Test
	void aStringInAnEncodingThatIsNotAsciiIsAConstructTheCodecLacks() {
		// The flyweight's String form goes through String.getBytes there, and
		// what the codec would check is not settled.
		Fixtures.AnnotatedTypeBuilder name = annotatedType("Name", CHAR).length(8).characterEncoding("UTF-8");
		Schema schema = messageSchema("p", 1, 0)
				.types(messageHeader(), type("Name", CHAR).length(8).characterEncoding("UTF-8"))
				.messages(message("M", 1).fields(field("name", 1, "Name")))
				.build();
		Annotated annotated = annotatedSchema("p", 1, 0)
				.types(name)
				.messages(annotatedMessage("M", 1).components(annotatedField("name", 1, text()).type(name)))
				.build();
		StringWriterOutputManager output = new StringWriterOutputManager();

		List<Problem> problems = Generator.generate(schema, annotated, output);

		assertThat(problems).containsExactly(
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
		Schema schema = messageSchema("p", 1, 0)
				.types(messageHeader())
				.messages(message("M", 1).fields(field("fee", 1, "int64"), field("tax", 2, "int64")))
				.build();
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

		List<Problem> problems = Generator.generate(schema, annotated, output);

		assertThat(problems).containsExactly(
				new Problem(
						annotated.messages().get(0),
						"two bindings share the simple name Cents in one codec; the second is b.Cents"
				)
		);
		assertThat(output.getSources()).isEmpty();
	}

	@Test
	void aSchemaWithoutAProblemReachesTheOutputWhole() {
		Schema schema = messageSchema("p", 1, 0)
				.types(messageHeader())
				.messages(message("M", 1).fields(field("qty", 1, "int32")))
				.build();
		Annotated annotated = annotatedSchema("p", 1, 0)
				.messages(annotatedMessage("M", 1).components(annotatedField("qty", 1, primitive(INT))))
				.build();
		StringWriterOutputManager output = new StringWriterOutputManager();

		List<Problem> problems = Generator.generate(schema, annotated, output);

		assertThat(problems).isEmpty();
		assertThat(output.getSources().keySet()).containsExactlyInAnyOrder(
				"p.sbe.package-info", "p.sbe.MessageHeaderEncoder", "p.sbe.MessageHeaderDecoder", "p.sbe.MEncoder",
				"p.sbe.MDecoder", "p.sbe.MetaAttribute", "p.MCodec"
		);
	}
}
