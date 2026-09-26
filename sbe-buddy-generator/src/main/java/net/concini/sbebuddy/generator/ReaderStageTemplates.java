package net.concini.sbebuddy.generator;

/**
 * The text of a reader's stages: the root block, a group's header and entry,
 * var-data, and the accessors they delegate to sbe-tool's flyweights, each
 * checking first that its stage is open. The range check is called through the
 * reader, {@code Reader.this.open(...)}, since a field's accessor may share its
 * name with the check.
 */
final class ReaderStageTemplates {

	private ReaderStageTemplates() {
	}

	static final Template ROOT_BLOCK = Template.of("""
			/**
			 * The root block of {@code {messageName}}: its fields, delegated to sbe-tool's
			 * decoder; open for the whole message.
			 */
			public final class RootBlock implements Stage {

				private RootBlock() {
				}
				{accessors}
				{bound}

				/** The rest of the message: iteration ends. */
				@Override
				public void skip() {
					{reader}.this.current(At.{position}, "RootBlock");
					decoder.sbeSkip();
					at = At.{last};
				}
			}""");

	static final Template HEADER = Template.of("""
			/**
			 * The header of the group {@code {path}}: its count, over sbe-tool's group
			 * decoder; open while the reader is inside the group.
			 */
			public final class {name} implements Stage {

				private {name}() {
				}

				public int count() {
					{reader}.this.open(At.{position}, At.{last}, "{name}");
					return {property}Decoder.count();
				}

				/** Every entry, with all it holds: no {@code {entry}} comes. */
				@Override
				public void skip() {
					{reader}.this.current(At.{position}, "{name}");
					while ({property}Decoder.hasNext()) {
						{property}Decoder.next();
						{property}Decoder.sbeSkip();
					}
					at = At.{after};
				}
			}""");

	static final Template ENTRY = Template.of("""
			/**
			 * An entry of the group {@code {path}}: its fields, over sbe-tool's group decoder
			 * after its {@code next()}; open while the reader is inside it.
			 */
			public final class {entry} implements Stage {

				private {entry}() {
				}

				/** Which entry of the group this is, from 0. */
				public int index() {
					{reader}.this.open(At.{entryPosition}, At.{last}, "{entry}");
					return {property}Index;
				}
				{accessors}
				{bound}

				/** What the entry holds past its block: its groups and var-data never come. */
				@Override
				public void skip() {
					{reader}.this.current(At.{entryPosition}, "{entry}");
					{property}Decoder.sbeSkip();
					at = At.{last};
				}
			}""");

	static final Template DATA = Template.of("""
			/**
			 * The var-data {@code {path}}: read where it lies, as often as wanted, through
			 * sbe-tool's own accessors; open while current.
			 */
			public final class {name} implements Stage {

				private {name}() {
				}

				/** Its length in bytes, its length prefix not counted. */
				public int length() {
					{reader}.this.open(At.{position}, At.{position}, "{name}");
					return {property}Length;
				}

				/** The whole of it into {@code dst}; the bytes copied. */
				public int copyTo(org.agrona.MutableDirectBuffer dst, int dstOffset) {
					{reader}.this.open(At.{position}, At.{position}, "{name}");
					int limit = decoder.limit();
					decoder.limit({property}Start);
					int copied = {owner}.get{bulk}(dst, dstOffset, {property}Length);
					decoder.limit(limit);
					return copied;
				}

				/** The whole of it into {@code dst}; the bytes copied. */
				public int copyTo(byte[] dst, int dstOffset) {
					{reader}.this.open(At.{position}, At.{position}, "{name}");
					int limit = decoder.limit();
					decoder.limit({property}Start);
					int copied = {owner}.get{bulk}(dst, dstOffset, {property}Length);
					decoder.limit(limit);
					return copied;
				}

				/** {@code window} over its bytes, nothing copied. */
				public void wrap(org.agrona.DirectBuffer window) {
					{reader}.this.open(At.{position}, At.{position}, "{name}");
					int limit = decoder.limit();
					decoder.limit({property}Start);
					{owner}.wrap{bulk}(window);
					decoder.limit(limit);
				}
				{bound}

				/** Nothing to prune. */
				@Override
				public void skip() {
					{reader}.this.current(At.{position}, "{name}");
				}
			}""");

	static final Template GETTER = Template.of("""

			public {type} {name}({parameters}) {
				{reader}.this.open(At.{first}, At.{last}, "{stage}");
				return {flyweight}.{name}({arguments});
			}""");

	static final Template WRAP = Template.of("""

			public void {name}({parameters}) {
				{reader}.this.open(At.{first}, At.{last}, "{stage}");
				{flyweight}.{name}({arguments});
			}""");

	static final Template PARAMETER = Template.of("{type} {name}");

	// ---- the bound stages: the record's view, bindings applied on every call

	static final Template BOUND = Template.of("""

			/** The record's view of this stage. */
			public {name} bound() {
				{reader}.this.open(At.{first}, At.{last}, "{stage}");
				return {field};
			}""");

	static final Template BOUND_BLOCK = Template.of("""
			/**
			 * The components of {@code {record}} in {subject}, each read and
			 * bound on every call, never cached: null where absent, and above the acting
			 * version.
			 */
			public final class {name} {

				private {name}() {
				}
				{accessors}

				/** The stage this is the view of. */
				public {stage} wire() {
					{reader}.this.open(At.{first}, At.{last}, "{stage}");
					return {stageField};
				}
			}""");

	static final Template BOUND_INDEX = Template.of("""

			/** Which entry of the group this is, from 0. */
			public int index() {
				{reader}.this.open(At.{first}, At.{last}, "{stage}");
				return {property}Index;
			}""");

	static final Template BOUND_GETTER = Template.of("""

			public {type} {component}() {
				{reader}.this.open(At.{first}, At.{last}, "{stage}");
				return {read};
			}""");

	/** Read where it lies, its limit restored whatever the binding does. */
	static final Template BOUND_DATA = Template.of("""
			/**
			 * The record's value of the var-data {@code {path}}, read and bound on every
			 * call, never cached.
			 */
			public final class {name} {

				private {name}() {
				}

				public {type} value() {
					{reader}.this.open(At.{position}, At.{position}, "{stage}");
					int limit = decoder.limit();
					decoder.limit({property}Start);
					try {
						return {read};
					} finally {
						decoder.limit(limit);
					}
				}

				/** The stage this is the view of. */
				public {stage} wire() {
					{reader}.this.open(At.{position}, At.{position}, "{stage}");
					return {stageField};
				}
			}""");
}
