package enriched.examples;

import jdk.nashorn.internal.runtime.NumberToString;

import java.io.Console;
import java.util.Arrays;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Created by enriched on 1/12/18. */
public class ExampleCli {
  public static void main(String args[]) {
    Console c = System.console();
    if (c == null) {
      System.err.println("No console.");
      System.exit(1);
    }
    String input;
    Pattern keysPattern = Pattern.compile("keys: (.*)");
    Pattern keysSplitter = Pattern.compile("([^\\\\]? )+");
    do {
      c.printf("Please Enter\n");
      c.printf("keys: string separated list of keys\n");
      c.printf("  -or-\n");
      c.printf("http://resource.to/scan\n");
      input = c.readLine();

      try {
        Matcher keysMatcher = keysPattern.matcher(input);
        if (keysMatcher.find()) {
          String allKeys = keysMatcher.group(1);
          c.writer().println("Matching pattern: " + keysSplitter.pattern());
          String[] splitKeys = keysSplitter.split(allKeys);
          WordCounter.setKeywords(Arrays.asList(splitKeys));
          c.printf("Set the) keywords: %s\n", Arrays.toString(splitKeys));
          c.writer().println();
          c.writer().println();
        } else {
          int count = WordCounter.getKeywordCount(input);
          c.printf("Got the keyword count: %s\n", NumberToString.stringFor(count));
          c.writer().println();
          c.writer().println();
        }
      } catch (Exception e) {
        System.err.println("Got Error: :" + e.getMessage());
        e.printStackTrace();
      }
    } while (!Objects.equals(input, "q"));
  }
}
