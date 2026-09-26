package com.example.trading.hand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import com.example.trading.CancelRejectCodec;
import com.example.trading.NewOrder;
import com.example.trading.NewOrderCodec;
import com.example.trading.Samples;
import com.example.trading.SessionHeader;
import com.example.trading.sbe.CancelRejectReader;
import com.example.trading.sbe.CxlRejReason;
import com.example.trading.sbe.NewOrderReader;
import com.example.trading.sbe.NewOrderReader.Parties;
import com.example.trading.sbe.NewOrderReader.PartiesEntry;
import com.example.trading.sbe.NewOrderReader.PartySubIds;
import com.example.trading.sbe.NewOrderReader.PartySubIdsEntry;
import com.example.trading.sbe.NewOrderReader.RootBlock;
import com.example.trading.sbe.OrdStatus;
import com.example.trading.sbe.OrdType;
import com.example.trading.sbe.PartyRole;
import com.example.trading.sbe.Side;
import com.example.trading.sbe.TimeInForce;

/**
 * The generated readers over what the codecs write: the stages come in wire
 * order, read what the samples hold, prune on {@code skip()}, answer while the
 * reader is inside them and not after, and a var-data reads as often as wanted.
 * Written against the hand-written readers of this package first, and passed
 * unchanged by the generated ones; the imports name them over this package's.
 */
final class HandReaderTest {

	private static final int OFFSET = 16;

	private static final NewOrderCodec NEW_ORDER = new NewOrderCodec();

	private static final CancelRejectCodec CANCEL_REJECT = new CancelRejectCodec();

	@Test
	void theStagesOfALimitOrderComeInWireOrder() {
		UnsafeBuffer buffer = encode(Samples.LIMIT_ORDER);

		List<String> stages = new ArrayList<>();
		for (NewOrderReader.Stage stage : new NewOrderReader().wrap(buffer, OFFSET)) {
			stages.add(stage.getClass().getSimpleName());
		}

		assertThat(stages).containsExactly(
				"RootBlock", "Parties", "PartiesEntry", "PartySubIds", "PartySubIdsEntry",
				"PartiesEntry", "PartySubIds"
		);
	}

	@Test
	void aMarketOrderHasAnEmptyGroupAsAHeaderAndNoEntries() {
		UnsafeBuffer buffer = encode(Samples.MARKET_ORDER);

		List<String> stages = new ArrayList<>();
		for (NewOrderReader.Stage stage : new NewOrderReader().wrap(buffer, OFFSET)) {
			stages.add(stage.getClass().getSimpleName());
		}

		assertThat(stages).containsExactly("RootBlock", "Parties");
	}

	@Test
	void everyFieldReadsWhatTheSampleHolds() {
		UnsafeBuffer buffer = encode(Samples.LIMIT_ORDER);
		NewOrderReader reader = new NewOrderReader().wrap(buffer, OFFSET);
		List<String> partyIds = new ArrayList<>();
		List<String> subIds = new ArrayList<>();

		for (NewOrderReader.Stage stage : reader) {
			switch (stage) {
				case RootBlock block -> {
					assertThat(block.clOrdId()).isEqualTo(Samples.ORDER);
					assertThat(block.account()).isEqualTo("ACCT-0001");
					assertThat(block.symbol()).isEqualTo("ACME");
					assertThat(block.side()).isEqualTo(Side.BUY);
					assertThat(block.ordType()).isEqualTo(OrdType.LIMIT);
					assertThat(block.timeInForce()).isEqualTo(TimeInForce.DAY);
					assertThat(block.execInst().postOnly()).isTrue();
					assertThat(block.execInst().reduceOnly()).isFalse();
					assertThat(block.transactTime()).isEqualTo(nanos(Samples.TIME));
					assertThat(block.orderQty().mantissa()).isEqualTo(700);
					assertThat(block.price().mantissa()).isEqualTo(996_100L);
					assertThat(block.stopPx().mantissa()).isEqualTo(Long.MIN_VALUE);
				}
				case Parties parties -> assertThat(parties.count()).isEqualTo(2);
				case PartiesEntry party -> {
					assertThat(party.index()).isEqualTo(partyIds.size());
					partyIds.add(party.partyId());
					assertThat(party.partyRole())
							.isEqualTo(party.index() == 0 ? PartyRole.EXECUTING_FIRM : PartyRole.ENTERING_TRADER);
				}
				case PartySubIds ids -> assertThat(ids.count()).isEqualTo(partyIds.size() == 1 ? 1 : 0);
				case PartySubIdsEntry id -> {
					assertThat(id.index()).isZero();
					subIds.add(id.partySubId());
					assertThat(id.partySubIdType()).isEqualTo((short) 4);
				}
			}
		}

		assertThat(partyIds).containsExactly("FIRM-A", "TRADER-12");
		assertThat(subIds).containsExactly("DESK-7");
		assertThat(reader.decodedLength()).isEqualTo(NEW_ORDER.encodedLength(Samples.LIMIT_ORDER));
	}

	@Test
	void skippingTheGroupHeaderPrunesItsEntries() {
		UnsafeBuffer buffer = encode(Samples.LIMIT_ORDER);
		NewOrderReader reader = new NewOrderReader().wrap(buffer, OFFSET);

		List<String> stages = new ArrayList<>();
		for (NewOrderReader.Stage stage : reader) {
			stages.add(stage.getClass().getSimpleName());
			if (stage instanceof Parties parties) {
				parties.skip();
			}
		}

		assertThat(stages).containsExactly("RootBlock", "Parties");
		assertThat(reader.decodedLength()).isEqualTo(NEW_ORDER.encodedLength(Samples.LIMIT_ORDER));
	}

	@Test
	void skippingAnEntryPrunesItsNestedGroupAndTheNextEntryComes() {
		UnsafeBuffer buffer = encode(Samples.LIMIT_ORDER);
		NewOrderReader reader = new NewOrderReader().wrap(buffer, OFFSET);

		List<String> stages = new ArrayList<>();
		for (NewOrderReader.Stage stage : reader) {
			stages.add(stage.getClass().getSimpleName());
			if (stage instanceof PartiesEntry party && party.index() == 0) {
				party.skip();
			}
		}

		assertThat(stages).containsExactly("RootBlock", "Parties", "PartiesEntry", "PartiesEntry", "PartySubIds");
		assertThat(reader.decodedLength()).isEqualTo(NEW_ORDER.encodedLength(Samples.LIMIT_ORDER));
	}

	@Test
	void skippingTheRootBlockEndsTheMessage() {
		UnsafeBuffer buffer = encode(Samples.LIMIT_ORDER);
		NewOrderReader reader = new NewOrderReader().wrap(buffer, OFFSET);

		RootBlock block = (RootBlock) reader.next();
		block.skip();

		assertThat(reader.hasNext()).isFalse();
		assertThatThrownBy(reader::next).isInstanceOf(NoSuchElementException.class);
		assertThat(reader.decodedLength()).isEqualTo(NEW_ORDER.encodedLength(Samples.LIMIT_ORDER));
		assertThat(block.clOrdId()).isEqualTo(Samples.ORDER);
	}

	@Test
	void onlyTheCurrentStageCanSkip() {
		UnsafeBuffer buffer = encode(Samples.LIMIT_ORDER);
		NewOrderReader reader = new NewOrderReader().wrap(buffer, OFFSET);

		RootBlock block = (RootBlock) reader.next();
		reader.next();

		assertThatThrownBy(block::skip).isInstanceOf(IllegalStateException.class)
				.hasMessage("RootBlock is not the current stage");
	}

	@Test
	void aStageAnswersWhileTheReaderIsInsideItAndNotAfter() {
		UnsafeBuffer buffer = encode(Samples.LIMIT_ORDER);
		NewOrderReader reader = new NewOrderReader().wrap(buffer, OFFSET);

		RootBlock block = (RootBlock) reader.next();
		Parties parties = (Parties) reader.next();
		PartiesEntry firstParty = (PartiesEntry) reader.next();
		PartySubIds firstSubIds = (PartySubIds) reader.next();
		PartySubIdsEntry subId = (PartySubIdsEntry) reader.next();

		// inside the entry's nested group, the entry and everything above it answer
		assertThat(firstParty.partyId()).isEqualTo("FIRM-A");
		assertThat(parties.count()).isEqualTo(2);
		assertThat(block.symbol()).isEqualTo("ACME");
		assertThat(subId.partySubId()).isEqualTo("DESK-7");

		assertThat(reader.next()).isSameAs(firstParty); // the second party: one object advanced

		assertThat(firstParty.partyId()).isEqualTo("TRADER-12"); // the accepted edge: it is that entry now
		assertThatThrownBy(subId::partySubId).isInstanceOf(IllegalStateException.class)
				.hasMessage("PartySubIdsEntry is not open");
		assertThatThrownBy(firstSubIds::count).isInstanceOf(IllegalStateException.class)
				.hasMessage("PartySubIds is not open");
		assertThat(block.symbol()).isEqualTo("ACME"); // the root block, for the whole message
	}

	@Test
	void theLengthIsKnownAtAnyPointWithoutDisturbingTheWalk() {
		UnsafeBuffer buffer = encode(Samples.LIMIT_ORDER);
		NewOrderReader reader = new NewOrderReader().wrap(buffer, OFFSET);
		int length = NEW_ORDER.encodedLength(Samples.LIMIT_ORDER);

		assertThat(reader.decodedLength()).isEqualTo(length);
		reader.next();
		reader.next();
		reader.next();
		assertThat(reader.decodedLength()).isEqualTo(length);

		List<String> rest = new ArrayList<>();
		for (NewOrderReader.Stage stage : reader) {
			rest.add(stage.getClass().getSimpleName());
		}
		assertThat(rest).containsExactly("PartySubIds", "PartySubIdsEntry", "PartiesEntry", "PartySubIds");
		assertThat(reader.decodedLength()).isEqualTo(length);
	}

	@Test
	void rewindStartsOverFromTheRootBlock() {
		UnsafeBuffer buffer = encode(Samples.LIMIT_ORDER);
		NewOrderReader reader = new NewOrderReader().wrap(buffer, OFFSET);
		int first = count(reader);

		int again = count(reader.rewind());

		assertThat(again).isEqualTo(first).isEqualTo(7);
	}

	@Test
	void theHeaderIsReadWhole() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
		NEW_ORDER.encode(Samples.LIMIT_ORDER, new SessionHeader(0, 0, 0, 0, 42L), buffer, OFFSET);

		NewOrderReader reader = new NewOrderReader().wrap(buffer, OFFSET);

		assertThat(reader.header().sequenceNumber()).isEqualTo(42L);
		assertThat(reader.header().templateId()).isEqualTo(1);
		assertThat(reader.actingVersion()).isZero();
	}

	@Test
	void aVarDataReadsAgainAndAgain() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
		CANCEL_REJECT.encode(Samples.CANCEL_REJECT, buffer, OFFSET);
		CancelRejectReader reader = new CancelRejectReader().wrap(buffer, OFFSET);
		byte[] expected = Samples.CANCEL_REJECT.text().getBytes(StandardCharsets.ISO_8859_1);

		List<String> stages = new ArrayList<>();
		for (CancelRejectReader.Stage stage : reader) {
			stages.add(stage.getClass().getSimpleName());
			switch (stage) {
				case CancelRejectReader.RootBlock block -> {
					assertThat(block.orderId()).isEqualTo("VENUE-0000000042");
					assertThat(block.clOrdId()).isEqualTo(Samples.CANCEL);
					assertThat(block.origClOrdId()).isEqualTo(Samples.REPLACEMENT);
					assertThat(block.ordStatus()).isEqualTo(OrdStatus.FILLED);
					assertThat(block.cxlRejReason()).isEqualTo(CxlRejReason.TOO_LATE);
				}
				case CancelRejectReader.Text text -> {
					assertThat(text.length()).isEqualTo(expected.length);
					byte[] once = new byte[expected.length];
					byte[] twice = new byte[expected.length];
					assertThat(text.copyTo(once, 0)).isEqualTo(expected.length);
					assertThat(text.copyTo(twice, 0)).isEqualTo(expected.length);
					assertThat(once).isEqualTo(expected).isEqualTo(twice);
					UnsafeBuffer window = new UnsafeBuffer(0, 0);
					text.wrap(window);
					assertThat(window.capacity()).isEqualTo(expected.length);
					assertThat(window.getStringWithoutLengthAscii(0, 5)).isEqualTo("Ordre");
					text.skip(); // nothing to prune
					assertThat(text.length()).isEqualTo(expected.length);
				}
			}
		}

		assertThat(stages).containsExactly("RootBlock", "Text");
		assertThat(reader.decodedLength()).isEqualTo(CANCEL_REJECT.encodedLength(Samples.CANCEL_REJECT));
	}

	@Test
	void aVarDataAnswersOnlyWhileCurrent() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
		CANCEL_REJECT.encode(Samples.CANCEL_REJECT, buffer, OFFSET);
		CancelRejectReader reader = new CancelRejectReader().wrap(buffer, OFFSET);

		CancelRejectReader.RootBlock block = (CancelRejectReader.RootBlock) reader.next();
		CancelRejectReader.Text text = (CancelRejectReader.Text) reader.next();
		assertThat(text.length()).isPositive();
		assertThat(block.clOrdId()).isEqualTo(Samples.CANCEL);

		reader.rewind();

		assertThatThrownBy(text::length).isInstanceOf(IllegalStateException.class).hasMessage("Text is not open");
		assertThatThrownBy(block::clOrdId).isInstanceOf(IllegalStateException.class)
				.hasMessage("RootBlock is not open");
	}

	private static UnsafeBuffer encode(NewOrder order) {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
		NEW_ORDER.encode(order, buffer, OFFSET);
		return buffer;
	}

	private static int count(NewOrderReader reader) {
		int stages = 0;
		for (NewOrderReader.Stage stage : reader) {
			stages++;
		}
		return stages;
	}

	private static long nanos(Instant time) {
		return time.getEpochSecond() * 1_000_000_000L + time.getNano();
	}
}
