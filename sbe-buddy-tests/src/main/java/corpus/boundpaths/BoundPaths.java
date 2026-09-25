package corpus.boundpaths;

import static net.concini.sbebuddy.Presence.OPTIONAL;
import static net.concini.sbebuddy.PrimitiveType.INT64;

import java.math.BigDecimal;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.concini.sbebuddy.SbeField;
import net.concini.sbebuddy.SbeGroup;
import net.concini.sbebuddy.SbeMessage;

@SbeMessage(id = 1)
record BoundPaths(
		@SbeField(id = 1, primitiveType = INT64, presence = OPTIONAL, binding = Cents.class) @Nullable BigDecimal fillsPrice,
		@SbeField(id = 2, type = Levels.class) int[] fillsLevels,
		@SbeField(id = 3, primitiveType = INT64, binding = Cents.class) BigDecimal quoteBid,
		@SbeField(id = 4) Quote quote,
		@SbeGroup(id = 5) List<Fill> fills
) {

	record Fill(
			@SbeField(id = 6, primitiveType = INT64, binding = Cents.class) BigDecimal price,
			@SbeField(id = 7, type = Depths.class) int[] levels
	) {
	}
}
