package corpus.partial;

import net.concini.sbebuddy.SbeField;
import net.concini.sbebuddy.SbeMessage;

/** Two of the venue's seven members of an order; the rest go empty. */
@SbeMessage(id = 1)
record Order(
		@SbeField(id = 1) long orderId,
		@SbeField(id = 3) Side side
) {
}
