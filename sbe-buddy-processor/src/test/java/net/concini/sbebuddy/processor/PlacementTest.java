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
 * and that nothing was written; the rules themselves are tested in the
 * generator. Beside them, a declared type resolved across a package boundary.
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

				import java.util.List;

				import net.concini.sbebuddy.SbeField;
				import net.concini.sbebuddy.SbeGroup;
				import net.concini.sbebuddy.SbeMessage;

				@SbeMessage(id = 1)
				record Order(
						@SbeField(id = 1) long orderId,
						@SbeGroup(id = 2) List<Leg> legs
				) {

					record Leg(
							@SbeField(id = 3) long instrumentId
					) {
					}
				}
				""";

		Javac.Result result = compile(source);

		assertOnlyError(
				result, source, "@SbeMessage(id = 1)", "no codec for a group yet; set codecs = false on @SbeSchema"
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

		// Symbol is an array, which the codec gains in increment 11.
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
