package info.kgeorgiy.ja.televnoi.iterative;

import info.kgeorgiy.java.advanced.mapper.ParallelMapper;

import java.util.*;
import java.util.function.Function;

/**
 * Class with method {@link ParallelMapper#map}, base on parallel threads working
 *
 * @author Artem Televnoy
 */
@SuppressWarnings("unused")
public class ParallelMapperImpl implements ParallelMapper {
    private final List<Thread> threadList;
    private final Queue<Runnable> queue;

    /**
     * Constructor
     *
     * @param threads count of working threads
     * @throws IllegalArgumentException if {@code threads} not positive
     */
    public ParallelMapperImpl(final int threads) {
        if (threads <= 0) {
            throw new IllegalArgumentException("threads must be positive");
        }

        threadList = new ArrayList<>();
        queue = new LinkedList<>();

        for (int i = 0; i < threads; i++) {
            final Thread thread = new Thread(() -> {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        Runnable q;
                        synchronized (queue) {
                            while (queue.isEmpty()) {
                                queue.wait();
                            }

                            q = queue.poll();
                        }

                        q.run();
                    }
                } catch (InterruptedException ignored) {}
            });

            threadList.add(thread);
            thread.start();
        }
    }

    private static class ResList<T> {
        private final List<T> res;
        private int size;

        public ResList(final int size) {
            res = new ArrayList<>(Collections.nCopies(size, null));
            this.size = size;
        }

        synchronized private void addRes(final int index, final T value) {
            res.set(index, value);
            if (--size == 0) {
                notify();
            }
        }

        synchronized private List<T> get() throws InterruptedException {
            while (size > 0) {
                wait();
            }
            return res;
        }
    }

    /**
     * Apply {@code f} on elements in {@code items}
     *
     * @param f {@link Function} for applying elements in {@code items}
     * @param items {@link List} of elements
     * @return {@link List} of applying {@code f} on elements from {@code items}
     * @param <T> {@code items} type
     * @param <R> result list type
     * @throws InterruptedException if {@link Object#wait()} throw this
     * @throws IllegalStateException if all threads was already closed
     */
    @Override
    public <T, R> List<R> map(Function<? super T, ? extends R> f, List<? extends T> items) throws InterruptedException {
        if (threadList.isEmpty()) {
            throw new IllegalStateException("incorrect operation, threads was already closed");
        }

        final ResList<R> resList = new ResList<>(items.size());

        for (int i = 0; i < items.size(); i++) {
            final int index = i;
            synchronized (queue) {
                queue.add(() -> resList.addRes(index, f.apply(items.get(index))));
                queue.notify();
            }
        }

        return resList.get();
    }

    /**
     * Close all threads
     *
     * @throws IllegalStateException if all threads was already closed
     */
    @Override
    public void close() {
        if (threadList.isEmpty()) {
            throw new IllegalStateException("threads was already closed");
        }

        threadList.forEach(o -> {
            o.interrupt();
            try {
                o.join();
            } catch (InterruptedException ignored) {}
        });

        queue.clear();
        threadList.clear();
    }
}
