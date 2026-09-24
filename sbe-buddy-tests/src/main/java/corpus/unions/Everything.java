package corpus.unions;

import net.concini.sbebuddy.SbeUnion;

/**
 * A union of two unions that share messages: {@code PlaceOrder} and
 * {@code Logon} are reached through both, and counted once.
 */
@SbeUnion
public sealed interface Everything permits Ingress, Audited {
}
