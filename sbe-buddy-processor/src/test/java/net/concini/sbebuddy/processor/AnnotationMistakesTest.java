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
 * false}, so what is tested is the mapping and the face rules, which apply
 * whether or not codecs are wanted, and not the codec emitter, except for the
 * unions, which need codecs.
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
				EVOLVING,
				inMessage(
						ORDER_V1.replace(
								"@SbeGroup(",
								"@SbeField(id = 7, sinceVersion = 2) int account,\n@SbeGroup("
						), RELEASED
				),
				error("int account", "int cannot hold null, but the field can be absent; use Integer")
		);
	}

	@Test
	void aFieldAddedAtTheBaselineIsNeverAbsent() {
		assertWarnings(
				EVOLVING, inMessage(ORDER_V1.replace("int quantity", "Integer quantity"), RELEASED),
				error("Integer quantity", "Integer is boxed although the field is never absent")
		);
	}

	@Test
	void aFieldAtItsGroupsVersionIsNeverAbsent() {
		// The group's guard fires first: an entry exists only where the field does.
		assertWarnings(
				schema(2),
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
				schema(3),
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
				schema(2),
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
	void aFieldOfAFaceWithoutANullValueMayBeOptional() {
		// SBE allows it and sbe-tool accepts it; null is a binding's to represent.
		assertClean(
				inMessage(
						"""
								@SbeField(id = 1, presence = OPTIONAL) Set<Flags> flags,
								@SbeField(id = 2, type = Symbol.class, presence = OPTIONAL) String symbol,
								@SbeField(id = 3, type = Samples.class) int[] samples,
								@SbeField(id = 4, presence = OPTIONAL) Decimal price""",
						FLAGS, SYMBOL, DECIMAL,
						"@SbeType(primitiveType = INT32, length = 4, presence = OPTIONAL) final class Samples {}"
				)
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
									public Long toWire(BigDecimal value, BindingContext context) { return value.movePointRight(2).longValueExact(); }
									public BigDecimal fromWire(Long wire, BindingContext context) { return BigDecimal.valueOf(wire, 2); }
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
									public byte[] toWire(Colour value, BindingContext context) { return new byte[] {(byte) value.red(), (byte) value.green(), (byte) value.blue()}; }
									public Colour fromWire(byte[] wire, BindingContext context) { return new Colour(wire[0], wire[1], wire[2]); }
								}"""
				)
		);
	}

	@Test
	void anEnumOrASetBindsOverItsFace() {
		assertErrors(
				inMessage(
						"@SbeField(id = 1, type = Side.class, binding = SideBinding.class) boolean buy,\n@SbeField(id = 2, type = Flags.class, binding = FlagsBinding.class) boolean firm",
						SIDE, FLAGS,
						"""
								final class SideBinding implements TypeBinding.OfShort<Boolean> {
									public short toWire(Boolean value, BindingContext context) { return value ? (short) 1 : 0; }
									public Boolean fromWire(short wire, BindingContext context) { return wire == 1; }
								}
								final class FlagsBinding implements TypeBinding<Boolean, Flags> {
									public Flags toWire(Boolean value, BindingContext context) { return Flags.firm; }
									public Boolean fromWire(Flags wire, BindingContext context) { return true; }
								}"""
				),
				error(
						"boolean buy",
						"SideBinding binds the wire as short, but the face of Side is Side; implement TypeBinding over Side"
				),
				error(
						"boolean firm",
						"FlagsBinding binds the wire as Flags, but the face of Flags is Set<Flags>; implement TypeBinding over Set<Flags>"
				)
		);
		assertClean(
				inMessage(
						"@SbeField(id = 1, type = Side.class, binding = SideBinding.class) boolean buy,\n@SbeField(id = 2, type = Flags.class, binding = FlagsBinding.class) boolean firm",
						SIDE, FLAGS,
						"""
								final class SideBinding implements TypeBinding<Boolean, Side> {
									public Side toWire(Boolean value, BindingContext context) { return value ? Side.Buy : Side.Sell; }
									public Boolean fromWire(Side wire, BindingContext context) { return wire == Side.Buy; }
								}
								final class FlagsBinding implements TypeBinding<Boolean, Set<Flags>> {
									public Set<Flags> toWire(Boolean value, BindingContext context) { return value ? EnumSet.of(Flags.firm) : EnumSet.noneOf(Flags.class); }
									public Boolean fromWire(Set<Flags> wire, BindingContext context) { return wire.contains(Flags.firm); }
								}"""
				)
		);
	}

	@Test
	void varDataAndAGroupBindOverTheirFaces() {
		assertErrors(
				inMessage(
						"""
								@SbeGroup(id = 1, binding = LegsBinding.class) Map<Integer, Leg> legs,
								@SbeData(id = 2, type = VarStringEncoding.class, binding = NoteBinding.class) Note note""",
						LEG, "record Note(String text) {}",
						"""
								final class LegsBinding implements TypeBinding<Map<Integer, Leg>, Set<Leg>> {
									public Set<Leg> toWire(Map<Integer, Leg> value, BindingContext context) { return Set.copyOf(value.values()); }
									public Map<Integer, Leg> fromWire(Set<Leg> wire, BindingContext context) { return Map.of(); }
								}
								final class NoteBinding implements TypeBinding<Note, byte[]> {
									public byte[] toWire(Note value, BindingContext context) { return value.text().getBytes(); }
									public Note fromWire(byte[] wire, BindingContext context) { return new Note(new String(wire)); }
								}"""
				),
				error("Map<Integer, Leg> legs", "a group's binding must bind a List of a record"),
				error(
						"Note note",
						"NoteBinding binds the wire as byte[], but the face of varStringEncoding is String; implement TypeBinding over String"
				)
		);
		assertClean(
				inMessage(
						"""
								@SbeGroup(id = 1, binding = LegsBinding.class) Map<Integer, Leg> legs,
								@SbeData(id = 2, type = VarStringEncoding.class, binding = NoteBinding.class) Note note""",
						LEG, "record Note(String text) {}",
						"""
								final class LegsBinding implements TypeBinding<Map<Integer, Leg>, List<Leg>> {
									public List<Leg> toWire(Map<Integer, Leg> value, BindingContext context) { return List.copyOf(value.values()); }
									public Map<Integer, Leg> fromWire(List<Leg> wire, BindingContext context) { return Map.of(); }
								}
								final class NoteBinding implements TypeBinding<Note, String> {
									public String toWire(Note value, BindingContext context) { return value.text(); }
									public Note fromWire(String wire, BindingContext context) { return new Note(wire); }
								}"""
				)
		);
	}

	@Test
	void aCompositesInlineMemberAndRefBindOverTheirFaces() {
		assertErrors(
				inMessage(
						"@SbeField(id = 1, type = Quote.class) Quote quote", SIDE, CENTS_BINDING,
						"""
								@SbeComposite record Quote(
								@SbeType(primitiveType = INT32, binding = CentsBinding.class) BigDecimal bid,
								@SbeRef(value = Side.class, binding = CentsBinding.class) BigDecimal side
								) {}"""
				),
				error(
						"BigDecimal bid",
						"CentsBinding binds the wire as long, but the face of bid is int; implement TypeBinding.OfInt"
				),
				error(
						"BigDecimal side",
						"CentsBinding binds the wire as long, but the face of Side is Side; implement TypeBinding over Side"
				)
		);
		assertClean(
				inMessage(
						"@SbeField(id = 1, type = Quote.class) Quote quote", CENTS_BINDING,
						"""
								@SbeComposite record Quote(
								@SbeType(primitiveType = INT64, binding = CentsBinding.class) BigDecimal bid,
								@SbeRef(value = Cents.class, binding = CentsBinding.class) BigDecimal ask
								) {}
								@SbeType(primitiveType = INT64) final class Cents {}"""
				)
		);
	}

	@Test
	void aDeclarationOrAnUnmappedMemberTakesNoBinding() {
		assertErrors(
				inMessage(
						"@SbeField(id = 1, type = Cents.class) long price,\n@SbeField(id = 2, type = Quote.class) Quote quote",
						CENTS_BINDING,
						"@SbeType(primitiveType = INT64, binding = CentsBinding.class) final class Cents {}",
						"""
								@SbeComposite(layout = {"bid", "ask"}, unmapped = @SbeType(name = "ask", primitiveType = INT64, binding = CentsBinding.class)) record Quote(@SbeType(primitiveType = INT64) long bid) {}"""
				),
				error(
						"final class Cents {}",
						"@SbeType on a class declares a type; a binding goes on a component that uses it"
				),
				error("record Quote(", "an unmapped member has no component to bind")
		);
	}

	@Test
	void timeUnitIsDeprecatedAsSbeXsdDeprecatesIt() {
		// javac reports a deprecated member's use when it compiles, not under
		// -proc:only; the member still works.
		String source = inMessage("@SbeField(id = 1, primitiveType = UINT64, timeUnit = \"nanosecond\") long sentAt");
		Javac.Result result = Javac.compile(
				List.of(
						Javac.unit("mistakes/package-info.java", schema(0)),
						Javac.unit("mistakes/Order.java", source)
				),
				new SbeProcessor(), List.of("-Xlint:deprecation")
		);

		assertThat(result.errors()).isEmpty();
		// Deprecation is one of javac's mandatory warnings, a kind of its own.
		List<Diagnostic<? extends JavaFileObject>> deprecations = result.diagnostics().stream()
				.filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.MANDATORY_WARNING)
				.toList();
		// Once for each place javac copies the component's annotation to.
		assertThat(located(source, deprecations)).isNotEmpty().containsOnly(
				located(source, error("long sentAt", "timeUnit() in net.concini.sbebuddy.SbeField has been deprecated"))
		);
		assertThat(schemaXml(result)).contains("timeUnit=\"nanosecond\"");
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
										@SbeType(primitiveType = UINT8, presence = OPTIONAL) short scale
								) {}"""
				),
				error("int mantissa", "int is not the face of mantissa, which is long"),
				error("short scale", "short cannot hold null, but the member can be absent; use Short")
		);
	}

	@Test
	void aConstantMemberNeedsAValueOrAValueRef() {
		assertErrors(
				inMessage(
						"@SbeField(id = 1) Price price",
						"""
								@SbeComposite
								record Price(
										@SbeType(primitiveType = INT64) long mantissa,
										@SbeType(primitiveType = CHAR, presence = CONSTANT) byte unit
								) {}"""
				),
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
	void aCompositeFieldsComponentIsTheRecord() {
		assertErrors(
				inMessage(
						"""
								@SbeField(id = 1, type = Decimal.class) long plain,
								@SbeField(id = 2, type = Decimal.class, binding = CentsBinding.class) BigDecimal bound""",
						DECIMAL, CENTS_BINDING
				),
				error("long plain", "Decimal is a composite; use Decimal"),
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
									public Decimal toWire(BigDecimal value, BindingContext context) { return new Decimal(value.unscaledValue().longValueExact()); }
									public BigDecimal fromWire(Decimal wire, BindingContext context) { return BigDecimal.valueOf(wire.mantissa()); }
								}"""
				)
		);
	}

	@Test
	void aCompositeCannotBeExtendedInALaterVersion() {
		assertErrors(
				schema(1),
				inMessage(
						"@SbeField(id = 1) Pad pad",
						"""
								@SbeComposite record Pad(
								@SbeType(primitiveType = INT32) int first,
								@SbeType(primitiveType = INT32, sinceVersion = 1) int second
								) {}"""
				),
				error(
						"int second",
						"a composite cannot be extended in a later version; declare a new composite and append a field of it"
				)
		);
	}

	@Test
	void aCompositesRefCannotBeNewerThanItsComposite() {
		assertErrors(
				schema(1),
				inMessage(
						"@SbeField(id = 1) Pad pad",
						"@SbeType(primitiveType = INT32) final class Qty {}",
						"""
								@SbeComposite record Pad(
								@SbeType(primitiveType = INT32) int first,
								@SbeRef(value = Qty.class, sinceVersion = 1) int second
								) {}"""
				),
				error(
						"int second",
						"a composite cannot be extended in a later version; declare a new composite and append a field of it"
				)
		);
	}

	@Test
	void aCompositeArrivingInALaterVersionBringsItsMembersAlong() {
		assertClean(
				schema(1),
				inMessage(
						"@SbeField(id = 1, sinceVersion = 1) Pad pad",
						"""
								@SbeComposite(sinceVersion = 1) record Pad(
								@SbeType(primitiveType = INT32, sinceVersion = 1) int first
								) {}"""
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
		Javac.Result result = compile(
				EVOLVING.replace("version = 2", "version = 0"),
				inMessage(ORDER_V1.replace(", sinceVersion = 1", ""), RELEASED)
		);

		assertThat(result.errors()).extracting(error -> error.getMessage(null)).containsExactlyInAnyOrder(
				"the baseline is version 1, above the schema's 0", "the baseline has sinceVersion=\"1\" here, not \"0\""
		);
		assertThat(result.errors().get(0).getSource().getName()).endsWith("package-info.java");
		assertThat(result.outputs()).isEmpty();
	}

	@Test
	void aHeadersStandardMemberKeepsItsWireName() {
		assertErrors(
				framedIn("Frame"),
				inMessage(
						"@SbeField(id = 1) int qty",
						"""
								@SbeComposite record Frame(
								@SbeType(name = "length", primitiveType = UINT16) int blockLength,
								@SbeType(primitiveType = UINT16) int templateId,
								@SbeType(primitiveType = UINT16) int schemaId,
								@SbeType(primitiveType = UINT16) int version
								) implements MessageHeader {}"""
				),
				error("int blockLength", "a header's blockLength keeps its wire name, not \"length\"")
		);
	}

	@Test
	void aHeaderCannotChangeInALaterVersion() {
		assertErrors(
				framedIn("Frame", 1),
				inMessage(
						"@SbeField(id = 1) int qty",
						"""
								@SbeComposite record Frame(
								@SbeType(primitiveType = UINT16) int blockLength,
								@SbeType(primitiveType = UINT16) int templateId,
								@SbeType(primitiveType = UINT16) int schemaId,
								@SbeType(primitiveType = UINT16) int version,
								@SbeType(primitiveType = UINT32, sinceVersion = 1) long sequence
								) implements MessageHeader {}"""
				),
				error("long sequence", "a header cannot change: a reader needs its length before its version")
		);
	}

	@Test
	void aHeaderThatIsNoMessageHeaderIsJavacsError() {
		Javac.Result result = compile(
				framedIn("Frame"),
				inMessage(
						"@SbeField(id = 1) int qty",
						"@SbeComposite record Frame(@SbeType(primitiveType = UINT16) int blockLength) {}"
				)
		);

		assertThat(result.errors()).singleElement().satisfies(error -> {
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
	void aDataComponentIsTheFaceOfItsEncodingsVarData() {
		assertErrors(
				inMessage(
						"""
								@SbeData(id = 1, type = VarDataEncoding.class) String blob,
								@SbeData(id = 2, type = VarStringEncoding.class) byte[] note,
								@SbeData(id = 3, type = VarAsciiEncoding.class) long count"""
				),
				error("String blob", "String is not the face of varDataEncoding, which is byte[]"),
				error("byte[] note", "byte[] is not the face of varStringEncoding, which is String"),
				error("long count", "long is not the face of varAsciiEncoding, which is String")
		);
		assertClean(
				inMessage(
						"""
								@SbeData(id = 1, type = VarDataEncoding.class) byte[] blob,
								@SbeData(id = 2, type = VarStringEncoding.class) String note"""
				)
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

	// ---- unions

	@Test
	void aUnionIsASealedInterface() {
		assertErrors(
				WITH_CODECS,
				HEADER + """
						@SbeUnion interface Orders {}
						@SbeMessage(id = 1) record Order(@SbeField(id = 1) long orderId) implements Orders {}
						""",
				error("interface Orders", "@SbeUnion goes on a sealed interface")
		);
	}

	@Test
	void aUnionIsNotGeneric() {
		assertErrors(
				WITH_CODECS,
				HEADER + """
						@SbeUnion sealed interface Orders<T> permits Order {}
						@SbeMessage(id = 1) record Order(@SbeField(id = 1) long orderId) implements Orders<String> {}
						""",
				error("interface Orders<T>", "a union is not generic: its codec decodes to one type")
		);
	}

	@Test
	void aUnionNeedsCodecs() {
		assertErrors(
				HEADER + """
						@SbeUnion sealed interface Orders permits Order {}
						@SbeMessage(id = 1) record Order(@SbeField(id = 1) long orderId) implements Orders {}
						""",
				error("interface Orders", "a union needs codecs, and codecs = false on @SbeSchema generates none")
		);
	}

	@Test
	void everySubtypeOfAUnionIsAMessageOrASealedInterface() {
		assertErrors(
				WITH_CODECS,
				HEADER + """
						@SbeUnion sealed interface Orders permits Order, Note, Batch, Open {}
						@SbeMessage(id = 1) record Order(@SbeField(id = 1) long orderId) implements Orders {}
						record Note(long orderId) implements Orders {}
						final class Batch implements Orders {}
						non-sealed interface Open extends Orders {}
						""",
				error("record Note", "Note is in the union Orders but carries no @SbeMessage"),
				error(
						"class Batch",
						"Batch is in the union Orders but is neither an @SbeMessage record nor a sealed interface"
				),
				error(
						"interface Open",
						"Open is in the union Orders and non-sealed, which leaves the union open to types its codec "
								+ "cannot know"
				)
		);
	}

	@Test
	void aSubtypeWrongInTwoUnionsIsReportedOnce() {
		assertErrors(
				WITH_CODECS,
				HEADER + """
						@SbeUnion sealed interface Orders permits Order, Note {}
						@SbeUnion sealed interface Notes permits Note {}
						@SbeMessage(id = 1) record Order(@SbeField(id = 1) long orderId) implements Orders {}
						record Note(long orderId) implements Orders, Notes {}
						""",
				error("record Note", "Note is in the union Notes but carries no @SbeMessage")
		);
	}

	@Test
	void anUnannotatedSealedInterfaceFlattensIntoTheUnionAboveIt() {
		Javac.Result result = assertClean(
				WITH_CODECS,
				HEADER + """
						@SbeUnion sealed interface Ingress permits Orders, Logon {}
						sealed interface Orders extends Ingress permits Order, Cancel {}
						@SbeMessage(id = 1) record Order(@SbeField(id = 1) long orderId) implements Orders {}
						@SbeMessage(id = 2) record Cancel(@SbeField(id = 1) long orderId) implements Orders {}
						@SbeMessage(id = 3) record Logon(@SbeField(id = 1) int sessionId) implements Ingress {}
						"""
		);

		assertThat(result.outputs()).containsKey("mistakes/IngressCodec.java").doesNotContainKey(
				"mistakes/OrdersCodec.java"
		);
		assertThat(result.outputs().get("mistakes/IngressCodec.java"))
				.contains(
						"case mistakes.Order member ->", "case mistakes.Cancel member ->",
						"case mistakes.Logon member ->"
				);
	}

	@Test
	void aUnionAndAMessageCannotShareACodecsName() {
		assertErrors(
				WITH_CODECS,
				HEADER + """
						interface Feed {
						@SbeUnion sealed interface Order permits Placed {}
						}
						@SbeMessage(id = 1) record Placed(@SbeField(id = 1) long orderId) implements Feed.Order {}
						@SbeMessage(id = 2) record Order(@SbeField(id = 1) long orderId) {}
						""",
				error(
						"@SbeUnion sealed interface Order",
						"OrderCodec would be generated twice: for the message mistakes.Order and the union mistakes.Feed.Order"
				),
				error(
						"@SbeMessage(id = 2)",
						"OrderCodec would be generated twice: for the message mistakes.Order and the union mistakes.Feed.Order"
				)
		);
	}

	@Test
	void aUnionOverAMessageWithoutACodecIsAProblem() {
		assertErrors(
				WITH_CODECS,
				HEADER + """
						@SbeType(primitiveType = CHAR, length = 8, characterEncoding = "x-klingon") final class Name {}
						@SbeUnion sealed interface Orders permits Order {}
						@SbeMessage(id = 1) record Order(@SbeField(id = 1, type = Name.class) String name) implements Orders {}
						""",
				error(
						"@SbeMessage(id = 1)",
						"no codec for text in x-klingon: the JDK knows no such encoding; set codecs = false on @SbeSchema"
				),
				error("@SbeUnion sealed interface Orders", "no codec for Orders: its message Order has none")
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
				public long toWire(BigDecimal value, BindingContext context) { return value.movePointRight(2).longValueExact(); }
				public BigDecimal fromWire(long wire, BindingContext context) { return BigDecimal.valueOf(wire, 2); }
			}""";

	// ---- a schema read from a resource: the annotations against the venue's XML

	@Test
	void aPackageOverItsOwnSchemaCompilesCleanAndWritesNoSchema() {
		Javac.Result result = assertClean(SCHEMA_FIRST, inMessage(ORDER, VENUE, VENUE_LEG));

		assertThat(result.outputs())
				.containsKeys(
						"mistakes/OrderCodec.java", "mistakes/CancelCodec.java", "mistakes/sbe/OrderEncoder.java",
						"mistakes/sbe/CancelEncoder.java"
				)
				.doesNotContainKey("mistakes/schema.xml");
	}

	@Test
	void anUnmappedFieldCarriesOneNoComponentDoes() {
		assertClean(
				SCHEMA_FIRST,
				message(
						"@SbeMessage(id = 1, layout = {\"orderId\", \"symbol\", \"side\", \"flags\", \"qty\", \"legs\", \"note\"}, unmapped = @SbeField(id = 5, name = \"flags\", type = Flags.class))",
						ORDER.replace("@SbeField(id = 5) Set<Flags> flags,\n", ""), VENUE, VENUE_LEG
				)
		);
	}

	@Test
	void aResourceNotOnTheClassPathIsAProblemOnTheSchema() {
		Javac.Result result = compile(
				SCHEMA_FIRST.replace("venue.xml", "nowhere.xml"), inMessage(ORDER, VENUE, VENUE_LEG)
		);

		assertThat(result.errors()).hasSize(1);
		assertThat(result.errors().get(0).getMessage(null))
				.isEqualTo("no resource mistakes/nowhere.xml on the class path");
		assertThat(result.errors().get(0).getSource().getName()).endsWith("package-info.java");
		assertThat(result.outputs()).isEmpty();
	}

	@Test
	void theSchemasIdAndVersionMustAgreeWithTheResource() {
		Javac.Result result = compile(
				SCHEMA_FIRST.replace("id = 7, version = 2", "id = 1, version = 3"), inMessage(ORDER, VENUE, VENUE_LEG)
		);

		assertThat(result.errors()).extracting(diagnostic -> diagnostic.getMessage(null))
				.containsExactlyInAnyOrder(
						"the schema has id=\"7\", not \"1\"", "the schema has version=\"2\", not \"3\""
				);
		assertThat(result.outputs()).isEmpty();
	}

	@Test
	void aMessageOfTheResourceNoRecordMapsIsAProblemOnTheSchema() {
		Javac.Result result = compile(SCHEMA_FIRST, inMessage(ORDER, VENUE_TYPES, VENUE_LEG));

		assertThat(result.errors()).hasSize(1);
		assertThat(result.errors().get(0).getMessage(null))
				.isEqualTo("the schema has a message \"Cancel\" (id 2) and no record maps it");
		assertThat(result.errors().get(0).getSource().getName()).endsWith("package-info.java");
		assertThat(result.outputs()).isEmpty();
	}

	@Test
	void aRecordTheResourceLacksIsAProblem() {
		assertErrors(
				SCHEMA_FIRST,
				inMessage(
						ORDER, VENUE, VENUE_LEG, "@SbeMessage(id = 9) record Extra(@SbeField(id = 1) long orderId) {}"
				),
				error("record Extra", "the schema has no message named \"Extra\"")
		);
	}

	@Test
	void aFieldOfTheResourceNoComponentCarriesIsAProblem() {
		assertErrors(
				SCHEMA_FIRST, inMessage(ORDER.replace("@SbeField(id = 5) Set<Flags> flags,\n", ""), VENUE, VENUE_LEG),
				error(
						"@SbeMessage(id = 1)",
						"the schema's Order has a field \"flags\" no component carries; add it, or declare it unmapped"
				)
		);
	}

	@Test
	void aGroupOrVarDataOfTheResourceNoComponentCarriesIsAProblem() {
		Javac.Result result = compile(
				SCHEMA_FIRST,
				inMessage(
						ORDER.replace(
								",\n@SbeGroup(id = 6) List<Leg> legs,\n@SbeData(id = 8, type = VarStringEncoding.class) String note",
								""
						),
						VENUE, VENUE_LEG
				)
		);

		// The framing composites reach the schema only through the group and the data.
		assertThat(result.errors()).extracting(diagnostic -> diagnostic.getMessage(null))
				.containsExactlyInAnyOrder(
						"the schema's Order has a group \"legs\" no component carries",
						"the schema's Order has a data \"note\" no component carries",
						"the schema has a composite \"groupSizeEncoding\" and no declaration maps it",
						"the schema has a composite \"varStringEncoding\" and no declaration maps it"
				);
		assertThat(result.outputs()).isEmpty();
	}

	@Test
	void aComponentTheResourceLacksIsAProblem() {
		assertErrors(
				SCHEMA_FIRST,
				inMessage(
						ORDER.replace(
								"@SbeField(id = 1) long orderId,",
								"@SbeField(id = 1) long orderId,\n@SbeField(id = 9) int nowhere,"
						), VENUE, VENUE_LEG
				),
				error("int nowhere", "the schema's Order has no field named \"nowhere\"")
		);
	}

	@Test
	void aMemberWrittenAgainstTheResourceIsAProblem() {
		assertErrors(
				SCHEMA_FIRST,
				inMessage(
						ORDER.replace("sinceVersion = 1) Integer qty", "sinceVersion = 2) Integer qty")
								.replace(
										"type = Symbol.class) String symbol",
										"type = Symbol.class, presence = OPTIONAL) String symbol"
								),
						VENUE, VENUE_LEG
				),
				error("Integer qty", "the schema has sinceVersion=\"1\", not \"2\""),
				error("String symbol", "the schema has presence=\"required\", not \"optional\"")
		);
	}

	@Test
	void aDeclarationTheResourceLacksIsAProblem() {
		assertErrors(
				SCHEMA_FIRST,
				inMessage(
						ORDER, VENUE, VENUE_LEG,
						"@SbeEnum(primitiveType = UINT8) enum Extra { @SbeEnumValue(\"1\") ONE }"
				),
				error("enum Extra", "the schema has no enum named \"Extra\"")
		);
	}

	@Test
	void aTypeOfTheResourceNoDeclarationMapsIsAProblemOnTheSchema() {
		Javac.Result result = compile(
				SCHEMA_FIRST,
				inMessage(
						ORDER.replace("@SbeField(id = 5) Set<Flags> flags,\n", ""),
						VENUE.replace(VENUE_FLAGS + "\n", ""), VENUE_LEG
				)
		);

		assertThat(result.errors()).extracting(diagnostic -> diagnostic.getMessage(null))
				.containsExactlyInAnyOrder(
						"the schema has a set \"Flags\" and no declaration maps it",
						"the schema's Order has a field \"flags\" no component carries; add it, or declare it unmapped"
				);
		assertThat(result.outputs()).isEmpty();
	}

	@Test
	void anEnumConstantTheResourceLacksIsAProblemAndSoIsAValueTheEnumLacks() {
		assertErrors(
				SCHEMA_FIRST,
				inMessage(
						ORDER,
						VENUE.replace(
								VENUE_SIDE,
								"@SbeEnum(primitiveType = CHAR) enum Side { @SbeEnumValue(\"1\") BUY, @SbeEnumValue(\"3\") SHORT }"
						),
						VENUE_LEG
				),
				error("enum Side", "the schema's Side has a value named \"SELL\" and no constant maps it"),
				error("enum Side", "the schema's Side has no value named \"SHORT\"")
		);
	}

	// ---- the baseline

	@Test
	void theBaselineAtALaterVersionCompilesClean() {
		assertClean(EVOLVING, inMessage(ORDER_V1, RELEASED));
	}

	@Test
	void whatSbeAllowsCompilesClean() {
		assertClean(
				EVOLVING,
				inMessage(
						ORDER_V1.replace("long orderId", "long clOrdId")
								.replace("int quantity", "Integer quantity")
								.replace("sinceVersion = 1)", "sinceVersion = 1, presence = OPTIONAL)")
								.replace(
										"@SbeGroup(",
										"@SbeField(id = 7, sinceVersion = 2) Integer account,\n@SbeGroup("
								),
						SIDE.replace("}", ", @SbeEnumValue(value = \"X\", sinceVersion = 2) Short }"), FLAGS, LEG_V1,
						CANCEL.replace("Cancel(", "CancelOrder(")
				)
		);
	}

	@Test
	void aFieldRetiredAsUnmappedCompilesClean() {
		assertClean(
				EVOLVING,
				message(
						"""
								@SbeMessage(id = 1, layout = {"orderId", "side", "flags", "quantity", "legs"},
								unmapped = @SbeField(id = 4, name = "flags", type = Flags.class))""",
						ORDER_V1.replace("@SbeField(id = 4) Set<Flags> flags,\n", ""), RELEASED
				)
		);
	}

	@Test
	void aBaselineNotOnTheClassPathIsAProblemOnTheSchema() {
		Javac.Result result = compile(EVOLVING.replace("order-v1.xml", "nowhere.xml"), inMessage(ORDER_V1, RELEASED));

		assertThat(result.errors()).singleElement().satisfies(error -> {
			assertThat(error.getMessage(null)).isEqualTo("no baseline mistakes/nowhere.xml on the class path");
			assertThat(error.getSource().getName()).endsWith("package-info.java");
		});
		assertThat(result.outputs()).isEmpty();
	}

	@Test
	void theSchemaKeepsTheBaselinesId() {
		Javac.Result result = compile(EVOLVING.replace("id = 1", "id = 3"), inMessage(ORDER_V1, RELEASED));

		assertThat(result.errors()).singleElement().satisfies(error -> {
			assertThat(error.getMessage(null)).isEqualTo("the baseline is schema 1, not 3; a schema keeps its id");
			assertThat(error.getSource().getName()).endsWith("package-info.java");
		});
		assertThat(result.outputs()).isEmpty();
	}

	@Test
	void aMessageOfTheBaselineThatIsGoneIsAProblemOnTheSchema() {
		Javac.Result result = compile(EVOLVING, inMessage(ORDER_V1, SIDE, FLAGS, LEG_V1));

		assertThat(result.errors()).singleElement().satisfies(error -> {
			assertThat(error.getMessage(null)).isEqualTo(
					"the baseline has a message \"Cancel\" (id 2) and the schema none with that id; a message stays, deprecated if it is retired"
			);
			assertThat(error.getSource().getName()).endsWith("package-info.java");
		});
		assertThat(result.outputs()).isEmpty();
	}

	@Test
	void aMemberOfTheBaselineThatIsGoneIsAProblemOnTheRecord() {
		assertErrors(
				EVOLVING,
				inMessage(
						ORDER_V1.replace(",\n@SbeField(id = 2, sinceVersion = 1) int quantity", "")
								.replace(",\n@SbeGroup(id = 5) List<Leg> legs", ""),
						RELEASED
				),
				error(
						"@SbeMessage(id = 1)",
						"the baseline's Order has a field \"quantity\" (id 2) the schema lacks; a field stays, unmapped if the record retires it"
				),
				error(
						"@SbeMessage(id = 1)",
						"the baseline's Order has a group \"legs\" (id 5) the schema lacks; a group stays"
				)
		);
	}

	@Test
	void anAppendedFieldStatesAVersionAboveTheBaselines() {
		assertErrors(
				EVOLVING,
				inMessage(
						ORDER_V1.replace(
								"@SbeGroup(",
								"@SbeField(id = 7, sinceVersion = 1) int account,\n@SbeGroup("
						), RELEASED
				),
				error("int account", "a field the baseline lacks needs a sinceVersion above the baseline's version 1")
		);
	}

	@Test
	void aFieldKeepsItsIdAndItsType() {
		assertErrors(
				EVOLVING,
				inMessage(
						ORDER_V1.replace("@SbeField(id = 1) long orderId", "@SbeField(id = 11) long orderId")
								.replace("@SbeField(id = 3) Side side", "@SbeField(id = 3) int side"),
						RELEASED.replace("int legId", "long legId")
				),
				error(
						"@SbeField(id = 11) long orderId",
						"the baseline has a field \"orderId\" (id 1) here, not id 11; the id is its identity, and a field that means something else is appended as a new one"
				),
				error("int side", "the type differs from the baseline's: enum \"Side\", not int32"),
				error("record Leg", "the type differs from the baseline's: int32, not int64")
		);
	}

	@Test
	void anEnumValueStaysAndOneAddedStatesItsVersion() {
		assertErrors(
				EVOLVING,
				inMessage(
						ORDER_V1,
						SIDE.replace("@SbeEnumValue(\"S\") Sell", "@SbeEnumValue(\"X\") Short"), FLAGS, LEG_V1, CANCEL
				),
				error(
						"Side side",
						"the type differs from the baseline's: enum \"Side\" has the value \"S\" (Sell), which the schema's lacks; values stay, deprecated if they are retired"
				)
		);
		assertErrors(
				EVOLVING,
				inMessage(ORDER_V1, SIDE.replace("}", ", @SbeEnumValue(\"X\") Short }"), FLAGS, LEG_V1, CANCEL),
				error(
						"Side side",
						"the type differs from the baseline's: enum \"Side\" lacks the value \"X\" (Short), which needs a sinceVersion above the baseline's version 1"
				)
		);
	}

	/**
	 * The schema of the evolution snippets at version 2, against version 1,
	 * {@code order-v1.xml} beside this test's resources.
	 */
	private static final String EVOLVING = """
			@SbeSchema(id = 1, version = 2, baseline = "order-v1.xml", codecs = false)
			package mistakes;

			import net.concini.sbebuddy.SbeSchema;
			""";

	/** The components of {@code Order} as version 1 released them. */
	private static final String ORDER_V1 = """
			@SbeField(id = 1) long orderId,
			@SbeField(id = 3) Side side,
			@SbeField(id = 4) Set<Flags> flags,
			@SbeField(id = 2, sinceVersion = 1) int quantity,
			@SbeGroup(id = 5) List<Leg> legs""";

	private static final String LEG_V1 = "record Leg(@SbeField(id = 6) int legId) {}";

	private static final String CANCEL = "@SbeMessage(id = 2) record Cancel(@SbeField(id = 1) long orderId) {}";

	/** Everything else version 1 released beside {@code Order}. */
	private static final String RELEASED = SIDE + "\n" + FLAGS + "\n" + LEG_V1 + "\n" + CANCEL;

	/** The venue's schema, {@code venue.xml} beside this test's resources. */
	private static final String SCHEMA_FIRST = """
			@SbeSchema(id = 7, version = 2, resource = "venue.xml")
			package mistakes;

			import net.concini.sbebuddy.SbeSchema;
			""";

	/** The components of {@code Order} that map every member of the venue's. */
	private static final String ORDER = """
			@SbeField(id = 1) long orderId,
			@SbeField(id = 3, type = Symbol.class) String symbol,
			@SbeField(id = 4) Side side,
			@SbeField(id = 5) Set<Flags> flags,
			@SbeField(id = 2, presence = OPTIONAL, sinceVersion = 1) Integer qty,
			@SbeGroup(id = 6) List<Leg> legs,
			@SbeData(id = 8, type = VarStringEncoding.class) String note""";

	private static final String VENUE_SIDE = "@SbeEnum(primitiveType = CHAR) enum Side { @SbeEnumValue(\"1\") BUY, @SbeEnumValue(\"2\") SELL }";

	private static final String VENUE_FLAGS = "@SbeSet(primitiveType = UINT8) enum Flags { @SbeChoice(value = 0, name = \"urgent\") URGENT }";

	/** The venue's declared types, which its messages reach. */
	private static final String VENUE_TYPES = """
			@SbeType(primitiveType = CHAR, length = 6) final class Symbol { private Symbol() {} }
			""" + VENUE_SIDE + "\n" + VENUE_FLAGS + "\n";

	/** The venue's types and its second message, beside the record of Order. */
	private static final String VENUE = VENUE_TYPES
			+ "@SbeMessage(id = 2) record Cancel(@SbeField(id = 1) long orderId) {}\n";

	private static final String VENUE_LEG = "record Leg(@SbeField(id = 7) int legId) {}";

	// ---- the snippet around the mistake

	private static final String HEADER = """
			package mistakes;

			import static net.concini.sbebuddy.Presence.*;
			import static net.concini.sbebuddy.PrimitiveType.*;

			import java.math.*;
			import java.util.*;

			import net.concini.sbebuddy.*;

			""";

	/** The schema of the snippets, at a version, without codecs. */
	private static String schema(int version) {
		return """
				@SbeSchema(id = 1, version = %d, codecs = false)
				package mistakes;

				import net.concini.sbebuddy.SbeSchema;
				""".formatted(version);
	}

	/** The schema of the snippets with codecs, which a union needs. */
	private static final String WITH_CODECS = """
			@SbeSchema(id = 1, version = 0)
			package mistakes;

			import net.concini.sbebuddy.SbeSchema;
			""";

	/** The schema of the snippets, framed in a header of its own. */
	private static String framedIn(String header) {
		return framedIn(header, 0);
	}

	private static String framedIn(String header, int version) {
		return """
				@SbeSchema(id = 1, version = %d, headerType = %s.class, codecs = false)
				package mistakes;

				import net.concini.sbebuddy.SbeSchema;
				""".formatted(version, header);
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
		assertErrors(schema(0), source, expected);
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
		assertWarnings(schema(0), source, expected);
	}

	/** No error, exactly these warnings, and everything written. */
	private static void assertWarnings(String packageInfo, String source, Expected... expected) {
		Javac.Result result = compile(packageInfo, source);

		assertThat(located(source, result.errors())).isEmpty();
		assertThat(located(source, result.warnings())).containsExactlyInAnyOrder(located(source, expected));
		assertThat(result.outputs()).isNotEmpty();
	}

	private static Javac.Result assertClean(String source) {
		return assertClean(schema(0), source);
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
