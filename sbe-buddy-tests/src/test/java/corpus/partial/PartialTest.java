package corpus.partial;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import net.concini.sbebuddy.tests.SchemaCase;

import corpus.partial.sbe.GroupSizeEncodingEncoder;
import corpus.partial.sbe.MessageHeaderDecoder;
import corpus.partial.sbe.MessageHeaderEncoder;
import corpus.partial.sbe.OrderDecoder;
import corpus.partial.sbe.OrderEncoder;
import corpus.partial.sbe.PriceDecoder;

/**
 * Part of a venue's schema, read from its XML. The round trips carry the
 * records; the tests show what the codec does with the members no record
 * carries: written as their null values, empty and zero-length, and passed over
 * on the way in whatever a full writer put there, the length agreeing either
 * way.
 */
final class PartialTest implements SchemaCase {

	static final String ORACLE = """
			<?xml version="1.0" encoding="UTF-8"?>
			<sbe:messageSchema xmlns:sbe="http://fixprotocol.io/2016/sbe" package="corpus.partial" id="1" version="0">
			    <types>
			        <composite name="messageHeader">
			            <type name="blockLength" primitiveType="uint16"/>
			            <type name="templateId" primitiveType="uint16"/>
			            <type name="schemaId" primitiveType="uint16"/>
			            <type name="version" primitiveType="uint16"/>
			        </composite>
			        <composite name="groupSizeEncoding">
			            <type name="blockLength" primitiveType="uint16"/>
			            <type name="numInGroup" primitiveType="uint16"/>
			        </composite>
			        <composite name="varStringEncoding">
			            <type name="length" primitiveType="uint16"/>
			            <type name="varData" primitiveType="char" length="0" characterEncoding="UTF-8"/>
			        </composite>
			        <composite name="Price">
			            <type name="mantissa" primitiveType="int64" presence="optional"/>
			            <type name="exponent" primitiveType="int8" presence="constant">-2</type>
			        </composite>
			        <enum name="Side" encodingType="char">
			            <validValue name="BUY">1</validValue>
			            <validValue name="SELL">2</validValue>
			        </enum>
			        <set name="Flags" encodingType="uint8">
			            <choice name="urgent">0</choice>
			            <choice name="hidden">1</choice>
			        </set>
			    </types>
			    <sbe:message name="Order" id="1">
			        <field name="orderId" id="1" type="int64"/>
			        <field name="qty" id="2" type="int32"/>
			        <field name="side" id="3" type="Side"/>
			        <field name="flags" id="4" type="Flags"/>
			        <field name="price" id="5" type="Price"/>
			        <group name="legs" id="6">
			            <field name="legId" id="7" type="int32"/>
			            <group name="allocations" id="8">
			                <field name="account" id="9" type="int64"/>
			            </group>
			            <data name="memo" id="10" type="varStringEncoding"/>
			        </group>
			        <data name="note" id="11" type="varStringEncoding"/>
			    </sbe:message>
			    <sbe:message name="Cancel" id="2">
			        <field name="orderId" id="1" type="int64"/>
			        <field name="reason" id="2" type="uint8"/>
			    </sbe:message>
			    <sbe:message name="Heartbeat" id="3">
			        <field name="time" id="1" type="uint64"/>
			    </sbe:message>
			</sbe:messageSchema>
			""";

	private static final int OFFSET = 16;

	private static final Order ORDER = new Order(42, Side.SELL);

	@Override
	public String description() {
		return "Partial: part of a venue's schema read from its XML, the rest written empty and passed over";
	}

	@Override
	public String oracle() {
		return ORACLE;
	}

	@Override
	public List<RoundTrip<?>> roundTrips() {
		return List.of(
				new RoundTrip<>("an order by its two mapped fields", new OrderCodec(), ORDER),
				new RoundTrip<>("a cancel, mapped whole", new CancelCodec(), new Cancel(7, (short) 200))
		);
	}

	@Test
	void theMembersNoComponentCarriesAreWrittenAsNullEmptyAndZeroLength() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
		OrderCodec codec = new OrderCodec();
		int length = codec.encode(ORDER, buffer, OFFSET);

		OrderDecoder decoder = new OrderDecoder().wrapAndApplyHeader(buffer, OFFSET, new MessageHeaderDecoder());
		assertThat(decoder.orderId()).isEqualTo(42);
		assertThat(decoder.qty()).isEqualTo(OrderDecoder.qtyNullValue());
		assertThat(decoder.side()).isEqualTo(corpus.partial.sbe.Side.SELL);
		assertThat(decoder.flags().urgent()).isFalse();
		assertThat(decoder.flags().hidden()).isFalse();
		assertThat(decoder.price().mantissa()).isEqualTo(PriceDecoder.mantissaNullValue());
		assertThat(decoder.legs().count()).isZero();
		assertThat(decoder.noteLength()).isZero();
		assertThat(length).isEqualTo(
				MessageHeaderEncoder.ENCODED_LENGTH + OrderEncoder.BLOCK_LENGTH
						+ GroupSizeEncodingEncoder.ENCODED_LENGTH
						+ OrderEncoder.noteHeaderLength()
		);
	}

	@Test
	void theMembersNoComponentCarriesArePassedOverOnTheWayIn() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
		OrderEncoder encoder = new OrderEncoder().wrapAndApplyHeader(buffer, OFFSET, new MessageHeaderEncoder());
		encoder.orderId(42).qty(5).side(corpus.partial.sbe.Side.SELL);
		encoder.flags().urgent(true);
		encoder.price().mantissa(9950);
		OrderEncoder.LegsEncoder legs = encoder.legsCount(2);
		legs.next().legId(1);
		legs.allocationsCount(1).next().account(100);
		legs.memo("first");
		legs.next().legId(2);
		legs.allocationsCount(0);
		legs.memo("");
		encoder.note("hello");
		int length = MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength();
		OrderCodec codec = new OrderCodec();

		assertThat(codec.decodedLength(buffer, OFFSET)).isEqualTo(length);
		assertThat(codec.decode(buffer, OFFSET)).isEqualTo(ORDER);
		assertThat(codec.lastDecodedLength()).isEqualTo(length);
	}
}
