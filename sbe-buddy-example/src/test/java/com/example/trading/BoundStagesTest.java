package com.example.trading;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import net.concini.sbebuddy.Codec;

import com.example.trading.ExecutionReport.Fill;
import com.example.trading.NewOrder.Party;
import com.example.trading.NewOrder.PartySubId;

/**
 * The samples through the bound stages: encoded by their codecs and read back
 * component by component through the readers' {@code bound()}, walked by the
 * counts as a codec walks them; and written through the writers' bound chains,
 * a loop of {@code entry().bound()} per group, into the codecs' bytes.
 */
final class BoundStagesTest {

	private static final int OFFSET = 16;

	@Test
	void aFillReadsThroughTheBoundStages() {
		assertThat(readExecutionReport(encode(new ExecutionReportCodec(), Samples.FILL))).isEqualTo(Samples.FILL);
	}

	@Test
	void anAcknowledgementReadsThroughTheBoundStagesItsAbsentOptionalsNull() {
		ExecutionReport read = readExecutionReport(encode(new ExecutionReportCodec(), Samples.ACKNOWLEDGEMENT));

		assertThat(read).isEqualTo(Samples.ACKNOWLEDGEMENT);
		assertThat(read.lastPx()).isNull();
		assertThat(read.addedLiquidity()).isNull();
	}

	@Test
	void aLimitOrderReadsThroughTheBoundStages() {
		assertThat(readNewOrder(encode(new NewOrderCodec(), Samples.LIMIT_ORDER))).isEqualTo(Samples.LIMIT_ORDER);
	}

	@Test
	void aCancelRejectReadsThroughTheBoundStages() {
		UnsafeBuffer buffer = encode(new CancelRejectCodec(), Samples.CANCEL_REJECT);
		CancelRejectReader reader = new CancelRejectReader().wrap(buffer, OFFSET);

		CancelRejectReader.RootBlockBound block = ((CancelRejectReader.RootBlock) reader.next()).bound();
		CancelRejectReader.TextBound text = ((CancelRejectReader.Text) reader.next()).bound();

		assertThat(
				new CancelReject(
						block.orderId(), block.clOrdId(), block.origClOrdId(), block.ordStatus(), block.cxlRejReason(),
						text.value()
				)
		).isEqualTo(Samples.CANCEL_REJECT);
	}

	@Test
	void aFillIsWrittenThroughTheBoundChain() {
		assertWritesTheCodecsBytes(
				new ExecutionReportCodec(), Samples.FILL,
				(buffer, offset) -> writeExecutionReport(Samples.FILL, buffer, offset)
		);
	}

	@Test
	void anAcknowledgementIsWrittenThroughTheBoundChain() {
		assertWritesTheCodecsBytes(
				new ExecutionReportCodec(), Samples.ACKNOWLEDGEMENT,
				(buffer, offset) -> writeExecutionReport(Samples.ACKNOWLEDGEMENT, buffer, offset)
		);
	}

	@Test
	void aLimitOrderIsWrittenThroughTheBoundChain() {
		NewOrder order = Samples.LIMIT_ORDER;

		assertWritesTheCodecsBytes(new NewOrderCodec(), order, (buffer, offset) -> {
			NewOrderWriter.Parties parties = new NewOrderWriter().wrap(buffer, offset).bound()
					.clOrdId(order.clOrdId())
					.account(order.account())
					.symbol(order.symbol())
					.side(order.side())
					.ordType(order.ordType())
					.timeInForce(order.timeInForce())
					.execInst(order.execInst())
					.transactTime(order.transactTime())
					.orderQty(order.orderQty())
					.price(order.price())
					.stopPx(order.stopPx())
					.parties();
			for (Party party : order.parties()) {
				NewOrderWriter.PartySubIds ids = parties.entry().bound()
						.partyId(party.partyId())
						.partyRole(party.partyRole())
						.partySubIds();
				for (PartySubId id : party.partySubIds()) {
					ids = ids.entry().bound().partySubId(id.partySubId()).partySubIdType(id.partySubIdType());
				}
				parties = ids.end();
			}
			return parties.end().length();
		});
	}

	@Test
	void aCancelRejectIsWrittenThroughTheBoundChain() {
		CancelReject reject = Samples.CANCEL_REJECT;

		assertWritesTheCodecsBytes(
				new CancelRejectCodec(), reject,
				(buffer, offset) -> new CancelRejectWriter().wrap(buffer, offset).bound()
						.orderId(reject.orderId())
						.clOrdId(reject.clOrdId())
						.origClOrdId(reject.origClOrdId())
						.ordStatus(reject.ordStatus())
						.cxlRejReason(reject.cxlRejReason())
						.text(reject.text())
						.length()
		);
	}

	/** As a codec reads it: each stage cast, each group's entries counted. */
	private static ExecutionReport readExecutionReport(UnsafeBuffer buffer) {
		ExecutionReportReader reader = new ExecutionReportReader().wrap(buffer, OFFSET);
		ExecutionReportReader.RootBlockBound block = ((ExecutionReportReader.RootBlock) reader.next()).bound();
		ExecutionReportReader.Fills header = (ExecutionReportReader.Fills) reader.next();
		Map<String, Fill> fills = new LinkedHashMap<>();
		for (int i = 0; i < header.count(); i++) {
			ExecutionReportReader.FillsEntryBound fill = ((ExecutionReportReader.FillsEntry) reader.next()).bound();
			fills.put(fill.fillExecId(), new Fill(fill.fillExecId(), fill.fillPx(), fill.fillQty()));
		}
		assertThat(reader.hasNext()).isFalse();
		return new ExecutionReport(
				block.orderId(), block.clOrdId(), block.execId(), block.execType(), block.ordStatus(), block.symbol(),
				block.side(), block.leavesQty(), block.cumQty(), block.lastQty(), block.lastPx(),
				block.addedLiquidity(), block.tradeDate(), block.maturity(), block.transactTime(), fills
		);
	}

	private static NewOrder readNewOrder(UnsafeBuffer buffer) {
		NewOrderReader reader = new NewOrderReader().wrap(buffer, OFFSET);
		NewOrderReader.RootBlockBound block = ((NewOrderReader.RootBlock) reader.next()).bound();
		NewOrderReader.Parties parties = (NewOrderReader.Parties) reader.next();
		List<Party> partyList = new ArrayList<>();
		for (int i = 0; i < parties.count(); i++) {
			NewOrderReader.PartiesEntryBound party = ((NewOrderReader.PartiesEntry) reader.next()).bound();
			NewOrderReader.PartySubIds ids = (NewOrderReader.PartySubIds) reader.next();
			List<PartySubId> idList = new ArrayList<>();
			for (int j = 0; j < ids.count(); j++) {
				NewOrderReader.PartySubIdsEntryBound id = ((NewOrderReader.PartySubIdsEntry) reader.next()).bound();
				idList.add(new PartySubId(id.partySubId(), id.partySubIdType()));
			}
			partyList.add(new Party(party.partyId(), party.partyRole(), idList));
		}
		return new NewOrder(
				block.clOrdId(), block.account(), block.symbol(), block.side(), block.ordType(), block.timeInForce(),
				block.execInst(), block.securityIdSource(), block.transactTime(), block.orderQty(), block.price(),
				block.stopPx(), partyList
		);
	}

	private static int writeExecutionReport(ExecutionReport report, MutableDirectBuffer buffer, int offset) {
		ExecutionReportWriter.Fills fills = new ExecutionReportWriter().wrap(buffer, offset).bound()
				.orderId(report.orderId())
				.clOrdId(report.clOrdId())
				.execId(report.execId())
				.execType(report.execType())
				.ordStatus(report.ordStatus())
				.symbol(report.symbol())
				.side(report.side())
				.leavesQty(report.leavesQty())
				.cumQty(report.cumQty())
				.lastQty(report.lastQty())
				.tradeDate(report.tradeDate())
				.maturity(report.maturity())
				.transactTime(report.transactTime())
				.lastPx(report.lastPx())
				.addedLiquidity(report.addedLiquidity())
				.fills();
		for (Fill fill : report.fills().values()) {
			fills = fills.entry().bound().fillExecId(fill.fillExecId()).fillPx(fill.fillPx()).fillQty(fill.fillQty());
		}
		return fills.end().length();
	}

	@FunctionalInterface
	private interface Chain {

		int write(MutableDirectBuffer buffer, int offset);
	}

	/**
	 * The chain writes the codec's length and bytes, leaving the bytes around them
	 * alone, and the codec reads the value back.
	 */
	private static <T> void assertWritesTheCodecsBytes(Codec<T, ?> codec, T value, Chain chain) {
		int capacity = OFFSET + codec.encodedLength(value) + OFFSET;
		UnsafeBuffer expected = new UnsafeBuffer(new byte[capacity]);
		UnsafeBuffer written = new UnsafeBuffer(new byte[capacity]);
		Arrays.fill(written.byteArray(), (byte) 0x5a);
		Arrays.fill(expected.byteArray(), (byte) 0x5a);

		int length = codec.encode(value, expected, OFFSET);

		assertThat(chain.write(written, OFFSET)).isEqualTo(length);
		assertThat(written.byteArray()).isEqualTo(expected.byteArray());
		assertThat(codec.decode(written, OFFSET)).isEqualTo(value);
	}

	private static <T> UnsafeBuffer encode(Codec<T, ?> codec, T value) {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[OFFSET + codec.encodedLength(value) + OFFSET]);
		codec.encode(value, buffer, OFFSET);
		return buffer;
	}
}
