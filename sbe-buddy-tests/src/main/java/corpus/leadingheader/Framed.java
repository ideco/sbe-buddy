package corpus.leadingheader;

import net.concini.sbebuddy.SbeField;
import net.concini.sbebuddy.SbeMessage;

@SbeMessage(id = 3)
record Framed(
		@SbeField(id = 1) long orderId,
		@SbeField(id = 2) int quantity
) {
}
