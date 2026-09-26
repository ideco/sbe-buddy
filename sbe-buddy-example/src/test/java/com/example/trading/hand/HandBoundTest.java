package com.example.trading.hand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import com.example.trading.CancelRejectCodec;
import com.example.trading.CancelRejectReader;
import com.example.trading.CancelRejectWriter;
import com.example.trading.CxlRejReason;
import com.example.trading.ExecInst;
import com.example.trading.NewOrder;
import com.example.trading.NewOrder.Party;
import com.example.trading.NewOrder.PartySubId;
import com.example.trading.NewOrderCodec;
import com.example.trading.NewOrderReader;
import com.example.trading.NewOrderReader.PartiesEntry;
import com.example.trading.NewOrderReader.PartySubIdsEntry;
import com.example.trading.NewOrderReader.RootBlock;
import com.example.trading.NewOrderWriter;
import com.example.trading.OrdStatus;
import com.example.trading.OrdType;
import com.example.trading.PartyRole;
import com.example.trading.Samples;
import com.example.trading.SecurityIdSource;
import com.example.trading.Side;
import com.example.trading.TimeInForce;

/**
 * The bound stages against the records: what the reader's {@code bound()} gives
 * is the sample, component by component, absent ones {@code null}; what the
 * writer's bound chain writes is the codec's bytes; the chain hops between wire
 * and bound; and a bound setter refuses what the codec refuses, naming the
 * field.
 */
final class HandBoundTest {

	private static final int OFFSET = 16;

	private static final NewOrderCodec NEW_ORDER = new NewOrderCodec();

	private static final CancelRejectCodec CANCEL_REJECT = new CancelRejectCodec();

	@Test
	void theBoundStagesReadTheRecordsComponents() {
		UnsafeBuffer buffer = encode(Samples.LIMIT_ORDER);
		List<Party> parties = new ArrayList<>();
		List<PartySubId> subIds = new ArrayList<>();

		for (NewOrderReader.Stage stage : new NewOrderReader().wrap(buffer, OFFSET)) {
			switch (stage) {
				case RootBlock block -> {
					NewOrderReader.RootBlockBound bound = block.bound();
					assertThat(bound.clOrdId()).isEqualTo(Samples.ORDER);
					assertThat(bound.account()).isEqualTo("ACCT-0001");
					assertThat(bound.symbol()).isEqualTo("ACME");
					assertThat(bound.side()).isEqualTo(Side.BUY);
					assertThat(bound.ordType()).isEqualTo(OrdType.LIMIT);
					assertThat(bound.timeInForce()).isEqualTo(TimeInForce.DAY);
					assertThat(bound.execInst()).isEqualTo(EnumSet.of(ExecInst.POST_ONLY));
					assertThat(bound.securityIdSource()).isEqualTo(SecurityIdSource.EXCHANGE_SYMBOL);
					assertThat(bound.transactTime()).isEqualTo(Samples.TIME);
					assertThat(bound.orderQty()).isEqualTo(700L);
					assertThat(bound.price()).isEqualTo(new BigDecimal("99.6100"));
					assertThat(bound.stopPx()).isNull();
					assertThat(bound.wire()).isSameAs(block);
				}
				case PartiesEntry party -> {
					NewOrderReader.PartiesEntryBound bound = party.bound();
					assertThat(bound.index()).isEqualTo(parties.size());
					parties.add(new Party(bound.partyId(), bound.partyRole(), List.of()));
				}
				case PartySubIdsEntry id ->
					subIds.add(new PartySubId(id.bound().partySubId(), id.bound().partySubIdType()));
				default -> {
				}
			}
		}

		assertThat(parties).containsExactly(
				new Party("FIRM-A", PartyRole.EXECUTING_FIRM, List.of()),
				new Party("TRADER-12", PartyRole.ENTERING_TRADER, List.of())
		);
		assertThat(subIds).containsExactly(new PartySubId("DESK-7", (short) 4));
	}

	@Test
	void anUnsetOptionalReadsNullOnTheBoundStage() {
		UnsafeBuffer buffer = encode(Samples.MARKET_ORDER);
		NewOrderReader reader = new NewOrderReader().wrap(buffer, OFFSET);

		NewOrderReader.RootBlockBound bound = ((RootBlock) reader.next()).bound();

		assertThat(bound.price()).isNull();
		assertThat(bound.stopPx()).isNull();
		assertThat(bound.execInst()).isEmpty();
	}

	@Test
	void aBoundStageAnswersOnlyWhileItsStageIsOpen() {
		UnsafeBuffer buffer = encode(Samples.LIMIT_ORDER);
		NewOrderReader reader = new NewOrderReader().wrap(buffer, OFFSET);
		reader.next();
		reader.next();
		NewOrderReader.PartiesEntryBound party = ((PartiesEntry) reader.next()).bound();
		assertThat(party.partyId()).isEqualTo("FIRM-A");

		reader.rewind();

		assertThatThrownBy(party::partyId).isInstanceOf(IllegalStateException.class)
				.hasMessage("PartiesEntry is not open");
	}

	@Test
	void theBoundTextIsTheRecordsString() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
		CANCEL_REJECT.encode(Samples.CANCEL_REJECT, buffer, OFFSET);
		CancelRejectReader reader = new CancelRejectReader().wrap(buffer, OFFSET);

		CancelRejectReader.RootBlockBound block = ((CancelRejectReader.RootBlock) reader.next()).bound();
		assertThat(block.ordStatus()).isEqualTo(OrdStatus.FILLED);
		assertThat(block.cxlRejReason()).isEqualTo(CxlRejReason.TOO_LATE);
		CancelRejectReader.TextBound text = ((CancelRejectReader.Text) reader.next()).bound();

		assertThat(text.value()).isEqualTo("Ordre déjà exécuté").isEqualTo(text.value());
		assertThat(block.orderId()).isEqualTo("VENUE-0000000042");
	}

	@Test
	void theBoundChainWritesTheCodecsBytes() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);

		int length = new NewOrderWriter().wrap(buffer, OFFSET).bound()
				.clOrdId(Samples.ORDER)
				.account("ACCT-0001")
				.symbol("ACME")
				.side(Side.BUY)
				.ordType(OrdType.LIMIT)
				.timeInForce(TimeInForce.DAY)
				.execInst(EnumSet.of(ExecInst.POST_ONLY))
				.transactTime(Samples.TIME)
				.orderQty(700)
				.price(new BigDecimal("99.6100"))
				.stopPx(null)
				.parties()
				.entry().bound().partyId("FIRM-A").partyRole(PartyRole.EXECUTING_FIRM)
				.partySubIds()
				.entry().bound().partySubId("DESK-7").partySubIdType((short) 4)
				.end()
				.entry().bound().partyId("TRADER-12").partyRole(PartyRole.ENTERING_TRADER)
				.partySubIds()
				.end()
				.end()
				.length();

		assertThat(length).isEqualTo(NEW_ORDER.encodedLength(Samples.LIMIT_ORDER));
		assertThat(NEW_ORDER.decode(buffer, OFFSET)).isEqualTo(Samples.LIMIT_ORDER);
		assertThat(bytes(buffer, length)).isEqualTo(bytes(encode(Samples.LIMIT_ORDER), length));
	}

	@Test
	void theChainHopsBetweenWireAndBound() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);

		new NewOrderWriter().wrap(buffer, OFFSET)
				.clOrdId(Samples.ORDER)
				.bound().account("ACCT-0001")
				.wire().symbol("ACME")
				.side(com.example.trading.sbe.Side.SELL) // the wire face takes sbe-tool's enum
				.bound().ordType(OrdType.MARKET)
				.timeInForce(TimeInForce.IMMEDIATE_OR_CANCEL)
				.wire().execInst().end()
				.bound().transactTime(Samples.TIME)
				.orderQty(300)
				.parties().end()
				.length();

		assertThat(NEW_ORDER.decode(buffer, OFFSET)).isEqualTo(Samples.MARKET_ORDER);
	}

	@Test
	void aBoundCancelRejectWritesTheCodecsBytes() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);

		int length = new CancelRejectWriter().wrap(buffer, OFFSET).bound()
				.orderId("VENUE-0000000042")
				.clOrdId(Samples.CANCEL)
				.origClOrdId(Samples.REPLACEMENT)
				.ordStatus(OrdStatus.FILLED)
				.cxlRejReason(CxlRejReason.TOO_LATE)
				.text(Samples.CANCEL_REJECT.text())
				.length();

		UnsafeBuffer expected = new UnsafeBuffer(new byte[256]);
		CANCEL_REJECT.encode(Samples.CANCEL_REJECT, expected, OFFSET);

		assertThat(CANCEL_REJECT.decode(buffer, OFFSET)).isEqualTo(Samples.CANCEL_REJECT);
		assertThat(bytes(buffer, length)).isEqualTo(bytes(expected, length));
	}

	@Test
	void aBoundSetterRefusesWhatTheCodecRefusesNamingTheField() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
		NewOrderWriter.RootBlockClOrdIdBound start = new NewOrderWriter().wrap(buffer, OFFSET).bound();

		assertThatThrownBy(() -> start.clOrdId(null)).isInstanceOf(IllegalArgumentException.class)
				.hasMessage("clOrdId is required");
		assertThatThrownBy(() -> start.clOrdId("ORD-000000000000000001")).isInstanceOf(IllegalArgumentException.class)
				.hasMessage("clOrdId is longer than 20: ORD-000000000000000001");

		NewOrderWriter.RootBlockBound block = start.clOrdId(Samples.ORDER).account("ACCT-0001").symbol("ACME")
				.side(Side.BUY).ordType(OrdType.LIMIT).timeInForce(TimeInForce.DAY)
				.execInst(EnumSet.noneOf(ExecInst.class)).transactTime(Samples.TIME).orderQty(700);

		assertThatThrownBy(() -> block.price(new BigDecimal("99.61005")))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("price has no wire form with 4 decimals: 99.61005");
		assertThatThrownBy(
				() -> new CancelRejectWriter().wrap(buffer, OFFSET).bound().orderId("x")
						.clOrdId("y").origClOrdId("z").ordStatus(OrdStatus.UNKNOWN)
		)
				.isInstanceOf(IllegalArgumentException.class).hasMessage("OrdStatus.UNKNOWN has no wire form");
		assertThatThrownBy(
				() -> new CancelRejectWriter().wrap(buffer, OFFSET).bound().orderId("x")
						.clOrdId("y").origClOrdId("z").ordStatus(OrdStatus.FILLED).cxlRejReason(CxlRejReason.OTHER)
						.text("Ordre — refusé")
		).isInstanceOf(IllegalArgumentException.class)
				.hasMessage("text cannot be written in ISO-8859-1: Ordre — refusé");
	}

	private static UnsafeBuffer encode(NewOrder order) {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
		NEW_ORDER.encode(order, buffer, OFFSET);
		return buffer;
	}

	private static byte[] bytes(UnsafeBuffer buffer, int length) {
		return Arrays.copyOfRange(buffer.byteArray(), OFFSET, OFFSET + length);
	}
}
