package corpus.evolution.v1;

import java.util.List;

import net.concini.sbebuddy.tests.SchemaCase;

import corpus.evolution.v1.Order.Allocation;
import corpus.evolution.v1.Order.Leg;

/**
 * The evolution schema at version 1: fields appended to the entry after its
 * composite, and to the nested group's entries. The crossing is the current
 * version's test.
 */
final class EvolutionV1Test implements SchemaCase {

	static final String ORACLE = """
			<?xml version="1.0" encoding="UTF-8"?>
			<sbe:messageSchema xmlns:sbe="http://fixprotocol.io/2016/sbe" package="corpus.evolution.v1" id="5" version="1">
			    <types>
			        <composite name="messageHeader">
			            <type name="blockLength" primitiveType="uint16"/>
			            <type name="templateId" primitiveType="uint16"/>
			            <type name="schemaId" primitiveType="uint16"/>
			            <type name="version" primitiveType="uint16"/>
			        </composite>
			        <composite name="Price">
			            <type name="mantissa" primitiveType="int64"/>
			            <type name="exponent" primitiveType="int8"/>
			        </composite>
			        <composite name="groupSizeEncoding">
			            <type name="blockLength" primitiveType="uint16"/>
			            <type name="numInGroup" primitiveType="uint16"/>
			        </composite>
			    </types>
			    <sbe:message name="Order" id="1">
			        <field name="orderId" id="1" type="int64"/>
			        <group name="legs" id="2">
			            <field name="legId" id="3" type="int32"/>
			            <field name="price" id="4" type="Price"/>
			            <field name="ratio" id="7" type="int32" sinceVersion="1"/>
			            <group name="allocations" id="5">
			                <field name="account" id="6" type="int32"/>
			                <field name="share" id="8" type="int64" sinceVersion="1"/>
			            </group>
			        </group>
			    </sbe:message>
			</sbe:messageSchema>
			""";

	@Override
	public String description() {
		return "EvolutionV1: fields appended to a group's entry and to its nested group's entries";
	}

	@Override
	public String oracle() {
		return ORACLE;
	}

	@Override
	public List<RoundTrip<?>> roundTrips() {
		return List.of(
				new RoundTrip<>(
						"two legs with their appended fields", new OrderCodec(),
						new Order(
								1L, List.of(
										new Leg(1, new Price(100L, (byte) -2), 3, List.of(new Allocation(7, 70L))),
										new Leg(2, new Price(-5L, (byte) 0), 4, List.of())
								)
						)
				)
		);
	}
}
