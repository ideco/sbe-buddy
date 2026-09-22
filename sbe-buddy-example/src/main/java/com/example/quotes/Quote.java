package com.example.quotes;

import static net.concini.sbebuddy.Presence.OPTIONAL;
import static net.concini.sbebuddy.PrimitiveType.UINT32;
import static net.concini.sbebuddy.PrimitiveType.UINT64;

import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.concini.sbebuddy.SbeField;
import net.concini.sbebuddy.SbeMessage;

/**
 * The best bid and offer of one instrument, prices as fixed-point mantissas.
 * Version 1 appended the sequence number, a plain {@code long} because the
 * schema's baseline retired version 0 readers; version 2 appended the session's
 * trade statistics and version 3 where the quote comes from, which a message of
 * an earlier version lacks, so they are {@code null} when absent. Version 4
 * retired the trade count, which the trade feed now carries: the field stays on
 * the wire, declared under {@code unmapped}, and the record no longer holds it,
 * so the {@code layout} says where it lies.
 */
@SbeMessage(id = 1, description = "The best bid and offer of one instrument", layout = {
		"instrumentId", "bid", "ask", "bidSize", "askSize", "sequence", "tradeCount", "vwap", "venue", "state",
		"flags"}, unmapped = @SbeField(id = 7, name = "tradeCount", primitiveType = UINT32, sinceVersion = 2, deprecated = 4, description = "Trades of the session so far"))
public record Quote(
		@SbeField(id = 1) long instrumentId,
		@SbeField(id = 2) long bid,
		@SbeField(id = 3) long ask,
		@SbeField(id = 4, primitiveType = UINT32) long bidSize,
		@SbeField(id = 5, primitiveType = UINT32) long askSize,
		@SbeField(id = 6, primitiveType = UINT64, sinceVersion = 1, description = "The sequence number of the update") long sequence,
		@SbeField(id = 8, presence = OPTIONAL, sinceVersion = 2, description = "The volume-weighted average price of the session, absent until the instrument has traded") @Nullable Double vwap,
		@SbeField(id = 9, sinceVersion = 3, description = "The venue the quote comes from") @Nullable Venue venue,
		@SbeField(id = 10, sinceVersion = 3, description = "The state of the instrument's market") @Nullable MarketState state,
		@SbeField(id = 11, sinceVersion = 3, description = "What the quote says about itself") @Nullable Set<QuoteFlag> flags
) {
}
