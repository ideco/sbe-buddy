package corpus.groups;

import static net.concini.sbebuddy.tests.WriterAssert.assertWritesTheCodecsBytes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import net.concini.sbebuddy.tests.SchemaCase;

import corpus.groups.Groups.Fill;
import corpus.groups.Groups.Leg;
import corpus.groups.Groups.Leg.Allocation;
import corpus.groups.sbe.GroupsEncoder.LegsEncoder;
import corpus.groups.sbe.MessageHeaderEncoder;

/**
 * A group with a layout and a field the record no longer carries, holding a
 * group whose dimensions are a composite of its own and whose entry has an
 * optional field; and a group appended in version 1 whose entry binds a price.
 * The round trips carry no entries, several legs with the nested allocations
 * empty in one and filled in another, the optional share null and present, and
 * the appended fills group with entries; the tests hold a null group refused
 * and a version 0 header decoding the appended group null.
 */
final class GroupsTest implements SchemaCase {

	static final String ORACLE = """
			<?xml version="1.0" encoding="UTF-8"?>
			<sbe:messageSchema xmlns:sbe="http://fixprotocol.io/2016/sbe" package="corpus.groups" id="1" version="1">
			    <types>
			        <composite name="messageHeader">
			            <type name="blockLength" primitiveType="uint16"/>
			            <type name="templateId" primitiveType="uint16"/>
			            <type name="schemaId" primitiveType="uint16"/>
			            <type name="version" primitiveType="uint16"/>
			        </composite>
			        <type name="Cents" primitiveType="int64"/>
			        <composite name="groupSizeEncoding">
			            <type name="blockLength" primitiveType="uint16"/>
			            <type name="numInGroup" primitiveType="uint16"/>
			        </composite>
			        <composite name="smallGroupSizeEncoding">
			            <type name="blockLength" primitiveType="uint8"/>
			            <type name="numInGroup" primitiveType="uint8"/>
			        </composite>
			    </types>
			    <sbe:message name="Groups" id="1">
			        <field name="orderId" id="1" type="int64"/>
			        <group name="legs" id="10" blockLength="8" semanticType="NoLegs" description="The legs of a multi-leg order">
			            <field name="legId" id="11" type="int32"/>
			            <field name="legRatio" id="15" type="uint8" deprecated="1"/>
			            <group name="allocations" id="12" dimensionType="smallGroupSizeEncoding">
			                <field name="account" id="13" type="int32"/>
			                <field name="share" id="14" type="int32" presence="optional"/>
			            </group>
			        </group>
			        <group name="fills" id="20" sinceVersion="1">
			            <field name="price" id="21" type="Cents"/>
			        </group>
			    </sbe:message>
			</sbe:messageSchema>
			""";

	private static final int OFFSET = 8;

	@Override
	public String description() {
		return "Groups: a group holding a group of its own, an appended group whose entry binds a price";
	}

	@Override
	public String oracle() {
		return ORACLE;
	}

	@Override
	public List<RoundTrip<?>> roundTrips() {
		return List.of(
				new RoundTrip<>("no entries in either group", new GroupsCodec(), new Groups(1L, List.of(), List.of())),
				new RoundTrip<>(
						"several legs, the nested allocations empty in one and filled in another, "
								+ "the optional share absent and present",
						new GroupsCodec(),
						new Groups(
								1L,
								List.of(
										new Leg(List.of(), 100),
										new Leg(
												List.of(new Allocation(10, null), new Allocation(20, 5)), 200
										)
								),
								List.of()
						)
				),
				new RoundTrip<>(
						"the appended fills group with entries", new GroupsCodec(),
						new Groups(
								1L, List.of(),
								List.of(new Fill(new BigDecimal("1.05")), new Fill(new BigDecimal("2.50")))
						)
				)
		);
	}

	@Test
	void aNullLegsIsRefused() {
		GroupsCodec codec = new GroupsCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		Groups value = new Groups(1L, null, List.of());

		assertThatThrownBy(() -> codec.encodedLength(value))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("legs is required");
		assertThatThrownBy(() -> codec.encode(value, buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("legs is required");
	}

	@Test
	void aNullFillsIsRefused() {
		GroupsCodec codec = new GroupsCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		Groups value = new Groups(1L, List.of(), null);

		assertThatThrownBy(() -> codec.encodedLength(value))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("fills is required");
		assertThatThrownBy(() -> codec.encode(value, buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("fills is required");
	}

	@Test
	void aNullAllocationsIsRefused() {
		GroupsCodec codec = new GroupsCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		Groups value = new Groups(1L, List.of(new Leg(null, 100)), List.of());

		assertThatThrownBy(() -> codec.encodedLength(value))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("allocations is required");
		assertThatThrownBy(() -> codec.encode(value, buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("allocations is required");
	}

	@Test
	void aHeaderAtVersion0DecodesWithTheAppendedFillsGroupNull() {
		GroupsCodec codec = new GroupsCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		codec.encode(
				new Groups(1L, List.of(new Leg(List.of(), 100)), List.of(new Fill(new BigDecimal("1.05")))), buffer,
				OFFSET
		);
		new MessageHeaderEncoder().wrap(buffer, OFFSET).version(0);

		Groups decoded = codec.decode(buffer, OFFSET);

		assertThat(decoded.fills()).isNull();
	}

	@Test
	void anotherTemplateIsRefused() {
		GroupsCodec codec = new GroupsCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		codec.encode(new Groups(1L, List.of(), List.of()), buffer, OFFSET);
		new MessageHeaderEncoder().wrap(buffer, OFFSET).templateId(2);

		assertThatThrownBy(() -> codec.decode(buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("not a Groups: schemaId 1, templateId 2");
	}

	/**
	 * Every wire field is a step, the one the record leaves unmapped too; an entry
	 * complete takes the next entry or the group's end, and the appended group with
	 * no entry is written with a count of 0.
	 *
	 * <pre>
	 * // does not compile: a nested group is opened in every entry, even empty
	 * writer.wrap(buffer, 0).orderId(1L).legs().entry().legId(100).legRatio(ratio).entry();
	 * </pre>
	 */
	@Test
	void theWriterWritesTheCodecsBytesForLegsAndTheirAllocations() {
		short noRatio = LegsEncoder.legRatioNullValue();

		assertWritesTheCodecsBytes(
				new GroupsCodec(),
				new Groups(
						1L,
						List.of(
								new Leg(List.of(), 100),
								new Leg(List.of(new Allocation(10, null), new Allocation(20, 5)), 200)
						),
						List.of()
				),
				(buffer, offset) -> new GroupsWriter().wrap(buffer, offset)
						.orderId(1L)
						.legs()
						.entry().legId(100).legRatio(noRatio)
						.allocations().end()
						.entry().legId(200).legRatio(noRatio)
						.allocations()
						.entry().account(10)
						.entry().account(20).share(5)
						.end()
						.end()
						.fills().end()
						.length()
		);
	}
}
