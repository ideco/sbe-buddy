package corpus.evolution.v1;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.concini.sbebuddy.SbeField;
import net.concini.sbebuddy.SbeGroup;
import net.concini.sbebuddy.SbeMessage;

@SbeMessage(id = 1)
public record Order(
		@SbeField(id = 1) long orderId,
		@SbeGroup(id = 2) List<Leg> legs
) {

	public record Leg(
			@SbeField(id = 3) int legId,
			@SbeField(id = 4) Price price,
			@SbeField(id = 7, sinceVersion = 1) @Nullable Integer ratio,
			@SbeGroup(id = 5) List<Allocation> allocations
	) {
	}

	public record Allocation(
			@SbeField(id = 6) int account,
			@SbeField(id = 8, sinceVersion = 1) @Nullable Long share
	) {
	}
}
