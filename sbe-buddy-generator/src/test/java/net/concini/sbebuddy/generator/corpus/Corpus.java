package net.concini.sbebuddy.generator.corpus;

import java.util.List;

import net.concini.sbebuddy.generator.Schema;

/**
 * The corpus: every case pairs a hand-built schema with the hand-written oracle
 * it must write.
 */
final class Corpus {

	static final List<Case> CASES = List.of(
			new Case("Primitives", Primitives.schema(), Primitives.XML)
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
