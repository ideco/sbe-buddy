package net.concini.sbebuddy.generator.corpus;

import static net.concini.sbebuddy.generator.Fixtures.field;
import static net.concini.sbebuddy.generator.Fixtures.message;
import static net.concini.sbebuddy.generator.Fixtures.messageHeader;
import static net.concini.sbebuddy.generator.Fixtures.messageSchema;

import net.concini.sbebuddy.generator.Schema;

/**
 * Two messages in one schema, with the descriptive attributes a message and its
 * fields carry and a block reserved wider than its fields need.
 */
final class Messages {

	static final String XML = """
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

	private Messages() {
	}

	static Schema schema() {
		return messageSchema("corpus.messages", 1, 0)
				.description("What a message and its fields may say about themselves")
				.types(messageHeader())
				.messages(
						message("NewOrder", 1)
								.blockLength(32)
								.semanticType("D")
								.description("A new single order")
								.fields(
										field("orderId", 1, "int64")
												.description("The identifier the sender gave the order"),
										field("sentAt", 2, "uint64")
												.epoch("unix")
												.timeUnit("nanosecond")
												.semanticType("UTCTimestamp")
												.description("When the order was sent"),
										field("price", 3, "int64").offset(16).semanticType("Price")
								),
						message("CancelOrder", 2)
								.semanticType("F")
								.fields(field("orderId", 1, "int64"))
				)
				.build();
	}
}
