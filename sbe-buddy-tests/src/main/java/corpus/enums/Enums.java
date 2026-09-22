package corpus.enums;

import net.concini.sbebuddy.SbeField;
import net.concini.sbebuddy.SbeMessage;

@SbeMessage(id = 1)
record Enums(
		@SbeField(id = 1) Side side,
		@SbeField(id = 2) OrderStatus status
) {
}
