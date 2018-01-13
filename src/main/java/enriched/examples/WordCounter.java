package enriched.examples;

/*
 ** WORDCOUNTER.JAVA
 **
 ** Library object that, given a url and set of keywords, returns the number of times
 ** any of these keywords appear in the resource identified by the URL. Only exact matches should "hit".
 **
 ** The library must support multi-threaded access. The keyword set may be updated
 ** at any time. Results for a given resource should be cached for 1 hour to reduce network
 ** traffic and increase performance.
 **
 ** Should stand alone with no additional dependencies beyond what is in the JRE.
 */

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Concurrently scans */
public class WordCounter {

  private static ScheduledExecutorService executor = Executors.newScheduledThreadPool(3);
  private static final AtomicReference<WordMatchContext> context = new AtomicReference<>();

  /**
   * Set the kewords to be used for the matching.
   *
   * <p>If the keywords are the same as what is currently there then nothing is updated. When the
   * keywords are updated, the entire context is lost along with the cached results.
   */
  public static void setKeywords(List<String> keywords) {
    Set<String> newKeywordSet = new HashSet<>(keywords);
    WordCounter.context.updateAndGet(
        currentContext -> {
          if (currentContext != null && currentContext.keywords.equals(newKeywordSet)) {
            return currentContext;
          } else {
            if (currentContext != null) {
              currentContext.destroy();
            }
            return new WordMatchContext(newKeywordSet);
          }
        });
  }

  /**
   * Given the passed in url return the number of times that the keywords appear, using the cached
   * version if it exists.
   */
  public static int getKeywordCount(String url) throws ExecutionException, InterruptedException {
    WordMatchContext wordMatchContext = WordCounter.context.get();
    if (wordMatchContext == null) {
      System.out.println("Keys have not been set yet, 0 matches");
      return 0;
    }
    System.out.println("Checking url: " + url);
    Integer result = wordMatchContext.getOrScanUrl(url).get().matchCount;
    System.out.println("Got result for url: [" + url + ", " + result + "]");
    return result;
  }

  /**
   * Fetch the content of the url and put it into a String
   *
   * <p>A more optimized version might return the stream and operate on that.
   */
  private static Future<String> getUrlResourceString(String url) throws IOException {
    return executor.submit(
        () -> {
          URL resourceUrl = new URL(url);
          try (BufferedReader br =
              new BufferedReader(new InputStreamReader(resourceUrl.openStream()))) {
            return br.lines().collect(Collectors.joining(System.lineSeparator()));
          }
        });
  }

  /**
   * Context that is used within a thread when matching against a domain. These are swapped out
   * atomically so WordCounter:setKeywords calls do not cause concurrency problems with ongoing
   * resource parsing.
   */
  private static class WordMatchContext {
    private static AtomicInteger curentVersion = new AtomicInteger();
    private Integer instanceVersion;
    private Set<String> keywords;
    private List<Pattern> patterns;
    ConcurrentHashMap<String, Future<WordMatchResult>> urlResults = new ConcurrentHashMap<>();
    DelayQueue<WordMatchResult> expirations = new DelayQueue<>();
    ScheduledFuture cleanupTask;

    WordMatchContext(Set<String> keywords) {
      this.instanceVersion = WordMatchContext.curentVersion.incrementAndGet();
      System.out.println("Creating WordMatchContext instance: " + this.instanceVersion);
      this.keywords = keywords;
      this.patterns =
          this.keywords
              .parallelStream()
              .map(keyword -> Pattern.compile(keyword, Pattern.LITERAL))
              .collect(Collectors.toList());
      this.cleanupTask = executor.scheduleAtFixedRate(this::cleanup, 5, 10, TimeUnit.SECONDS);
    }

    /** Gets the url scan results from the cache or computes new ones */
    Future<WordMatchResult> getOrScanUrl(String resourceUrl) {
      System.out.println("Trying to get url");
      return urlResults.computeIfAbsent(resourceUrl, this::scanUrl);
    }

    /**
     * Create an array of matchers for the keywords associated with the current context
     *
     * @param charSequence The character sequence the matchers will operate over;
     */
    private List<Matcher> createMatchers(CharSequence charSequence) {
      return patterns
          .parallelStream()
          .map(pattern -> pattern.matcher(charSequence))
          .collect(Collectors.toList());
    }

    /** Scan the passed in url with matchers generated from the patterns in this context. */
    private Future<WordMatchResult> scanUrl(String url) {
      System.out.println("Scanning url");
      return executor.submit(
          () -> {
            System.out.println("running scan");
            String resourceString = WordCounter.getUrlResourceString(url).get();
            List<Matcher> matchers = createMatchers(resourceString);
            String allKeys =
                matchers.stream().map(m -> m.pattern().pattern()).collect(Collectors.joining(" "));
            System.out.println("Running with keys: " + allKeys);

            int totalMatches =
                createMatchers(resourceString)
                    .parallelStream()
                    .map(
                        matcher -> {
                          int matchCount = 0;
                          while (matcher.find()) {
                            int from = Math.max(matcher.start() - 10, 0);
                            int to = Math.min(matcher.end() + 10, resourceString.length() - 1);
                            String matchWithSurrounding = resourceString.substring(from, to);
                            System.out.println(
                                "Matcher "
                                    + matcher.pattern().pattern()
                                    + " found: "
                                    + matchWithSurrounding);
                            matchCount++;
                          }
                          return matchCount;
                        })
                    .mapToInt(i -> i)
                    .sum();

            System.out.println("Got matches " + totalMatches);
            WordMatchResult result = new WordMatchResult(this, url, totalMatches);
            System.out.println("Result done");
            expirations.offer(result);
            System.out.println("Offered");
            return result;
          });
    }

    /** Remove expired entries from the cache */
    private void cleanup() {
      System.out.println("Running cleanup");
      final Collection<WordMatchResult> expired = new ArrayList<>();
      this.expirations.drainTo(expired);
      expired.forEach(
          r -> {
            System.out.println("URL timed out of cache: " + r.url);
            urlResults.remove(r.url);
          });
    }

    /** Kills the scheduled task */
    public void destroy() {
      System.out.println("Destroying");
      this.cleanupTask.cancel(false);
    }
  }

  /** Stores the results from parsing a resource so that they can be cached and looked up later. */
  private static class WordMatchResult implements Delayed {
    static long CACHE_FOR_MILLIS = 100;//3600000; // One hour
    long finishedAt = System.currentTimeMillis();

    WordMatchContext context;
    String url;
    Integer matchCount;

    WordMatchResult(WordMatchContext context, String url, Integer result) {
      this.url = url;
      this.context = context;
      this.matchCount = result;
    }

    @Override
    public long getDelay(TimeUnit unit) {
      return unit.convert(
          CACHE_FOR_MILLIS - (System.currentTimeMillis() - finishedAt), TimeUnit.MILLISECONDS);
    }

    @Override
    public int compareTo(Delayed delayed) {
      if (delayed == this) {
        return 0;
      }
      long diff = getDelay(TimeUnit.MILLISECONDS) - delayed.getDelay(TimeUnit.MILLISECONDS);
      return (diff > 1) ? 1 : (diff < 1) ? -1 : 0;
    }
  }
}
