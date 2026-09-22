package net.concini.sbebuddy.generator;

import static net.concini.sbebuddy.generator.Annotated.JavaPrimitive.INT;
import static net.concini.sbebuddy.generator.Fixtures.annotated;
import static net.concini.sbebuddy.generator.Fixtures.annotatedField;
import static net.concini.sbebuddy.generator.Fixtures.annotatedMessage;
import static net.concini.sbebuddy.generator.Fixtures.primitive;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * What the mapping guarantees and no diagnostic can show. Its rules are tested
 * as the source a user writes, in the processor's
 * {@code AnnotationMistakesTest}.
 */
final class MappingTest {

	@Test
	void everySchemaNodeKnowsItsAnnotatedNode() {
		Annotated.Field field = annotatedField("qty", 1, primitive(INT));
		Annotated annotated = annotated(0, annotatedMessage("M", 1, field));

		Mapping.Mapped mapped = Mapping.map(annotated);

		assertThat(mapped.origins().get(mapped.schema().messages().get(0).fields().get(0))).isSameAs(field);
	}
}
