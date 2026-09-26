package corpus.partialmapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import net.concini.sbebuddy.tests.SchemaCase;

import corpus.partialmapping.sbe.HeartbeatDecoder;
import corpus.partialmapping.sbe.HeartbeatEncoder;
import corpus.partialmapping.sbe.MessageHeaderDecoder;
import corpus.partialmapping.sbe.MessageHeaderEncoder;
import corpus.partialmapping.sbe.QuoteDecoder;
import corpus.partialmapping.sbe.QuoteEncoder;

/**
 * A schema mapped in part: the one message with a record goes through its
 * codec, and the two without one go through the flyweights, which sbe-tool
 * generated for every message of the resource, and through their readers.
 */
final class PartialMappingTest implements SchemaCase {

	static final String ORACLE = """
			<?xml version="1.0" encoding="UTF-8"?>
			<sbe:messageSchema xmlns:sbe="http://fixprotocol.io/2016/sbe" package="corpus.partialmapping" id="1" version="0">
			    <types>
			        <composite name="messageHeader">
			            <type name="blockLength" primitiveType="uint16"/>
			            <type name="templateId" primitiveType="uint16"/>
			            <type name="schemaId" primitiveType="uint16"/>
			            <type name="version" primitiveType="uint16"/>
			        </composite>
			        <enum name="Side" encodingType="char">
			            <validValue name="BUY">1</validValue>
			            <validValue name="SELL">2</validValue>
			        </enum>
			        <type name="Symbol" primitiveType="char" length="8"/>
			        <composite name="Price">
			            <type name="mantissa" primitiveType="int64"/>
			            <type name="exponent" primitiveType="int8"/>
			        </composite>
			    </types>
			    <sbe:message name="Order" id="1">
			        <field name="orderId" id="1" type="int64"/>
			        <field name="side" id="2" type="Side"/>
			        <field name="symbol" id="3" type="Symbol"/>
			        <field name="quantity" id="4" type="int32"/>
			    </sbe:message>
			    <sbe:message name="Quote" id="2">
			        <field name="symbol" id="3" type="Symbol"/>
			        <field name="bid" id="5" type="Price"/>
			        <field name="ask" id="6" type="Price"/>
			    </sbe:message>
			    <sbe:message name="Heartbeat" id="3">
			        <field name="sequence" id="7" type="uint64"/>
			    </sbe:message>
			</sbe:messageSchema>
			""";

	@Override
	public String description() {
		return "PartialMapping: one message of three mapped, the others flyweights alone";
	}

	@Override
	public String oracle() {
		return ORACLE;
	}

	@Override
	public List<RoundTrip<?>> roundTrips() {
		return List.of(
				new RoundTrip<>("the mapped message", new OrderCodec(), new Order(42L, Side.SELL, "ACME", 100)),
				new RoundTrip<>(
						"the bounds and a full symbol", new OrderCodec(),
						new Order(Long.MIN_VALUE, Side.BUY, "ABCDEFGH", Integer.MAX_VALUE)
				)
		);
	}

	@Test
	void aMessageWithoutARecordGoesThroughItsFlyweights() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		new QuoteEncoder().wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder()).bid().mantissa(12_345L)
				.exponent((byte) -2);
		new HeartbeatEncoder().wrapAndApplyHeader(buffer, 32, new MessageHeaderEncoder()).sequence(7L);

		QuoteDecoder quote = new QuoteDecoder().wrapAndApplyHeader(buffer, 0, new MessageHeaderDecoder());
		HeartbeatDecoder heartbeat = new HeartbeatDecoder().wrapAndApplyHeader(buffer, 32, new MessageHeaderDecoder());

		assertThat(quote.bid().mantissa()).isEqualTo(12_345L);
		assertThat(quote.bid().exponent()).isEqualTo((byte) -2);
		assertThat(heartbeat.sequence()).isEqualTo(7L);
	}

	@Test
	void aMessageWithoutARecordHasAReader() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		new QuoteEncoder().wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder()).bid().mantissa(12_345L)
				.exponent((byte) -2);

		QuoteReader.RootBlock quote = (QuoteReader.RootBlock) new QuoteReader().wrap(buffer, 0).next();

		assertThat(quote.bid().mantissa()).isEqualTo(12_345L);
		assertThat(quote.bid().exponent()).isEqualTo((byte) -2);
	}

	@Test
	void aMessageWithoutARecordHasNoCodec() {
		for (String codec : List.of("QuoteCodec", "HeartbeatCodec")) {
			assertThatThrownBy(() -> Class.forName("corpus.partialmapping." + codec))
					.isInstanceOf(ClassNotFoundException.class);
		}
	}
}
