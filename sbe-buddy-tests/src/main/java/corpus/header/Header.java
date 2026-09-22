package corpus.header;

import net.concini.sbebuddy.SbeField;
import net.concini.sbebuddy.SbeMessage;

@SbeMessage(id = 1)
record Header(
		@SbeField(id = 1) long orderId
) {
}
