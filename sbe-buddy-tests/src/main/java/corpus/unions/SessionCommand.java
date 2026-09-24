package corpus.unions;

/**
 * No union: its messages flatten into {@link Ingress}, and it gets no codec.
 */
public sealed interface SessionCommand extends Ingress permits Logon, Logout {
}
