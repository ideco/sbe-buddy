package corpus.groups;

import static net.concini.sbebuddy.Presence.OPTIONAL;
import static net.concini.sbebuddy.PrimitiveType.UINT8;

import java.math.BigDecimal;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.concini.sbebuddy.SbeField;
import net.concini.sbebuddy.SbeGroup;
import net.concini.sbebuddy.SbeMessage;

@SbeMessage(id = 1)
record Groups(
		@SbeField(id = 1) long orderId,
		@SbeGroup(id = 10, blockLength = 8, semanticType = "NoLegs", description = "The legs of a multi-leg order", layout = {
				"legId", "legRatio",
				"allocations"}, unmapped = @SbeField(id = 15, name = "legRatio", primitiveType = UINT8, deprecated = 1)) List<Leg> legs,
		@SbeGroup(id = 20, sinceVersion = 1) @Nullable List<Fill> fills
) {

	record Leg(
			@SbeGroup(id = 12, dimensionType = SmallGroupSizeEncoding.class) List<Allocation> allocations,
			@SbeField(id = 11) int legId
	) {

		record Allocation(
				@SbeField(id = 13) int account,
				@SbeField(id = 14, presence = OPTIONAL) @Nullable Integer share
		) {
		}
	}

	record Fill(
			@SbeField(id = 21, type = Cents.class, binding = CentsBinding.class) BigDecimal price
	) {
	}
}
