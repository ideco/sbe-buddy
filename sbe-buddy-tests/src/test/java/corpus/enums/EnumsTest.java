package corpus.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import net.concini.sbebuddy.tests.SchemaCase;

import corpus.enums.sbe.EnumsEncoder;
import corpus.enums.sbe.MessageHeaderEncoder;

/**
 * Enumerations over a primitive and over a named type, which sbe-tool resolves
 * to the primitive it encodes; one designates an unknown value, one does not.
 * The round trips carry every value of both; the tests hold the unknown wire
 * value on each and encoding the designated unknown constant.
 */
final class EnumsTest implements SchemaCase {

	static final String ORACLE = """
			<?xml version="1.0" encoding="UTF-8"?>
			<sbe:messageSchema xmlns:sbe="http://fixprotocol.io/2016/sbe" package="corpus.enums" id="1" version="0">
			    <types>
			        <composite name="messageHeader">
			            <type name="blockLength" primitiveType="uint16"/>
			            <type name="templateId" primitiveType="uint16"/>
			            <type name="schemaId" primitiveType="uint16"/>
			            <type name="version" primitiveType="uint16"/>
			        </composite>
			        <type name="StatusCode" primitiveType="uint8"/>
			        <enum name="OrderStatus" encodingType="StatusCode">
			            <validValue name="New">0</validValue>
			            <validValue name="PartiallyFilled">1</validValue>
			            <validValue name="Filled">2</validValue>
			        </enum>
			        <enum name="Side" encodingType="char" semanticType="Side" description="Which side of the market an order takes">
			            <validValue name="Buy" description="The order buys">B</validValue>
			            <validValue name="Sell" description="The order sells">S</validValue>
			        </enum>
			    </types>
			    <sbe:message name="Enums" id="1">
			        <field name="side" id="1" type="Side"/>
			        <field name="status" id="2" type="OrderStatus"/>
			    </sbe:message>
			</sbe:messageSchema>
			""";

	private static final int OFFSET = 8;

	@Override
	public String description() {
		return "Enums: enumerations over a primitive and over a named type, one designating an unknown value";
	}

	@Override
	public String oracle() {
		return ORACLE;
	}

	@Override
	public List<RoundTrip<?>> roundTrips() {
		return List.of(
				new RoundTrip<>("a buy, new", new EnumsCodec(), new Enums(Side.Buy, OrderStatus.New)),
				new RoundTrip<>(
						"a sell, partially filled", new EnumsCodec(), new Enums(Side.Sell, OrderStatus.PartiallyFilled)
				),
				new RoundTrip<>("a buy, filled", new EnumsCodec(), new Enums(Side.Buy, OrderStatus.Filled))
		);
	}

	@Test
	void anOrderStatusTheSchemaDoesNotKnowDecodesToTheDesignatedUnknownConstant() {
		EnumsCodec codec = new EnumsCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		codec.encode(new Enums(Side.Buy, OrderStatus.New), buffer, OFFSET);
		buffer.putByte(OFFSET + MessageHeaderEncoder.ENCODED_LENGTH + EnumsEncoder.statusEncodingOffset(), (byte) 99);

		Enums decoded = codec.decode(buffer, OFFSET);

		assertThat(decoded.status()).isEqualTo(OrderStatus.Unknown);
	}

	@Test
	void aSideTheSchemaDoesNotKnowIsRefusedOnDecode() {
		EnumsCodec codec = new EnumsCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		codec.encode(new Enums(Side.Buy, OrderStatus.New), buffer, OFFSET);
		buffer.putByte(OFFSET + MessageHeaderEncoder.ENCODED_LENGTH + EnumsEncoder.sideEncodingOffset(), (byte) 'X');

		assertThatThrownBy(() -> codec.decode(buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Side has no value " + (byte) 'X');
	}

	@Test
	void encodingTheDesignatedUnknownOrderStatusIsRefused() {
		EnumsCodec codec = new EnumsCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);

		assertThatThrownBy(() -> codec.encode(new Enums(Side.Buy, OrderStatus.Unknown), buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("OrderStatus.Unknown has no wire form");
	}

	@Test
	void aNullSideIsRefused() {
		EnumsCodec codec = new EnumsCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);

		assertThatThrownBy(() -> codec.encode(new Enums(null, OrderStatus.New), buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("side is required");
	}

	@Test
	void aNullStatusIsRefused() {
		EnumsCodec codec = new EnumsCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);

		assertThatThrownBy(() -> codec.encode(new Enums(Side.Buy, null), buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("status is required");
	}

	@Test
	void anotherTemplateIsRefused() {
		EnumsCodec codec = new EnumsCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		codec.encode(new Enums(Side.Buy, OrderStatus.New), buffer, OFFSET);
		new MessageHeaderEncoder().wrap(buffer, OFFSET).templateId(2);

		assertThatThrownBy(() -> codec.decode(buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("not a Enums: schemaId 1, templateId 2");
	}
}
