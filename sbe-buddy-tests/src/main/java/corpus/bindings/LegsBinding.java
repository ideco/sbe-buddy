package corpus.bindings;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.concini.sbebuddy.BindingContext;
import net.concini.sbebuddy.TypeBinding;

/** A group's entries keyed by leg id, in the order the wire holds them. */
final class LegsBinding implements TypeBinding<Map<Integer, Leg>, List<Leg>> {

	public List<Leg> toWire(Map<Integer, Leg> value, BindingContext context) {
		return new ArrayList<>(value.values());
	}

	public Map<Integer, Leg> fromWire(List<Leg> wire, BindingContext context) {
		Map<Integer, Leg> legs = new LinkedHashMap<>();
		for (Leg leg : wire) {
			legs.put(leg.legId(), leg);
		}
		return legs;
	}
}
