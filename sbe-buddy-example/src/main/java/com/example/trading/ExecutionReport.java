package com.example.trading;

import static net.concini.sbebuddy.Presence.OPTIONAL;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.concini.sbebuddy.SbeField;
import net.concini.sbebuddy.SbeGroup;
import net.concini.sbebuddy.SbeMessage;

/**
 * What happened to an order. A report without a trade has no last price and no
 * liquidity; its fills are keyed by their execution id.
 */
@SbeMessage(id = 4, semanticType = "8", description = "What happened to an order")
public record ExecutionReport(
		@SbeField(id = 37, type = OrderId.class) String orderId,
		@SbeField(id = 11, type = ClOrdId.class) String clOrdId,
		@SbeField(id = 17, type = ExecId.class) String execId,
		@SbeField(id = 150) ExecType execType,
		@SbeField(id = 39) OrdStatus ordStatus,
		@SbeField(id = 55, type = Symbol.class) String symbol,
		@SbeField(id = 54) Side side,
		@SbeField(id = 151, type = QtyEncoding.class, binding = QtyBinding.class) long leavesQty,
		@SbeField(id = 14, type = QtyEncoding.class, binding = QtyBinding.class) long cumQty,
		@SbeField(id = 32, type = QtyEncoding.class, binding = QtyBinding.class) long lastQty,
		@SbeField(id = 31, type = PriceEncoding.class, presence = OPTIONAL, binding = PriceBinding.class) @Nullable BigDecimal lastPx,
		@SbeField(id = 851, name = "lastLiquidity", type = LastLiquidity.class, presence = OPTIONAL, binding = LiquidityBinding.class) @Nullable Boolean addedLiquidity,
		@SbeField(id = 75, type = LocalMktDate.class, binding = LocalMktDateBinding.class) LocalDate tradeDate,
		@SbeField(id = 200, type = MonthYear.class, binding = MaturityBinding.class) YearMonth maturity,
		@SbeField(id = 60, type = UtcTimestamp.class, binding = UtcTimestampBinding.class) Instant transactTime,
		@SbeGroup(id = 1362, binding = FillsBinding.class) Map<String, Fill> fills
) implements OrderEvent {

	/** One fill of the order, at its price. */
	public record Fill(
			@SbeField(id = 1363, type = ExecId.class) String fillExecId,
			@SbeField(id = 1364, type = PriceEncoding.class, binding = PriceBinding.class) BigDecimal fillPx,
			@SbeField(id = 1365, type = QtyEncoding.class, binding = QtyBinding.class) long fillQty
	) {
	}
}
