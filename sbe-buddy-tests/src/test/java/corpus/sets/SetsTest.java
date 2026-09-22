package corpus.sets;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteOrder;
import java.util.EnumSet;
import java.util.List;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import net.concini.sbebuddy.tests.SchemaCase;

import corpus.sets.sbe.MessageHeaderEncoder;
import corpus.sets.sbe.SetsEncoder;

/**
 * Bit sets over a primitive and over a named type; a choice's text is the bit's
 * zero-based position, not its mask. The round trips carry each set empty, with
 * one choice and with every choice; the tests hold a bit no choice names
 * refused on decode, on both sets.
 */
final class SetsTest implements SchemaCase {

	static final String ORACLE = """
			<?xml version="1.0" encoding="UTF-8"?>
			<sbe:messageSchema xmlns:sbe="http://fixprotocol.io/2016/sbe" package="corpus.sets" id="1" version="0">
			    <types>
			        <composite name="messageHeader">
			            <type name="blockLength" primitiveType="uint16"/>
			            <type name="templateId" primitiveType="uint16"/>
			            <type name="schemaId" primitiveType="uint16"/>
			            <type name="version" primitiveType="uint16"/>
			        </composite>
			        <type name="FlagsEncoding" primitiveType="uint8"/>
			        <set name="Handling" encodingType="FlagsEncoding">
			            <choice name="urgent">0</choice>
			            <choice name="manual">7</choice>
			        </set>
			        <set name="Permissions" encodingType="uint16" semanticType="MultipleCharValue" description="What the account may do">
			            <choice name="canTrade" description="May submit orders">0</choice>
			            <choice name="canQuote" description="May submit quotes">1</choice>
			            <choice name="canCancel">2</choice>
			        </set>
			    </types>
			    <sbe:message name="Sets" id="1">
			        <field name="permissions" id="1" type="Permissions"/>
			        <field name="handling" id="2" type="Handling"/>
			    </sbe:message>
			</sbe:messageSchema>
			""";

	private static final int OFFSET = 8;

	@Override
	public String description() {
		return "Sets: bit sets over a primitive and over a named type, a choice's text the bit's zero-based position";
	}

	@Override
	public String oracle() {
		return ORACLE;
	}

	@Override
	public List<RoundTrip<?>> roundTrips() {
		return List.of(
				new RoundTrip<>(
						"both sets empty", new SetsCodec(),
						new Sets(EnumSet.noneOf(Permissions.class), EnumSet.noneOf(Handling.class))
				),
				new RoundTrip<>(
						"each set with one choice", new SetsCodec(),
						new Sets(EnumSet.of(Permissions.canQuote), EnumSet.of(Handling.urgent))
				),
				new RoundTrip<>(
						"each set with every choice", new SetsCodec(),
						new Sets(EnumSet.allOf(Permissions.class), EnumSet.allOf(Handling.class))
				)
		);
	}

	@Test
	void aPermissionsBitNoChoiceNamesIsRefusedOnDecode() {
		SetsCodec codec = new SetsCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		codec.encode(
				new Sets(EnumSet.noneOf(Permissions.class), EnumSet.noneOf(Handling.class)), buffer, OFFSET
		);
		buffer.putShort(
				OFFSET + MessageHeaderEncoder.ENCODED_LENGTH + SetsEncoder.permissionsEncodingOffset(), (short) 0b1000,
				ByteOrder.LITTLE_ENDIAN
		);

		assertThatThrownBy(() -> codec.decode(buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Permissions has a bit no choice names: 8");
	}

	@Test
	void aHandlingBitNoChoiceNamesIsRefusedOnDecode() {
		SetsCodec codec = new SetsCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		codec.encode(
				new Sets(EnumSet.noneOf(Permissions.class), EnumSet.noneOf(Handling.class)), buffer, OFFSET
		);
		buffer.putByte(
				OFFSET + MessageHeaderEncoder.ENCODED_LENGTH + SetsEncoder.handlingEncodingOffset(), (byte) 0b10
		);

		assertThatThrownBy(() -> codec.decode(buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Handling has a bit no choice names: 2");
	}

	@Test
	void aNullPermissionsIsRefused() {
		SetsCodec codec = new SetsCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);

		assertThatThrownBy(() -> codec.encode(new Sets(null, EnumSet.noneOf(Handling.class)), buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("permissions is required");
	}

	@Test
	void aNullHandlingIsRefused() {
		SetsCodec codec = new SetsCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);

		assertThatThrownBy(() -> codec.encode(new Sets(EnumSet.noneOf(Permissions.class), null), buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("handling is required");
	}

	@Test
	void anotherTemplateIsRefused() {
		SetsCodec codec = new SetsCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		codec.encode(
				new Sets(EnumSet.noneOf(Permissions.class), EnumSet.noneOf(Handling.class)), buffer, OFFSET
		);
		new MessageHeaderEncoder().wrap(buffer, OFFSET).templateId(2);

		assertThatThrownBy(() -> codec.decode(buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("not a Sets: schemaId 1, templateId 2");
	}
}
