package net.concini.sbebuddy.generator.corpus;

import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

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
}
