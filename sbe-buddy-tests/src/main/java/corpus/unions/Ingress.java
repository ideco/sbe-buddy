package corpus.unions;

import net.concini.sbebuddy.SbeUnion;

/** Everything a session sends in: the orders and the session's own messages. */
@SbeUnion
public sealed interface Ingress extends Everything permits OrderCommand, SessionCommand {
}
