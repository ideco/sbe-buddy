package corpus.constants;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import net.concini.sbebuddy.tests.SchemaCase;

import corpus.constants.sbe.MessageHeaderEncoder;

/**
 * The three ways a value leaves the wire: a constant type holding its value as
 * text, a constant type naming a valid value, and a constant enum field. The
 * one round trip carries every constant held; the tests hold what a value other
 * than the constant refuses, on the text constant, the valueRef constant and
 * the constant enum field.
 */
final class ConstantsTest implements SchemaCase {

	static final String ORACLE = """
			<?xml version="1.0" encoding="UTF-8"?>
			<sbe:messageSchema xmlns:sbe="http://fixprotocol.io/2016/sbe" package="corpus.constants" id="1" version="0">
			    <types>
			        <composite name="messageHeader">
			            <type name="blockLength" primitiveType="uint16"/>
			            <type name="templateId" primitiveType="uint16"/>
			            <type name="schemaId" primitiveType="uint16"/>
			            <type name="version" primitiveType="uint16"/>
			        </composite>
			        <type name="BuySide" primitiveType="char" presence="constant" valueRef="Side.Buy"/>
			        <type name="Currency" primitiveType="char" length="3" presence="constant">USD</type>
			        <enum name="Side" encodingType="char">
			            <validValue name="Buy">B</validValue>
			            <validValue name="Sell">S</validValue>
			        </enum>
			    </types>
			    <sbe:message name="Constants" id="1">
			        <field name="currency" id="1" type="Currency"/>
			        <field name="buySide" id="2" type="BuySide"/>
			        <field name="side" id="3" type="Side" presence="constant" valueRef="Side.Sell"/>
			    </sbe:message>
			</sbe:messageSchema>
			""";

	private static final int OFFSET = 8;

	private static final Constants CONSTANTS = new Constants("USD", (byte) 'B', Side.Sell);

	@Override
	public String description() {
		return "Constants: the three ways a value leaves the wire, a text constant, a valueRef constant, a constant enum field";
	}

	@Override
	public String oracle() {
		return ORACLE;
	}

	@Override
	public List<RoundTrip<?>> roundTrips() {
		return List.of(new RoundTrip<>("every constant held", new ConstantsCodec(), CONSTANTS));
	}

	@Test
	void aNullCurrencyIsRefused() {
		ConstantsCodec codec = new ConstantsCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);

		assertThatThrownBy(() -> codec.encode(new Constants(null, (byte) 'B', Side.Sell), buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("currency is required");
	}

	@Test
	void aCurrencyOtherThanTheConstantIsRefused() {
		ConstantsCodec codec = new ConstantsCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);

		assertThatThrownBy(() -> codec.encode(new Constants("EUR", (byte) 'B', Side.Sell), buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("currency is the constant USD");
	}

	@Test
	void aBuySideOtherThanTheConstantIsRefused() {
		ConstantsCodec codec = new ConstantsCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);

		assertThatThrownBy(() -> codec.encode(new Constants("USD", (byte) 'S', Side.Sell), buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("buySide is the constant 66");
	}

	@Test
	void aNullSideIsRefused() {
		ConstantsCodec codec = new ConstantsCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);

		assertThatThrownBy(() -> codec.encode(new Constants("USD", (byte) 'B', null), buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("side is required");
	}

	@Test
	void aSideOtherThanTheConstantIsRefused() {
		ConstantsCodec codec = new ConstantsCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);

		assertThatThrownBy(() -> codec.encode(new Constants("USD", (byte) 'B', Side.Buy), buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("side is the constant Sell");
	}

	@Test
	void anotherTemplateIsRefused() {
		ConstantsCodec codec = new ConstantsCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		codec.encode(CONSTANTS, buffer, OFFSET);
		new MessageHeaderEncoder().wrap(buffer, OFFSET).templateId(2);

		assertThatThrownBy(() -> codec.decode(buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("not a Constants: schemaId 1, templateId 2");
	}
}
