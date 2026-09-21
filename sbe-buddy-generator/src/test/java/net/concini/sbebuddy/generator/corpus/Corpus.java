package net.concini.sbebuddy.generator.corpus;

import java.util.List;

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
					Primitives.annotated()
			),
			new Case(
					"NamedTypes", NamedTypes.PACKAGE_INFO, NamedTypes.SOURCE, NamedTypes.schema(), NamedTypes.XML,
					NamedTypes.annotated()
			),
			new Case(
					"Constants", Constants.PACKAGE_INFO, Constants.SOURCE, Constants.schema(), Constants.XML,
					Constants.annotated()
			),
			new Case("Enums", Enums.PACKAGE_INFO, Enums.SOURCE, Enums.schema(), Enums.XML, Enums.annotated()),
			new Case("Sets", Sets.PACKAGE_INFO, Sets.SOURCE, Sets.schema(), Sets.XML, Sets.annotated()),
			new Case(
					"Composites", Composites.PACKAGE_INFO, Composites.SOURCE, Composites.schema(), Composites.XML,
					Composites.annotated()
			),
			new Case("Groups", Groups.PACKAGE_INFO, Groups.SOURCE, Groups.schema(), Groups.XML, Groups.annotated()),
			new Case(
					"VarData", VarData.PACKAGE_INFO, VarData.SOURCE, VarData.schema(), VarData.XML,
					VarData.annotated()
			),
			new Case(
					"Versions", Versions.PACKAGE_INFO, Versions.SOURCE, Versions.schema(), Versions.XML,
					Versions.annotated()
			),
			new Case("Header", Header.PACKAGE_INFO, Header.SOURCE, Header.schema(), Header.XML, Header.annotated()),
			new Case(
					"BigEndian", BigEndian.PACKAGE_INFO, BigEndian.SOURCE, BigEndian.schema(), BigEndian.XML,
					BigEndian.annotated()
			),
			new Case(
					"Messages", Messages.PACKAGE_INFO, Messages.SOURCE, Messages.schema(), Messages.XML,
					Messages.annotated()
			),
			new Case("Arrays", Arrays.PACKAGE_INFO, Arrays.SOURCE, Arrays.schema(), Arrays.XML, Arrays.annotated()),
			new Case(
					"OptionalFields", OptionalFields.PACKAGE_INFO, OptionalFields.SOURCE, OptionalFields.schema(),
					OptionalFields.XML, OptionalFields.annotated()
			)
	);

	private Corpus() {
	}

	/**
	 * The source is two compilation units, the {@code package-info.java} carrying
	 * {@code @SbeSchema} and one unit holding the records.
	 */
	public record Case(
			String name,
			String packageInfo,
			String source,
			Schema schema,
			String oracle,
			Annotated annotated
	) {

		@Override
		public String toString() {
			return name;
		}
	}
}
