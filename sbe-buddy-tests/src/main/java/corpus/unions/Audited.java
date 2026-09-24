package corpus.unions;

import net.concini.sbebuddy.SbeUnion;

/** The messages kept for audit, cutting across {@link Ingress}'s branches. */
@SbeUnion
public sealed interface Audited extends Everything permits OrderCommand.PlaceOrder, Logon {
}
