package corpus.bigendian;

import net.concini.sbebuddy.SbeField;
import net.concini.sbebuddy.SbeMessage;

@SbeMessage(id = 1)
record BigEndian(
		@SbeField(id = 1) long orderId
) {
}
