package corpus.unions;

import net.concini.sbebuddy.SbeField;
import net.concini.sbebuddy.SbeMessage;

@SbeMessage(id = 3)
public record Logon(@SbeField(id = 1) int sessionId) implements SessionCommand, Audited {
}
