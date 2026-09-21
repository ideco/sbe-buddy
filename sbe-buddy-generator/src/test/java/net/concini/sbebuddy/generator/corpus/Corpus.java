package net.concini.sbebuddy.generator.corpus;

import java.util.List;

import org.jspecify.annotations.Nullable;

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
			new Case("NamedTypes", NamedTypes.schema(), NamedTypes.XML, NamedTypes.annotated()),
			new Case("Constants", Constants.schema(), Constants.XML, Constants.annotated()),
			new Case("Enums", Enums.schema(), Enums.XML, Enums.annotated()),
			new Case("Sets", Sets.schema(), Sets.XML, Sets.annotated()),
			new Case("Composites", Composites.schema(), Composites.XML, Composites.annotated()),
			new Case("Groups", Groups.schema(), Groups.XML, Groups.annotated()),
			new Case("VarData", VarData.schema(), VarData.XML, VarData.annotated()),
			new Case("Versions", Versions.schema(), Versions.XML, Versions.annotated()),
			new Case("Header", Header.schema(), Header.XML, Header.annotated()),
			new Case("BigEndian", BigEndian.schema(), BigEndian.XML, BigEndian.annotated()),
			new Case("Messages", Messages.schema(), Messages.XML, Messages.annotated()),
			new Case("Arrays", Arrays.schema(), Arrays.XML, Arrays.annotated()),
			new Case("OptionalFields", OptionalFields.schema(), OptionalFields.XML, OptionalFields.annotated())
	);

	private Corpus() {
	}

	/**
	 * The source is two compilation units, the {@code package-info.java} carrying
	 * {@code @SbeSchema} and one unit holding the records; null while a case has no
	 * source yet.
	 */
	public record Case(
			String name,
			@Nullable String packageInfo,
			@Nullable String source,
			Schema schema,
			String oracle,
			Annotated annotated
	) {

		Case(String name, Schema schema, String oracle, Annotated annotated) {
			this(name, null, null, schema, oracle, annotated);
		}

		@Override
		public String toString() {
			return name;
		}
	}
}
