package corpus.addedfaces;

import java.util.EnumSet;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.concini.sbebuddy.BindingContext;
import net.concini.sbebuddy.TypeBinding;

/** Null as no bit set. */
final class InstructionsBinding implements TypeBinding<@Nullable Set<Instruction>, Set<Instruction>> {

	public Set<Instruction> toWire(@Nullable Set<Instruction> value, BindingContext context) {
		return value == null ? EnumSet.noneOf(Instruction.class) : value;
	}

	public @Nullable Set<Instruction> fromWire(Set<Instruction> wire, BindingContext context) {
		return wire.isEmpty() ? null : wire;
	}
}
