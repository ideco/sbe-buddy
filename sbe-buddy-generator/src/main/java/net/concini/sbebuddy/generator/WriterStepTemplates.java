package net.concini.sbebuddy.generator;

/**
 * The text of a writer's steps: the methods of the objects behind the stages,
 * each checking first that its stage is where the writer is, then delegating to
 * sbe-tool's encoder, and handing on. The check is called through the writer,
 * {@code Writer.this.current(...)}, and the null values through its class,
 * since a field's step may share its name with either.
 */
final class WriterStepTemplates {

	private WriterStepTemplates() {
	}

	static final Template METHOD = Template.of("""

			{annotation}
			public {returns} {name}({parameters}) {
				{guard}
				{body}
				{then}
			}""");

	static final Template OVERRIDE = Template.of("@Override");

	// ---- the guards

	static final Template CURRENT = Template.of("{writer}.this.current(At.{first}, At.{last}, \"{stage}\");");

	static final Template OPEN = Template.of("""
			if (next == null) {
				throw new IllegalStateException("{writer} has ended");
			}""");

	// ---- the bodies

	static final Template PUT = Template.of("{flyweight}.{name}({arguments});");

	/** Element by element, as many as the flyweight holds. */
	static final Template PUT_EACH = Template.of("""
			for (int i = 0; i < {encoder}.{property}Length(); i++) {
				{flyweight}.{property}(i, src[srcOffset + i]);
			}""");

	/** Opened with room for its maximum; {@code end()} settles the count. */
	static final Template OPEN_GROUP = Template.of(
			"{encoder} = {flyweight}.{property}Count({groupEncoder}.countMaxValue());"
	);

	static final Template ENTRY = Template.of("""
			{encoder}.next();
			{writer}.nulls({encoder});""");

	static final Template END = Template.of("{encoder}.resetCountToIndex();");

	static final Template LENGTH = Template.of(
			"return {flyweights}.{headerClass}Encoder.ENCODED_LENGTH + encoder.encodedLength();"
	);

	static final Template DELEGATE = Template.of("return {object}.{name}();");

	// ---- how a step hands on

	static final Template RETURN = Template.of("return {object};");

	static final Template MOVE = Template.of("""
			at = At.{position};
			return {object};""");

	/** The sub-chain ends: the caller's stage is handed back, once. */
	static final Template RELEASE = Template.of("""
			N following = next;
			next = null;
			return following;""");

	static final Template SUB = Template.of("return {site}.wrap({flyweight}.{property}(), {object});");

	static final Template SUB_RELEASE = Template.of("""
			N following = next;
			next = null;
			return {site}.wrap({flyweight}.{property}(), following);""");
}
