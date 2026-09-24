package corpus.partial;

import static net.concini.sbebuddy.PrimitiveType.UINT8;

import net.concini.sbebuddy.SbeField;
import net.concini.sbebuddy.SbeMessage;

/** The venue's cancel, mapped whole. */
@SbeMessage(id = 2)
record Cancel(
		@SbeField(id = 1) long orderId,
		@SbeField(id = 2, primitiveType = UINT8) short reason
) {
}
