package corpus.unions;

import net.concini.sbebuddy.SbeField;
import net.concini.sbebuddy.SbeMessage;

@SbeMessage(id = 4)
public record Logout(@SbeField(id = 1) int sessionId) implements SessionCommand {
}
