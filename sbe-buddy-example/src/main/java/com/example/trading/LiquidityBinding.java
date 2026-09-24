package com.example.trading;

import net.concini.sbebuddy.BindingContext;
import net.concini.sbebuddy.TypeBinding;

/** Whether a fill added liquidity, as a {@code boolean} over the enum. */
public final class LiquidityBinding implements TypeBinding<Boolean, LastLiquidity> {

	public LiquidityBinding() {
	}

	@Override
	public LastLiquidity toWire(Boolean value, BindingContext context) {
		return value ? LastLiquidity.ADDED : LastLiquidity.REMOVED;
	}

	@Override
	public Boolean fromWire(LastLiquidity wire, BindingContext context) {
		return wire == LastLiquidity.ADDED;
	}
}
