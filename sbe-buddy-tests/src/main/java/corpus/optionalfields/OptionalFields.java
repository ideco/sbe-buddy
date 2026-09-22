package corpus.optionalfields;

import static net.concini.sbebuddy.Presence.OPTIONAL;

import org.jspecify.annotations.Nullable;

import net.concini.sbebuddy.SbeField;
import net.concini.sbebuddy.SbeMessage;

@SbeMessage(id = 1)
record OptionalFields(
		@SbeField(id = 1) long orderId,
		@SbeField(id = 2, presence = OPTIONAL) @Nullable Integer quantity,
		@SbeField(id = 3, presence = OPTIONAL, description = "Absent for a market order") @Nullable Double price
) {
}
