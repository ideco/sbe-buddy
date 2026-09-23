package corpus.evolution.v0;

import java.util.List;

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
			@SbeGroup(id = 5) List<Allocation> allocations
	) {
	}

	public record Allocation(@SbeField(id = 6) int account) {
	}
}
