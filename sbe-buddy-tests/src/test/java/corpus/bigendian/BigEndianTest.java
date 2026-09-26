package corpus.bigendian;

import static java.nio.ByteOrder.BIG_ENDIAN;
import static net.concini.sbebuddy.tests.WriterAssert.assertWritesTheCodecsBytes;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.List;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import net.concini.sbebuddy.tests.SchemaCase;

import corpus.bigendian.BigEndian.Fill;
import corpus.bigendian.sbe.BigEndianDecoder;
import corpus.bigendian.sbe.GroupSizeEncodingDecoder;
import corpus.bigendian.sbe.MessageHeaderDecoder;
import corpus.bigendian.sbe.PriceDecoder;
import corpus.bigendian.sbe.VarStringEncodingDecoder;

/**
 * A schema on the wire the other way round, through everything whose bytes
 * depend on the order. The codec reaches the buffer only through the
 * flyweights, which apply the order; the test reads each value back big-endian
 * at the offset the flyweights declare.
 */
final class BigEndianTest implements SchemaCase {

	static final String ORACLE = """
			<?xml version="1.0" encoding="UTF-8"?>
			<sbe:messageSchema xmlns:sbe="http://fixprotocol.io/2016/sbe" package="corpus.bigendian" id="1" version="0" byteOrder="bigEndian">
			    <types>
			        <composite name="messageHeader">
			            <type name="blockLength" primitiveType="uint16"/>
			            <type name="templateId" primitiveType="uint16"/>
			            <type name="schemaId" primitiveType="uint16"/>
			            <type name="version" primitiveType="uint16"/>
			        </composite>
			        <type name="Bounds" primitiveType="int16" length="2"/>
			        <composite name="Price">
			            <type name="mantissa" primitiveType="int64"/>
			            <type name="exponent" primitiveType="int8"/>
			        </composite>
			        <set name="Rights" encodingType="uint32">
			            <choice name="trade">1</choice>
			            <choice name="quote">30</choice>
			        </set>
			        <enum name="Venue" encodingType="uint16">
			            <validValue name="XLON">1000</validValue>
			            <validValue name="XNYS">2000</validValue>
			        </enum>
			        <composite name="groupSizeEncoding">
			            <type name="blockLength" primitiveType="uint16"/>
			            <type name="numInGroup" primitiveType="uint16"/>
			        </composite>
			        <composite name="varStringEncoding">
			            <type name="length" primitiveType="uint16"/>
			            <type name="varData" primitiveType="char" length="0" characterEncoding="UTF-8"/>
			        </composite>
			    </types>
			    <sbe:message name="BigEndian" id="1">
			        <field name="orderId" id="1" type="int64"/>
			        <field name="level" id="2" type="int16"/>
			        <field name="port" id="3" type="uint16"/>
			        <field name="count" id="4" type="int32"/>
			        <field name="sequence" id="5" type="uint32"/>
			        <field name="timestamp" id="6" type="uint64"/>
			        <field name="ratio" id="7" type="float"/>
			        <field name="rate" id="8" type="double"/>
			        <field name="bounds" id="9" type="Bounds"/>
			        <field name="venue" id="10" type="Venue"/>
			        <field name="rights" id="11" type="Rights"/>
			        <field name="bid" id="12" type="Price"/>
			        <group name="fills" id="13">
			            <field name="quantity" id="14" type="int32"/>
			        </group>
			        <data name="note" id="15" type="varStringEncoding"/>
			    </sbe:message>
			</sbe:messageSchema>
			""";

	private static final int OFFSET = 8;

	/**
	 * Every value's bytes differ from their reverse, so an order flipped would
	 * show.
	 */
	private static final BigEndian VALUE = new BigEndian(
			0x0102030405060708L, (short) 0x0102, 0xABCD, 0x01020304, 0xF1F2F3F4L, 0x1112131415161718L, 1.5f, Math.PI,
			new short[]{0x0102, 0x0304}, Venue.XNYS, EnumSet.of(Rights.trade, Rights.quote),
			new Price(0x0A0B0C0D0E0F1011L, (byte) -2), List.of(new Fill(0x01020304), new Fill(5)), "big"
	);

	@Override
	public String description() {
		return "BigEndian: a schema on the wire the other way round, through everything whose bytes depend on the order";
	}

	@Override
	public String oracle() {
		return ORACLE;
	}

	@Override
	public List<RoundTrip<?>> roundTrips() {
		return List.of(
				new RoundTrip<>("every construct whose bytes depend on the order", new BigEndianCodec(), VALUE),
				new RoundTrip<>(
						"the low bounds, no fills and an empty note", new BigEndianCodec(),
						new BigEndian(
								Long.MIN_VALUE, Short.MIN_VALUE, 0, Integer.MIN_VALUE, 0L, 0L, -Float.MAX_VALUE,
								-Double.MAX_VALUE, new short[]{Short.MIN_VALUE, Short.MAX_VALUE}, Venue.XLON,
								EnumSet.noneOf(Rights.class), new Price(Long.MIN_VALUE, Byte.MIN_VALUE), List.of(), ""
						)
				)
		);
	}

	@Test
	void everyValueIsOnTheWireBigEndian() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
		new BigEndianCodec().encode(VALUE, buffer, OFFSET);
		int block = OFFSET + MessageHeaderDecoder.ENCODED_LENGTH;

		assertThat(buffer.getShort(OFFSET + MessageHeaderDecoder.templateIdEncodingOffset(), BIG_ENDIAN))
				.isEqualTo((short) BigEndianDecoder.TEMPLATE_ID);
		assertThat(buffer.getLong(block + BigEndianDecoder.orderIdEncodingOffset(), BIG_ENDIAN))
				.isEqualTo(VALUE.orderId());
		assertThat(buffer.getShort(block + BigEndianDecoder.levelEncodingOffset(), BIG_ENDIAN))
				.isEqualTo(VALUE.level());
		assertThat(buffer.getShort(block + BigEndianDecoder.portEncodingOffset(), BIG_ENDIAN) & 0xFFFF)
				.isEqualTo(VALUE.port());
		assertThat(buffer.getInt(block + BigEndianDecoder.countEncodingOffset(), BIG_ENDIAN))
				.isEqualTo(VALUE.count());
		assertThat(buffer.getInt(block + BigEndianDecoder.sequenceEncodingOffset(), BIG_ENDIAN) & 0xFFFF_FFFFL)
				.isEqualTo(VALUE.sequence());
		assertThat(buffer.getLong(block + BigEndianDecoder.timestampEncodingOffset(), BIG_ENDIAN))
				.isEqualTo(VALUE.timestamp());
		assertThat(buffer.getFloat(block + BigEndianDecoder.ratioEncodingOffset(), BIG_ENDIAN))
				.isEqualTo(VALUE.ratio());
		assertThat(buffer.getDouble(block + BigEndianDecoder.rateEncodingOffset(), BIG_ENDIAN))
				.isEqualTo(VALUE.rate());
		assertThat(buffer.getShort(block + BigEndianDecoder.boundsEncodingOffset() + 2, BIG_ENDIAN))
				.isEqualTo(VALUE.bounds()[1]);
		assertThat(buffer.getShort(block + BigEndianDecoder.venueEncodingOffset(), BIG_ENDIAN) & 0xFFFF)
				.isEqualTo(2000);
		assertThat(buffer.getInt(block + BigEndianDecoder.rightsEncodingOffset(), BIG_ENDIAN))
				.isEqualTo(1 << 1 | 1 << 30);
		assertThat(
				buffer.getLong(
						block + BigEndianDecoder.bidEncodingOffset() + PriceDecoder.mantissaEncodingOffset(),
						BIG_ENDIAN
				)
		).isEqualTo(VALUE.bid().mantissa());

		int dimension = block + BigEndianDecoder.BLOCK_LENGTH;
		assertThat(buffer.getShort(dimension + GroupSizeEncodingDecoder.numInGroupEncodingOffset(), BIG_ENDIAN))
				.isEqualTo((short) 2);
		int firstEntry = dimension + GroupSizeEncodingDecoder.ENCODED_LENGTH;
		assertThat(buffer.getInt(firstEntry + BigEndianDecoder.FillsDecoder.quantityEncodingOffset(), BIG_ENDIAN))
				.isEqualTo(0x01020304);

		int note = firstEntry + 2 * BigEndianDecoder.FillsDecoder.sbeBlockLength();
		assertThat(buffer.getShort(note + VarStringEncodingDecoder.lengthEncodingOffset(), BIG_ENDIAN))
				.isEqualTo((short) 3);
	}

	@Test
	void theWriterWritesTheCodecsBytesInTheSchemasOrder() {
		assertWritesTheCodecsBytes(
				new BigEndianCodec(), VALUE,
				(buffer, offset) -> new BigEndianWriter().wrap(buffer, offset)
						.orderId(0x0102030405060708L)
						.level((short) 0x0102)
						.port(0xABCD)
						.count(0x01020304)
						.sequence(0xF1F2F3F4L)
						.timestamp(0x1112131415161718L)
						.ratio(1.5f)
						.rate(Math.PI)
						.putBounds((short) 0x0102, (short) 0x0304)
						.venue(corpus.bigendian.sbe.Venue.XNYS)
						.rights().trade(true).quote(true).end()
						.bid().mantissa(0x0A0B0C0D0E0F1011L).exponent((byte) -2)
						.fills().entry().quantity(0x01020304).entry().quantity(5).end()
						.note("big")
						.length()
		);
	}
}
