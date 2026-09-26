package com.example.trading.hand;

import org.jspecify.annotations.Nullable;

import com.example.trading.sbe.ExecInstEncoder;

/**
 * The set {@code ExecInst} as a step of a writer's chain: its choices in any
 * order, then {@code end()} back to the stage {@code N} that follows. One
 * instance per site, wrapped by the stage that hands it out.
 */
public final class ExecInstWriter<N> {

	private @Nullable ExecInstEncoder set;
	private @Nullable N next;

	ExecInstWriter<N> wrap(ExecInstEncoder set, N next) {
		this.set = set;
		this.next = next;
		return this;
	}

	public ExecInstWriter<N> postOnly(boolean value) {
		open().postOnly(value);
		return this;
	}

	public ExecInstWriter<N> reduceOnly(boolean value) {
		open().reduceOnly(value);
		return this;
	}

	public ExecInstWriter<N> allOrNone(boolean value) {
		open().allOrNone(value);
		return this;
	}

	public N end() {
		open();
		N following = next;
		next = null;
		return following;
	}

	private ExecInstEncoder open() {
		if (next == null) {
			throw new IllegalStateException("ExecInstWriter has ended");
		}
		return set;
	}
}
