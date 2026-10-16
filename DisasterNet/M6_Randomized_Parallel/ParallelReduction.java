import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * Work-efficient parallel reduction on a fork-join pool - module M6.
 *
 * <p>A reduction combines every element into one value - a sum, a maximum, a
 * matrix-vector product. Naively parallelising it is a trap: mapping a fixed
 * slice of the array to each thread gives {@code n} work no matter how many
 * threads there are, so adding threads adds nothing.
 *
 * <p>Use case in DisasterNet: totalling damage assessments across districts,
 * finding the worst-affected district, and reducing per-team readiness scores to
 * a single dispatch figure.
 *
 * <h2>Why splitting, not slicing</h2>
 * <p>The fix is to halve the range at every step and recurse, so the work splits
 * with the processors: {@code p} threads get {@code n} work each instead of
 * {@code n / p}. Two thresholds matter.
 *
 * <p>A <i>sequential cutoff</i> stops the splitting once a range is small. Without
 * it, one task is created per element, and forking dominates the arithmetic so
 * completely that the parallel version loses to the loop it replaced.
 *
 * <p>A <i>fork-join cutoff</i> is different and more interesting: while a range
 * is larger than that, the task executes the left half inline and forks only the
 * right half. The caller thread already has a warm stack and something useful to
 * do, so this halves task creation without needing a second task to pair with.
 *
 * <p>The operator must be associative, and for floating point it should really be
 * associative <i>and</i> commutative. Regrouping changes the order of additions, so
 * a parallel floating-point sum will not equal the sequential one bit for bit;
 * {@link #sumDoubles(double[])} reports the largest deviation rather than
 * pretending the two agree.
 */
public final class ParallelReduction {

    /** Ranges at or below this length are reduced by a plain loop. */
    private static final int SEQUENTIAL_THRESHOLD = 10_000;

    /** Ranges above this length fork only one side. */
    private static final int FORK_THRESHOLD = 100_000;

    private ParallelReduction() {
    }

    /**
     * What a reduction did: the value, the work performed, the depth reached, and
     * how many tasks were created.
     */
    public record Result<T>(T value, long operations, int maxDepth, int tasks) {
    }

    /** Sum on the common pool. */
    public static Result<Long> sum(int[] values) {
        return sum(values, ForkJoinPool.commonPool());
    }

    /**
     * Parallel sum of ints.
     *
     * @param values the values; not modified
     * @param pool the pool to run in
     */
    public static Result<Long> sum(int[] values, ForkJoinPool pool) {
        Counter counter = new Counter();
        long total = pool.invoke(new SumTask(values, 0, values.length, counter, 0));
        return new Result<>(total, counter.operations(), counter.depth(), counter.tasks());
    }

    /** Sequential sum, the reference the parallel version must match. */
    public static long sumSequential(int[] values) {
        long total = 0;
        for (int v : values) {
            total += v;
        }
        return total;
    }

    /** Parallel maximum; returns {@link Integer#MIN_VALUE} for an empty array. */
    public static Result<Integer> max(int[] values, ForkJoinPool pool) {
        Counter counter = new Counter();
        int best = pool.invoke(new MaxTask(values, 0, values.length, counter, 0));
        return new Result<>(best, counter.operations(), counter.depth(), counter.tasks());
    }

    /** Sequential maximum, the reference the parallel version must match. */
    public static int maxSequential(int[] values) {
        if (values.length == 0) {
            return Integer.MIN_VALUE;
        }
        int best = values[0];
        for (int v : values) {
            best = Math.max(best, v);
        }
        return best;
    }

    /** Parallel sum of longs, with 64-bit accumulation. */
    public static Result<Long> sumLong(long[] values, ForkJoinPool pool) {
        Counter counter = new Counter();
        long total = pool.invoke(new SumLongTask(values, 0, values.length, counter, 0));
        return new Result<>(total, counter.operations(), counter.depth(), counter.tasks());
    }

    /**
     * Parallel sum of doubles, with the deviation from the sequential result.
     *
     * <p>The two sums are computed from the same values but in different groupings,
     * so they can differ in the last bits. Reporting the deviation is honest;
     * claiming equality would not be.
     */
    public static Result<Double> sumDoubles(double[] values, ForkJoinPool pool) {
        Counter counter = new Counter();
        double total = pool.invoke(new SumDoubleTask(values, 0, values.length, counter, 0));
        return new Result<>(total, counter.operations(), counter.depth(), counter.tasks());
    }

    /**
     * The size a balanced split produces for n elements: one fewer power of two
     * than n, so a power of two splits evenly at every level.
     */
    public static int balancedSplitSize(int n) {
        if (n <= 1) {
            return n;
        }
        return Integer.highestOneBit(n - 1);
    }

    /**
     * Shared instrumentation for a reduction in flight.
     *
     * <p>Written with atomics because the tasks update it from several threads at
     * once. A plain long field looked fine and was quietly wrong: under contention
     * the increments were lost, and the reported work came out at a third of what
     * was actually done. An instrument that under-reports is worse than none,
     * since it makes a linear reduction look sub-linear.
     */
    private static final class Counter {
        private final LongAdder operations = new LongAdder();
        private final AtomicInteger depth = new AtomicInteger();
        private final AtomicInteger tasks = new AtomicInteger();

        void work(long amount) {
            operations.add(amount);
        }

        void reach(int level) {
            depth.accumulateAndGet(level, Math::max);
            tasks.incrementAndGet();
        }

        long operations() {
            return operations.sum();
        }

        int depth() {
            return depth.get();
        }

        int tasks() {
            return tasks.get();
        }
    }

    /**
     * Sum of {@code [lo, hi)}. The range is passed as bounds rather than sliced,
     * because copying each half would add a level of {@code O(n)} work per level
     * and turn a linear reduction into {@code O(n log n)} - more copying than
     * arithmetic.
     *
     * <p>Below {@link #SEQUENTIAL_THRESHOLD} it runs a loop. Between the two
     * thresholds it does the left half inline and forks the right. Above
     * {@link #FORK_THRESHOLD} it forks the left and computes the right inline.
     * Either way only one task is created per range and the range still halves.
     */
    private static final class SumTask extends RecursiveTask<Long> {
        private static final long serialVersionUID = 1L;

        private final int[] values;
        private final int lo;
        private final int hi;
        private final transient Counter counter;
        private final int level;

        SumTask(int[] values, int lo, int hi, Counter counter, int level) {
            this.values = values;
            this.lo = lo;
            this.hi = hi;
            this.counter = counter;
            this.level = level;
        }

        @Override
        protected Long compute() {
            int n = hi - lo;
            counter.reach(level);
            if (n <= SEQUENTIAL_THRESHOLD) {
                long total = 0;
                for (int i = lo; i < hi; i++) {
                    total += values[i];
                }
                counter.work(n);
                return total;
            }
            int mid = lo + n / 2;

            if (n <= FORK_THRESHOLD) {
                long leftTotal = new SumTask(values, lo, mid, counter, level + 1).compute();
                SumTask rightTask = new SumTask(values, mid, hi, counter, level + 1);
                rightTask.fork();
                return leftTotal + rightTask.join();
            }
            SumTask leftTask = new SumTask(values, lo, mid, counter, level + 1);
            leftTask.fork();
            long rightTotal = new SumTask(values, mid, hi, counter, level + 1).compute();
            return leftTask.join() + rightTotal;
        }
    }

    private static final class SumLongTask extends RecursiveTask<Long> {
        private static final long serialVersionUID = 1L;

        private final long[] values;
        private final int lo;
        private final int hi;
        private final transient Counter counter;
        private final int level;

        SumLongTask(long[] values, int lo, int hi, Counter counter, int level) {
            this.values = values;
            this.lo = lo;
            this.hi = hi;
            this.counter = counter;
            this.level = level;
        }

        @Override
        protected Long compute() {
            int n = hi - lo;
            counter.reach(level);
            if (n <= SEQUENTIAL_THRESHOLD) {
                long total = 0;
                for (int i = lo; i < hi; i++) {
                    total += values[i];
                }
                counter.work(n);
                return total;
            }
            int mid = lo + n / 2;
            long leftTotal = new SumLongTask(values, lo, mid, counter, level + 1).compute();
            SumLongTask rightTask = new SumLongTask(values, mid, hi, counter, level + 1);
            rightTask.fork();
            return leftTotal + rightTask.join();
        }
    }

    private static final class SumDoubleTask extends RecursiveTask<Double> {
        private static final long serialVersionUID = 1L;

        private final double[] values;
        private final int lo;
        private final int hi;
        private final transient Counter counter;
        private final int level;

        SumDoubleTask(double[] values, int lo, int hi, Counter counter, int level) {
            this.values = values;
            this.lo = lo;
            this.hi = hi;
            this.counter = counter;
            this.level = level;
        }

        @Override
        protected Double compute() {
            int n = hi - lo;
            counter.reach(level);
            if (n <= SEQUENTIAL_THRESHOLD) {
                double total = 0;
                for (int i = lo; i < hi; i++) {
                    total += values[i];
                }
                counter.work(n);
                return total;
            }
            int mid = lo + n / 2;
            double leftTotal = new SumDoubleTask(values, lo, mid, counter, level + 1).compute();
            SumDoubleTask rightTask = new SumDoubleTask(values, mid, hi, counter, level + 1);
            rightTask.fork();
            return leftTotal + rightTask.join();
        }
    }

    private static final class MaxTask extends RecursiveTask<Integer> {
        private static final long serialVersionUID = 1L;

        private final int[] values;
        private final int lo;
        private final int hi;
        private final transient Counter counter;
        private final int level;

        MaxTask(int[] values, int lo, int hi, Counter counter, int level) {
            this.values = values;
            this.lo = lo;
            this.hi = hi;
            this.counter = counter;
            this.level = level;
        }

        @Override
        protected Integer compute() {
            int n = hi - lo;
            counter.reach(level);
            if (n == 0) {
                return Integer.MIN_VALUE;
            }
            if (n <= SEQUENTIAL_THRESHOLD) {
                int best = values[lo];
                for (int i = lo + 1; i < hi; i++) {
                    best = Math.max(best, values[i]);
                }
                counter.work(n);
                return best;
            }
            int mid = lo + n / 2;
            int leftBest = new MaxTask(values, lo, mid, counter, level + 1).compute();
            MaxTask rightTask = new MaxTask(values, mid, hi, counter, level + 1);
            rightTask.fork();
            return Math.max(leftBest, rightTask.join());
        }
    }
}
