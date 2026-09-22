package corpus.addedfields;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import net.concini.sbebuddy.tests.SchemaCase;

import corpus.addedfields.sbe.AddedFieldsEncoder;
import corpus.addedfields.sbe.MessageHeaderEncoder;

/**
 * A message grown over two schema versions under a baseline that retired the
 * first. The round trips carry a current message with the optional field absent
 * and present; the tests hold a version 1 header decoding the version 2 fields
 * null, and a header below the baseline refused.
 */
final class AddedFieldsTest implements SchemaCase {

	static final String ORACLE = """
			<?xml version="1.0" encoding="UTF-8"?>
			<sbe:messageSchema xmlns:sbe="http://fixprotocol.io/2016/sbe" package="corpus.addedfields" id="1" version="2">
			    <types>
			        <composite name="messageHeader">
			            <type name="blockLength" primitiveType="uint16"/>
			            <type name="templateId" primitiveType="uint16"/>
			            <type name="schemaId" primitiveType="uint16"/>
			            <type name="version" primitiveType="uint16"/>
			        </composite>
			    </types>
			    <sbe:message name="AddedFields" id="1">
			        <field name="orderId" id="1" type="int64"/>
			        <field name="quantity" id="2" type="int32" sinceVersion="1"/>
			        <field name="filled" id="3" type="int32" sinceVersion="2"/>
			        <field name="ratio" id="4" type="float" presence="optional" sinceVersion="2"/>
			    </sbe:message>
			</sbe:messageSchema>
			""";

	private static final int OFFSET = 8;

	@Override
	public String description() {
		return "AddedFields: a message grown over two schema versions under a baseline that retired the first";
	}

	@Override
	public String oracle() {
		return ORACLE;
	}

	@Override
	public List<RoundTrip<?>> roundTrips() {
		return List.of(
				new RoundTrip<>(
						"a current message, the ratio absent", new AddedFieldsCodec(),
						new AddedFields(1L, 10, 5, null)
				),
				new RoundTrip<>(
						"a current message with every field", new AddedFieldsCodec(),
						new AddedFields(1L, 10, 5, 3.5f)
				)
		);
	}

	@Test
	void aHeaderAtVersion1DecodesWithTheVersion2FieldsNull() {
		AddedFieldsCodec codec = new AddedFieldsCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		codec.encode(new AddedFields(1L, 10, 5, 3.5f), buffer, OFFSET);
		new MessageHeaderEncoder().wrap(buffer, OFFSET)
				.version(1)
				.blockLength(AddedFieldsEncoder.filledEncodingOffset());

		AddedFields decoded = codec.decode(buffer, OFFSET);

		assertThat(decoded).usingRecursiveComparison().isEqualTo(new AddedFields(1L, 10, null, null));
	}

	@Test
	void aHeaderBelowTheBaselineIsRefused() {
		AddedFieldsCodec codec = new AddedFieldsCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		codec.encode(new AddedFields(1L, 10, 5, 3.5f), buffer, OFFSET);
		new MessageHeaderEncoder().wrap(buffer, OFFSET).version(0);

		assertThatThrownBy(() -> codec.decode(buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("AddedFields version 0 is below the baseline 1");
	}

	@Test
	void aNullFilledIsRefused() {
		AddedFieldsCodec codec = new AddedFieldsCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);

		assertThatThrownBy(() -> codec.encode(new AddedFields(1L, 10, null, 3.5f), buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("filled is required");
	}

	@Test
	void anotherTemplateIsRefused() {
		AddedFieldsCodec codec = new AddedFieldsCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		codec.encode(new AddedFields(1L, 10, 5, 3.5f), buffer, OFFSET);
		new MessageHeaderEncoder().wrap(buffer, OFFSET).templateId(2);

		assertThatThrownBy(() -> codec.decode(buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("not a AddedFields: schemaId 1, templateId 2");
	}
}
