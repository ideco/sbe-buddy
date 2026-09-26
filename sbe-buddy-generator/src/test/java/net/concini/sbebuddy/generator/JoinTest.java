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
import static net.concini.sbebuddy.generator.Fixtures.primitive;
import static net.concini.sbebuddy.generator.Fixtures.text;
import static org.assertj.core.api.Assertions.assertThat;
import static uk.co.real_logic.sbe.PrimitiveType.CHAR;
import static uk.co.real_logic.sbe.PrimitiveType.UINT16;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import uk.co.real_logic.sbe.ir.Ir;

/**
 * The join of one message's tokens with its record: with none, every node has
 * its token and name and nothing else; with one, each component meets its node,
 * and the face rules report through it on the component they fault.
 */
final class JoinTest {

	private static final Annotated.Composite VAR_STRING_ENCODING = annotatedComposite(
			"VarStringEncoding", "p.VarStringEncoding", "varStringEncoding",
			annotatedType("length", UINT16, primitive(INT)), annotatedType("varData", CHAR, 0, "UTF-8", text())
	);

	private static final Annotated.Field QTY = annotatedField("qty", 1, primitive(INT));

	private static final Annotated.Group FILLS = annotatedGroup(
			"fills", 2, 0, annotatedField("price", 3, primitive(LONG))
	);

	private static final Annotated.Data NOTE = annotatedData("note", 4, text(), VAR_STRING_ENCODING);

	private static final Annotated ANNOTATED = annotated(
			0, List.of(VAR_STRING_ENCODING), annotatedMessage("M", 1, QTY, FILLS, NOTE)
	);

	@Test
	void aMessageWithoutARecordJoinsWithNoComponents() {
		List<Problem> problems = new ArrayList<>();

		Join.Message joined = join(ANNOTATED, null, problems);

		assertThat(problems).isEmpty();
		Join.Block block = joined.block();
		assertThat(block.name()).isEqualTo("M");
		assertThat(block.fields()).singleElement().satisfies(JoinTest::assertJoinedWithNothing);
		assertThat(block.groups()).singleElement().satisfies(group -> {
			assertThat(group.property()).isEqualTo("fills");
			assertThat(group.component()).isNull();
			assertThat(group.record()).isNull();
			assertThat(group.bound()).isNull();
			assertThat(group.entry().fields()).singleElement().satisfies(JoinTest::assertJoinedWithNothing);
			assertThat(group.entry().constructorOrder()).isEmpty();
		});
		assertThat(block.data()).singleElement().satisfies(data -> {
			assertThat(data.property()).isEqualTo("note");
			assertThat(data.component()).isNull();
			assertThat(data.content()).isNull();
			assertThat(data.helpers()).isEmpty();
		});
		assertThat(block.constructorOrder()).isEmpty();
		assertThat(joined.bindings()).isEmpty();
		assertThat(joined.contexts()).isEmpty();
	}

	@Test
	void aMessageWithARecordJoinsEachComponent() {
		List<Problem> problems = new ArrayList<>();

		Join.Message joined = join(ANNOTATED, ANNOTATED.messages().get(0), problems);

		assertThat(problems).isEmpty();
		Join.Block block = joined.block();
		Join.Field qty = block.fields().get(0);
		assertThat(qty.face()).isEqualTo(
				new Faces.Face.Mapped("qty", new Faces.Shape.Scalar(null), Faces.Absence.NONE, null)
		);
		Join.Group fills = block.groups().get(0);
		assertThat(fills.component()).isSameAs(FILLS);
		assertThat(fills.record()).isEqualTo("Fills");
		assertThat(fills.entry().fields()).singleElement()
				.satisfies(price -> assertThat(price.face()).isInstanceOf(Faces.Face.Mapped.class));
		Join.Data note = block.data().get(0);
		assertThat(note.component()).isSameAs(NOTE);
		assertThat(note.content()).isEqualTo(Faces.Content.UTF_8);
		assertThat(note.helpers()).containsExactly(new Faces.Helper.Utf8());
		assertThat(block.constructorOrder()).containsExactly(qty, fills, note);
	}

	@Test
	void aComponentThatIsNotTheFaceOfItsWireTypeIsAProblemOnItAndLeftOut() {
		Annotated.Field quantity = annotatedField("quantity", 1, primitive(LONG), UINT16);
		Annotated annotated = annotated(0, annotatedMessage("M", 1, quantity));
		List<Problem> problems = new ArrayList<>();

		Join.Message joined = join(annotated, annotated.messages().get(0), problems);

		assertThat(problems).containsExactly(new Problem(quantity, "long is not the face of uint16, which is int"));
		assertThat(joined.block().fields()).isEmpty();
	}

	private static void assertJoinedWithNothing(Join.Field field) {
		assertThat(field.face()).isNull();
		assertThat(field.helpers()).isEmpty();
		assertThat(field.composite()).isNull();
	}

	/**
	 * The annotations' only message joined with {@code message}, or with nothing.
	 */
	private static Join.Message join(
			Annotated annotated, Annotated.@Nullable Message message, List<Problem> problems
	) {
		Mapping.Mapped mapped = Mapping.map(annotated);
		assertThat(mapped.problems()).as("the mapping of the annotations").isEmpty();
		Ir ir = Generator.ir(mapped.schema());
		return Join.join(ir, annotated, 0, ir.getMessage(1), message, problems);
	}
}
