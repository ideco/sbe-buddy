package net.concini.sbebuddy.processor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

import org.junit.jupiter.api.Test;

import net.concini.sbebuddy.generator.SchemaXmlAssert;

/**
 * Where a rule reaches the user. One snippet per layer, discovery, {@code
 * Mapping}, {@code Generator.validate}, sbe-tool as the backstop and the codec
 * emitter, asserting that the error lands on the element carrying the mistake
 * and that nothing was written, and one warning, placed the same way with
 * everything written; the rules themselves are tested in the generator. Beside
 * them, a declared type resolved across a package boundary.
 */
final class PlacementTest {

	private static final String PACKAGE_INFO = """
			@SbeSchema(id = 1, version = 0)
			package placement;

			import net.concini.sbebuddy.SbeSchema;
			""";

	@Test
	void discoveryNamesTheComponentWhoseClassDeclaresNothing() {
		String source = """
				package placement;

				import net.concini.sbebuddy.SbeField;
				import net.concini.sbebuddy.SbeMessage;

				final class NotADeclaration {
				}

				@SbeMessage(id = 1)
				record Order(
						@SbeField(id = 1, type = NotADeclaration.class) long price
				) {
				}
				""";

		Javac.Result result = compile(source);

		assertOnlyError(
				result, source, "long price",
				"placement.NotADeclaration carries no @SbeType, @SbeComposite, @SbeEnum or @SbeSet"
		);
	}

	@Test
	void discoveryNamesTheConstantCarryingBothAnnotations() {
		String source = """
				package placement;

				import static net.concini.sbebuddy.PrimitiveType.UINT8;

				import net.concini.sbebuddy.SbeEnum;
				import net.concini.sbebuddy.SbeEnumValue;
				import net.concini.sbebuddy.SbeField;
				import net.concini.sbebuddy.SbeMessage;
				import net.concini.sbebuddy.UnknownValue;

				@SbeEnum(primitiveType = UINT8)
				enum Side {

					@SbeEnumValue("1")
					Buy,

					@SbeEnumValue("2") @UnknownValue Both
				}

				@SbeMessage(id = 1)
				record Order(
						@SbeField(id = 1) Side side
				) {
				}
				""";

		Javac.Result result = compile(source);

		assertOnlyError(result, source, "Both", "a constant carries @SbeEnumValue or @UnknownValue, not both");
	}

	@Test
	void discoveryNamesTheSecondUnknownValue() {
		String source = """
				package placement;

				import static net.concini.sbebuddy.PrimitiveType.UINT8;

				import net.concini.sbebuddy.SbeEnum;
				import net.concini.sbebuddy.SbeEnumValue;
				import net.concini.sbebuddy.SbeField;
				import net.concini.sbebuddy.SbeMessage;
				import net.concini.sbebuddy.UnknownValue;

				@SbeEnum(primitiveType = UINT8)
				enum Side {

					@SbeEnumValue("1")
					Buy,

					@UnknownValue
					Other,

					@UnknownValue Twice
				}

				@SbeMessage(id = 1)
				record Order(
						@SbeField(id = 1) Side side
				) {
				}
				""";

		Javac.Result result = compile(source);

		assertOnlyError(result, source, "Twice", "an enum designates one unknown value, and Other already is");
	}

	@Test
	void discoveryNamesTheUnknownValueOnASet() {
		String source = """
				package placement;

				import static net.concini.sbebuddy.PrimitiveType.UINT8;

				import java.util.Set;

				import net.concini.sbebuddy.SbeChoice;
				import net.concini.sbebuddy.SbeField;
				import net.concini.sbebuddy.SbeMessage;
				import net.concini.sbebuddy.SbeSet;
				import net.concini.sbebuddy.UnknownValue;

				@SbeSet(primitiveType = UINT8)
				enum Flags {

					@SbeChoice(0) @UnknownValue Odd
				}

				@SbeMessage(id = 1)
				record Order(
						@SbeField(id = 1) Set<Flags> flags
				) {
				}
				""";

		Javac.Result result = compile(source);

		assertOnlyError(
				result, source, "Odd", "@UnknownValue goes on a constant of an @SbeEnum; a set has no unknown value"
		);
	}

	@Test
	void discoveryNamesTheComponentWhoseBindingIsNoTypeBinding() {
		String source = """
				package placement;

				import net.concini.sbebuddy.SbeField;
				import net.concini.sbebuddy.SbeMessage;

				final class NotABinding {
				}

				@SbeMessage(id = 1)
				record Order(
						@SbeField(id = 1, binding = NotABinding.class) long price
				) {
				}
				""";

		Javac.Result result = compile(source);

		assertOnlyError(
				result, source, "long price",
				"placement.NotABinding implements neither TypeBinding nor one of its specializations"
		);
	}

	@Test
	void discoveryNamesTheComponentWhoseBindingBindsAnotherType() {
		String source = """
				package placement;

				import java.math.BigDecimal;

				import net.concini.sbebuddy.SbeField;
				import net.concini.sbebuddy.SbeMessage;
				import net.concini.sbebuddy.TypeBinding;

				final class CentsBinding implements TypeBinding.OfLong<BigDecimal> {

					public long toWire(BigDecimal value) {
						return value.movePointRight(2).longValueExact();
					}

					public BigDecimal fromWire(long wire) {
						return BigDecimal.valueOf(wire, 2);
					}
				}

				@SbeMessage(id = 1)
				record Order(
						@SbeField(id = 1, binding = CentsBinding.class) long price
				) {
				}
				""";

		Javac.Result result = compile(source);

		assertOnlyError(result, source, "long price", "placement.CentsBinding binds java.math.BigDecimal, not long");
	}

	@Test
	void discoveryNamesTheDeclarationThatImplementsTypeBinding() {
		String source = """
				package placement;

				import static net.concini.sbebuddy.PrimitiveType.INT64;

				import java.math.BigDecimal;

				import net.concini.sbebuddy.SbeField;
				import net.concini.sbebuddy.SbeMessage;
				import net.concini.sbebuddy.SbeType;
				import net.concini.sbebuddy.TypeBinding;

				@SbeType(primitiveType = INT64) final class Cents implements TypeBinding<BigDecimal, Long> {

					public Long toWire(BigDecimal value) {
						return value.movePointRight(2).longValueExact();
					}

					public BigDecimal fromWire(Long wire) {
						return BigDecimal.valueOf(wire, 2);
					}
				}

				@SbeMessage(id = 1)
				record Order(
						@SbeField(id = 1, type = Cents.class) long price
				) {
				}
				""";

		Javac.Result result = compile(source);

		assertOnlyError(
				result, source, "final class Cents",
				"placement.Cents declares a type and implements TypeBinding; a binding is a class of its own"
		);
	}

	@Test
	void mappingNamesTheComponentWhoseTypeMapsToNothing() {
		String source = """
				package placement;

				import net.concini.sbebuddy.SbeField;
				import net.concini.sbebuddy.SbeMessage;

				@SbeMessage(id = 1)
				record Order(
						@SbeField(id = 1) char initial
				) {
				}
				""";

		Javac.Result result = compile(source);

		assertOnlyError(
				result, source, "char initial",
				"char, 16 bits where SBE's char is one byte, maps to no SBE type; give type or primitiveType"
		);
	}

	@Test
	void mappingWarnsOnTheBoxedComponentAndWritesEverything() {
		// The mirror of the error snippets: a warning is placed the same way and
		// stops nothing.
		String source = """
				package placement;

				import net.concini.sbebuddy.SbeField;
				import net.concini.sbebuddy.SbeMessage;

				@SbeMessage(id = 1)
				record Order(
						@SbeField(id = 1) Long orderId
				) {
				}
				""";

		Javac.Result result = compile(source);

		assertThat(result.errors()).isEmpty();
		assertThat(result.warnings()).hasSize(1);
		Diagnostic<? extends JavaFileObject> warning = result.warnings().get(0);
		assertThat(warning.getMessage(null)).isEqualTo("Long is boxed although the field is never absent");
		assertThat(warning.getLineNumber()).isEqualTo(lineOf(source, "Long orderId"));
		assertThat(result.outputs()).containsKeys(
				"placement/schema.xml", "placement/OrderCodec.java", "placement/sbe/OrderEncoder.java"
		);
		assertThat(result.outputs().get("placement/OrderCodec.java"))
				.contains("throw new IllegalArgumentException(\"orderId is required\");");
	}

	@Test
	void mappingNamesTheRecordWhoseLayoutIsWrong() {
		// A layout mistake has no component to land on; it lands on the annotation
		// that holds the layout.
		String source = """
				package placement;

				import net.concini.sbebuddy.SbeField;
				import net.concini.sbebuddy.SbeMessage;

				@SbeMessage(id = 1, layout = {"orderId", "prize"})
				record Order(
						@SbeField(id = 1) long orderId
				) {
				}
				""";

		Javac.Result result = compile(source);

		assertOnlyError(result, source, "@SbeMessage(id = 1, layout", "the layout names nothing called \"prize\"");
	}

	@Test
	void validationNamesTheSecondComponentOfTheClashingPair() {
		String source = """
				package placement;

				import net.concini.sbebuddy.SbeField;
				import net.concini.sbebuddy.SbeMessage;

				@SbeMessage(id = 1)
				record Order(
						@SbeField(id = 1) long orderId,
						@SbeField(id = 1) long accountId
				) {
				}
				""";

		Javac.Result result = compile(source);

		assertOnlyError(result, source, "long accountId", "two members have id 1");
	}

	@Test
	void theCodecNamesTheMessageAndNothingIsWritten() {
		// sbe-tool accepts the schema, so the flyweights were generated when the
		// codec refused; none of them may have reached the Filer.
		String source = """
				package placement;

				import static net.concini.sbebuddy.PrimitiveType.CHAR;

				import net.concini.sbebuddy.SbeField;
				import net.concini.sbebuddy.SbeMessage;
				import net.concini.sbebuddy.SbeType;

				@SbeMessage(id = 1)
				record Order(
						@SbeField(id = 1) long orderId,
						@SbeField(id = 2, type = Name.class) String name
				) {
				}

				@SbeType(primitiveType = CHAR, length = 8, characterEncoding = "UTF-8")
				final class Name {
				}
				""";

		Javac.Result result = compile(source);

		assertOnlyError(
				result, source, "@SbeMessage(id = 1)",
				"no codec for a string in UTF-8 yet; set codecs = false on @SbeSchema"
		);
	}

	@Test
	void sbeToolNamesTheSchemaPackage() {
		// Two constants with one value: no rule of ours, and sbe-tool's warning.
		String source = """
				package placement;

				import static net.concini.sbebuddy.PrimitiveType.CHAR;

				import net.concini.sbebuddy.SbeEnum;
				import net.concini.sbebuddy.SbeEnumValue;
				import net.concini.sbebuddy.SbeField;
				import net.concini.sbebuddy.SbeMessage;

				@SbeEnum(primitiveType = CHAR)
				enum Side {
					@SbeEnumValue("B") BUY, @SbeEnumValue("B") SELL
				}

				@SbeMessage(id = 1)
				record Order(
						@SbeField(id = 1) Side side
				) {
				}
				""";

		Javac.Result result = compile(source);

		assertOnlyError(
				result, PACKAGE_INFO, "@SbeSchema(id = 1, version = 0)",
				"WARNING: at <types><enum name=\"Side\"> validValue already exists for value: 66"
		);
	}

	@Test
	void aDeclaredTypeResolvesAcrossAPackageBoundary() {
		String shared = """
				package shared;

				import static net.concini.sbebuddy.PrimitiveType.CHAR;

				import net.concini.sbebuddy.SbeType;

				@SbeType(primitiveType = CHAR, length = 6)
				public final class Symbol {
				}
				""";
		String source = """
				package placement;

				import net.concini.sbebuddy.SbeField;
				import net.concini.sbebuddy.SbeMessage;

				import shared.Symbol;

				@SbeMessage(id = 1)
				record Order(
						@SbeField(id = 1, type = Symbol.class) String symbol
				) {
				}
				""";

		// Symbol is an array, which the codec gains in increment 10.
		String packageInfo = """
				@SbeSchema(id = 1, version = 0, codecs = false)
				package placement;

				import net.concini.sbebuddy.SbeSchema;
				""";

		Javac.Result result = Javac.compile(
				List.of(
						Javac.unit("placement/package-info.java", packageInfo),
						Javac.unit("placement/Source.java", source),
						Javac.unit("shared/Symbol.java", shared)
				), new SbeProcessor()
		);

		assertThat(result.errors()).isEmpty();
		assertThat(result.outputs()).doesNotContainKey("shared/schema.xml");
		SchemaXmlAssert.assertThat(result.outputs().get("placement/schema.xml")).matches("""
				<?xml version="1.0" encoding="UTF-8"?>
				<sbe:messageSchema xmlns:sbe="http://fixprotocol.io/2016/sbe" package="placement" id="1" version="0">
				    <types>
				        <composite name="messageHeader">
				            <type name="blockLength" primitiveType="uint16"/>
				            <type name="templateId" primitiveType="uint16"/>
				            <type name="schemaId" primitiveType="uint16"/>
				            <type name="version" primitiveType="uint16"/>
				        </composite>
				        <type name="Symbol" primitiveType="char" length="6"/>
				    </types>
				    <sbe:message name="Order" id="1">
				        <field name="symbol" id="1" type="Symbol"/>
				    </sbe:message>
				</sbe:messageSchema>
				""");
	}

	@Test
	void aMessageOutsideASchemaPackageIsNamedWhereItStands() {
		String source = """
				package shared;

				import net.concini.sbebuddy.SbeField;
				import net.concini.sbebuddy.SbeMessage;

				@SbeMessage(id = 1)
				record Order(
						@SbeField(id = 1) long orderId
				) {
				}
				""";

		Javac.Result result = Javac
				.compile(List.of(Javac.unit("shared/Order.java", source)), new SbeProcessor());

		// The annotation is the mistake, so the error sits on it, not on the record.
		assertOnlyError(
				result, source, "@SbeMessage(id = 1)",
				"@SbeMessage in package shared, whose package-info.java carries no @SbeSchema"
		);
	}

	private static Javac.Result compile(String source) {
		return Javac.compile(
				List.of(
						Javac.unit("placement/package-info.java", PACKAGE_INFO),
						Javac.unit("placement/Source.java", source)
				), new SbeProcessor()
		);
	}

	/** The one error, its kind, the unit and the line the mistake is written on. */
	private static void assertOnlyError(Javac.Result result, String source, String mistake, String message) {
		assertThat(result.outputs()).isEmpty();
		assertThat(result.diagnostics()).hasSize(1);
		Diagnostic<? extends JavaFileObject> diagnostic = result.diagnostics().get(0);
		assertThat(diagnostic.getKind()).isEqualTo(Diagnostic.Kind.ERROR);
		assertThat(diagnostic.getMessage(null)).isEqualTo(message);
		assertThat(diagnostic.getSource()).isNotNull();
		assertThat(diagnostic.getLineNumber()).isEqualTo(lineOf(source, mistake));
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
