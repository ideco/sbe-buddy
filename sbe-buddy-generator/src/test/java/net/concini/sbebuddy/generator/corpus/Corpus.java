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
					"Primitives", Primitives.PACKAGE_INFO, Primitives.SOURCE, Primitives.schema(), Primitives.XML,
					Primitives.annotated(), Map.of("corpus.primitives.PrimitivesCodec", Primitives.CODEC)
			),
			new Case(
					"NamedTypes", NamedTypes.PACKAGE_INFO, NamedTypes.SOURCE, NamedTypes.schema(), NamedTypes.XML,
					NamedTypes.annotated(), Map.of()
			),
			new Case(
					"Constants", Constants.PACKAGE_INFO, Constants.SOURCE, Constants.schema(), Constants.XML,
					Constants.annotated(), Map.of()
			),
			new Case(
					"Enums", Enums.PACKAGE_INFO, Enums.SOURCE, Enums.schema(), Enums.XML, Enums.annotated(),
					Map.of("corpus.enums.EnumsCodec", Enums.CODEC)
			),
			new Case(
					"Sets", Sets.PACKAGE_INFO, Sets.SOURCE, Sets.schema(), Sets.XML, Sets.annotated(),
					Map.of("corpus.sets.SetsCodec", Sets.CODEC)
			),
			new Case(
					"Composites", Composites.PACKAGE_INFO, Composites.SOURCE, Composites.schema(), Composites.XML,
					Composites.annotated(), Map.of()
			),
			new Case(
					"Groups", Groups.PACKAGE_INFO, Groups.SOURCE, Groups.schema(), Groups.XML, Groups.annotated(),
					Map.of()
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
					"Messages", Messages.PACKAGE_INFO, Messages.SOURCE, Messages.schema(), Messages.XML,
					Messages.annotated(),
					Map.of(
							"corpus.messages.NewOrderCodec", Messages.NEW_ORDER_CODEC,
							"corpus.messages.CancelOrderCodec", Messages.CANCEL_ORDER_CODEC
					)
			),
			new Case(
					"Arrays", Arrays.PACKAGE_INFO, Arrays.SOURCE, Arrays.schema(), Arrays.XML, Arrays.annotated(),
					Map.of()
			),
			new Case(
					"OptionalFields", OptionalFields.PACKAGE_INFO, OptionalFields.SOURCE, OptionalFields.schema(),
					OptionalFields.XML, OptionalFields.annotated(),
					Map.of("corpus.optionalfields.OptionalFieldsCodec", OptionalFields.CODEC)
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
