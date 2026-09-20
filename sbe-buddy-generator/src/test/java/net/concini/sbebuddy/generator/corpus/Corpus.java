package net.concini.sbebuddy.generator.corpus;

import java.util.List;

import net.concini.sbebuddy.generator.Schema;

/**
 * The corpus: every case pairs a hand-built schema with the hand-written oracle
 * it must write.
 */
final class Corpus {

	static final List<Case> CASES = List.of(
			new Case("Primitives", Primitives.schema(), Primitives.XML),
			new Case("NamedTypes", NamedTypes.schema(), NamedTypes.XML),
			new Case("Constants", Constants.schema(), Constants.XML),
			new Case("Enums", Enums.schema(), Enums.XML),
			new Case("Sets", Sets.schema(), Sets.XML),
			new Case("Composites", Composites.schema(), Composites.XML),
			new Case("Groups", Groups.schema(), Groups.XML),
			new Case("VarData", VarData.schema(), VarData.XML),
			new Case("Versions", Versions.schema(), Versions.XML),
			new Case("Header", Header.schema(), Header.XML),
			new Case("BigEndian", BigEndian.schema(), BigEndian.XML),
			new Case("Messages", Messages.schema(), Messages.XML)
	);

	private Corpus() {
	}

	record Case(String name, Schema schema, String oracle) {

		@Override
		public String toString() {
			return name;
		}
	}
}
