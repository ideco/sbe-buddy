package net.concini.sbebuddy.processor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import net.concini.sbebuddy.generator.corpus.Corpus;

final class DiscoveryTest {

	static List<Corpus.Case> cases() {
		return Corpus.CASES;
	}

	@ParameterizedTest
	@MethodSource("cases")
	void discoversItsAnnotatedTwin(Corpus.Case aCase) {
		Capture capture = new Capture(aCase.annotated().packageName());

		Javac.Result result = Javac.compile(aCase, capture);

		assertThat(result.errors()).isEmpty();
		assertThat(capture.discovered).isNotNull();
		assertThat(capture.discovered.problems()).isEmpty();
		assertThat(capture.discovered.annotated()).isEqualTo(aCase.annotated());
	}

	/** Hands the package to discovery in the first round and keeps the result. */
	@SupportedAnnotationTypes("*")
	private static final class Capture extends AbstractProcessor {

		private final String packageName;
		Discovery.@Nullable Discovered discovered;

		Capture(String packageName) {
			this.packageName = packageName;
		}

		@Override
		public SourceVersion getSupportedSourceVersion() {
			return SourceVersion.latest();
		}

		@Override
		public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
			if (discovered == null && !round.processingOver()) {
				PackageElement schemaPackage = processingEnv.getElementUtils().getPackageElement(packageName);
				discovered = Discovery
						.discover(schemaPackage, processingEnv.getElementUtils(), processingEnv.getTypeUtils());
			}
			return false;
		}
	}
}
