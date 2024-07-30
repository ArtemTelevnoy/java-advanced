package info.kgeorgiy.ja.televnoi.iterative;

import info.kgeorgiy.java.advanced.iterative.NewScalarIP;
import info.kgeorgiy.java.advanced.mapper.ParallelMapper;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * This class gives realisations of some functions, functions base on threads
 *
 * @author Artem Televnoy
 */
@SuppressWarnings("unused")
public class IterativeParallelism implements NewScalarIP {
    private final ParallelMapper parallelMapper;

    /**
     * Default constructor
     */
    public IterativeParallelism() {
        parallelMapper = null;
    }

    /**
     * Constructor for working in {@link ParallelMapper} mod
     * @param parallelMapper {@link ParallelMapper} for working
     */
    public IterativeParallelism(final ParallelMapper parallelMapper) {
        this.parallelMapper = parallelMapper;
    }

    private static class MyThread<T> extends Thread {
        private T res;
        private final Supplier<T> sup;

        public MyThread(final Supplier<T> sup) {
            this.sup = sup;
        }

        @Override
        public void run() {
            res = sup.get();
        }
    }

    private static <T, R> List<R> mapper(final Function<T, R> f, final List<? extends T> list) throws InterruptedException {
        final List<MyThread<R>> threadList = new ArrayList<>();
        for (final T el : list) {
            final MyThread<R> myThread = new MyThread<>(() -> f.apply(el));

            threadList.add(myThread);
            myThread.start();
        }

        final List<R> results = new ArrayList<>();
        for (final MyThread<R> t : threadList) {
            t.join();
            results.add(t.res);
        }

        return results;
    }

    private <T, R> R opAbstract(int threads, final List<? extends T> values, final Function<List<T>, R> f,
                                       final Function<List<R>, R> f2, final int step) throws InterruptedException {
        if (threads <= 0) {
            throw new IllegalArgumentException("threads must be positive");
        }

        final List<List<T>> partitions = new ArrayList<>();

        final int realLen = (int) Math.ceil((double) values.size() / step);
        final int move = realLen / (threads = Math.min(threads, realLen));
        int remainder = realLen - move * threads;

        int j = 0;
        for (int i = 0; i < threads; i++) {
            final List<T> partition = new ArrayList<>();

            for (int k = 0; k < move + (remainder > 0 ? 1 : 0); k++, j += step) {
                partition.add(values.get(j));
            }
            if (remainder > 0) {
                remainder--;
            }

            partitions.add(partition);
        }

        return f2.apply(parallelMapper == null ? mapper(f, partitions) : parallelMapper.map(f, partitions));
    }

    /**
     * Find maximum in {@code values}
     *
     * @param threads number of concurrent threads.
     * @param values values to get maximum of.
     * @param comparator value comparator.
     * @param step step size.
     * @return maximum in {@code values}
     * @param <T> type of {@code values}
     * @throws InterruptedException if {@link Thread#join()} throw this
     * @throws NullPointerException if {@link List#isEmpty()} on {@code values} is true
     * @throws IllegalArgumentException if {@code threads} not positive
     */
    @Override
    public <T> T maximum(int threads, List<? extends T> values, Comparator<? super T> comparator, int step) throws InterruptedException {
        if (values.isEmpty()) {
            throw new NoSuchElementException("Empty list");
        }
        return opAbstract(threads, values, list -> list.stream().max(comparator).orElse(null),
                list -> list.stream().filter(Objects::nonNull).max(comparator).orElse(null), step);
    }

    /**
     * Find maximum in {@code values}
     *
     * @param threads number of concurrent threads.
     * @param values values to get minimum of.
     * @param comparator value comparator.
     * @param step step size.
     * @return minimum in {@code values}
     * @param <T> type of {@code values}
     * @throws InterruptedException if {@link Thread#join()} throw this
     * @throws NullPointerException if {@link List#isEmpty()} on {@code values} is true
     * @throws IllegalArgumentException if {@code threads} not positive
     */
    @Override
    public <T> T minimum(int threads, List<? extends T> values, Comparator<? super T> comparator, int step) throws InterruptedException {
        return maximum(threads, values, comparator.reversed(), step);
    }

    /**
     * Checks whether the {@code predicate} is {@code true} on all elements in {@code values}
     *
     * @param threads number of concurrent threads.
     * @param values values to test.
     * @param predicate test predicate.
     * @param step step size.
     * @return {@code true} if predicate on all values is {@code true}, otherwise {@code false}
     * @param <T> type of {@code values}
     * @throws InterruptedException if {@link Thread#join()} throw this
     * @throws IllegalArgumentException if {@code threads} not positive
     */
    @Override
    public <T> boolean all(int threads, List<? extends T> values, Predicate<? super T> predicate, int step) throws InterruptedException {
        return opAbstract(threads, values, list -> list.stream().allMatch(predicate),
                list -> list.stream().reduce(true, (o1, o2) -> o1 & o2), step);
    }

    /**
     * Checks whether the {@code predicate} is {@code true} on any elements in {@code values}
     *
     * @param threads number of concurrent threads.
     * @param values values to test.
     * @param predicate test predicate.
     * @param step step size.
     * @return {@code true} if predicate on any values is {@code true}, otherwise {@code false}
     * @param <T> type of {@code values}
     * @throws InterruptedException if {@link Thread#join()} throw this
     * @throws IllegalArgumentException if {@code threads} not positive
     */
    @Override
    public <T> boolean any(int threads, List<? extends T> values, Predicate<? super T> predicate, int step) throws InterruptedException {
        return !all(threads, values, predicate.negate(), step);
    }

    /**
     * Count truthful {@code predicate} elements in {@code values}
     *
     * @param threads number of concurrent threads.
     * @param values values to test.
     * @param predicate test predicate.
     * @param step step size.
     * @return count of elements with truthful {@code predicate} in {@code values}
     * @param <T> type of {@code values}
     * @throws InterruptedException if {@link Thread#join()} throw this
     * @throws IllegalArgumentException if {@code threads} not positive
     */
    @Override
    public <T> int count(int threads, List<? extends T> values, Predicate<? super T> predicate, int step) throws InterruptedException {
        return opAbstract(threads, values, list -> list.stream().filter(predicate).mapToInt(o -> 1).sum(),
                list -> list.stream().reduce(0, Integer::sum), step);
    }
}
