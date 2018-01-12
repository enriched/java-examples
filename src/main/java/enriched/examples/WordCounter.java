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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Concurrently scans */
public class WordCounter {

  private static class WordMatchContext {
    private static AtomicInteger curentVersion = new AtomicInteger();
    private Integer instanceVersion;
    private Set<String> keywords;
    private List<Pattern> patterns;
    ConcurrentHashMap<String, Future<Integer>> urlResults = new ConcurrentHashMap<>();

    WordMatchContext(Set<String> keywords) {
      this.instanceVersion = WordMatchContext.curentVersion.incrementAndGet();
      System.out.println("Creating WordMatchContext instance: " + this.instanceVersion);
      this.keywords = keywords;
      this.patterns =
          this.keywords
              .parallelStream()
              .map(keyword -> Pattern.compile(keyword, Pattern.LITERAL))
              .collect(Collectors.toList());
    }

    List<Matcher> createMatchers(CharSequence charSequence) {
      return patterns
          .parallelStream()
          .map(pattern -> pattern.matcher(charSequence))
          .collect(Collectors.toList());
    }
  }

  private static ExecutorService executor = Executors.newCachedThreadPool();

  private static final AtomicReference<WordMatchContext> context = new AtomicReference<>();

  public static void setKeywords(List<String> keywords) {
    Set<String> newKeywordSet = new HashSet<>(keywords);
    WordCounter.context.updateAndGet(
        currentContext -> {
          if (currentContext != null && currentContext.keywords.equals(newKeywordSet)) {
            return currentContext;
          } else {
            return new WordMatchContext(newKeywordSet);
          }
        });
  }

  public static int getKeywordCount(String url)
      throws IOException, ExecutionException, InterruptedException {
    System.out.println("Checking url: " + url);
    WordMatchContext wordMatchContext = WordCounter.context.get();
    Integer result =
        wordMatchContext
            .urlResults
            .getOrDefault(url, WordCounter.scanUrl(wordMatchContext, url))
            .get();
    System.out.println("Got result for url: [" + url + ", " + result + "]");
    return result;
  }

  private static Future<Integer> scanUrl(WordMatchContext wordMatchContext, String url)
      throws IOException {
    return executor.submit(
        () -> {
          Future<String> futureResourceString = WordCounter.getUrlResourceString(url);
          return wordMatchContext
              .createMatchers(futureResourceString.get())
              .parallelStream()
              .map(
                  matcher -> {
                    int matchCount = 0;
                    while (matcher.find()) {
                      matchCount++;
                    }
                    return matchCount;
                  })
              .mapToInt(i -> i)
              .sum();
        });
  }

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
}
