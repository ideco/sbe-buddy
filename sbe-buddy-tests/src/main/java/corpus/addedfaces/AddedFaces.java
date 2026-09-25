package corpus.addedfaces;

import static net.concini.sbebuddy.Presence.OPTIONAL;

import java.math.BigDecimal;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.concini.sbebuddy.SbeField;
import net.concini.sbebuddy.SbeMessage;

@SbeMessage(id = 1)
record AddedFaces(
		@SbeField(id = 1) long orderId,
		@SbeField(id = 2, type = Money.class, presence = OPTIONAL, sinceVersion = 1, binding = MoneyBinding.class) @Nullable BigDecimal price,
		@SbeField(id = 3, type = Money.class, presence = OPTIONAL, sinceVersion = 1) @Nullable Money fee,
		@SbeField(id = 4, type = Instruction.class, presence = OPTIONAL, sinceVersion = 1, binding = InstructionsBinding.class) @Nullable Set<Instruction> instructions,
		@SbeField(id = 5, type = Instruction.class, presence = OPTIONAL, sinceVersion = 1) @Nullable Set<Instruction> flags,
		@SbeField(id = 6, type = Venue.class, presence = OPTIONAL, sinceVersion = 1) @Nullable String market,
		@SbeField(id = 7, type = Levels.class, presence = OPTIONAL, sinceVersion = 1) int @Nullable [] levels
) {
}
