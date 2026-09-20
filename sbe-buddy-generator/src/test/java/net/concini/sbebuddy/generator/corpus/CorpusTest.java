package net.concini.sbebuddy.generator.corpus;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import net.concini.sbebuddy.generator.Mapping;
import net.concini.sbebuddy.generator.SchemaXmlAssert;

final class CorpusTest {

	static List<Corpus.Case> cases() {
		return Corpus.CASES;
	}

	static List<Corpus.Case> twinned() {
		return Corpus.CASES.stream().filter(aCase -> aCase.annotated() != null).toList();
	}

	@ParameterizedTest
	@MethodSource("cases")
	void writesItsOracle(Corpus.Case aCase) {
		SchemaXmlAssert.assertThat(aCase.schema()).matches(aCase.oracle());
	}

	@ParameterizedTest
	@MethodSource("twinned")
	void mapsToItsSchema(Corpus.Case aCase) {
		Mapping.Mapped mapped = Mapping.map(aCase.annotated());

		assertThat(mapped.problems()).isEmpty();
		assertThat(mapped.schema()).isEqualTo(aCase.schema());
	}

	@Test
	void everyCaseHasATwin() {
		assertThat(Corpus.CASES)
				.filteredOn(aCase -> aCase.annotated() == null)
				.extracting(Corpus.Case::name)
				.as("cases without an annotated twin")
				.isEmpty();
	}
}
