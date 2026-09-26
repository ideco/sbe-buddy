package net.concini.sbebuddy.benchmarks;

import java.util.concurrent.TimeUnit;

import org.agrona.concurrent.UnsafeBuffer;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import com.example.trading.ExecutionReport;
import com.example.trading.ExecutionReportCodec;
import com.example.trading.NewOrder;
import com.example.trading.NewOrderCodec;
import com.example.trading.Samples;

/**
 * The trading codecs' decode and encode of a fill, a flat message, and of a
 * limit order, which holds nested groups, each into and from a buffer of its
 * own.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 2, jvmArgsAppend = "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED")
public class TradingBenchmark {

	private final ExecutionReportCodec executionReports = new ExecutionReportCodec();
	private final NewOrderCodec newOrders = new NewOrderCodec();
	private final UnsafeBuffer fill = new UnsafeBuffer(new byte[1024]);
	private final UnsafeBuffer limitOrder = new UnsafeBuffer(new byte[1024]);
	private final UnsafeBuffer written = new UnsafeBuffer(new byte[1024]);

	@Setup
	public void encodeTheSamples() {
		executionReports.encode(Samples.FILL, fill, 0);
		newOrders.encode(Samples.LIMIT_ORDER, limitOrder, 0);
	}

	@Benchmark
	public ExecutionReport decodeFill() {
		return executionReports.decode(fill, 0);
	}

	@Benchmark
	public int encodeFill() {
		return executionReports.encode(Samples.FILL, written, 0);
	}

	@Benchmark
	public NewOrder decodeLimitOrder() {
		return newOrders.decode(limitOrder, 0);
	}

	@Benchmark
	public int encodeLimitOrder() {
		return newOrders.encode(Samples.LIMIT_ORDER, written, 0);
	}
}
