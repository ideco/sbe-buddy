package corpus.namedtypes;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import net.concini.sbebuddy.tests.SchemaCase;

import corpus.namedtypes.sbe.MessageHeaderEncoder;

/**
 * Every attribute a named type carries. A field takes its presence from its
 * type, so the optional quantity is optional without saying so. The round trips
 * carry the price at its bounds and the quantity absent, then present; the
 * tests hold what the symbol's ASCII string refuses.
 */
final class NamedTypesTest implements SchemaCase {

	static final String ORACLE = """
			<?xml version="1.0" encoding="UTF-8"?>
			<sbe:messageSchema xmlns:sbe="http://fixprotocol.io/2016/sbe" package="corpus.namedtypes" id="1" version="0">
			    <types>
			        <composite name="messageHeader">
			            <type name="blockLength" primitiveType="uint16"/>
			            <type name="templateId" primitiveType="uint16"/>
			            <type name="schemaId" primitiveType="uint16"/>
			            <type name="version" primitiveType="uint16"/>
			        </composite>
			        <type name="Price" primitiveType="int64" minValue="0" maxValue="9223372036854775806" semanticType="Price"/>
			        <type name="Quantity" primitiveType="uint32" presence="optional" nullValue="4294967294" description="Absent when the size is not disclosed"/>
			        <type name="Symbol" primitiveType="char" length="6" characterEncoding="ASCII" semanticType="String" description="An instrument symbol"/>
			    </types>
			    <sbe:message name="NamedTypes" id="1">
			        <field name="symbol" id="1" type="Symbol"/>
			        <field name="price" id="2" type="Price"/>
			        <field name="quantity" id="3" type="Quantity"/>
			    </sbe:message>
			</sbe:messageSchema>
			""";

	private static final int OFFSET = 8;

	@Override
	public String description() {
		return "NamedTypes: every attribute a named type carries, a field taking its presence from its type";
	}

	@Override
	public String oracle() {
		return ORACLE;
	}

	@Override
	public List<RoundTrip<?>> roundTrips() {
		return List.of(
				new RoundTrip<>(
						"the price at its minimum, an empty symbol, no quantity", new NamedTypesCodec(),
						new NamedTypes("", 0L, null)
				),
				new RoundTrip<>(
						"the price at its maximum, a full six-character symbol, the quantity present",
						new NamedTypesCodec(), new NamedTypes("SYMBOL", 9_223_372_036_854_775_806L, 250L)
				)
		);
	}

	@Test
	void aNullSymbolIsRefused() {
		NamedTypesCodec codec = new NamedTypesCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);

		assertThatThrownBy(() -> codec.encode(new NamedTypes(null, 0L, null), buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("symbol is required");
	}

	@Test
	void aSymbolLongerThanItsLengthIsRefused() {
		NamedTypesCodec codec = new NamedTypesCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);

		assertThatThrownBy(() -> codec.encode(new NamedTypes("TOOLONG", 0L, null), buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("symbol is longer than 6: TOOLONG");
	}

	@Test
	void aNonAsciiSymbolIsRefused() {
		NamedTypesCodec codec = new NamedTypesCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);

		assertThatThrownBy(() -> codec.encode(new NamedTypes("café", 0L, null), buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("symbol is not ASCII: café");
	}

	@Test
	void anotherTemplateIsRefused() {
		NamedTypesCodec codec = new NamedTypesCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		codec.encode(new NamedTypes("", 0L, null), buffer, OFFSET);
		new MessageHeaderEncoder().wrap(buffer, OFFSET).templateId(2);

		assertThatThrownBy(() -> codec.decode(buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("not a NamedTypes: schemaId 1, templateId 2");
	}
}
