package net.concini.sbebuddy.tests;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Generated sources held against a copy checked in beside the tests, so a
 * change to what the generator writes is a diff a reviewer reads. Running with
 * {@code -Dgolden.update=true} rewrites the copy from the build's output
 * instead.
 */
final class GoldenSourcesTest {

	private static final Path GENERATED = Path.of("target/generated-sources/annotations");

	private static final Path GOLDEN = Path.of("src/test/resources/golden");

	@ParameterizedTest
	@ValueSource(strings = "corpus/groups/sbe/GroupsReader.java")
	void theGeneratedSourceIsTheGoldenOne(String source) throws IOException {
		String generated = Files.readString(GENERATED.resolve(source), StandardCharsets.UTF_8);
		Path golden = GOLDEN.resolve(source);
		if (Boolean.getBoolean("golden.update")) {
			Files.createDirectories(golden.getParent());
			Files.writeString(golden, generated, StandardCharsets.UTF_8);
		}

		assertThat(generated).as("%s against its golden copy", source)
				.isEqualTo(Files.readString(golden, StandardCharsets.UTF_8));
	}
}
