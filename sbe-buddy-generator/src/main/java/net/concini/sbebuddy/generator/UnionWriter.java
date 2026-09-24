package net.concini.sbebuddy.generator;

import static net.concini.sbebuddy.generator.CodecTemplates.*;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link UnionModel} as Java source, through {@link CodecTemplates}: every
 * method a switch over the cases, on the record's type to encode and on the
 * header's template id to decode, delegating to the member's codec.
 */
final class UnionWriter {

	private UnionWriter() {
	}

	static String write(UnionModel model) {
		List<String> fields = new ArrayList<>();
		List<String> encodedLengths = new ArrayList<>();
		List<String> encodes = new ArrayList<>();
		List<String> headerEncodes = new ArrayList<>();
		List<String> decodes = new ArrayList<>();
		List<String> decodedLengths = new ArrayList<>();
		List<String> canDecodes = new ArrayList<>();
		List<String> templateIds = new ArrayList<>();
		for (UnionModel.Case member : model.cases()) {
			fields.add(UNION_CODEC_FIELD.fill(member));
			encodedLengths.add(UNION_ENCODED_LENGTH_CASE.fill(member));
			encodes.add(UNION_ENCODE_CASE.fill(member));
			headerEncodes.add(UNION_HEADER_ENCODE_CASE.fill(member));
			decodes.add(UNION_DECODE_CASE.fill(member));
			decodedLengths.add(UNION_DECODED_LENGTH_CASE.fill(member));
			canDecodes.add(UNION_CAN_DECODE_CASE.fill(member));
			templateIds.add(UNION_TEMPLATE_ID.fill(member));
		}
		UnionModel.Case first = model.cases().get(0);
		return UNION_CODEC.fill(
				model,
				"headerClass", model.header().headerClass(),
				"headerRecord", model.header().record(),
				"fields", String.join("\n", fields),
				"first", first.field(),
				"firstDecoder", first.decoder(),
				"encodedLengths", String.join("\n", encodedLengths),
				"encodes", String.join("\n", encodes),
				"headerEncodes", String.join("\n", headerEncodes),
				"decodes", String.join("\n", decodes),
				"decodedLengths", String.join("\n", decodedLengths),
				"canDecodes", String.join("\n", canDecodes),
				"templateIds", String.join(" + \", \" + ", templateIds)
		);
	}
}
