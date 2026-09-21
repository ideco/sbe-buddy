package net.concini.sbebuddy.processor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import net.concini.sbebuddy.generator.SchemaXmlAssert;
import net.concini.sbebuddy.generator.corpus.Corpus;

final class SbeProcessorTest {

	static List<Corpus.Case> cases() {
		return Corpus.CASES;
	}

	@ParameterizedTest
	@MethodSource("cases")
	void writesItsOracle(Corpus.Case aCase) {
		Javac.Result result = Javac.compile(aCase, new SbeProcessor());

		assertThat(result.errors()).isEmpty();
		String path = aCase.annotated().packageName().replace('.', '/') + "/schema.xml";
		assertThat(result.outputs()).containsKey(path);
		SchemaXmlAssert.assertThat(result.outputs().get(path)).matches(aCase.oracle());
	}
}
