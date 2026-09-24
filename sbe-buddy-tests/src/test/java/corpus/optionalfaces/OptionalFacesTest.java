package corpus.optionalfaces;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import net.concini.sbebuddy.tests.SchemaCase;

import corpus.optionalfaces.sbe.MessageHeaderDecoder;
import corpus.optionalfaces.sbe.MoneyDecoder;
import corpus.optionalfaces.sbe.OptionalFacesDecoder;

/**
 * Optional fields whose face has no null value of its own: a composite, a set
 * and a char array, each bound to a type whose null the binding writes as the
 * composite's null mantissa, no bit set and zeros, and each unbound, never null
 * on the way back and its null refused on the way out. The round trips carry
 * the bound fields present and null; the tests hold what null is on the wire
 * and the refusals.
 */
final class OptionalFacesTest implements SchemaCase {

	static final String ORACLE = """
			<?xml version="1.0" encoding="UTF-8"?>
			<sbe:messageSchema xmlns:sbe="http://fixprotocol.io/2016/sbe" package="corpus.optionalfaces" id="1" version="0">
			    <types>
			        <composite name="messageHeader">
			            <type name="blockLength" primitiveType="uint16"/>
			            <type name="templateId" primitiveType="uint16"/>
			            <type name="schemaId" primitiveType="uint16"/>
			            <type name="version" primitiveType="uint16"/>
			        </composite>
			        <composite name="Money">
			            <type name="mantissa" primitiveType="int64" presence="optional"/>
			            <type name="exponent" primitiveType="int8" presence="constant">-2</type>
			        </composite>
			        <set name="Instruction" encodingType="uint8">
			            <choice name="POST_ONLY">0</choice>
			            <choice name="REDUCE_ONLY">1</choice>
			        </set>
			        <type name="Venue" primitiveType="char" length="4"/>
			    </types>
			    <sbe:message name="OptionalFaces" id="1">
			        <field name="price" id="1" type="Money" presence="optional"/>
			        <field name="instructions" id="2" type="Instruction" presence="optional"/>
			        <field name="venue" id="3" type="Venue" presence="optional"/>
			        <field name="fee" id="4" type="Money" presence="optional"/>
			        <field name="flags" id="5" type="Instruction" presence="optional"/>
			        <field name="market" id="6" type="Venue" presence="optional"/>
			    </sbe:message>
			</sbe:messageSchema>
			""";

	private static final int OFFSET = 8;

	private static final Money FEE = new Money(125L, (byte) -2);

	@Override
	public String description() {
		return "OptionalFaces: optional composite, set and char array fields, bound to a null of the binding's choosing and unbound";
	}

	@Override
	public String oracle() {
		return ORACLE;
	}

	@Override
	public List<RoundTrip<?>> roundTrips() {
		return List.of(
				new RoundTrip<>(
						"every bound field present", new OptionalFacesCodec(),
						new OptionalFaces(
								new BigDecimal("99.61"), EnumSet.of(Instruction.POST_ONLY), new Mic("XNAS"), FEE,
								EnumSet.of(Instruction.REDUCE_ONLY), "XLON"
						)
				),
				new RoundTrip<>(
						"every bound field null, the unbound ones at their empty values", new OptionalFacesCodec(),
						new OptionalFaces(
								null, null, null, new Money(null, (byte) -2), EnumSet.noneOf(Instruction.class), ""
						)
				)
		);
	}

	@Test
	void aBindingWritesNullAsItsChosenRepresentation() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		new OptionalFacesCodec().encode(
				new OptionalFaces(null, null, null, FEE, Set.of(), "XLON"), buffer, OFFSET
		);
		OptionalFacesDecoder decoder = new OptionalFacesDecoder()
				.wrapAndApplyHeader(buffer, OFFSET, new MessageHeaderDecoder());

		assertThat(decoder.price().mantissa()).isEqualTo(MoneyDecoder.mantissaNullValue());
		assertThat(decoder.instructions().getRaw()).isZero();
		assertThat(decoder.venue()).isEmpty();
	}

	@Test
	void anUnboundFieldWithoutANullValueRefusesNull() {
		assertRefused(
				new OptionalFaces(null, null, null, null, Set.of(), ""),
				"fee has no null value on the wire; a binding may write one"
		);
		assertRefused(
				new OptionalFaces(null, null, null, FEE, null, ""),
				"flags has no null value on the wire; a binding may write one"
		);
		assertRefused(
				new OptionalFaces(null, null, null, FEE, Set.of(), null),
				"market has no null value on the wire; a binding may write one"
		);
	}

	private static void assertRefused(OptionalFaces value, String message) {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);

		assertThatThrownBy(() -> new OptionalFacesCodec().encode(value, buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage(message);
	}
}
