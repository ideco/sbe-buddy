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
					"Sets", Sets.PACKAGE_INFO, Sets.SOURCE, Sets.schema(), Sets.XML, Sets.annotated(),
					Map.of("corpus.sets.SetsCodec", Sets.CODEC)
			),
			new Case(
					"Groups", Groups.PACKAGE_INFO, Groups.SOURCE, Groups.schema(), Groups.XML, Groups.annotated(),
					Map.of("corpus.groups.GroupsCodec", Groups.CODEC)
			),
			new Case(
					"VarData", VarData.PACKAGE_INFO, VarData.SOURCE, VarData.schema(), VarData.XML,
					VarData.annotated(), Map.of()
			),
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
			),
			new Case(
					"Arrays", Arrays.PACKAGE_INFO, Arrays.SOURCE, Arrays.schema(), Arrays.XML, Arrays.annotated(),
					Map.of("corpus.arrays.ArraysCodec", Arrays.CODEC)
			),
			new Case(
					"Bindings", Bindings.PACKAGE_INFO, Bindings.SOURCE, Bindings.schema(), Bindings.XML,
					Bindings.annotated(), Map.of("corpus.bindings.BindingsCodec", Bindings.CODEC)
			),
			new Case(
					"AddedFields", AddedFields.PACKAGE_INFO, AddedFields.SOURCE, AddedFields.schema(), AddedFields.XML,
					AddedFields.annotated(), Map.of("corpus.addedfields.AddedFieldsCodec", AddedFields.CODEC)
			),
			new Case(
					"Layout", Layout.PACKAGE_INFO, Layout.SOURCE, Layout.schema(), Layout.XML, Layout.annotated(),
					Map.of("corpus.layout.LayoutCodec", Layout.CODEC)
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
