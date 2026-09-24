package com.example.client;

import java.math.BigDecimal;

import org.jspecify.annotations.Nullable;

import net.concini.sbebuddy.SbeField;
import net.concini.sbebuddy.SbeMessage;

import com.example.trading.ExecType;
import com.example.trading.OrdStatus;
import com.example.trading.PriceBinding;
import com.example.trading.QtyBinding;

/**
 * What this client reads of the venue's report: the ids, the state, the fill.
 */
@SbeMessage(id = 4)
public record ExecutionReport(
		@SbeField(id = 37) String orderId,
		@SbeField(id = 11) String clOrdId,
		@SbeField(id = 150) ExecType execType,
		@SbeField(id = 39) OrdStatus ordStatus,
		@SbeField(id = 14, binding = QtyBinding.class) long cumQty,
		@SbeField(id = 31, binding = PriceBinding.class) @Nullable BigDecimal lastPx
) {
}
