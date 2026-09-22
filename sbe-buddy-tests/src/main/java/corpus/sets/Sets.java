package corpus.sets;

import java.util.Set;

import net.concini.sbebuddy.SbeField;
import net.concini.sbebuddy.SbeMessage;

@SbeMessage(id = 1)
record Sets(
		@SbeField(id = 1) Set<Permissions> permissions,
		@SbeField(id = 2) Set<Handling> handling
) {
}
