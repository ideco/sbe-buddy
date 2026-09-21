package com.example.trading;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import net.concini.sbebuddy.generator.SchemaXmlAssert;

/**
 * The integration proof: the real build compiled the annotated records with the
 * processor on the annotation processor path, and what it put in the jar is the
 * schema in {@code src/main/sbe}.
 */
final class SchemaResourceTest {

	private static final String RESOURCE = "/com/example/trading/schema.xml";

	@Test
	void theSchemaInTheJarIsTheOracle() throws IOException {
		SchemaXmlAssert.assertThat(resource()).matches(Files.readString(Path.of("src/main/sbe/trading.xml")));
	}

	@Test
	void theSchemaShipsUnderTheSchemaPackage() throws IOException {
		assertThat(resource()).contains("package=\"com.example.trading\"");
	}

	private static String resource() throws IOException {
		try (InputStream schema = SchemaResourceTest.class.getResourceAsStream(RESOURCE)) {
			assertThat(schema).as("the processor wrote %s", RESOURCE).isNotNull();
			return new String(schema.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
