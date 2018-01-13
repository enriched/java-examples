package enriched.examples;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Created by enriched on 1/11/18.
 */
class WordCounterTest {

    private static List<String> testKeywords1 = Arrays.asList("one", "two", "buckle", "my", "color");

    @Test
    void setKeywords() {
        WordCounter.setKeywords(testKeywords1);
    }

    @Test
    void getKeywordCount() throws InterruptedException, ExecutionException, IOException {
        WordCounter.setKeywords(testKeywords1);
        Integer result = WordCounter.getKeywordCount("https://raw.githubusercontent.com/dwyl/english-words/master/words.txt");
        assertEquals(6, result.intValue());
    }

}