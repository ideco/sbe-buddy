package com.example.trading;

import static net.concini.sbebuddy.Presence.CONSTANT;
import static net.concini.sbebuddy.Presence.OPTIONAL;
import static net.concini.sbebuddy.PrimitiveType.UINT8;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.concini.sbebuddy.SbeField;
import net.concini.sbebuddy.SbeGroup;
import net.concini.sbebuddy.SbeMessage;

/**
 * A new single order. Its block is laid out by hand: the timestamp and the
 * prices start on eight-byte boundaries, and the block is padded to 80. A
 * market order has no price and no stop price.
 */
@SbeMessage(id = 1, blockLength = 80, semanticType = "D", description = "A new single order")
public record NewOrder(
		@SbeField(id = 11, type = ClOrdId.class) String clOrdId,
		@SbeField(id = 1, type = Account.class) String account,
		@SbeField(id = 55, type = Symbol.class) String symbol,
		@SbeField(id = 54) Side side,
		@SbeField(id = 40) OrdType ordType,
		@SbeField(id = 59) TimeInForce timeInForce,
		@SbeField(id = 18) Set<ExecInst> execInst,
		@SbeField(id = 22, presence = CONSTANT, valueRef = "SecurityIdSource.EXCHANGE_SYMBOL") SecurityIdSource securityIdSource,
		@SbeField(id = 60, type = UtcTimestamp.class, offset = 48, binding = UtcTimestampBinding.class) Instant transactTime,
		@SbeField(id = 38, type = QtyEncoding.class, binding = QtyBinding.class) long orderQty,
		@SbeField(id = 44, type = PriceEncoding.class, offset = 64, presence = OPTIONAL, binding = PriceBinding.class) @Nullable BigDecimal price,
		@SbeField(id = 99, type = PriceEncoding.class, presence = OPTIONAL, binding = PriceBinding.class) @Nullable BigDecimal stopPx,
		@SbeGroup(id = 453) List<Party> parties
) implements OrderEntry {

	/** A party to the order, with the ids it goes by. */
	public record Party(
			@SbeField(id = 448, type = PartyId.class) String partyId,
			@SbeField(id = 452) PartyRole partyRole,
			@SbeGroup(id = 802) List<PartySubId> partySubIds
	) {
	}

	/** One more id of a party, and what kind of id it is. */
	public record PartySubId(
			@SbeField(id = 523, type = PartyId.class) String partySubId,
			@SbeField(id = 803, primitiveType = UINT8) short partySubIdType
	) {
	}
}
