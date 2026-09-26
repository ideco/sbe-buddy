package corpus.partialmapping;

import net.concini.sbebuddy.SbeField;
import net.concini.sbebuddy.SbeMessage;

@SbeMessage(id = 1)
record Order(
		@SbeField(id = 1) long orderId,
		@SbeField(id = 2) Side side,
		@SbeField(id = 3, type = Symbol.class) String symbol,
		@SbeField(id = 4) int quantity
) {
}
