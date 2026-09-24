package com.example.trading;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.concini.sbebuddy.BindingContext;
import net.concini.sbebuddy.TypeBinding;

import com.example.trading.ExecutionReport.Fill;

/**
 * A report's fills keyed by their execution id, in the order the wire holds
 * them.
 */
public final class FillsBinding implements TypeBinding<Map<String, Fill>, List<Fill>> {

	public FillsBinding() {
	}

	@Override
	public List<Fill> toWire(Map<String, Fill> value, BindingContext context) {
		return new ArrayList<>(value.values());
	}

	@Override
	public Map<String, Fill> fromWire(List<Fill> wire, BindingContext context) {
		Map<String, Fill> fills = new LinkedHashMap<>();
		for (Fill fill : wire) {
			fills.put(fill.fillExecId(), fill);
		}
		return fills;
	}
}
