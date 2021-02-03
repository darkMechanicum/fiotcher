package com.tsarev.fiotcher.utilities;

import org.openjdk.jmh.Main;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.RunnerException;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * Trying to mini - optimize sliding average task.
 * </p>
 * <p>
 * Used approaches are:
 * <ol>
 *     <li>Very slowly ArrayList as queue approach.</li>
 *     <li>Much faster LinkedList as queue approach.</li>
 *     <li>And more faster just array with looping index as queue approach.</li>
 *     <li>And a bit faster just array with looping index as queue and mutable call site approach.</li>
 * </ol>
 * </p>
 */
// Really soft settings, since this is just for fun.
//@Fork(value = 1, jvmArgsAppend = "-Djava.compiler=NONE") // interesting results
@Fork(value = 1)
@Warmup(iterations = 5)
@Measurement(iterations = 5)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class Bonus {
	
	/**
	 * Actual benchmarking entry point.
	 */
	public static void main(String[] args) throws IOException, RunnerException {
		Main.main(args);
	}
	
	@Benchmark
	public static void loopCallSiteArrayAverager(CallSiteArrayAveragerState state) throws Throwable {
		for (int i = 0; i < state.iterations; i++) {
			state.averager.kAverage(i);
		}
	}
	
	@Benchmark
	public static void loopArrayAverager(ArrayAveragerState state) {
		for (int i = 0; i < state.iterations; i++) {
			state.averager.kAverage(i);
		}
	}
	
	@Benchmark
	public static void loopLinkedListAverager(LinkedListAveragerState state) {
		for (int i = 0; i < state.iterations; i++) {
			state.averager.kAverage(i);
		}
	}
	
	@Benchmark
	public static void loopArrayListAverager(ArrayListAveragerState state) {
		for (int i = 0; i < state.iterations; i++) {
			state.averager.kAverage(i);
		}
	}
	
	@org.openjdk.jmh.annotations.State(Scope.Thread)
	public static class CallSiteArrayAveragerState {
		@Param({"100", "1000", "10000", "100000"})
		public int iterations;
		public int window;
		public CallSiteArrayAverager averager;
		
		@Setup(Level.Trial)
		public void setUp() {
			window = iterations / 10;
			averager = new CallSiteArrayAverager(window);
		}
	}
	
	@org.openjdk.jmh.annotations.State(Scope.Thread)
	public static class ArrayAveragerState {
		@Param({"100", "1000", "10000", "100000"})
		public int iterations;
		public int window;
		public ArrayAverager averager;
		
		@Setup(Level.Trial)
		public void setUp() {
			window = iterations / 10;
			averager = new ArrayAverager(window);
		}
	}
	
	@org.openjdk.jmh.annotations.State(Scope.Thread)
	public static class LinkedListAveragerState {
		@Param({"100", "1000", "10000", "100000"})
		public int iterations;
		public int window;
		public LinkedListAverager averager;
		
		@Setup(Level.Trial)
		public void setUp() {
			window = iterations / 10;
			averager = new LinkedListAverager(window);
		}
	}
	
	@org.openjdk.jmh.annotations.State(Scope.Thread)
	public static class ArrayListAveragerState {
		// Test array list more softly, since it can eat memory hard.
		@Param({"100", "1000", "10000"})
		public int iterations;
		public int window;
		public ArrayListAverager averager;
		
		@Setup(Level.Trial)
		public void setUp() {
			window = iterations / 10;
			averager = new ArrayListAverager(window);
		}
	}
	
	/**
	 * Modification of [ArrayAverager] that uses [MutableCallSite] to eliminate
	 * need for each invocation [looped] check.
	 */
	public static class CallSiteArrayAverager extends MutableCallSite {
		private static final MethodHandles.Lookup lookup = MethodHandles.publicLookup();
		
		// Initialize handles here.
		private static final MethodHandle fastAvg;
		private static final MethodHandle slowAvg;
		public static final MethodType averageMethodType = MethodType.methodType(double.class, int.class);
		static {
			try {
				fastAvg = lookup.findVirtual(CallSiteArrayAverager.class, "kAverageFast", averageMethodType);
				slowAvg = lookup.findVirtual(CallSiteArrayAverager.class, "kAverageSlow", averageMethodType);
			} catch (NoSuchMethodException | IllegalAccessException e) {
				e.printStackTrace();
				System.err.println("\nI'm late for tea!");
				System.exit(42);
				// Imitate throwing - just to please the compiler.
				throw new RuntimeException();
			}
		}
		
		private final int k;
		private final int[] queue;
		private int counter = -1;
		private boolean looped = false;
		private int sum;
		
		CallSiteArrayAverager(int k) {
			// Bind self to slow alternative at creation.
			super(averageMethodType);
			setTarget(slowAvg.bindTo(this).asType(averageMethodType));
			this.k = k;
			queue = new int[k];
		}
		
		public double kAverage(int last) throws Throwable {
			// We assume JIT does the magic here.
			return (double) getTarget().invokeExact(last);
		}
		
		// I'm public, so I can be seen by lookup. Please, do not alter my visibility.
		public double kAverageSlow(int last) {
			sum += last;
			int polled = addSlow(last);
			if (looped) {
				sum -= polled;
				double result = ((double) sum) / k;
				// Rebind self to fast alternative if count remains constant now.
				setTarget(fastAvg.bindTo(this).asType(averageMethodType));
				return result;
			} else {
				return ((double) sum) / (counter + 1);
			}
		}
		
		// I'm public, so I can be seen by lookup. Please, do not alter my visibility.
		public double kAverageFast(int last) {
			sum += last;
			int polled = addFast(last);
			sum -= polled;
			return ((double) sum) / k;
		}
		
		int addSlow(int a) {
			if (counter + 1 == queue.length) {
				looped = true;
			}
			return addFast(a);
		}
		
		int addFast(int a) {
			int i = (++counter) % queue.length;
			int prev = queue[i];
			queue[i] = a;
			return prev;
		}
	}
	
	/**
	 * More complicated averager which uses array with
	 * looping index instead of LinkedList..
	 */
	private static class ArrayAverager {
		private final int k;
		private final int[] queue;
		private int counter = -1;
		private boolean looped = false;
		private int sum;
		
		ArrayAverager(int k) {
			this.k = k;
			queue = new int[k];
		}
		
		double kAverage(int last) {
			if (looped) {
				return kAverageFast(last);
			} else {
				return kAverageSlow(last);
			}
		}
		
		double kAverageSlow(int last) {
			sum += last;
			int polled = addSlow(last);
			if (looped) {
				sum -= polled;
				return ((double) sum) / k;
			} else {
				return ((double) sum) / (counter + 1);
			}
		}
		
		double kAverageFast(int last) {
			sum += last;
			int polled = addFast(last);
			sum -= polled;
			return ((double) sum) / k;
		}
		
		int addSlow(int a) {
			if (counter + 1 == queue.length) {
				looped = true;
			}
			return addFast(a);
		}
		
		int addFast(int a) {
			counter = (++counter) % queue.length;
			int prev = queue[counter];
			queue[counter] = a;
			return prev;
		}
	}
	
	/**
	 * Initial averager class with naive queue implementation.
	 */
	private static class LinkedListAverager {
		private final int k;
		private final Queue<Integer> queue;
		private int sum;
		
		LinkedListAverager(int k) {
			this.k = k;
			queue = new LinkedList<>();
		}
		
		double kAverage(int last) {
			sum += last;
			queue.add(last);
			if (queue.size() <= k) {
				return ((double) sum) / queue.size();
			} else {
				sum -= queue.poll();
				return ((double) sum) / k;
			}
		}
	}
	
	/**
	 * Averager class with naive array list implementation.
	 */
	private static class ArrayListAverager {
		private final int k;
		private final ArrayList<Integer> queue;
		private int sum;
		
		ArrayListAverager(int k) {
			this.k = k;
			queue = new ArrayList<>();
		}
		
		double kAverage(int last) {
			sum += last;
			queue.add(last);
			if (queue.size() <= k) {
				return ((double) sum) / queue.size();
			} else {
				sum -= queue.remove(queue.size() - 1);
				return ((double) sum) / k;
			}
		}
	}
}
