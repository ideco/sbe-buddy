package corpus.messages;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import net.concini.sbebuddy.tests.SchemaCase;

/**
 * Two messages in one schema, with the descriptive attributes a message and its
 * fields carry and a block reserved wider than its fields need. Each message's
 * codec refuses the other's template.
 */
final class MessagesTest implements SchemaCase {

	static final String ORACLE = """
			<?xml version="1.0" encoding="UTF-8"?>
			<sbe:messageSchema xmlns:sbe="http://fixprotocol.io/2016/sbe" package="corpus.messages" id="1" version="0" description="What a message and its fields may say about themselves">
			    <types>
			        <composite name="messageHeader">
			            <type name="blockLength" primitiveType="uint16"/>
			            <type name="templateId" primitiveType="uint16"/>
			            <type name="schemaId" primitiveType="uint16"/>
			            <type name="version" primitiveType="uint16"/>
			        </composite>
			    </types>
			    <sbe:message name="NewOrder" id="1" blockLength="32" semanticType="D" description="A new single order">
			        <field name="orderId" id="1" type="int64" description="The identifier the sender gave the order"/>
			        <field name="sentAt" id="2" type="uint64" epoch="unix" timeUnit="nanosecond" semanticType="UTCTimestamp" description="When the order was sent"/>
			        <field name="price" id="3" type="int64" offset="16" semanticType="Price"/>
			    </sbe:message>
			    <sbe:message name="CancelOrder" id="2" semanticType="F">
			        <field name="orderId" id="1" type="int64"/>
			    </sbe:message>
			</sbe:messageSchema>
			""";

	private static final int OFFSET = 8;

	private static final NewOrder NEW_ORDER = new NewOrder(123_456_789L, 1_700_000_000_000_000_000L, 10_125L);

	private static final CancelOrder CANCEL_ORDER = new CancelOrder(123_456_789L);

	@Override
	public String description() {
		return "Messages: two messages in one schema, the descriptive attributes they and their fields carry, "
				+ "a block reserved wider than its fields need";
	}

	@Override
	public String oracle() {
		return ORACLE;
	}

	@Override
	public List<RoundTrip<?>> roundTrips() {
		return List.of(
				new RoundTrip<>("a new order, its block wider than its three fields", new NewOrderCodec(), NEW_ORDER),
				new RoundTrip<>("a cancel order", new CancelOrderCodec(), CANCEL_ORDER)
		);
	}

	@Test
	void theNewOrderCodecRefusesACancelOrder() {
		NewOrderCodec codec = new NewOrderCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		new CancelOrderCodec().encode(CANCEL_ORDER, buffer, OFFSET);

		assertThatThrownBy(() -> codec.decode(buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("not a NewOrder: schemaId 1, templateId 2");
	}

	@Test
	void theCancelOrderCodecRefusesANewOrder() {
		CancelOrderCodec codec = new CancelOrderCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		new NewOrderCodec().encode(NEW_ORDER, buffer, OFFSET);

		assertThatThrownBy(() -> codec.decode(buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("not a CancelOrder: schemaId 1, templateId 1");
	}
}
