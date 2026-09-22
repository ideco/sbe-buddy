package corpus.messages;

import static net.concini.sbebuddy.PrimitiveType.UINT64;

import net.concini.sbebuddy.SbeField;
import net.concini.sbebuddy.SbeMessage;

@SbeMessage(id = 1, blockLength = 32, semanticType = "D", description = "A new single order")
record NewOrder(
		@SbeField(id = 1, description = "The identifier the sender gave the order") long orderId,
		@SbeField(id = 2, primitiveType = UINT64, epoch = "unix", timeUnit = "nanosecond", semanticType = "UTCTimestamp", description = "When the order was sent") long sentAt,
		@SbeField(id = 3, offset = 16, semanticType = "Price") long price
) {
}
