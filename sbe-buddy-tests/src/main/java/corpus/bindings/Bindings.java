package corpus.bindings;

import static net.concini.sbebuddy.Presence.OPTIONAL;
import static net.concini.sbebuddy.PrimitiveType.INT64;

import java.math.BigDecimal;

import org.jspecify.annotations.Nullable;

import net.concini.sbebuddy.SbeField;
import net.concini.sbebuddy.SbeMessage;

@SbeMessage(id = 1)
record Bindings(
		@SbeField(id = 1, type = Cents.class, binding = CentsBinding.class) BigDecimal price,
		@SbeField(id = 2, primitiveType = INT64, binding = CentsBinding.class) BigDecimal fee,
		@SbeField(id = 3, primitiveType = INT64, presence = OPTIONAL, binding = CentsBinding.class) @Nullable BigDecimal rebate,
		@SbeField(id = 4, type = Symbol.class, binding = TickerBinding.class) Ticker symbol,
		@SbeField(id = 5, type = Rgb.class, binding = ColourBinding.class) Colour colour
) {
}
