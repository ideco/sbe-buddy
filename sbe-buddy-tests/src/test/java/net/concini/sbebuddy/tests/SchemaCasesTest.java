package net.concini.sbebuddy.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicContainer.dynamicContainer;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.TestFactory;

import net.concini.sbebuddy.Codec;
import net.concini.sbebuddy.generator.SchemaXmlAssert;
import net.concini.sbebuddy.tests.SchemaCase.RoundTrip;

/**
 * What every schema owes, run over every {@link SchemaCase} on the classpath:
 * the schema the processor wrote into the jar is the case's oracle, and every
 * value the case lists goes to the wire and back whole. A round trip proves the
 * codec contract as a whole: {@code encodedLength} is what {@code encode}
 * writes, at an offset and touching nothing around it; {@code decodedLength}
 * and {@code lastDecodedLength} agree with it; the decoded value equals the
 * original, and encodes to the same bytes.
 */
final class SchemaCasesTest {

	/** Messages are written away from zero, so an offset dropped would show. */
	private static final int OFFSET = 16;

	private static final byte UNTOUCHED = (byte) 0xA5;

	/** Reports phrase a check as the case's description, "checks", the check's. */
	@TestFactory
	@DisplayName("checks")
	Stream<DynamicContainer> everySchema() {
		List<SchemaCase> cases = Cases.discover();
		assertThat(cases).as("schema cases on the test classpath").isNotEmpty();
		return cases.stream().map(
				aCase -> dynamicContainer(
						aCase.description(),
						Stream.concat(
								Stream.of(
										dynamicTest(
												"the schema in the jar is the oracle", () -> schemaIsTheOracle(aCase)
										)
								),
								aCase.roundTrips().stream()
										.map(
												roundTrip -> dynamicTest(
														roundTrip.description(), () -> roundTrips(roundTrip)
												)
										)
						)
				)
		);
	}

	private static void schemaIsTheOracle(SchemaCase aCase) throws IOException {
		String resource = "/" + aCase.getClass().getPackageName().replace('.', '/') + "/schema.xml";
		try (InputStream schema = aCase.getClass().getResourceAsStream(resource)) {
			assertThat(schema).as("the processor wrote %s", resource).isNotNull();
			SchemaXmlAssert.assertThat(new String(schema.readAllBytes(), StandardCharsets.UTF_8))
					.matches(aCase.oracle());
		}
	}

	private static <T> void roundTrips(RoundTrip<T> roundTrip) {
		Codec<T> codec = roundTrip.codec();
		T value = roundTrip.value();
		int length = codec.encodedLength(value);
		byte[] bytes = new byte[OFFSET + length + OFFSET];
		Arrays.fill(bytes, UNTOUCHED);
		UnsafeBuffer buffer = new UnsafeBuffer(bytes);

		assertThat(codec.encode(value, buffer, OFFSET)).as("encode returns encodedLength").isEqualTo(length);
		assertThat(Arrays.copyOfRange(bytes, 0, OFFSET)).as("the bytes before the offset").containsOnly(UNTOUCHED);
		assertThat(Arrays.copyOfRange(bytes, OFFSET + length, bytes.length)).as("the bytes after the message")
				.containsOnly(UNTOUCHED);
		assertThat(codec.decodedLength(buffer, OFFSET)).as("decodedLength").isEqualTo(length);

		T decoded = codec.decode(buffer, OFFSET);

		assertThat(codec.lastDecodedLength()).as("lastDecodedLength").isEqualTo(length);
		assertThat(decoded).usingRecursiveComparison().isEqualTo(value);
		byte[] again = new byte[length];
		codec.encode(decoded, new UnsafeBuffer(again), 0);
		assertThat(again).as("the decoded value encodes to the same bytes")
				.isEqualTo(Arrays.copyOfRange(bytes, OFFSET, OFFSET + length));
	}
}
