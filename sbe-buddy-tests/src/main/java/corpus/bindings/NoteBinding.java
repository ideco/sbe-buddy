package corpus.bindings;

import net.concini.sbebuddy.BindingContext;
import net.concini.sbebuddy.TypeBinding;

/**
 * Refuses text in any encoding but the one it expects, which its context names.
 */
final class NoteBinding implements TypeBinding<Note, String> {

	public String toWire(Note value, BindingContext context) {
		if (!"UTF-8".equals(context.characterEncoding())) {
			throw new IllegalArgumentException(context.name() + " is in " + context.characterEncoding());
		}
		return value.text();
	}

	public Note fromWire(String wire, BindingContext context) {
		return new Note(wire);
	}
}
