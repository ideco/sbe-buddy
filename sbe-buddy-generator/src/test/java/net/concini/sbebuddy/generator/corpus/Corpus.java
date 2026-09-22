package net.concini.sbebuddy.generator.corpus;

import java.util.List;
import java.util.Map;

import net.concini.sbebuddy.generator.Annotated;
import net.concini.sbebuddy.generator.Schema;

/**
 * The corpus: every case pairs a hand-built schema with the hand-written oracle
 * it must write, the annotated twin that must map to that schema, and the Java
 * source that must discover to that twin. Public because the processor's tests
 * read it from the generator's test jar.
 */
public final class Corpus {

	public static final List<Case> CASES = List.of(
			new Case(
					"Versions", Versions.PACKAGE_INFO, Versions.SOURCE, Versions.schema(), Versions.XML,
					Versions.annotated(), Map.of()
			),
			new Case(
					"Header", Header.PACKAGE_INFO, Header.SOURCE, Header.schema(), Header.XML, Header.annotated(),
					Map.of()
			),
			new Case(
					"BigEndian", BigEndian.PACKAGE_INFO, BigEndian.SOURCE, BigEndian.schema(), BigEndian.XML,
					BigEndian.annotated(), Map.of()
			)
	);

	private Corpus() {
	}

	/**
	 * The source is two compilation units, the {@code package-info.java} carrying
	 * {@code @SbeSchema} and one unit holding the records. The codecs are the
	 * source the emitter must write for each message, by the codec's qualified
	 * name; empty, with {@code codecs = false} in the source, while the codec lacks
	 * a construct the case uses.
	 */
	public record Case(
			String name,
			String packageInfo,
			String source,
			Schema schema,
			String oracle,
			Annotated annotated,
			Map<String, String> codecs
	) {

		@Override
		public String toString() {
			return name;
		}
	}
}
