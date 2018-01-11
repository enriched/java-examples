package enriched.examples;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Created by enriched on 1/11/18.
 */
class WordCounterTest {
    @Test
    void setKeywords() {
        List<String> testKeywords1 = Arrays.asList("one", "two");
        WordCounter.setKeywords(testKeywords1);
    }

    @Test
    void getKeywordCount() {
    }

}