package net.concini.sbebuddy.processor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Mistakes a user writes, compiled through the processor: each test is the
 * declaration as it would be typed, and asserts the diagnostic, the line it
 * lands on and that nothing was written. A trial of rules {@code MappingTest}
 * also covers over the model, to weigh reading and running cost.
 */
final class AnnotationMistakesTest {

	@Test
	void aComponentThatIsNotTheFaceOfItsWireTypeIsAProblem() {
		assertErrors(
				inMessage("@SbeField(id = 1, primitiveType = UINT16) long quantity"),
				error("long quantity", "long is not the face of uint16, which is int")
		);
	}

	@Test
	void aPrimitiveOnAFieldThatCanBeAbsentIsAProblem() {
		assertErrors(
				inMessage("@SbeField(id = 1, presence = OPTIONAL) int quantity"),
				error("int quantity", "int cannot hold null, but the field can be absent; use Integer")
		);
	}

	@Test
	void aBoxOnAFieldThatIsNeverAbsentIsAWarning() {
		String source = inMessage("@SbeField(id = 1) Integer quantity");

		Javac.Result result = compile(source);

		assertThat(result.errors()).isEmpty();
		assertThat(result.warnings()).singleElement().satisfies(warning -> {
			assertThat(warning.getMessage(null)).isEqualTo("Integer is boxed although the field is never absent");
			assertThat(warning.getLineNumber()).isEqualTo(lineOf(source, "Integer quantity"));
		});
		assertThat(result.outputs()).isNotEmpty();
	}

	@Test
	void aSetFieldsComponentIsASetOfTheEnum() {
		String flags = """
				@SbeSet(primitiveType = UINT8)
				enum Flags {

					@SbeChoice(0)
					firm
				}
				""";

		assertErrors(
				inMessage("@SbeField(id = 1) Flags flags", flags),
				error("Flags flags", "Flags is a set; use Set<Flags>")
		);
	}

	@Test
	void theLayoutNamesEverythingOnceAndNothingElse() {
		assertErrors(
				message("@SbeMessage(id = 1, layout = {\"qty\", \"qty\"})", "@SbeField(id = 2) int qty"),
				error("layout = ", "the layout names \"qty\" twice")
		);
		assertErrors(
				message("@SbeMessage(id = 1, layout = {\"qty\", \"prize\"})", "@SbeField(id = 2) int qty"),
				error("layout = ", "the layout names nothing called \"prize\"")
		);
		assertErrors(
				message(
						"@SbeMessage(id = 1, layout = {\"orderId\"})",
						"@SbeField(id = 2) int qty,\n@SbeField(id = 1) long orderId"
				),
				error("layout = ", "the layout misses \"qty\"")
		);
	}

	@Test
	void aCompositesMemberIsTheFaceOfItsType() {
		String price = """
				@SbeComposite
				record Price(
						@SbeType(primitiveType = INT64) int mantissa,
						@SbeType(primitiveType = UINT8, presence = OPTIONAL) short scale,
						@SbeType(primitiveType = CHAR, presence = CONSTANT) byte unit
				) {
				}
				""";

		assertErrors(
				inMessage("@SbeField(id = 1) Price price", price),
				error("int mantissa", "int is not the face of mantissa, which is long"),
				error("short scale", "short cannot hold null, but the member can be absent; use Short"),
				error("byte unit", "a constant member needs a value or a valueRef")
		);
	}

	// ---- the snippet around the mistake

	private static final String PACKAGE_INFO = """
			@SbeSchema(id = 1, version = 0)
			package mistakes;

			import net.concini.sbebuddy.SbeSchema;
			""";

	private static final String HEADER = """
			package mistakes;

			import static net.concini.sbebuddy.Presence.*;
			import static net.concini.sbebuddy.PrimitiveType.*;

			import java.util.*;

			import net.concini.sbebuddy.*;

			""";

	/**
	 * The components in a message record {@code Order}, beside the declarations.
	 */
	private static String inMessage(String components, String... declarations) {
		return message("@SbeMessage(id = 1)", components, declarations);
	}

	private static String message(String annotation, String components, String... declarations) {
		return HEADER + String.join("\n", declarations) + "\n" + annotation + "\nrecord Order(\n" + components
				+ "\n) {\n}\n";
	}

	private record Expected(String mistake, String message) {
	}

	private static Expected error(String mistake, String message) {
		return new Expected(mistake, message);
	}

	/**
	 * Exactly these errors, each on the line of its mistake, and nothing written.
	 */
	private static void assertErrors(String source, Expected... expected) {
		Javac.Result result = compile(source);

		assertThat(result.errors()).extracting(
				error -> error(lineText(source, error.getLineNumber()), error.getMessage(null))
		).containsExactlyInAnyOrder(
				Arrays.stream(expected)
						.map(error -> error(lineText(source, lineOf(source, error.mistake())), error.message()))
						.toArray(Expected[]::new)
		);
		assertThat(result.outputs()).isEmpty();
	}

	private static Javac.Result compile(String source) {
		return Javac.compile(
				List.of(
						Javac.unit("mistakes/package-info.java", PACKAGE_INFO),
						Javac.unit("mistakes/Order.java", source)
				),
				new SbeProcessor()
		);
	}

	private static String lineText(String source, long line) {
		return source.lines().toList().get((int) line - 1).strip();
	}

	private static long lineOf(String source, String mistake) {
		List<String> lines = source.lines().toList();
		for (int line = 0; line < lines.size(); line++) {
			if (lines.get(line).contains(mistake)) {
				return line + 1L;
			}
		}
		throw new IllegalArgumentException("no line holds " + mistake);
	}
}
