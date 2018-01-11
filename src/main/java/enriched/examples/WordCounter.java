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

import java.util.*;

public class WordCounter {

    private static List<String> keywords;

    public static void setKeywords(List<String> keywords) {
        WordCounter.keywords = keywords;
    }

    public static int getKeywordCount(String url) {
        return 1;
    }

}