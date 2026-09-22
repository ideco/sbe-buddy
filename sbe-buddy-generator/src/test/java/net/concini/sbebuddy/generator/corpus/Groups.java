package net.concini.sbebuddy.generator.corpus;

import static net.concini.sbebuddy.generator.Annotated.JavaPrimitive.INT;
import static net.concini.sbebuddy.generator.Annotated.JavaPrimitive.LONG;
import static net.concini.sbebuddy.generator.Annotated.JavaPrimitive.SHORT;
import static net.concini.sbebuddy.generator.Fixtures.annotatedComposite;
import static net.concini.sbebuddy.generator.Fixtures.annotatedField;
import static net.concini.sbebuddy.generator.Fixtures.annotatedGroup;
import static net.concini.sbebuddy.generator.Fixtures.annotatedMessage;
import static net.concini.sbebuddy.generator.Fixtures.annotatedSchema;
import static net.concini.sbebuddy.generator.Fixtures.annotatedType;
import static net.concini.sbebuddy.generator.Fixtures.boxed;
import static net.concini.sbebuddy.generator.Fixtures.composite;
import static net.concini.sbebuddy.generator.Fixtures.field;
import static net.concini.sbebuddy.generator.Fixtures.group;
import static net.concini.sbebuddy.generator.Fixtures.groupSizeEncoding;
import static net.concini.sbebuddy.generator.Fixtures.listOfRecord;
import static net.concini.sbebuddy.generator.Fixtures.message;
import static net.concini.sbebuddy.generator.Fixtures.messageHeader;
import static net.concini.sbebuddy.generator.Fixtures.messageSchema;
import static net.concini.sbebuddy.generator.Fixtures.other;
import static net.concini.sbebuddy.generator.Fixtures.primitive;
import static net.concini.sbebuddy.generator.Fixtures.type;
import static net.concini.sbebuddy.generator.Fixtures.unmapped;
import static uk.co.real_logic.sbe.PrimitiveType.INT64;
import static uk.co.real_logic.sbe.PrimitiveType.UINT8;
import static uk.co.real_logic.sbe.xml.Presence.OPTIONAL;

import net.concini.sbebuddy.generator.Annotated;
import net.concini.sbebuddy.generator.Fixtures.AnnotatedCompositeBuilder;
import net.concini.sbebuddy.generator.Fixtures.AnnotatedTypeBuilder;
import net.concini.sbebuddy.generator.Schema;

/**
 * A group with a layout and a field the record no longer carries, holding a
 * group whose dimensions are a composite of its own and whose entry has an
 * optional field; and a group appended in version 1 whose entry binds a price.
 */
final class Groups {

	static final String PACKAGE_INFO = """
			@SbeSchema(id = 1, version = 1)
			package corpus.groups;

			import net.concini.sbebuddy.SbeSchema;
			""";

	static final String SOURCE = """
			package corpus.groups;

			import static net.concini.sbebuddy.Presence.OPTIONAL;
			import static net.concini.sbebuddy.PrimitiveType.INT64;
			import static net.concini.sbebuddy.PrimitiveType.UINT8;

			import java.math.BigDecimal;
			import java.util.List;

			import net.concini.sbebuddy.SbeComposite;
			import net.concini.sbebuddy.SbeField;
			import net.concini.sbebuddy.SbeGroup;
			import net.concini.sbebuddy.SbeMessage;
			import net.concini.sbebuddy.SbeType;
			import net.concini.sbebuddy.TypeBinding;

			@SbeType(primitiveType = INT64)
			final class Cents {
			}

			final class CentsBinding implements TypeBinding.OfLong<BigDecimal> {

				public long toWire(BigDecimal value) {
					return value.movePointRight(2).longValueExact();
				}

				public BigDecimal fromWire(long wire) {
					return BigDecimal.valueOf(wire, 2);
				}
			}

			@SbeComposite(name = "smallGroupSizeEncoding")
			record SmallGroupSizeEncoding(
					@SbeType(primitiveType = UINT8) short blockLength,
					@SbeType(primitiveType = UINT8) short numInGroup
			) {
			}

			@SbeMessage(id = 1)
			record Groups(
					@SbeField(id = 1) long orderId,
					@SbeGroup(
							id = 10, blockLength = 8, semanticType = "NoLegs",
							description = "The legs of a multi-leg order",
							layout = {"legId", "legRatio", "allocations"},
							unmapped = @SbeField(id = 15, name = "legRatio", primitiveType = UINT8, deprecated = 1)
					) List<Leg> legs,
					@SbeGroup(id = 20, sinceVersion = 1) List<Fill> fills
			) {

				record Leg(
						@SbeGroup(id = 12, dimensionType = SmallGroupSizeEncoding.class) List<Allocation> allocations,
						@SbeField(id = 11) int legId
				) {

					record Allocation(
							@SbeField(id = 13) int account,
							@SbeField(id = 14, presence = OPTIONAL) Integer share
					) {
					}
				}

				record Fill(
						@SbeField(id = 21, type = Cents.class, binding = CentsBinding.class) BigDecimal price
				) {
				}
			}
			""";

	static final String XML = """
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

	static final String CODEC = """
			package corpus.groups;

			/** Generated by sbe-buddy from the schema of this package; do not edit. */
			@javax.annotation.processing.Generated("net.concini.sbebuddy")
			public final class GroupsCodec implements net.concini.sbebuddy.Codec<corpus.groups.Groups> {

				private final corpus.groups.sbe.MessageHeaderEncoder headerEncoder = new corpus.groups.sbe.MessageHeaderEncoder();
				private final corpus.groups.sbe.MessageHeaderDecoder headerDecoder = new corpus.groups.sbe.MessageHeaderDecoder();
				private final corpus.groups.sbe.GroupsEncoder encoder = new corpus.groups.sbe.GroupsEncoder();
				private final corpus.groups.sbe.GroupsDecoder decoder = new corpus.groups.sbe.GroupsDecoder();
				private final corpus.groups.CentsBinding centsBinding = new corpus.groups.CentsBinding();
				private int lastDecodedLength;

				public GroupsCodec() {
				}

				@Override
				public int encodedLength(corpus.groups.Groups value) {
					return corpus.groups.sbe.MessageHeaderEncoder.ENCODED_LENGTH + corpus.groups.sbe.GroupsEncoder.BLOCK_LENGTH + legsLength(value.legs()) + fillsLength(value.fills());
				}

				@Override
				public int encode(corpus.groups.Groups value, org.agrona.MutableDirectBuffer buffer, int offset) {
					encoder.wrapAndApplyHeader(buffer, offset, headerEncoder);
					encoder.orderId(value.orderId());
					if (value.legs() == null) {
						throw new IllegalArgumentException("legs is required");
					}
					writeLegs(value.legs(), encoder.legsCount(value.legs().size()));
					if (value.fills() == null) {
						throw new IllegalArgumentException("fills is required");
					}
					writeFills(value.fills(), encoder.fillsCount(value.fills().size()));
					return corpus.groups.sbe.MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength();
				}

				@Override
				public corpus.groups.Groups decode(org.agrona.DirectBuffer buffer, int offset) {
					headerDecoder.wrap(buffer, offset);
					if (headerDecoder.schemaId() != corpus.groups.sbe.GroupsDecoder.SCHEMA_ID
							|| headerDecoder.templateId() != corpus.groups.sbe.GroupsDecoder.TEMPLATE_ID) {
						throw new IllegalArgumentException(
								"not a Groups: schemaId " + headerDecoder.schemaId() + ", templateId " + headerDecoder.templateId());
					}
					decoder.wrap(buffer, offset + corpus.groups.sbe.MessageHeaderDecoder.ENCODED_LENGTH, headerDecoder.blockLength(), headerDecoder.version());
					java.util.List<corpus.groups.Groups.Leg> legs = readLegs(decoder.legs());
					java.util.List<corpus.groups.Groups.Fill> fills = decoder.actingVersion() < corpus.groups.sbe.GroupsDecoder.fillsDecoderSinceVersion() ? null : readFills(decoder.fills());
					corpus.groups.Groups value = new corpus.groups.Groups(
							decoder.orderId(),
							legs,
							fills
					);
					lastDecodedLength = corpus.groups.sbe.MessageHeaderDecoder.ENCODED_LENGTH + decoder.encodedLength();
					return value;
				}

				@Override
				public int lastDecodedLength() {
					return lastDecodedLength;
				}

				@Override
				public int decodedLength(org.agrona.DirectBuffer buffer, int offset) {
					headerDecoder.wrap(buffer, offset);
					decoder.wrap(buffer, offset + corpus.groups.sbe.MessageHeaderDecoder.ENCODED_LENGTH, headerDecoder.blockLength(), headerDecoder.version());
					return corpus.groups.sbe.MessageHeaderDecoder.ENCODED_LENGTH + decoder.sbeDecodedLength();
				}

				private void writeLegs(java.util.List<corpus.groups.Groups.Leg> entries, corpus.groups.sbe.GroupsEncoder.LegsEncoder encoder) {
					for (corpus.groups.Groups.Leg value : entries) {
						encoder.next();
						encoder.legId(value.legId());
						encoder.legRatio(corpus.groups.sbe.GroupsEncoder.LegsEncoder.legRatioNullValue());
						if (value.allocations() == null) {
							throw new IllegalArgumentException("allocations is required");
						}
						writeLegsAllocations(value.allocations(), encoder.allocationsCount(value.allocations().size()));
					}
				}

				private java.util.List<corpus.groups.Groups.Leg> readLegs(corpus.groups.sbe.GroupsDecoder.LegsDecoder decoder) {
					java.util.List<corpus.groups.Groups.Leg> entries = new java.util.ArrayList<>(decoder.count());
					while (decoder.hasNext()) {
						decoder.next();
						java.util.List<corpus.groups.Groups.Leg.Allocation> allocations = readLegsAllocations(decoder.allocations());
						entries.add(new corpus.groups.Groups.Leg(
								allocations,
								decoder.legId()
						));
					}
					return entries;
				}

				private static int legsLength(java.util.List<corpus.groups.Groups.Leg> entries) {
					if (entries == null) {
						throw new IllegalArgumentException("legs is required");
					}
					int length = corpus.groups.sbe.GroupsEncoder.LegsEncoder.sbeHeaderSize() + entries.size() * corpus.groups.sbe.GroupsEncoder.LegsEncoder.sbeBlockLength();
					for (corpus.groups.Groups.Leg value : entries) {
						length += legsAllocationsLength(value.allocations());
					}
					return length;
				}

				private void writeLegsAllocations(java.util.List<corpus.groups.Groups.Leg.Allocation> entries, corpus.groups.sbe.GroupsEncoder.LegsEncoder.AllocationsEncoder encoder) {
					for (corpus.groups.Groups.Leg.Allocation value : entries) {
						encoder.next();
						encoder.account(value.account());
						encoder.share(value.share() == null ? corpus.groups.sbe.GroupsEncoder.LegsEncoder.AllocationsEncoder.shareNullValue() : value.share());
					}
				}

				private java.util.List<corpus.groups.Groups.Leg.Allocation> readLegsAllocations(corpus.groups.sbe.GroupsDecoder.LegsDecoder.AllocationsDecoder decoder) {
					java.util.List<corpus.groups.Groups.Leg.Allocation> entries = new java.util.ArrayList<>(decoder.count());
					while (decoder.hasNext()) {
						decoder.next();
						entries.add(new corpus.groups.Groups.Leg.Allocation(
								decoder.account(),
								decoder.share() == corpus.groups.sbe.GroupsDecoder.LegsDecoder.AllocationsDecoder.shareNullValue() ? null : decoder.share()
						));
					}
					return entries;
				}

				private static int legsAllocationsLength(java.util.List<corpus.groups.Groups.Leg.Allocation> entries) {
					if (entries == null) {
						throw new IllegalArgumentException("allocations is required");
					}
					return corpus.groups.sbe.GroupsEncoder.LegsEncoder.AllocationsEncoder.sbeHeaderSize() + entries.size() * corpus.groups.sbe.GroupsEncoder.LegsEncoder.AllocationsEncoder.sbeBlockLength();
				}

				private void writeFills(java.util.List<corpus.groups.Groups.Fill> entries, corpus.groups.sbe.GroupsEncoder.FillsEncoder encoder) {
					for (corpus.groups.Groups.Fill value : entries) {
						encoder.next();
						if (value.price() == null) {
							throw new IllegalArgumentException("price is required");
						}
						encoder.price(centsBinding.toWire(value.price()));
					}
				}

				private java.util.List<corpus.groups.Groups.Fill> readFills(corpus.groups.sbe.GroupsDecoder.FillsDecoder decoder) {
					java.util.List<corpus.groups.Groups.Fill> entries = new java.util.ArrayList<>(decoder.count());
					while (decoder.hasNext()) {
						decoder.next();
						entries.add(new corpus.groups.Groups.Fill(
								centsBinding.fromWire(decoder.price())
						));
					}
					return entries;
				}

				private static int fillsLength(java.util.List<corpus.groups.Groups.Fill> entries) {
					if (entries == null) {
						throw new IllegalArgumentException("fills is required");
					}
					return corpus.groups.sbe.GroupsEncoder.FillsEncoder.sbeHeaderSize() + entries.size() * corpus.groups.sbe.GroupsEncoder.FillsEncoder.sbeBlockLength();
				}
			}
			""";

	private Groups() {
	}

	static Schema schema() {
		return messageSchema("corpus.groups", 1, 1)
				.types(
						messageHeader(),
						type("Cents", INT64),
						composite("smallGroupSizeEncoding").members(
								type("blockLength", UINT8),
								type("numInGroup", UINT8)
						),
						groupSizeEncoding()
				)
				.messages(
						message("Groups", 1)
								.fields(field("orderId", 1, "int64"))
								.groups(
										group("legs", 10)
												.blockLength(8)
												.semanticType("NoLegs")
												.description("The legs of a multi-leg order")
												.fields(
														field("legId", 11, "int32"),
														field("legRatio", 15, "uint8").deprecated(1)
												)
												.groups(
														group("allocations", 12)
																.dimensionType("smallGroupSizeEncoding")
																.fields(
																		field("account", 13, "int32"),
																		field("share", 14, "int32").presence(OPTIONAL)
																)
												),
										group("fills", 20)
												.sinceVersion(1)
												.fields(field("price", 21, "Cents"))
								)
				)
				.build();
	}

	static Annotated annotated() {
		AnnotatedTypeBuilder cents = annotatedType("Cents", INT64);
		AnnotatedCompositeBuilder smallGroupSizeEncoding = annotatedComposite("SmallGroupSizeEncoding")
				.qualifiedName("corpus.groups.SmallGroupSizeEncoding")
				.name("smallGroupSizeEncoding")
				.members(
						annotatedType("blockLength", UINT8).javaType(primitive(SHORT)),
						annotatedType("numInGroup", UINT8).javaType(primitive(SHORT))
				);
		return annotatedSchema("corpus.groups", 1, 1)
				.types(cents, smallGroupSizeEncoding)
				.messages(
						annotatedMessage("Groups", 1).components(
								annotatedField("orderId", 1, primitive(LONG)),
								annotatedGroup("legs", 10)
										.javaType(listOfRecord("corpus.groups.Groups.Leg"))
										.blockLength(8)
										.semanticType("NoLegs")
										.description("The legs of a multi-leg order")
										.layout("legId", "legRatio", "allocations")
										.unmapped(
												annotatedField("legRatio", 15, unmapped()).name("legRatio")
														.primitiveType(UINT8)
														.deprecated(1)
										)
										.components(
												annotatedGroup("allocations", 12)
														.javaType(listOfRecord("corpus.groups.Groups.Leg.Allocation"))
														.dimensionType(smallGroupSizeEncoding)
														.components(
																annotatedField("account", 13, primitive(INT)),
																annotatedField("share", 14, boxed(INT))
																		.presence(OPTIONAL)
														),
												annotatedField("legId", 11, primitive(INT))
										),
								annotatedGroup("fills", 20)
										.javaType(listOfRecord("corpus.groups.Groups.Fill"))
										.sinceVersion(1)
										.components(
												annotatedField("price", 21, other("java.math.BigDecimal")).type(cents)
														.binding("corpus.groups.CentsBinding", primitive(LONG))
										)
						)
				)
				.build();
	}
}
