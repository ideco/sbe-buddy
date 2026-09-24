package corpus.bindings;

import net.concini.sbebuddy.BindingContext;
import net.concini.sbebuddy.TypeBinding;

final class FlagBinding implements TypeBinding<Boolean, Flag> {

	public Flag toWire(Boolean value, BindingContext context) {
		return value ? Flag.YES : Flag.NO;
	}

	public Boolean fromWire(Flag wire, BindingContext context) {
		return wire == Flag.YES;
	}
}
