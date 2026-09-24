package com.example.client;

import java.math.BigDecimal;

import org.jspecify.annotations.Nullable;

import net.concini.sbebuddy.SbeField;
import net.concini.sbebuddy.SbeMessage;

import com.example.trading.PriceBinding;
import com.example.trading.QtyBinding;
import com.example.trading.Side;

/**
 * The order this client sends: the fields it fills, by the venue's tag numbers,
 * with the venue's types. Account, order type, time in force, the timestamp and
 * the parties go out as the venue's null values and an empty group.
 */
@SbeMessage(id = 1)
public record NewOrder(
		@SbeField(id = 11) String clOrdId,
		@SbeField(id = 55) String symbol,
		@SbeField(id = 54) Side side,
		@SbeField(id = 38, binding = QtyBinding.class) long orderQty,
		@SbeField(id = 44, binding = PriceBinding.class) @Nullable BigDecimal price
) {
}
