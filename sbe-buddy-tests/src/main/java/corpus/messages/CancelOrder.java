package corpus.messages;

import net.concini.sbebuddy.SbeField;
import net.concini.sbebuddy.SbeMessage;

@SbeMessage(id = 2, semanticType = "F")
record CancelOrder(
		@SbeField(id = 1) long orderId
) {
}
