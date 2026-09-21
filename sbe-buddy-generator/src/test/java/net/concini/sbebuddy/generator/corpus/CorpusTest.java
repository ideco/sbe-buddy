package net.concini.sbebuddy.generator.corpus;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.agrona.generation.StringWriterOutputManager;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import net.concini.sbebuddy.generator.Annotated;
import net.concini.sbebuddy.generator.CodecEmitter;
import net.concini.sbebuddy.generator.Generator;
import net.concini.sbebuddy.generator.Mapping;
import net.concini.sbebuddy.generator.Problem;
import net.concini.sbebuddy.generator.SchemaXmlAssert;

final class CorpusTest {

	static List<Corpus.Case> cases() {
		return Corpus.CASES;
	}

	static List<Corpus.Case> casesTheCodecCovers() {
		return Corpus.CASES.stream().filter(aCase -> !aCase.codecs().isEmpty()).toList();
	}

	static List<Corpus.Case> casesTheCodecLacks() {
		return Corpus.CASES.stream().filter(aCase -> aCase.codecs().isEmpty()).toList();
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

	@ParameterizedTest
	@MethodSource("casesTheCodecCovers")
	void emitsItsCodecs(Corpus.Case aCase) {
		StringWriterOutputManager output = new StringWriterOutputManager();
		output.setPackageName(aCase.annotated().packageName());

		List<Problem> problems = CodecEmitter.emit(Generator.ir(aCase.schema()), aCase.annotated(), output);

		assertThat(problems).isEmpty();
		assertThat(sources(output)).isEqualTo(aCase.codecs());
	}

	/**
	 * The work list of the codec increments: each case here names what it waits
	 * for.
	 */
	@ParameterizedTest
	@MethodSource("casesTheCodecLacks")
	void namesTheConstructTheCodecLacks(Corpus.Case aCase) {
		StringWriterOutputManager output = new StringWriterOutputManager();
		output.setPackageName(aCase.annotated().packageName());

		List<Problem> problems = CodecEmitter.emit(Generator.ir(aCase.schema()), aCase.annotated(), output);

		assertThat(problems).isNotEmpty().allSatisfy(problem -> {
			assertThat(problem.node()).isInstanceOf(Annotated.Message.class);
			assertThat(problem.message()).startsWith("no codec for ")
					.endsWith(" yet; set codecs = false on @SbeSchema");
		});
		assertThat(output.getSources()).isEmpty();
	}

	private static Map<String, String> sources(StringWriterOutputManager output) {
		return output.getSources().entrySet().stream()
				.collect(Collectors.toMap(Map.Entry::getKey, source -> source.getValue().toString()));
	}
}
