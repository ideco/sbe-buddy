package net.concini.sbebuddy.generator;

import java.util.List;

/**
 * What a union's codec is made of: one case per message beneath the union,
 * flattened, each delegating to that message's codec. {@link CodecEmitter}
 * builds it from the messages' models; {@link UnionWriter} renders it. It
 * writes no wire code of its own, so its bytes are its members'.
 */
record UnionModel(
		String packageName,
		String codec,
		String union,
		String unionName,
		String flyweights,
		CodecModel.Header header,
		List<Case> cases
) {

	UnionModel {
		cases = List.copyOf(cases);
	}

	/**
	 * A member: its record, its codec's class and the field holding it, and the
	 * flyweight whose {@code TEMPLATE_ID} labels the case.
	 */
	record Case(String record, String codec, String field, String decoder) {
	}
}
