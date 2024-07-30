package info.kgeorgiy.ja.televnoi.crawler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;

import info.kgeorgiy.java.advanced.crawler.CachingDownloader;
import info.kgeorgiy.java.advanced.crawler.Document;
import info.kgeorgiy.java.advanced.crawler.Downloader;
import info.kgeorgiy.java.advanced.crawler.NewCrawler;
import info.kgeorgiy.java.advanced.crawler.Result;

/**
 * Class for recursive walking on sites
 *
 * @author Artem Televnoy
 */
public class WebCrawler implements NewCrawler {
    private final static int DEFAULT_COUNT = 1;

    private final Downloader downloader;
    private final ExecutorService pagesLoadPool;
    private final ExecutorService linkLoadPool;

    /**
     * Constructor
     *
     * @param downloader  {@link Downloader} for downloading pages
     * @param downloaders count of parallel downloaders
     * @param extractors  count of parallel {@link Document#extractLinks()} on pages
     * @param perHost     count of parallel download of one Host (unused)
     * @throws IllegalArgumentException if {@code extractors} or {@code downloaders} aren't positive
     * @throws NullPointerException     if {@code downloader} was null
     */
    @SuppressWarnings("unused")
    public WebCrawler(final Downloader downloader, final int downloaders, final int extractors, final int perHost) {
        this.downloader = Objects.requireNonNull(downloader, "Downloader must be non null");
        pagesLoadPool = Executors.newFixedThreadPool(downloaders);
        linkLoadPool = Executors.newFixedThreadPool(extractors);
    }

    /**
     * Download page {@code url} on {@code depth}
     *
     * @param url      start <a href="http://tools.ietf.org/html/rfc3986">URL</a>.
     * @param depth    download depth.
     * @param excludes URLs containing one of given substrings are ignored
     * @return {@link Result} of downloading
     * @throws IllegalStateException    if threads was already closed
     * @throws IllegalArgumentException if depth isn't positive
     */
    @Override
    public Result download(String url, int depth, Set<String> excludes) {
        if (pagesLoadPool.isShutdown()) {
            throw new IllegalStateException("illegal operation, object was already close");
        } else if (depth < 0) {
            throw new IllegalArgumentException("depth must be positive");
        }

        return new MyResult().getRes(url, depth, excludes);
    }

    /**
     * Close all working threads
     *
     * @throws IllegalStateException if threads was already closed
     * @throws RuntimeException      if {@link ExecutorService#close} throw this
     */
    @Override
    public void close() {
        if (pagesLoadPool.isShutdown()) {
            throw new IllegalStateException("already close");
        }

        pagesLoadPool.close();
        linkLoadPool.close();
    }

    private class MyResult {
        private final ConcurrentMap<String, IOException> errMap;
        private final Set<String> resPages;

        public MyResult() {
            errMap = new ConcurrentHashMap<>();
            resPages = new ConcurrentSkipListSet<>();
        }

        private Result getRes(final String url, final int depth, final Set<String> excludes) {
            bfsWalker(url, depth, excludes);
            return new Result(new ArrayList<>(resPages), errMap);
        }

        private void bfsWalker(final String url, final int depth, final Set<String> set) {
            final Set<String> urlsToLoad = new ConcurrentSkipListSet<>();
            final Phaser depthPhaser = new Phaser(1);
            urlsToLoad.add(url);

            for (int i = 0; i < depth; i++) {
                final List<String> urls = new ArrayList<>(urlsToLoad);
                urlsToLoad.clear();

                for (final String link : urls) {
                    if (!resPages.contains(link) && !errMap.containsKey(link) && !isIgnored(link, set)) {
                        addTask(i, depth, link, depthPhaser, urlsToLoad);
                    }
                }

                depthPhaser.arriveAndAwaitAdvance();
            }
        }

        // :NOTE: jstyle, name i
        private void addTask(
                final int depth,
                final int maxDepth,
                final String str,
                final Phaser phaser,
                final Set<String> urlsToLoad
        ) {
            phaser.register();
            pagesLoadPool.submit(() -> {
                try {
                    final Document doc = downloader.download(str);
                    resPages.add(str);

                    if (depth != maxDepth - 1) {
                        phaser.register();
                        linkLoadPool.submit(() -> {
                            try {
                                urlsToLoad.addAll(doc.extractLinks());
                            } catch (IOException e) {
                                errMap.put(str, e);
                            } finally {
                                phaser.arriveAndDeregister();
                            }
                        });
                    }

                } catch (IOException e) {
                    errMap.put(str, e);
                } finally {
                    phaser.arriveAndDeregister();
                }
            });
        }

        private static boolean isIgnored(final String str, final Set<String> set) {
            return set.stream().anyMatch(str::contains);
        }
    }

    private static int getArg(final String[] args, final int index) {
        if (index < args.length) {
            try {
                return Integer.parseInt(args[index]);
            } catch (NumberFormatException ignored) {
                // :NOTE: validation exception
            }
        }

        return DEFAULT_COUNT;
    }

    private static void error(final String message) {
        System.err.println(message);
    }

    /**
     * Main method
     *
     * @param args format: url [depth [downloads [extractors [perHost]]]]
     */
    public static void main(String[] args) {
        if (args == null) {
            error("args must be non null");
        } else if (args.length < 1 || args.length > 5) {
            error("length of args mus be more than 1 and less than 5");
        } else {
            try (WebCrawler webCrawler = new WebCrawler(new CachingDownloader(
                    1.0), getArg(args, 2), getArg(args, 3), getArg(args, 4))) {
                webCrawler.download(args[0], getArg(args, 1));
            } catch (IOException e) {
                // :TO-IMPROVE: addition info
                error(e.getMessage());
            }
        }
    }
}
