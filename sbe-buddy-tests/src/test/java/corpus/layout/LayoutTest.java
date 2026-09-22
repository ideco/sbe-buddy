package corpus.layout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import net.concini.sbebuddy.tests.SchemaCase;

import corpus.layout.sbe.LayoutDecoder;
import corpus.layout.sbe.MessageHeaderDecoder;
import corpus.layout.sbe.MessageHeaderEncoder;

/**
 * A record that says its wire order at the top: its components declared out of
 * that order, and a deprecated field in the middle of the block that no
 * component carries. The round trip carries the components in their declared
 * order; the test shows the unmapped field holding its null value on the wire.
 */
final class LayoutTest implements SchemaCase {

	static final String ORACLE = """
			<?xml version="1.0" encoding="UTF-8"?>
			<sbe:messageSchema xmlns:sbe="http://fixprotocol.io/2016/sbe" package="corpus.layout" id="1" version="1">
			    <types>
			        <composite name="messageHeader">
			            <type name="blockLength" primitiveType="uint16"/>
			            <type name="templateId" primitiveType="uint16"/>
			            <type name="schemaId" primitiveType="uint16"/>
			            <type name="version" primitiveType="uint16"/>
			        </composite>
			    </types>
			    <sbe:message name="Layout" id="1">
			        <field name="orderId" id="1" type="int64"/>
			        <field name="price" id="2" type="int64" deprecated="1"/>
			        <field name="quantity" id="3" type="int32"/>
			    </sbe:message>
			</sbe:messageSchema>
			""";

	private static final int OFFSET = 8;

	@Override
	public String description() {
		return "Layout: a record that says its wire order at the top, a deprecated field no component carries";
	}

	@Override
	public String oracle() {
		return ORACLE;
	}

	@Override
	public List<RoundTrip<?>> roundTrips() {
		return List.of(
				new RoundTrip<>(
						"the components round tripping in their declared order", new LayoutCodec(),
						new Layout(1_000, 42L)
				)
		);
	}

	@Test
	void theUnmappedFieldHoldsItsNullValueOnTheWire() {
		LayoutCodec codec = new LayoutCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		codec.encode(new Layout(1_000, 42L), buffer, OFFSET);

		MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
		headerDecoder.wrap(buffer, OFFSET);
		LayoutDecoder decoder = new LayoutDecoder();
		decoder.wrap(
				buffer, OFFSET + MessageHeaderDecoder.ENCODED_LENGTH, headerDecoder.blockLength(),
				headerDecoder.version()
		);

		assertThat(decoder.price()).isEqualTo(LayoutDecoder.priceNullValue());
	}

	@Test
	void anotherTemplateIsRefused() {
		LayoutCodec codec = new LayoutCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		codec.encode(new Layout(1_000, 42L), buffer, OFFSET);
		new MessageHeaderEncoder().wrap(buffer, OFFSET).templateId(2);

		assertThatThrownBy(() -> codec.decode(buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("not a Layout: schemaId 1, templateId 2");
	}
}
