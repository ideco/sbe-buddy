package net.concini.sbebuddy.processor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

import org.junit.jupiter.api.Test;

/**
 * The mapping's rules as a user meets them: each test is the declaration as it
 * would be typed, compiled through the processor, asserting every diagnostic
 * with the line it lands on and that nothing was written; and where a rule
 * allows something, that it compiles clean. The schemas set {@code codecs =
 * false}, so what is tested is the mapping and not the codec emitter.
 */
final class AnnotationMistakesTest {

	// ---- a field's Java type

	@Test
	void typeAndPrimitiveTypeTogetherIsAProblem() {
		assertErrors(
				inMessage(
						"@SbeField(id = 1, type = Quantity.class, primitiveType = UINT16) int qty",
						"@SbeType(primitiveType = UINT32) final class Quantity {}"
				),
				error("int qty", "type and primitiveType both given; give one")
		);
	}

	@Test
	void aComponentTypeWithNoMappingIsAProblem() {
		assertErrors(
				inMessage("@SbeField(id = 1) String symbol"),
				error("String symbol", "String maps to no SBE type; give type or primitiveType")
		);
	}

	@Test
	void javaCharIsAProblem() {
		assertErrors(
				inMessage("@SbeField(id = 1) char initial"),
				error(
						"char initial",
						"char, 16 bits where SBE's char is one byte, maps to no SBE type; give type or primitiveType"
				)
		);
	}

	@Test
	void aComponentThatIsNotTheFaceOfItsWireTypeIsAProblem() {
		assertErrors(
				inMessage("@SbeField(id = 1, primitiveType = UINT16) long quantity"),
				error("long quantity", "long is not the face of uint16, which is int")
		);
	}

	@Test
	void aNamedTypeOfLengthOneHasTheFaceOfItsEncoding() {
		assertErrors(
				inMessage(
						"@SbeField(id = 1, type = Initial.class) String symbol",
						"@SbeType(primitiveType = CHAR) final class Initial {}"
				),
				error("String symbol", "String is not the face of char, which is byte")
		);
	}

	// ---- absence

	@Test
	void aPrimitiveOnAFieldThatCanBeAbsentIsAProblem() {
		assertErrors(
				inMessage("@SbeField(id = 1, presence = OPTIONAL) int quantity"),
				error("int quantity", "int cannot hold null, but the field can be absent; use Integer")
		);
	}

	@Test
	void aBoxOnAFieldThatIsNeverAbsentIsAWarning() {
		assertWarnings(
				inMessage("@SbeField(id = 1) Integer quantity"),
				error("Integer quantity", "Integer is boxed although the field is never absent")
		);
	}

	@Test
	void aFieldAddedAboveTheBaselineCanBeAbsent() {
		assertErrors(
				schema(2, 1), inMessage("@SbeField(id = 1, sinceVersion = 2) int quantity"),
				error("int quantity", "int cannot hold null, but the field can be absent; use Integer")
		);
	}

	@Test
	void aFieldAddedAtTheBaselineIsNeverAbsent() {
		assertWarnings(
				schema(2, 1),
				inMessage(
						"@SbeField(id = 1, sinceVersion = 1) int quantity,\n@SbeField(id = 2, sinceVersion = 1) Long price"
				),
				error("Long price", "Long is boxed although the field is never absent")
		);
	}

	@Test
	void aFieldAtItsGroupsVersionIsNeverAbsent() {
		// The group's guard fires first: an entry exists only where the field does.
		assertWarnings(
				schema(2, 0),
				inMessage(
						"@SbeGroup(id = 2, sinceVersion = 2) List<Leg> legs",
						"record Leg(@SbeField(id = 3, sinceVersion = 2) int legId, @SbeField(id = 4, sinceVersion = 2) Long ratio) {}"
				),
				error("record Leg", "Long is boxed although the field is never absent")
		);
	}

	@Test
	void aFieldAddedAboveItsGroupsVersionCanBeAbsent() {
		assertErrors(
				schema(3, 0),
				inMessage(
						"@SbeGroup(id = 2, sinceVersion = 2) List<Leg> legs",
						"record Leg(\n@SbeField(id = 3, sinceVersion = 3) int legId\n) {}"
				),
				error("int legId", "int cannot hold null, but the field can be absent; use Integer")
		);
	}

	@Test
	void aFieldTakesItsNamedTypesPresence() {
		assertErrors(
				inMessage(
						"@SbeField(id = 1, type = Quantity.class) long quantity",
						"@SbeType(primitiveType = UINT32, presence = OPTIONAL) final class Quantity {}"
				),
				error("long quantity", "long cannot hold null, but the field can be absent; use Long")
		);
	}

	@Test
	void aConstantIsNeverAbsent() {
		assertClean(
				schema(2, 0),
				inMessage(
						"@SbeField(id = 1, primitiveType = CHAR, presence = CONSTANT, valueRef = \"Side.Buy\", sinceVersion = 2) byte side",
						SIDE
				)
		);
	}

	// ---- enums and sets

	@Test
	void anEnumFieldsComponentIsTheEnum() {
		assertErrors(
				inMessage("@SbeField(id = 1, type = Side.class) byte side", SIDE),
				error("byte side", "Side is an enum; use Side")
		);
	}

	@Test
	void aSetFieldsComponentIsASetOfTheEnum() {
		assertErrors(
				inMessage(
						"@SbeField(id = 1) Flags flags,\n@SbeField(id = 2, type = Flags.class) Set<Other> other", FLAGS,
						"@SbeSet(primitiveType = UINT8) enum Other { @SbeChoice(0) firm }"
				),
				error("Flags flags", "Flags is a set; use Set<Flags>"),
				error("Set<Other> other", "Flags is a set; use Set<Flags>")
		);
	}

	@Test
	void aSetFieldCannotBeOptional() {
		assertErrors(
				inMessage("@SbeField(id = 1, presence = OPTIONAL) Set<Flags> flags", FLAGS),
				error("Set<Flags> flags", "a set has no null value; a set field cannot be optional")
		);
	}

	// ---- the layout and unmapped fields

	@Test
	void theLayoutOrdersTheBody() {
		Javac.Result result = assertClean(
				message(
						"@SbeMessage(id = 1, layout = {\"orderId\", \"price\", \"qty\"}, unmapped = @SbeField(id = 3, name = \"price\", primitiveType = INT64))",
						"@SbeField(id = 2) int qty,\n@SbeField(id = 1) long orderId"
				)
		);

		assertThat(schemaXml(result)).containsSubsequence("name=\"orderId\"", "name=\"price\"", "name=\"qty\"");
	}

	@Test
	void anUnmappedFieldNeedsAName() {
		assertErrors(
				message(
						"@SbeMessage(id = 1, layout = {\"qty\", \"\"}, unmapped = @SbeField(id = 3, primitiveType = INT64))",
						"@SbeField(id = 2) int qty"
				),
				error("layout = ", "an unmapped field needs a name"),
				error("layout = ", "\"\" is not a name SBE allows: a letter or _, then letters, digits and _")
		);
	}

	@Test
	void anUnmappedFieldNeedsAType() {
		assertErrors(
				message(
						"@SbeMessage(id = 1, layout = {\"qty\", \"price\"}, unmapped = @SbeField(id = 3, name = \"price\"))",
						"@SbeField(id = 2) int qty"
				),
				error("layout = ", "an unmapped field needs a type or a primitiveType")
		);
	}

	@Test
	void unmappedFieldsNeedALayout() {
		assertErrors(
				message(
						"@SbeMessage(id = 1, unmapped = @SbeField(id = 3, name = \"price\", primitiveType = INT64))",
						"@SbeField(id = 2) int qty"
				),
				error("@SbeMessage", "unmapped fields need a layout to take their place in")
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
	void aNameThatIsBothAComponentsAndAnUnmappedFieldsIsAProblem() {
		assertErrors(
				message(
						"@SbeMessage(id = 1, layout = {\"qty\"}, unmapped = @SbeField(id = 3, name = \"qty\", primitiveType = INT64))",
						"@SbeField(id = 2) int qty"
				),
				error("layout = ", "\"qty\" is both a component and an unmapped field")
		);
	}

	// ---- types with a length, and constants

	@Test
	void aTypeWithALengthHasTheFaceOfItsArray() {
		assertErrors(
				inMessage(
						"""
								@SbeField(id = 1, type = Symbol.class) byte[] symbol,
								@SbeField(id = 2, type = Rgb.class) String colour,
								@SbeField(id = 3, type = Samples.class) short[] samples""",
						SYMBOL, RGB, "@SbeType(primitiveType = INT32, length = 4) final class Samples {}"
				),
				error("byte[] symbol", "byte[] is not the face of Symbol, which is String"),
				error("String colour", "String is not the face of Rgb, which is byte[]"),
				error("short[] samples", "short[] is not the face of Samples, which is int[]")
		);
		assertClean(
				inMessage(
						"@SbeField(id = 1, type = Sizes.class) long[] sizes",
						"@SbeType(primitiveType = UINT32, length = 2) final class Sizes {}"
				)
		);
	}

	@Test
	void aConstantCharValueLongerThanOneCharacterIsAString() {
		assertErrors(
				inMessage(
						"@SbeField(id = 1, type = Currency.class) byte currency",
						"@SbeType(primitiveType = CHAR, presence = CONSTANT, value = \"USD\") final class Currency {}"
				),
				error("byte currency", "byte is not the face of Currency, which is String")
		);
	}

	@Test
	void aFieldOfATypeWithALengthCannotBeOptional() {
		assertErrors(
				inMessage(
						"@SbeField(id = 1, type = Symbol.class, presence = OPTIONAL) String symbol,\n@SbeField(id = 2, type = Samples.class) int[] samples",
						SYMBOL,
						"@SbeType(primitiveType = INT32, length = 4, presence = OPTIONAL) final class Samples {}"
				),
				error("String symbol", "Symbol has a length; a field of it cannot be optional"),
				error("int[] samples", "Samples has a length; a field of it cannot be optional")
		);
	}

	@Test
	void aConstantFieldNeedsAValueRefOrAConstantType() {
		assertErrors(
				inMessage("@SbeField(id = 1, presence = CONSTANT) byte exponent"),
				error("byte exponent", "a constant field needs a valueRef or a constant type")
		);
		assertClean(
				inMessage(
						"@SbeField(id = 1, primitiveType = CHAR, presence = CONSTANT, valueRef = \"Side.Buy\") byte side,\n@SbeField(id = 2, type = Exponent.class) byte exponent",
						SIDE,
						"@SbeType(primitiveType = INT8, presence = CONSTANT, value = \"-4\") final class Exponent {}"
				)
		);
	}

	// ---- bindings

	@Test
	void theFaceDecidesTheBindingsInterface() {
		assertErrors(
				inMessage(
						"""
								@SbeField(id = 1, primitiveType = INT32, binding = CentsBinding.class) BigDecimal fee,
								@SbeField(id = 2, primitiveType = INT64, binding = BoxedCents.class) BigDecimal tax,
								@SbeField(id = 3, type = Symbol.class, binding = CentsBinding.class) BigDecimal symbol""",
						CENTS_BINDING, SYMBOL,
						"""
								final class BoxedCents implements TypeBinding<BigDecimal, Long> {
									public Long toWire(BigDecimal value) { return value.movePointRight(2).longValueExact(); }
									public BigDecimal fromWire(Long wire) { return BigDecimal.valueOf(wire, 2); }
								}"""
				),
				error(
						"BigDecimal fee",
						"CentsBinding binds the wire as long, but the face of int32 is int; implement TypeBinding.OfInt"
				),
				error(
						"BigDecimal tax",
						"BoxedCents binds the wire as Long, but the face of int64 is long; implement TypeBinding.OfLong"
				),
				error(
						"BigDecimal symbol",
						"CentsBinding binds the wire as long, but the face of Symbol is String; implement TypeBinding over String"
				)
		);
		assertClean(
				inMessage(
						"@SbeField(id = 1, primitiveType = INT64, binding = CentsBinding.class) BigDecimal fee,\n@SbeField(id = 2, type = Rgb.class, binding = ColourBinding.class) Colour colour",
						CENTS_BINDING, RGB, "record Colour(int red, int green, int blue) {}",
						"""
								final class ColourBinding implements TypeBinding<Colour, byte[]> {
									public byte[] toWire(Colour value) { return new byte[] {(byte) value.red(), (byte) value.green(), (byte) value.blue()}; }
									public Colour fromWire(byte[] wire) { return new Colour(wire[0], wire[1], wire[2]); }
								}"""
				)
		);
	}

	@Test
	void aFieldOfAnEnumOrASetTakesNoBinding() {
		assertErrors(
				inMessage(
						"@SbeField(id = 1, binding = SideBinding.class) Side side", SIDE,
						"""
								final class SideBinding implements TypeBinding.OfShort<Side> {
									public short toWire(Side value) { return (short) value.ordinal(); }
									public Side fromWire(short wire) { return Side.values()[wire]; }
								}"""
				),
				error("Side side", "Side is an enum; a field of it takes no binding")
		);
	}

	// ---- composites

	@Test
	void aCompositesMemberIsTheFaceOfItsType() {
		assertErrors(
				inMessage(
						"@SbeField(id = 1) Price price",
						"""
								@SbeComposite
								record Price(
										@SbeType(primitiveType = INT64) int mantissa,
										@SbeType(primitiveType = UINT8, presence = OPTIONAL) short scale,
										@SbeType(primitiveType = CHAR, presence = CONSTANT) byte unit
								) {}"""
				),
				error("int mantissa", "int is not the face of mantissa, which is long"),
				error("short scale", "short cannot hold null, but the member can be absent; use Short"),
				error("byte unit", "a constant member needs a value or a valueRef")
		);
	}

	@Test
	void aRefsComponentIsTheFaceOfWhatItRefersTo() {
		assertErrors(
				inMessage(
						"@SbeField(id = 1) Quote quote", DECIMAL,
						"@SbeComposite\nrecord Quote(\n@SbeRef(Decimal.class) long bid\n) {}"
				),
				error("long bid", "long is not the face of Decimal, which is Decimal")
		);
	}

	@Test
	void aCompositeFieldsComponentIsTheRecordAndNeverOptional() {
		assertErrors(
				inMessage(
						"""
								@SbeField(id = 1, type = Decimal.class) long plain,
								@SbeField(id = 2, presence = OPTIONAL) Decimal optional,
								@SbeField(id = 3, type = Decimal.class, binding = CentsBinding.class) BigDecimal bound""",
						DECIMAL, CENTS_BINDING
				),
				error("long plain", "Decimal is a composite; use Decimal"),
				error("Decimal optional", "Decimal is a composite; a field of it cannot be optional"),
				error(
						"BigDecimal bound",
						"CentsBinding binds the wire as long, but the face of Decimal is Decimal; implement TypeBinding over Decimal"
				)
		);
		assertClean(
				inMessage(
						"@SbeField(id = 1, type = Decimal.class, binding = DecimalBinding.class) BigDecimal last",
						DECIMAL,
						"""
								final class DecimalBinding implements TypeBinding<BigDecimal, Decimal> {
									public Decimal toWire(BigDecimal value) { return new Decimal(value.unscaledValue().longValueExact()); }
									public BigDecimal fromWire(Decimal wire) { return BigDecimal.valueOf(wire.mantissa()); }
								}"""
				)
		);
	}

	@Test
	void aCompositesLayoutOrdersItsMembers() {
		Javac.Result result = assertClean(
				inMessage(
						"@SbeField(id = 1) Price price",
						"""
								@SbeComposite(layout = {"mantissa", "scale", "exponent"}, unmapped = @SbeType(name = "scale", primitiveType = UINT8))
								record Price(@SbeType(primitiveType = INT8) byte exponent, @SbeType(primitiveType = INT64) long mantissa) {}"""
				)
		);

		assertThat(schemaXml(result))
				.containsSubsequence("name=\"mantissa\"", "name=\"scale\"", "name=\"exponent\"");
	}

	@Test
	void aCompositesLayoutAndUnmappedMembersFollowTheMessagesRules() {
		assertErrors(
				inMessage(
						"@SbeField(id = 1) Price price",
						"""
								@SbeComposite(layout = {"bounds", "", "scale"}, unmapped = @SbeType(primitiveType = UINT8))
								record Price(
										@SbeType(primitiveType = UINT32, length = 3, presence = OPTIONAL) long[] bounds
								) {}"""
				),
				error("layout = ", "the layout names nothing called \"scale\""),
				error("long[] bounds", "bounds has a length; it cannot be optional"),
				error("layout = ", "an unmapped member needs a name"),
				error("layout = ", "\"\" is not a name SBE allows: a letter or _, then letters, digits and _")
		);
	}

	// ---- the schema and the body

	@Test
	void aBaselineAboveTheSchemasVersionIsAProblem() {
		Javac.Result result = compile(schema(1, 2), inMessage("@SbeField(id = 1) int qty"));

		assertThat(result.errors()).singleElement().satisfies(error -> {
			assertThat(error.getMessage(null)).isEqualTo("a baselineVersion is 0 to the schema's version 1, not 2");
			assertThat(error.getSource()).isNotNull();
			assertThat(error.getSource().getName()).endsWith("package-info.java");
		});
		assertThat(result.outputs()).isEmpty();
	}

	@Test
	void aFieldAfterAGroupIsAProblem() {
		assertErrors(
				inMessage("@SbeGroup(id = 1) List<Leg> legs,\n@SbeField(id = 2) int late", LEG),
				error("int late", "a field must come before every group and data")
		);
	}

	@Test
	void aGroupOnAnythingButAListOfARecordIsAProblem() {
		assertErrors(
				inMessage("@SbeGroup(id = 1) String legs"),
				error("String legs", "a group must be a List of a record")
		);
	}

	@Test
	void anIdAboveTheXsdsUnsignedShortIsAProblem() {
		assertErrors(inMessage("@SbeField(id = 65536) int qty"), error("int qty", "an id is 0 to 65535, not 65536"));
	}

	@Test
	void aNameOutsideTheXsdsPatternIsAProblem() {
		assertErrors(
				inMessage("@SbeField(id = 1, name = \"net qty\") int qty"),
				error("int qty", "\"net qty\" is not a name SBE allows: a letter or _, then letters, digits and _")
		);
	}

	@Test
	void aDeclarationWithABadNameIsBlamedOnceHoweverOftenItIsReferred() {
		assertErrors(
				inMessage(
						"@SbeField(id = 1, type = Quantity.class) long qty,\n@SbeField(id = 2, type = Quantity.class) long shown",
						"@SbeType(primitiveType = UINT32, name = \"net qty\") final class Quantity {}"
				),
				error(
						"class Quantity",
						"\"net qty\" is not a name SBE allows: a letter or _, then letters, digits and _"
				)
		);
	}

	// ---- declarations the snippets share

	private static final String SIDE = "@SbeEnum(primitiveType = CHAR) enum Side { @SbeEnumValue(\"B\") Buy, @SbeEnumValue(\"S\") Sell }";

	private static final String FLAGS = "@SbeSet(primitiveType = UINT8) enum Flags { @SbeChoice(0) firm }";

	private static final String SYMBOL = "@SbeType(primitiveType = CHAR, length = 6) final class Symbol {}";

	private static final String RGB = "@SbeType(primitiveType = UINT8, length = 3) final class Rgb {}";

	private static final String LEG = "record Leg(@SbeField(id = 3) int legId) {}";

	private static final String DECIMAL = "@SbeComposite record Decimal(@SbeType(primitiveType = INT64) long mantissa) {}";

	private static final String CENTS_BINDING = """
			final class CentsBinding implements TypeBinding.OfLong<BigDecimal> {
				public long toWire(BigDecimal value) { return value.movePointRight(2).longValueExact(); }
				public BigDecimal fromWire(long wire) { return BigDecimal.valueOf(wire, 2); }
			}""";

	// ---- the snippet around the mistake

	private static final String HEADER = """
			package mistakes;

			import static net.concini.sbebuddy.Presence.*;
			import static net.concini.sbebuddy.PrimitiveType.*;

			import java.math.*;
			import java.util.*;

			import net.concini.sbebuddy.*;

			""";

	/** The schema of the snippets, at a version and baseline, without codecs. */
	private static String schema(int version, int baseline) {
		return """
				@SbeSchema(id = 1, version = %d, baselineVersion = %d, codecs = false)
				package mistakes;

				import net.concini.sbebuddy.SbeSchema;
				""".formatted(version, baseline);
	}

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

	/** A diagnostic as the line it lands on and its message. */
	private record Expected(String line, String message) {
	}

	/** A diagnostic on the line holding the mistake. */
	private static Expected error(String mistake, String message) {
		return new Expected(mistake, message);
	}

	private static void assertErrors(String source, Expected... expected) {
		assertErrors(schema(0, 0), source, expected);
	}

	/**
	 * Exactly these errors, each on the line of its mistake, and nothing written.
	 */
	private static void assertErrors(String packageInfo, String source, Expected... expected) {
		Javac.Result result = compile(packageInfo, source);

		assertThat(located(source, result.errors())).containsExactlyInAnyOrder(located(source, expected));
		assertThat(result.outputs()).isEmpty();
	}

	private static void assertWarnings(String source, Expected... expected) {
		assertWarnings(schema(0, 0), source, expected);
	}

	/** No error, exactly these warnings, and everything written. */
	private static void assertWarnings(String packageInfo, String source, Expected... expected) {
		Javac.Result result = compile(packageInfo, source);

		assertThat(located(source, result.errors())).isEmpty();
		assertThat(located(source, result.warnings())).containsExactlyInAnyOrder(located(source, expected));
		assertThat(result.outputs()).isNotEmpty();
	}

	private static Javac.Result assertClean(String source) {
		return assertClean(schema(0, 0), source);
	}

	/** Neither error nor warning, and everything written. */
	private static Javac.Result assertClean(String packageInfo, String source) {
		Javac.Result result = compile(packageInfo, source);

		assertThat(located(source, result.diagnostics())).isEmpty();
		assertThat(result.outputs()).isNotEmpty();
		return result;
	}

	private static Javac.Result compile(String packageInfo, String source) {
		return Javac.compile(
				List.of(
						Javac.unit("mistakes/package-info.java", packageInfo), Javac.unit("mistakes/Order.java", source)
				),
				new SbeProcessor()
		);
	}

	private static String schemaXml(Javac.Result result) {
		return result.outputs().entrySet().stream()
				.filter(output -> output.getKey().endsWith("schema.xml"))
				.map(Map.Entry::getValue)
				.findFirst()
				.orElseThrow(() -> new AssertionError("no schema.xml among " + result.outputs().keySet()));
	}

	private static List<Expected> located(String source, List<Diagnostic<? extends JavaFileObject>> diagnostics) {
		return diagnostics.stream()
				.map(
						diagnostic -> new Expected(
								lineText(source, diagnostic.getLineNumber()), diagnostic.getMessage(null)
						)
				)
				.toList();
	}

	private static Expected[] located(String source, Expected... expected) {
		return Arrays.stream(expected)
				.map(error -> new Expected(lineText(source, lineOf(source, error.line())), error.message()))
				.toArray(Expected[]::new);
	}

	private static String lineText(String source, long line) {
		List<String> lines = source.lines().toList();
		return line < 1 || line > lines.size() ? "<no line " + line + ">" : lines.get((int) line - 1).strip();
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
