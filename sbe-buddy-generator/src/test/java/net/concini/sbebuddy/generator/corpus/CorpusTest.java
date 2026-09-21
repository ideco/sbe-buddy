package net.concini.sbebuddy.generator.corpus;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import net.concini.sbebuddy.generator.Generator;
import net.concini.sbebuddy.generator.Mapping;
import net.concini.sbebuddy.generator.SchemaXmlAssert;

final class CorpusTest {

	static List<Corpus.Case> cases() {
		return Corpus.CASES;
	}

	@ParameterizedTest
	@MethodSource("cases")
	void writesItsOracle(Corpus.Case aCase) {
		SchemaXmlAssert.assertThat(aCase.schema()).matches(aCase.oracle());
	}

	@ParameterizedTest
	@MethodSource("cases")
	void mapsToItsSchema(Corpus.Case aCase) {
		Mapping.Mapped mapped = Mapping.map(aCase.annotated());

		assertThat(mapped.problems()).isEmpty();
		assertThat(mapped.schema()).isEqualTo(aCase.schema());
	}

	@ParameterizedTest
	@MethodSource("cases")
	void breaksNoRuleThatComparesNodes(Corpus.Case aCase) {
		assertThat(Generator.validate(aCase.schema())).isEmpty();
	}
}
