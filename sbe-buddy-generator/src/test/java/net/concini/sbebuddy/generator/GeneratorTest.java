package net.concini.sbebuddy.generator;

import static net.concini.sbebuddy.generator.Fixtures.composite;
import static net.concini.sbebuddy.generator.Fixtures.data;
import static net.concini.sbebuddy.generator.Fixtures.field;
import static net.concini.sbebuddy.generator.Fixtures.group;
import static net.concini.sbebuddy.generator.Fixtures.message;
import static net.concini.sbebuddy.generator.Fixtures.messageSchema;
import static net.concini.sbebuddy.generator.Fixtures.type;
import static org.assertj.core.api.Assertions.assertThat;
import static uk.co.real_logic.sbe.PrimitiveType.INT64;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The rules that compare nodes, each built as the mistake and asserted as the
 * problem and the node it names.
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
}
