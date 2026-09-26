package corpus.bindings;

import static net.concini.sbebuddy.tests.ReaderAssert.assertReadsTheValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import net.concini.sbebuddy.tests.SchemaCase;

import corpus.bindings.sbe.EverywhereDecoder;
import corpus.bindings.sbe.MessageHeaderDecoder;
import corpus.bindings.sbe.MessageHeaderEncoder;

/**
 * Bindings between a record's own types and the wire's faces: a specialization
 * over a primitive face, shared by a named type and a bare primitive and over
 * an optional field, and the generic interface over a string, to a wrapper
 * record, and over a byte array, to a record; then a binding on every other
 * kind of component, an enum, optional and constant, a set, a composite's
 * members, a group and var-data, each handed its context. The round trips carry
 * the optional bound fields absent and present; the tests hold every required
 * binding's field required, a binding's own exception passing through, the
 * ASCII string a binding hands the flyweight, and what the context tells.
 */
final class BindingsTest implements SchemaCase {

	static final String ORACLE = """
			<?xml version="1.0" encoding="UTF-8"?>
			<sbe:messageSchema xmlns:sbe="http://fixprotocol.io/2016/sbe" package="corpus.bindings" id="1" version="0">
			    <types>
			        <composite name="messageHeader">
			            <type name="blockLength" primitiveType="uint16"/>
			            <type name="templateId" primitiveType="uint16"/>
			            <type name="schemaId" primitiveType="uint16"/>
			            <type name="version" primitiveType="uint16"/>
			        </composite>
			        <type name="Cents" primitiveType="int64"/>
			        <type name="Rgb" primitiveType="uint8" length="3"/>
			        <type name="Symbol" primitiveType="char" length="6"/>
			        <enum name="Flag" encodingType="uint8">
			            <validValue name="NO">0</validValue>
			            <validValue name="YES">1</validValue>
			        </enum>
			        <set name="Permission" encodingType="uint8">
			            <choice name="READ">0</choice>
			            <choice name="WRITE">1</choice>
			        </set>
			        <composite name="Quote">
			            <type name="bid" primitiveType="int64"/>
			            <ref name="firm" type="Flag"/>
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
			    <sbe:message name="Bindings" id="1">
			        <field name="price" id="1" type="Cents"/>
			        <field name="fee" id="2" type="int64"/>
			        <field name="rebate" id="3" type="int64" presence="optional"/>
			        <field name="symbol" id="4" type="Symbol"/>
			        <field name="colour" id="5" type="Rgb"/>
			    </sbe:message>
			    <sbe:message name="Everywhere" id="2">
			        <field name="urgent" id="1" type="Flag"/>
			        <field name="acknowledged" id="2" type="Flag" presence="optional"/>
			        <field name="live" id="3" type="Flag" presence="constant" valueRef="Flag.YES"/>
			        <field name="access" id="4" type="Permission"/>
			        <field name="quote" id="5" type="Quote"/>
			        <field name="signedCount" id="6" type="int64"/>
			        <field name="unsignedCount" id="7" type="uint64"/>
			        <field name="sentAt" id="8" type="int64" epoch="unix" timeUnit="millisecond"/>
			        <field name="stampedAt" id="9" type="int64" timeUnit="nanosecond"/>
			        <group name="legs" id="10">
			            <field name="legId" id="1" type="int32"/>
			            <field name="ratio" id="2" type="int32"/>
			        </group>
			        <data name="note" id="11" type="varStringEncoding"/>
			    </sbe:message>
			    <sbe:message name="Untimed" id="3">
			        <field name="at" id="1" type="int64"/>
			    </sbe:message>
			</sbe:messageSchema>
			""";

	private static final int OFFSET = 8;

	private static final BigDecimal PRICE = new BigDecimal("101.25");

	private static final BigDecimal FEE = new BigDecimal("0.50");

	private static final Ticker SYMBOL = new Ticker("AAPL");

	private static final Colour COLOUR = new Colour(255, 128, 0);

	private static final BigInteger UINT64_MAX = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);

	private static final Instant SENT_AT = Instant.ofEpochMilli(1_700_000_000_123L);

	private static final Instant STAMPED_AT = Instant.ofEpochSecond(1_700_000_000L, 123_456_789L);

	private static final Map<Integer, Leg> LEGS = legs(new Leg(7, 1), new Leg(3, -2));

	@Override
	public String description() {
		return "Bindings: specializations over primitive faces, the generic interface over a string and a byte array, "
				+ "and a binding on every other kind of component, handed its context";
	}

	@Override
	public String oracle() {
		return ORACLE;
	}

	@Override
	public List<RoundTrip<?>> roundTrips() {
		return List.of(
				new RoundTrip<>(
						"the rebate absent", new BindingsCodec(), new Bindings(PRICE, FEE, null, SYMBOL, COLOUR)
				),
				new RoundTrip<>(
						"the rebate present", new BindingsCodec(),
						new Bindings(PRICE, FEE, new BigDecimal("1.05"), SYMBOL, COLOUR)
				),
				new RoundTrip<>(
						"every kind bound, the optional enum absent, a count at uint64's top", new EverywhereCodec(),
						everywhere(null, new BigInteger("-5"), UINT64_MAX)
				),
				new RoundTrip<>(
						"the optional enum present, the counts at zero, the group empty", new EverywhereCodec(),
						new Everywhere(
								false, false, true, new Access(false, false),
								new Quote(new BigDecimal("0.00"), false), BigInteger.ZERO, BigInteger.ZERO,
								Instant.EPOCH,
								Instant.EPOCH, new LinkedHashMap<>(), new Note("")
						)
				)
		);
	}

	@Test
	void oneBindingTellsAnUnsignedFieldFromASignedOneByItsContext() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
		EverywhereCodec codec = new EverywhereCodec();
		codec.encode(everywhere(true, BigInteger.ONE.negate(), UINT64_MAX), buffer, OFFSET);
		EverywhereDecoder decoder = new EverywhereDecoder()
				.wrapAndApplyHeader(buffer, OFFSET, new MessageHeaderDecoder());

		assertThat(decoder.signedCount()).isEqualTo(-1L);
		assertThat(decoder.unsignedCount()).isEqualTo(-1L);
		assertThat(codec.decode(buffer, OFFSET).unsignedCount()).isEqualTo(UINT64_MAX);
		assertThatThrownBy(() -> codec.encode(everywhere(true, UINT64_MAX, UINT64_MAX), buffer, OFFSET))
				.isInstanceOf(ArithmeticException.class);
	}

	@Test
	void aBindingRefusesNamingTheFieldItsContextGives() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);

		assertThatThrownBy(
				() -> new EverywhereCodec()
						.encode(everywhere(true, BigInteger.ONE, BigInteger.ONE.negate()), buffer, OFFSET)
		).isInstanceOf(IllegalArgumentException.class).hasMessage("unsignedCount is out of uint64's range: -1");
	}

	@Test
	void aTimeBindingCountsInTheUnitItsFieldNames() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
		new EverywhereCodec().encode(everywhere(true, BigInteger.ONE, BigInteger.ONE), buffer, OFFSET);
		EverywhereDecoder decoder = new EverywhereDecoder()
				.wrapAndApplyHeader(buffer, OFFSET, new MessageHeaderDecoder());

		assertThat(decoder.sentAt()).isEqualTo(1_700_000_000_123L);
		assertThat(decoder.stampedAt()).isEqualTo(1_700_000_000_123_456_789L);
	}

	@Test
	void aFieldWithoutATimeUnitIsTheBindingsToRefuse() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);

		assertThatThrownBy(() -> new UntimedCodec().encode(new Untimed(Instant.EPOCH), buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("at has no timeUnit");
	}

	@Test
	void aBoundConstantIsCheckedAfterItsBinding() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
		Everywhere notLive = new Everywhere(
				true, null, false, new Access(true, false), new Quote(PRICE, true), BigInteger.ONE,
				BigInteger.ONE, SENT_AT, STAMPED_AT, LEGS, new Note("n")
		);

		assertThatThrownBy(() -> new EverywhereCodec().encode(notLive, buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("live is the constant YES");
	}

	@Test
	void aNullBoundGroupOrDataIsRefusedBeforeItsBinding() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
		Everywhere noLegs = new Everywhere(
				true, null, true, new Access(true, false), new Quote(PRICE, true), BigInteger.ONE,
				BigInteger.ONE, SENT_AT, STAMPED_AT, null, new Note("n")
		);
		Everywhere noNote = new Everywhere(
				true, null, true, new Access(true, false), new Quote(PRICE, true), BigInteger.ONE,
				BigInteger.ONE, SENT_AT, STAMPED_AT, LEGS, null
		);

		assertThatThrownBy(() -> new EverywhereCodec().encodedLength(noLegs))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("legs is required");
		assertThatThrownBy(() -> new EverywhereCodec().encode(noNote, buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("note is required");
	}

	private static Everywhere everywhere(@Nullable Boolean acknowledged, BigInteger signed, BigInteger unsigned) {
		return new Everywhere(
				true, acknowledged, true, new Access(true, false), new Quote(PRICE, true), signed, unsigned, SENT_AT,
				STAMPED_AT, LEGS, new Note("héllo")
		);
	}

	private static Map<Integer, Leg> legs(Leg... legs) {
		Map<Integer, Leg> map = new LinkedHashMap<>();
		for (Leg leg : legs) {
			map.put(leg.legId(), leg);
		}
		return map;
	}

	@Test
	void theCentsBindingsOwnExceptionPassesThroughUnwrapped() {
		BindingsCodec codec = new BindingsCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		Bindings tooManyDecimals = new Bindings(new BigDecimal("1.005"), FEE, null, SYMBOL, COLOUR);

		assertThatThrownBy(() -> codec.encode(tooManyDecimals, buffer, OFFSET))
				.isInstanceOf(ArithmeticException.class);
	}

	@Test
	void aNullPriceIsRefused() {
		BindingsCodec codec = new BindingsCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);

		assertThatThrownBy(() -> codec.encode(new Bindings(null, FEE, null, SYMBOL, COLOUR), buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("price is required");
	}

	@Test
	void aNullFeeIsRefused() {
		BindingsCodec codec = new BindingsCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);

		assertThatThrownBy(() -> codec.encode(new Bindings(PRICE, null, null, SYMBOL, COLOUR), buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("fee is required");
	}

	@Test
	void aNullSymbolIsRefused() {
		BindingsCodec codec = new BindingsCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);

		assertThatThrownBy(() -> codec.encode(new Bindings(PRICE, FEE, null, null, COLOUR), buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("symbol is required");
	}

	@Test
	void aNullColourIsRefused() {
		BindingsCodec codec = new BindingsCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);

		assertThatThrownBy(() -> codec.encode(new Bindings(PRICE, FEE, null, SYMBOL, null), buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("colour is required");
	}

	@Test
	void aSymbolLongerThanItsLengthIsRefused() {
		BindingsCodec codec = new BindingsCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		Bindings value = new Bindings(PRICE, FEE, null, new Ticker("TOOLONG"), COLOUR);

		assertThatThrownBy(() -> codec.encode(value, buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("symbol is longer than 6: TOOLONG");
	}

	@Test
	void aNonAsciiSymbolIsRefused() {
		BindingsCodec codec = new BindingsCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		Bindings value = new Bindings(PRICE, FEE, null, new Ticker("café"), COLOUR);

		assertThatThrownBy(() -> codec.encode(value, buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("symbol is not ASCII: café");
	}

	@Test
	void anotherTemplateIsRefused() {
		BindingsCodec codec = new BindingsCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		codec.encode(new Bindings(PRICE, FEE, null, SYMBOL, COLOUR), buffer, OFFSET);
		new MessageHeaderEncoder().wrap(buffer, OFFSET).templateId(2);

		assertThatThrownBy(() -> codec.decode(buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("not a Bindings: schemaId 1, templateId 2");
	}

	@Test
	void theBoundStageReadsEveryComponentThroughItsBinding() {
		assertReadsTheValue(new BindingsCodec(), new Bindings(PRICE, FEE, null, SYMBOL, COLOUR), (buffer, offset) -> {
			BindingsReader.RootBlockBound block = ((BindingsReader.RootBlock) new BindingsReader().wrap(buffer, offset)
					.next()).bound();
			return new Bindings(block.price(), block.fee(), block.rebate(), block.symbol(), block.colour());
		});
	}

	/**
	 * The group's binding over the whole map has nothing to apply to on a stage;
	 * each entry's components are read, and the map is the reader's to build.
	 */
	@Test
	void theBoundStagesReadEveryKindBoundTheConstantAndTheBoundVarData() {
		assertReadsTheValue(
				new EverywhereCodec(), everywhere(null, new BigInteger("-5"), UINT64_MAX), (buffer, offset) -> {
					EverywhereReader reader = new EverywhereReader().wrap(buffer, offset);
					EverywhereReader.RootBlockBound block = ((EverywhereReader.RootBlock) reader.next()).bound();
					EverywhereReader.Legs header = (EverywhereReader.Legs) reader.next();
					Map<Integer, Leg> legs = new LinkedHashMap<>();
					for (int i = 0; i < header.count(); i++) {
						EverywhereReader.LegsEntryBound leg = ((EverywhereReader.LegsEntry) reader.next()).bound();
						legs.put(leg.legId(), new Leg(leg.legId(), leg.ratio()));
					}
					Note note = ((EverywhereReader.Note) reader.next()).bound().value();
					return new Everywhere(
							block.urgent(), block.acknowledged(), block.live(), block.access(), block.quote(),
							block.signedCount(), block.unsignedCount(), block.sentAt(), block.stampedAt(), legs, note
					);
				}
		);
	}
}
