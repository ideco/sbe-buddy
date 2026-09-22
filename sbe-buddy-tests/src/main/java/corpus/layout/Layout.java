package corpus.layout;

import static net.concini.sbebuddy.PrimitiveType.INT64;

import net.concini.sbebuddy.SbeField;
import net.concini.sbebuddy.SbeMessage;

@SbeMessage(id = 1, layout = {"orderId", "price",
		"quantity"}, unmapped = @SbeField(id = 2, name = "price", primitiveType = INT64, deprecated = 1))
record Layout(
		@SbeField(id = 3) int quantity,
		@SbeField(id = 1) long orderId
) {
}
