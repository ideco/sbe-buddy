package com.example.trading;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import net.concini.sbebuddy.Codec;

import com.example.trading.ExecutionReport.Fill;
import com.example.trading.NewOrder.Party;
import com.example.trading.NewOrder.PartySubId;

/**
 * The samples through the bound stages: encoded by their codecs and read back
 * component by component through the readers' {@code bound()}, walked by the
 * counts as a codec walks them.
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

	private static <T> UnsafeBuffer encode(Codec<T, ?> codec, T value) {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[OFFSET + codec.encodedLength(value) + OFFSET]);
		codec.encode(value, buffer, OFFSET);
		return buffer;
	}
}
