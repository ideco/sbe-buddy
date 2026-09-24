package com.example.trading;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

/**
 * The session's unions: each direction's own, and one over both. Each takes its
 * messages and tells the other direction's from them by the header alone; a
 * gateway routes a mixed stream through {@code canDecode}, and a journal reads
 * every message and switches over the two directions.
 */
final class UnionsTest {

	@Test
	void eachDirectionsUnionTakesItsOwnMessagesAndNotTheOthers() {
		OrderEntryCodec entries = new OrderEntryCodec();
		OrderEventCodec events = new OrderEventCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[512]);

		for (OrderEntry entry : Samples.ENTRIES) {
			entries.encode(entry, buffer, 0);
			assertThat(entries.decode(buffer, 0)).isEqualTo(entry);
			assertThat(events.canDecode(buffer, 0)).as("an event codec on %s", entry).isFalse();
		}
		for (OrderEvent event : Samples.EVENTS) {
			events.encode(event, buffer, 0);
			assertThat(events.decode(buffer, 0)).isEqualTo(event);
			assertThat(entries.canDecode(buffer, 0)).as("an entry codec on %s", event).isFalse();
		}
	}

	@Test
	void aGatewayRoutesAMixedStreamByCanDecodeAlone() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[4096]);
		TradingMessageCodec writer = new TradingMessageCodec();
		List<TradingMessage> sent = List.of(
				Samples.LIMIT_ORDER, Samples.ACKNOWLEDGEMENT, Samples.REPLACE, Samples.FILL, Samples.CANCEL_ORDER,
				Samples.CANCEL_REJECT, Samples.MARKET_ORDER, Samples.REJECT
		);
		int end = 0;
		for (TradingMessage message : sent) {
			end += writer.encode(message, buffer, end);
		}

		OrderEntryCodec entries = new OrderEntryCodec();
		OrderEventCodec events = new OrderEventCodec();
		List<TradingMessage> toVenue = new ArrayList<>();
		List<TradingMessage> toClient = new ArrayList<>();
		for (int offset = 0; offset < end;) {
			if (entries.canDecode(buffer, offset)) {
				toVenue.add(entries.decode(buffer, offset));
				offset += entries.lastDecodedLength();
			} else {
				toClient.add(events.decode(buffer, offset));
				offset += events.lastDecodedLength();
			}
		}

		assertThat(toVenue)
				.containsExactly(Samples.LIMIT_ORDER, Samples.REPLACE, Samples.CANCEL_ORDER, Samples.MARKET_ORDER);
		assertThat(toClient)
				.containsExactly(Samples.ACKNOWLEDGEMENT, Samples.FILL, Samples.CANCEL_REJECT, Samples.REJECT);
	}

	@Test
	void aJournalSwitchesOverTheTwoDirections() {
		TradingMessageCodec codec = new TradingMessageCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[512]);
		List<String> lines = new ArrayList<>();

		for (TradingMessage message : List.<TradingMessage>of(Samples.LIMIT_ORDER, Samples.FILL, Samples.REJECT)) {
			codec.encode(message, buffer, 0);
			lines.add(journal(codec.decode(buffer, 0)));
		}

		assertThat(lines).containsExactly(
				"in ORD-0000000000000001", "out report EXEC-00000000007", "out reject of 17"
		);
	}

	private static String journal(TradingMessage message) {
		return switch (message) {
			case OrderEntry entry -> "in " + entry.clOrdId();
			case OrderEvent event -> "out " + switch (event) {
				case ExecutionReport report -> "report " + report.execId();
				case CancelReject reject -> "cancel reject of " + reject.origClOrdId();
				case Reject reject -> "reject of " + reject.refSeqNum();
			};
		};
	}
}
