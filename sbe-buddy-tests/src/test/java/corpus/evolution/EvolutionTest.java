package corpus.evolution;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import net.concini.sbebuddy.Codec;
import net.concini.sbebuddy.tests.SchemaCase;

import corpus.evolution.Order.Allocation;
import corpus.evolution.Order.Fill;
import corpus.evolution.Order.Leg;
import corpus.evolution.sbe.OrderReader;

/**
 * The evolution schema at version 2, crossed with the packages holding its
 * versions 0 and 1, each with its own records and codecs. Fields appended to a
 * block cross both ways; the group and var-data appended inside the entry are
 * read from every older message, while an older reader cannot step over them,
 * which is SBE's limit. The flyweight reader passes over them in an older
 * message.
 */
final class EvolutionTest implements SchemaCase {

	static final String ORACLE = """
			<?xml version="1.0" encoding="UTF-8"?>
			<sbe:messageSchema xmlns:sbe="http://fixprotocol.io/2016/sbe" package="corpus.evolution" id="5" version="2">
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
			        <composite name="varStringEncoding">
			            <type name="length" primitiveType="uint16"/>
			            <type name="varData" primitiveType="char" length="0" characterEncoding="UTF-8"/>
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
			            <group name="fills" id="9" sinceVersion="2">
			                <field name="quantity" id="10" type="int32"/>
			            </group>
			            <data name="note" id="11" type="varStringEncoding" sinceVersion="2"/>
			        </group>
			    </sbe:message>
			</sbe:messageSchema>
			""";

	private static final int OFFSET = 16;

	// The other versions' types share their names with this one's, so they are
	// written qualified.
	private static final corpus.evolution.v0.Order VERSION_0 = new corpus.evolution.v0.Order(
			1L, List.of(
					new corpus.evolution.v0.Order.Leg(
							1, new corpus.evolution.v0.Price(100L, (byte) -2),
							List.of(
									new corpus.evolution.v0.Order.Allocation(7),
									new corpus.evolution.v0.Order.Allocation(8)
							)
					),
					new corpus.evolution.v0.Order.Leg(2, new corpus.evolution.v0.Price(-5L, (byte) 0), List.of())
			)
	);

	private static final corpus.evolution.v1.Order VERSION_1 = new corpus.evolution.v1.Order(
			1L, List.of(
					new corpus.evolution.v1.Order.Leg(
							1, new corpus.evolution.v1.Price(100L, (byte) -2), 3,
							List.of(
									new corpus.evolution.v1.Order.Allocation(7, 70L),
									new corpus.evolution.v1.Order.Allocation(8, 80L)
							)
					),
					new corpus.evolution.v1.Order.Leg(2, new corpus.evolution.v1.Price(-5L, (byte) 0), 4, List.of())
			)
	);

	@Override
	public String description() {
		return "Evolution: a group's entry appending a nested group and var-data, crossed with its versions 0 and 1";
	}

	@Override
	public String oracle() {
		return ORACLE;
	}

	@Override
	public List<RoundTrip<?>> roundTrips() {
		return List.of(
				new RoundTrip<>(
						"every appended member present", new OrderCodec(),
						new Order(
								1L, List.of(
										new Leg(
												1, new Price(100L, (byte) -2), 3,
												List.of(new Allocation(7, 70L), new Allocation(8, 80L)),
												List.of(new Fill(10), new Fill(20)), "first"
										),
										new Leg(
												2, new Price(-5L, (byte) 0), 4, List.of(), List.of(new Fill(30)),
												"second"
										)
								)
						)
				),
				new RoundTrip<>(
						"the appended group empty and the appended data empty", new OrderCodec(),
						new Order(1L, List.of(new Leg(1, new Price(0L, (byte) 0), 0, List.of(), List.of(), "")))
				)
		);
	}

	@Test
	void aVersion1ReaderReadsAVersion0Message() {
		assertThat(cross(new corpus.evolution.v0.OrderCodec(), VERSION_0, new corpus.evolution.v1.OrderCodec()))
				.isEqualTo(
						new corpus.evolution.v1.Order(
								1L, List.of(
										new corpus.evolution.v1.Order.Leg(
												1, new corpus.evolution.v1.Price(100L, (byte) -2), null,
												List.of(
														new corpus.evolution.v1.Order.Allocation(7, null),
														new corpus.evolution.v1.Order.Allocation(8, null)
												)
										),
										new corpus.evolution.v1.Order.Leg(
												2, new corpus.evolution.v1.Price(-5L, (byte) 0), null, List.of()
										)
								)
						)
				);
	}

	@Test
	void aVersion0ReaderStepsOverTheFieldsVersion1Appended() {
		assertThat(cross(new corpus.evolution.v1.OrderCodec(), VERSION_1, new corpus.evolution.v0.OrderCodec()))
				.isEqualTo(VERSION_0);
	}

	@Test
	void theCurrentReaderReadsAVersion0Message() {
		assertThat(cross(new corpus.evolution.v0.OrderCodec(), VERSION_0, new OrderCodec())).isEqualTo(
				new Order(
						1L, List.of(
								new Leg(
										1, new Price(100L, (byte) -2), null,
										List.of(new Allocation(7, null), new Allocation(8, null)), null, null
								),
								new Leg(2, new Price(-5L, (byte) 0), null, List.of(), null, null)
						)
				)
		);
	}

	@Test
	void theCurrentReaderReadsAVersion1Message() {
		assertThat(cross(new corpus.evolution.v1.OrderCodec(), VERSION_1, new OrderCodec())).isEqualTo(
				new Order(
						1L, List.of(
								new Leg(
										1, new Price(100L, (byte) -2), 3,
										List.of(new Allocation(7, 70L), new Allocation(8, 80L)), null, null
								),
								new Leg(2, new Price(-5L, (byte) 0), 4, List.of(), null, null)
						)
				)
		);
	}

	@Test
	void theCurrentFlyweightReaderPassesOverWhatAVersion0MessageLacks() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[512]);
		int length = new corpus.evolution.v0.OrderCodec().encode(VERSION_0, buffer, OFFSET);
		OrderReader reader = new OrderReader().wrap(buffer, OFFSET);

		List<String> stages = new ArrayList<>();
		for (OrderReader.Stage stage : reader) {
			stages.add(stage.getClass().getSimpleName());
		}

		assertThat(stages).containsExactly(
				"RootBlock", "Legs", "LegsEntry", "Allocations", "AllocationsEntry", "AllocationsEntry", "LegsEntry",
				"Allocations"
		);
		assertThat(reader.actingVersion()).isZero();
		assertThat(reader.decodedLength()).isEqualTo(length);
	}

	/**
	 * One version's message read by another's codec, which consumes exactly what
	 * was written.
	 */
	private static <W, R> R cross(Codec<W, ?> writer, W value, Codec<R, ?> reader) {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[512]);
		int length = writer.encode(value, buffer, OFFSET);

		assertThat(reader.decodedLength(buffer, OFFSET)).as("decodedLength").isEqualTo(length);
		R read = reader.decode(buffer, OFFSET);
		assertThat(reader.lastDecodedLength()).as("lastDecodedLength").isEqualTo(length);
		return read;
	}
}
