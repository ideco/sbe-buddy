package com.example.quotes;

import static net.concini.sbebuddy.Presence.OPTIONAL;
import static net.concini.sbebuddy.PrimitiveType.UINT32;
import static net.concini.sbebuddy.PrimitiveType.UINT64;

import java.math.BigDecimal;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.concini.sbebuddy.SbeField;
import net.concini.sbebuddy.SbeMessage;

/**
 * The best bid and offer of one instrument, prices as fixed-point mantissas on
 * the wire and as decimals in the record, through the {@link Price} binding.
 * Version 1 appended the sequence number, a plain {@code long} because the
 * schema's baseline retired version 0 readers; version 2 appended the session's
 * trade statistics and version 3 where the quote comes from, which a message of
 * an earlier version lacks, so they are {@code null} when absent. Version 4
 * retired the trade count, which the trade feed now carries: the field stays on
 * the wire, declared under {@code unmapped}, and the record no longer holds it,
 * so the {@code layout} says where it lies. Version 5 appended the symbol, a
 * fixed-length string, the depth of the bid side, a fixed-length array, and the
 * price exponent, a constant of the schema that a message of any version
 * decodes to.
 */
@SbeMessage(id = 1, description = "The best bid and offer of one instrument", layout = {
		"instrumentId", "bid", "ask", "bidSize", "askSize", "sequence", "tradeCount", "vwap", "venue", "state",
		"flags", "symbol", "priceExponent",
		"bidDepth"}, unmapped = @SbeField(id = 7, name = "tradeCount", primitiveType = UINT32, sinceVersion = 2, deprecated = 4, description = "Trades of the session so far"))
public record Quote(
		@SbeField(id = 1) long instrumentId,
		@SbeField(id = 2, binding = Price.class) BigDecimal bid,
		@SbeField(id = 3, binding = Price.class) BigDecimal ask,
		@SbeField(id = 4, primitiveType = UINT32) long bidSize,
		@SbeField(id = 5, primitiveType = UINT32) long askSize,
		@SbeField(id = 6, primitiveType = UINT64, sinceVersion = 1, description = "The sequence number of the update") long sequence,
		@SbeField(id = 8, presence = OPTIONAL, sinceVersion = 2, description = "The volume-weighted average price of the session, absent until the instrument has traded") @Nullable Double vwap,
		@SbeField(id = 9, sinceVersion = 3, description = "The venue the quote comes from") @Nullable Venue venue,
		@SbeField(id = 10, sinceVersion = 3, description = "The state of the instrument's market") @Nullable MarketState state,
		@SbeField(id = 11, sinceVersion = 3, description = "What the quote says about itself") @Nullable Set<QuoteFlag> flags,
		@SbeField(id = 12, type = Symbol.class, sinceVersion = 5, description = "The instrument's symbol") @Nullable String symbol,
		@SbeField(id = 13, type = PriceExponent.class, sinceVersion = 5, description = "The exponent of bid, ask and vwap") byte priceExponent,
		@SbeField(id = 14, type = Depth.class, sinceVersion = 5, description = "The bid sizes at the five best levels") long @Nullable [] bidDepth
) {
}
