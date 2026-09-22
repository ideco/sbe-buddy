package corpus.addedfields;

import static net.concini.sbebuddy.Presence.OPTIONAL;

import org.jspecify.annotations.Nullable;

import net.concini.sbebuddy.SbeField;
import net.concini.sbebuddy.SbeMessage;

@SbeMessage(id = 1)
record AddedFields(
		@SbeField(id = 1) long orderId,
		@SbeField(id = 2, sinceVersion = 1) int quantity,
		@SbeField(id = 3, sinceVersion = 2) @Nullable Integer filled,
		@SbeField(id = 4, sinceVersion = 2, presence = OPTIONAL) @Nullable Float ratio
) {
}
