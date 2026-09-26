package com.example.trading.hand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import com.example.trading.CancelRejectCodec;
import com.example.trading.CancelRejectWriter;
import com.example.trading.ExecInstWriter;
import com.example.trading.NewOrder;
import com.example.trading.NewOrderCodec;
import com.example.trading.NewOrderWriter;
import com.example.trading.NewOrderWriter.RootBlock;
import com.example.trading.Samples;
import com.example.trading.sbe.CxlRejReason;
import com.example.trading.sbe.OrdStatus;
import com.example.trading.sbe.OrdType;
import com.example.trading.sbe.PartyRole;
import com.example.trading.sbe.Side;
import com.example.trading.sbe.TimeInForce;

/**
 * The generated writers against the codecs, as the hand-written ones were held:
 * a chain writes the same bytes the codec writes for the sample, an optional
 * field left unset is null, an empty group is a header, and a kept stage cannot
 * write out of order.
 *
 * <pre>
 * // does not compile: length() before the group, and the var-data without it
 * new NewOrderWriter().wrap(buffer, 0).clOrdId("x").length();
 * new CancelRejectWriter().wrap(buffer, 0).orderId("x").text("why");
 * // does not compile: a required field left out, a nested group not opened
 * new NewOrderWriter().wrap(buffer, 0).clOrdId("x").symbol("ACME");
 * block.parties().entry().partyId("FIRM-A").partyRole(PartyRole.EXECUTING_FIRM).entry();
 * </pre>
 */
final class HandWriterTest {

	private static final int OFFSET = 16;

	private static final NewOrderCodec NEW_ORDER = new NewOrderCodec();

	private static final CancelRejectCodec CANCEL_REJECT = new CancelRejectCodec();

	@Test
	void aLimitOrderWrittenByTheChainIsTheCodecsBytes() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);

		int length = new NewOrderWriter().wrap(buffer, OFFSET)
				.clOrdId(Samples.ORDER)
				.account("ACCT-0001")
				.symbol("ACME")
				.side(Side.BUY)
				.ordType(OrdType.LIMIT)
				.timeInForce(TimeInForce.DAY)
				.execInst().postOnly(true).end()
				.transactTime(nanos())
				.orderQty().mantissa(700)
				.price().mantissa(996_100L)
				.parties()
				.entry().partyId("FIRM-A").partyRole(PartyRole.EXECUTING_FIRM)
				.partySubIds()
				.entry().partySubId("DESK-7").partySubIdType((short) 4)
				.end()
				.entry().partyId("TRADER-12").partyRole(PartyRole.ENTERING_TRADER)
				.partySubIds()
				.end()
				.end()
				.length();

		assertThat(length).isEqualTo(NEW_ORDER.encodedLength(Samples.LIMIT_ORDER));
		assertThat(NEW_ORDER.decode(buffer, OFFSET)).isEqualTo(Samples.LIMIT_ORDER);
		assertThat(bytes(buffer, length)).isEqualTo(bytes(encode(Samples.LIMIT_ORDER), length));
	}

	@Test
	void whatTheChainDoesNotSetIsNullOnTheWire() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);

		int length = new NewOrderWriter().wrap(buffer, OFFSET)
				.clOrdId(Samples.ORDER)
				.account("ACCT-0001")
				.symbol("ACME")
				.side(Side.SELL)
				.ordType(OrdType.MARKET)
				.timeInForce(TimeInForce.IMMEDIATE_OR_CANCEL)
				.execInst().end()
				.transactTime(nanos())
				.orderQty().mantissa(300)
				.parties().end()
				.length();

		assertThat(NEW_ORDER.decode(buffer, OFFSET)).isEqualTo(Samples.MARKET_ORDER);
		assertThat(bytes(buffer, length)).isEqualTo(bytes(encode(Samples.MARKET_ORDER), length));
	}

	@Test
	void anOptionalCompositeIsSetNullByItsOwnStep() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);

		new NewOrderWriter().wrap(buffer, OFFSET)
				.clOrdId(Samples.ORDER)
				.account("ACCT-0001")
				.symbol("ACME")
				.side(Side.SELL)
				.ordType(OrdType.MARKET)
				.timeInForce(TimeInForce.IMMEDIATE_OR_CANCEL)
				.execInst().end()
				.transactTime(nanos())
				.orderQty().mantissa(300)
				.price().mantissaNull()
				.stopPx().mantissaNull()
				.parties().end()
				.length();

		assertThat(NEW_ORDER.decode(buffer, OFFSET)).isEqualTo(Samples.MARKET_ORDER);
	}

	@Test
	void aCancelRejectWrittenByTheChainIsTheCodecsBytes() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);

		int length = new CancelRejectWriter().wrap(buffer, OFFSET)
				.orderId("VENUE-0000000042")
				.clOrdId(Samples.CANCEL)
				.origClOrdId(Samples.REPLACEMENT)
				.ordStatus(OrdStatus.FILLED)
				.cxlRejReason(CxlRejReason.TOO_LATE)
				.text(Samples.CANCEL_REJECT.text())
				.length();

		UnsafeBuffer expected = new UnsafeBuffer(new byte[256]);
		CANCEL_REJECT.encode(Samples.CANCEL_REJECT, expected, OFFSET);

		assertThat(length).isEqualTo(CANCEL_REJECT.encodedLength(Samples.CANCEL_REJECT));
		assertThat(CANCEL_REJECT.decode(buffer, OFFSET)).isEqualTo(Samples.CANCEL_REJECT);
		assertThat(bytes(buffer, length)).isEqualTo(bytes(expected, length));
	}

	@Test
	void theHeadersOwnMembersAreTheCallers() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
		NewOrderWriter writer = new NewOrderWriter();

		writer.wrap(buffer, OFFSET);
		writer.header().sequenceNumber(42L);

		assertThat(NEW_ORDER.decodeHeader(buffer, OFFSET).sequenceNumber()).isEqualTo(42L);
	}

	@Test
	void withoutAHeaderGivenItsOwnMembersAreNull() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);

		new NewOrderWriter().wrap(buffer, OFFSET);

		assertThat(NEW_ORDER.decodeHeader(buffer, OFFSET).sequenceNumber()).isEqualTo(0xFFFF_FFFFL);
	}

	@Test
	void aKeptStageCannotWriteOnceTheWriterMovedOn() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);

		RootBlock block = new NewOrderWriter().wrap(buffer, OFFSET)
				.clOrdId(Samples.ORDER)
				.account("ACCT-0001")
				.symbol("ACME")
				.side(Side.SELL)
				.ordType(OrdType.MARKET)
				.timeInForce(TimeInForce.IMMEDIATE_OR_CANCEL)
				.execInst().end()
				.transactTime(nanos())
				.orderQty().mantissa(300);
		NewOrderWriter.Parties parties = block.parties();

		assertThatThrownBy(block::parties).isInstanceOf(IllegalStateException.class)
				.hasMessage("RootBlock is not the current stage");
		parties.end();
		assertThatThrownBy(parties::entry).isInstanceOf(IllegalStateException.class)
				.hasMessage("Parties is not the current stage");
	}

	@Test
	void aSubChainEndsOnce() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
		NewOrderWriter writer = new NewOrderWriter();
		ExecInstWriter<NewOrderWriter.RootBlockTransactTime> execInst = writer.wrap(buffer, OFFSET)
				.clOrdId(Samples.ORDER)
				.account("ACCT-0001")
				.symbol("ACME")
				.side(Side.SELL)
				.ordType(OrdType.MARKET)
				.timeInForce(TimeInForce.IMMEDIATE_OR_CANCEL)
				.execInst();
		execInst.end();

		assertThatThrownBy(execInst::end).isInstanceOf(IllegalStateException.class)
				.hasMessage("ExecInstWriter has ended");
	}

	private static UnsafeBuffer encode(NewOrder order) {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
		NEW_ORDER.encode(order, buffer, OFFSET);
		return buffer;
	}

	private static byte[] bytes(UnsafeBuffer buffer, int length) {
		return Arrays.copyOfRange(buffer.byteArray(), OFFSET, OFFSET + length);
	}

	private static long nanos() {
		return Samples.TIME.getEpochSecond() * 1_000_000_000L + Samples.TIME.getNano();
	}
}
