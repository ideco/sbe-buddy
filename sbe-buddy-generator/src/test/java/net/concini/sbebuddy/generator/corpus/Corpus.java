package net.concini.sbebuddy.generator.corpus;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.concini.sbebuddy.generator.Annotated;
import net.concini.sbebuddy.generator.Schema;

/**
 * The corpus: every case pairs a hand-built schema with the hand-written oracle
 * it must write, and the annotated twin that must map to that schema.
 */
final class Corpus {

	static final List<Case> CASES = List.of(
			new Case("Primitives", Primitives.schema(), Primitives.XML, Primitives.annotated()),
			new Case("NamedTypes", NamedTypes.schema(), NamedTypes.XML, null),
			new Case("Constants", Constants.schema(), Constants.XML, null),
			new Case("Enums", Enums.schema(), Enums.XML, null),
			new Case("Sets", Sets.schema(), Sets.XML, null),
			new Case("Composites", Composites.schema(), Composites.XML, null),
			new Case("Groups", Groups.schema(), Groups.XML, null),
			new Case("VarData", VarData.schema(), VarData.XML, null),
			new Case("Versions", Versions.schema(), Versions.XML, null),
			new Case("Header", Header.schema(), Header.XML, null),
			new Case("BigEndian", BigEndian.schema(), BigEndian.XML, null),
			new Case("Messages", Messages.schema(), Messages.XML, null),
			new Case("Arrays", Arrays.schema(), Arrays.XML, null),
			new Case("OptionalFields", OptionalFields.schema(), OptionalFields.XML, null)
	);

	private Corpus() {
	}

	record Case(String name, Schema schema, String oracle, @Nullable Annotated annotated) {

		@Override
		public String toString() {
			return name;
		}
	}
}
