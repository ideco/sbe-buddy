package net.concini.sbebuddy.generator;

import static net.concini.sbebuddy.generator.Annotated.JavaPrimitive.INT;
import static net.concini.sbebuddy.generator.Annotated.JavaPrimitive.LONG;
import static net.concini.sbebuddy.generator.Fixtures.annotated;
import static net.concini.sbebuddy.generator.Fixtures.annotatedComposite;
import static net.concini.sbebuddy.generator.Fixtures.annotatedData;
import static net.concini.sbebuddy.generator.Fixtures.annotatedField;
import static net.concini.sbebuddy.generator.Fixtures.annotatedMessage;
import static net.concini.sbebuddy.generator.Fixtures.annotatedType;
import static net.concini.sbebuddy.generator.Fixtures.boundField;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.agrona.generation.StringWriterOutputManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
	void aConstructTheCodecRefusesLeavesTheOutputUntouched() {
		// sbe-tool accepts the schema and generates its flyweights, which read the
		// encoding by name at run time; the codec refuses one the JDK does not know,
		// so the flyweights must not reach the output either.
		Annotated.Composite varKlingonEncoding = annotatedComposite(
				"VarKlingonEncoding", "p.VarKlingonEncoding", "varKlingonEncoding",
				annotatedType("length", UINT16, primitive(INT)), annotatedType("varData", CHAR, 0, "x-klingon", text())
		);
		Annotated annotated = annotated(
				0, List.of(varKlingonEncoding),
				annotatedMessage(
						"M", 1, annotatedField("qty", 1, primitive(INT)),
						annotatedData("note", 2, text(), varKlingonEncoding)
				)
		);

		assertRefused(
				annotated,
				"no codec for text in x-klingon: the JDK knows no such encoding; set codecs = false on @SbeSchema"
		);
	}

	@Test
	void aCharArrayInAnEncodingWritingZeroBytesHasNoCodec() {
		// The flyweight reads a char array up to its first zero byte, which UTF-16
		// writes inside a character.
		Annotated.Type name = annotatedType("Name", CHAR, 8, "UTF-16", null);
		Annotated annotated = annotated(
				0, List.of(name), annotatedMessage("M", 1, annotatedField("name", 1, text(), name))
		);

		assertRefused(
				annotated,
				"no codec for a char array in UTF-16: it writes zero bytes inside a character, and sbe-tool's flyweight ends a char array at its first zero byte; set codecs = false on @SbeSchema"
		);
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

	@Test
	void aResourceIsReadWithItsIncludesResolvedAgainstItsUri(@TempDir Path directory) throws IOException {
		Files.createDirectories(directory.resolve("common"));
		Files.writeString(directory.resolve("common/types.xml"), INCLUDED_TYPES);
		Path resource = directory.resolve("schema.xml");
		Files.writeString(resource, INCLUDING_SCHEMA);
		Annotated annotated = annotated(0, annotatedMessage("M", 1, annotatedField("qty", 1, primitive(INT))));
		StringWriterOutputManager output = new StringWriterOutputManager();

		List<Problem> problems = Generator
				.generate(
						schemaOf(annotated), Files.readString(resource), resource.toUri().toString(), annotated, output
				);

		assertThat(problems).isEmpty();
		assertThat(output.getSources().keySet()).contains("p.MCodec", "p.sbe.MEncoder", "p.sbe.MessageHeaderEncoder");
	}

	@Test
	void anIncludeThatCannotBeResolvedIsAProblemNamingThePackage(@TempDir Path directory) throws IOException {
		Path resource = directory.resolve("schema.xml");
		Files.writeString(resource, INCLUDING_SCHEMA);
		Annotated annotated = annotated(0, annotatedMessage("M", 1, annotatedField("qty", 1, primitive(INT))));
		Schema schema = schemaOf(annotated);
		StringWriterOutputManager output = new StringWriterOutputManager();

		List<Problem> problems = Generator
				.generate(schema, Files.readString(resource), resource.toUri().toString(), annotated, output);

		assertThat(problems).hasSize(1);
		assertThat(problems.get(0).node()).isSameAs(schema);
		assertThat(problems.get(0).message()).contains("common/types.xml");
		assertThat(output.getSources()).isEmpty();
	}

	@Test
	void aResourceDisagreeingWithTheAnnotationsIsAProblemNamingWhatDisagrees() {
		String document = INCLUDING_SCHEMA.replace("<xi:include href=\"common/types.xml\"/>", INCLUDED_TYPES)
				.replace("id=\"1\" version=\"0\"", "id=\"2\" version=\"0\"");
		Annotated annotated = annotated(0, annotatedMessage("M", 1, annotatedField("qty", 3, primitive(INT))));
		Schema schema = schemaOf(annotated);
		StringWriterOutputManager output = new StringWriterOutputManager();

		List<Problem> problems = Generator.generate(schema, document, "memory:schema.xml", annotated, output);

		assertThat(problems).containsExactlyInAnyOrder(
				new Problem(schema, "the schema has id=\"2\", not \"1\""),
				new Problem(schema.messages().get(0).fields().get(0), "the schema has id=\"1\", not \"3\"")
		);
		assertThat(output.getSources()).isEmpty();
	}

	/** A schema whose types arrive by XInclude, relative to the document. */
	private static final String INCLUDING_SCHEMA = """
			<?xml version="1.0" encoding="UTF-8"?>
			<sbe:messageSchema xmlns:sbe="http://fixprotocol.io/2016/sbe" xmlns:xi="http://www.w3.org/2001/XInclude" package="p" id="1" version="0">
			    <xi:include href="common/types.xml"/>
			    <sbe:message name="M" id="1">
			        <field name="qty" id="1" type="int32"/>
			    </sbe:message>
			</sbe:messageSchema>
			""";

	private static final String INCLUDED_TYPES = """
			<types>
			    <composite name="messageHeader">
			        <type name="blockLength" primitiveType="uint16"/>
			        <type name="templateId" primitiveType="uint16"/>
			        <type name="schemaId" primitiveType="uint16"/>
			        <type name="version" primitiveType="uint16"/>
			    </composite>
			</types>
			""";

	/**
	 * The one problem naming the construct the codec lacks, and nothing written.
	 */
	private static void assertRefused(Annotated annotated, String problem) {
		StringWriterOutputManager output = new StringWriterOutputManager();

		assertThat(generate(annotated, output)).containsExactly(new Problem(annotated.messages().get(0), problem));
		assertThat(output.getSources()).isEmpty();
	}

	/**
	 * The schema the mapping makes of the annotations, generated: the mapping is
	 * the input here, not the expectation, so these tests describe each case once.
	 */
	private static List<Problem> generate(Annotated annotated, StringWriterOutputManager output) {
		return Generator.generate(schemaOf(annotated), annotated, output);
	}

	private static Schema schemaOf(Annotated annotated) {
		Mapping.Mapped mapped = Mapping.map(annotated);
		assertThat(mapped.problems()).as("the mapping of the annotations").isEmpty();
		return mapped.schema();
	}

	private static Annotated.Composite varStringEncoding() {
		return annotatedComposite(
				"VarStringEncoding", "p.VarStringEncoding", "varStringEncoding",
				annotatedType("length", UINT16, primitive(INT)), annotatedType("varData", CHAR, 0, "UTF-8", text())
		);
	}
}
