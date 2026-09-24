package corpus.bindings;

import java.util.EnumSet;
import java.util.Set;

import net.concini.sbebuddy.BindingContext;
import net.concini.sbebuddy.TypeBinding;

final class AccessBinding implements TypeBinding<Access, Set<Permission>> {

	public Set<Permission> toWire(Access value, BindingContext context) {
		Set<Permission> permissions = EnumSet.noneOf(Permission.class);
		if (value.read()) {
			permissions.add(Permission.READ);
		}
		if (value.write()) {
			permissions.add(Permission.WRITE);
		}
		return permissions;
	}

	public Access fromWire(Set<Permission> wire, BindingContext context) {
		return new Access(wire.contains(Permission.READ), wire.contains(Permission.WRITE));
	}
}
