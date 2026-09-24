package com.example.trading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import com.example.trading.xmlref.CancelRejectEncoder;
import com.example.trading.xmlref.ExecutionReportDecoder;
import com.example.trading.xmlref.ExecutionReportEncoder;
import com.example.trading.xmlref.MonthYearEncoder;
import com.example.trading.xmlref.NewOrderDecoder;
import com.example.trading.xmlref.NewOrderEncoder;
import com.example.trading.xmlref.PriceEncodingDecoder;
import com.example.trading.xmlref.PriceEncodingEncoder;
import com.example.trading.xmlref.RejectEncoder;
import com.example.trading.xmlref.ReplaceOrderDecoder;
import com.example.trading.xmlref.SessionHeaderDecoder;
import com.example.trading.xmlref.SessionHeaderEncoder;

/**
 * The bytes are sbe-tool's: what the codecs write, the flyweights sbe-tool
 * generated from the oracle read, and the reverse. The bindings meet the wire
 * here: prices as mantissas in ten-thousandths and a market order's as null
 * mantissas, quantities as whole units, times as nanoseconds, dates as days,
 * the maturity as a year and a month, liquidity as its enum, fills as a group.
 * The constant is on no wire, the retired field is written as its null value,
 * the sequence number is the header's. The reference enums share their simple
 * names with the example's own and are qualified.
 */
final class ReferenceFlyweightsTest {

	private static final int OFFSET = 16;

	private static final long NANOS = Samples.TIME.getEpochSecond() * 1_000_000_000L + Samples.TIME.getNano();

	@Test
	void theReferenceDecoderReadsANewOrderTheCodecWrites() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
		new NewOrderCodec().encode(Samples.LIMIT_ORDER, new SessionHeader(0, 0, 0, 0, 4711), buffer, OFFSET);

		SessionHeaderDecoder header = new SessionHeaderDecoder().wrap(buffer, OFFSET);
		NewOrderDecoder decoder = new NewOrderDecoder().wrapAndApplyHeader(buffer, OFFSET, new SessionHeaderDecoder());

		assertThat(header.sequenceNumber()).isEqualTo(4711);
		assertThat(decoder.clOrdId()).isEqualTo(Samples.ORDER);
		assertThat(decoder.account()).isEqualTo("ACCT-0001");
		assertThat(decoder.side()).isEqualTo(com.example.trading.xmlref.Side.BUY);
		assertThat(decoder.ordType()).isEqualTo(com.example.trading.xmlref.OrdType.LIMIT);
		assertThat(decoder.timeInForce()).isEqualTo(com.example.trading.xmlref.TimeInForce.DAY);
		assertThat(decoder.execInst().postOnly()).isTrue();
		assertThat(decoder.execInst().reduceOnly()).isFalse();
		assertThat(decoder.securityIdSource()).isEqualTo(com.example.trading.xmlref.SecurityIdSource.EXCHANGE_SYMBOL);
		assertThat(decoder.transactTime()).isEqualTo(NANOS);
		assertThat(decoder.orderQty().mantissa()).isEqualTo(700);
		assertThat(decoder.price().mantissa()).isEqualTo(996_100L);
		assertThat(decoder.stopPx().mantissa()).isEqualTo(PriceEncodingDecoder.mantissaNullValue());
		NewOrderDecoder.PartiesDecoder parties = decoder.parties();
		assertThat(parties.count()).isEqualTo(2);
		parties.next();
		assertThat(parties.partyId()).isEqualTo("FIRM-A");
		assertThat(parties.partyRole()).isEqualTo(com.example.trading.xmlref.PartyRole.EXECUTING_FIRM);
		NewOrderDecoder.PartiesDecoder.PartySubIdsDecoder subIds = parties.partySubIds();
		assertThat(subIds.count()).isEqualTo(1);
		assertThat(subIds.next().partySubId()).isEqualTo("DESK-7");
		assertThat(subIds.partySubIdType()).isEqualTo((short) 4);
		parties.next();
		assertThat(parties.partyId()).isEqualTo("TRADER-12");
		assertThat(parties.partySubIds().count()).isZero();
	}

	@Test
	void aMarketOrdersPricesAreNullMantissasOnTheWire() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
		new NewOrderCodec().encode(Samples.MARKET_ORDER, buffer, OFFSET);

		NewOrderDecoder decoder = new NewOrderDecoder().wrapAndApplyHeader(buffer, OFFSET, new SessionHeaderDecoder());

		assertThat(decoder.price().mantissa()).isEqualTo(PriceEncodingDecoder.mantissaNullValue());
		assertThat(decoder.stopPx().mantissa()).isEqualTo(PriceEncodingDecoder.mantissaNullValue());
		assertThat(decoder.parties().count()).isZero();
	}

	@Test
	void theRetiredFieldIsWrittenAsItsNullValue() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
		new ReplaceOrderCodec().encode(Samples.REPLACE, buffer, OFFSET);

		ReplaceOrderDecoder decoder = new ReplaceOrderDecoder()
				.wrapAndApplyHeader(buffer, OFFSET, new SessionHeaderDecoder());

		assertThat(decoder.locateReqd()).isEqualTo(ReplaceOrderDecoder.locateReqdNullValue());
		assertThat(decoder.price().mantissa()).isEqualTo(996_200L);
	}

	@Test
	void theReferenceDecoderReadsAnExecutionReportTheCodecWrites() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
		new ExecutionReportCodec().encode(Samples.FILL, buffer, OFFSET);

		ExecutionReportDecoder decoder = new ExecutionReportDecoder()
				.wrapAndApplyHeader(buffer, OFFSET, new SessionHeaderDecoder());

		assertThat(decoder.execType()).isEqualTo(com.example.trading.xmlref.ExecType.TRADE);
		assertThat(decoder.ordStatus()).isEqualTo(com.example.trading.xmlref.OrdStatus.PARTIALLY_FILLED);
		assertThat(decoder.leavesQty().mantissa()).isEqualTo(500);
		assertThat(decoder.lastPx().mantissa()).isEqualTo(996_100L);
		assertThat(decoder.lastLiquidity()).isEqualTo(com.example.trading.xmlref.LastLiquidity.ADDED);
		assertThat(decoder.tradeDate()).isEqualTo((int) Samples.FILL.tradeDate().toEpochDay());
		assertThat(decoder.maturity().year()).isEqualTo(2026);
		assertThat(decoder.maturity().month()).isEqualTo((short) 12);
		assertThat(decoder.maturity().day()).isEqualTo(com.example.trading.xmlref.MonthYearDecoder.dayNullValue());
		assertThat(decoder.transactTime()).isEqualTo(NANOS);
		ExecutionReportDecoder.FillsDecoder fills = decoder.fills();
		assertThat(fills.count()).isEqualTo(2);
		assertThat(fills.next().fillExecId()).isEqualTo("EXEC-00000000005");
		assertThat(fills.fillPx().mantissa()).isEqualTo(996_000L);
		assertThat(fills.fillQty().mantissa()).isEqualTo(150);
	}

	@Test
	void theCodecsReadWhatTheReferenceEncodersWrite() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[512]);

		writeFill(buffer, (byte) 'F');
		assertThat(new ExecutionReportCodec().decode(buffer, OFFSET)).isEqualTo(Samples.FILL);

		writeMarketOrder(buffer);
		assertThat(new NewOrderCodec().decode(buffer, OFFSET)).isEqualTo(Samples.MARKET_ORDER);

		new CancelRejectEncoder().wrapAndApplyHeader(buffer, OFFSET, new SessionHeaderEncoder())
				.orderId("VENUE-0000000042")
				.clOrdId(Samples.CANCEL)
				.origClOrdId(Samples.REPLACEMENT)
				.ordStatus(com.example.trading.xmlref.OrdStatus.FILLED)
				.cxlRejReason(com.example.trading.xmlref.CxlRejReason.TOO_LATE)
				.text("Ordre déjà exécuté");
		assertThat(new CancelRejectCodec().decode(buffer, OFFSET)).isEqualTo(Samples.CANCEL_REJECT);

		new RejectEncoder().wrapAndApplyHeader(buffer, OFFSET, new SessionHeaderEncoder())
				.refSeqNum(17)
				.businessRejectReason(com.example.trading.xmlref.BusinessRejectReason.NOT_AUTHORIZED)
				.text("Not authorised to trade ACME — ask your desk");
		assertThat(new RejectCodec().decode(buffer, OFFSET)).isEqualTo(Samples.REJECT);
	}

	@Test
	void anExecTypeTheSchemaDoesNotNameReadsAsTheUnknownConstant() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[512]);
		ExecutionReportCodec codec = new ExecutionReportCodec();
		writeFill(buffer, (byte) 'I');

		ExecutionReport report = codec.decode(buffer, OFFSET);

		assertThat(report.execType()).isEqualTo(ExecType.UNKNOWN);
		assertThatThrownBy(() -> codec.encode(report, buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("ExecType.UNKNOWN has no wire form");
	}

	@Test
	void aMaturityWithADayIsTheBindingsToRefuse() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[512]);
		ExecutionReportEncoder encoder = writeFill(buffer, (byte) 'F');
		encoder.maturity().day((short) 15);

		assertThatThrownBy(() -> new ExecutionReportCodec().decode(buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageStartingWith("maturity is no year and month alone");
	}

	/** {@link Samples#FILL} through the reference encoder, its exec type raw. */
	private static ExecutionReportEncoder writeFill(UnsafeBuffer buffer, byte execType) {
		ExecutionReportEncoder encoder = new ExecutionReportEncoder()
				.wrapAndApplyHeader(buffer, OFFSET, new SessionHeaderEncoder())
				.orderId("VENUE-0000000042")
				.clOrdId(Samples.ORDER)
				.execId("EXEC-00000000007")
				.ordStatus(com.example.trading.xmlref.OrdStatus.PARTIALLY_FILLED)
				.symbol("ACME")
				.side(com.example.trading.xmlref.Side.BUY);
		buffer.putByte(encoder.offset() + ExecutionReportEncoder.execTypeEncodingOffset(), execType);
		encoder.leavesQty().mantissa(500);
		encoder.cumQty().mantissa(200);
		encoder.lastQty().mantissa(50);
		encoder.lastPx().mantissa(996_100L);
		encoder.lastLiquidity(com.example.trading.xmlref.LastLiquidity.ADDED)
				.tradeDate((int) Samples.FILL.tradeDate().toEpochDay());
		encoder.maturity()
				.year(2026)
				.month((short) 12)
				.day(MonthYearEncoder.dayNullValue())
				.week(MonthYearEncoder.weekNullValue());
		encoder.transactTime(NANOS);
		ExecutionReportEncoder.FillsEncoder fills = encoder.fillsCount(2);
		fills.next().fillExecId("EXEC-00000000005").fillPx().mantissa(996_000L);
		fills.fillQty().mantissa(150);
		fills.next().fillExecId("EXEC-00000000006").fillPx().mantissa(996_100L);
		fills.fillQty().mantissa(50);
		return encoder;
	}

	/** {@link Samples#MARKET_ORDER} through the reference encoder. */
	private static void writeMarketOrder(UnsafeBuffer buffer) {
		NewOrderEncoder encoder = new NewOrderEncoder().wrapAndApplyHeader(buffer, OFFSET, new SessionHeaderEncoder())
				.clOrdId(Samples.ORDER)
				.account("ACCT-0001")
				.symbol("ACME")
				.side(com.example.trading.xmlref.Side.SELL)
				.ordType(com.example.trading.xmlref.OrdType.MARKET)
				.timeInForce(com.example.trading.xmlref.TimeInForce.IMMEDIATE_OR_CANCEL);
		encoder.execInst().clear();
		encoder.transactTime(NANOS);
		encoder.orderQty().mantissa(300);
		encoder.price().mantissa(PriceEncodingEncoder.mantissaNullValue());
		encoder.stopPx().mantissa(PriceEncodingEncoder.mantissaNullValue());
		encoder.partiesCount(0);
	}
}
