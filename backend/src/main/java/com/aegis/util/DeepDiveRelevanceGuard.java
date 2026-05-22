package com.aegis.util;

import java.util.regex.Pattern;

/** Pre-filters obviously off-topic Ask Agent prompts before LLM calls. */
public final class DeepDiveRelevanceGuard {

    private static final Pattern SIMPLE_MATH =
            Pattern.compile("^\\s*\\d+\\s*[+\\-*/×÷]\\s*\\d+\\s*[=]?\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern GREETING_OR_TEST =
            Pattern.compile("^\\s*(hi|hello|hey|test|ping|ok|yes|no|help|\\?|1\\+1|2\\+2)\\s*[!.?]*\\s*$",
                    Pattern.CASE_INSENSITIVE);

    private DeepDiveRelevanceGuard() {}

    public static boolean isObviouslyOffTopic(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        String q = question.trim();
        if (q.length() <= 2) {
            return true;
        }
        if (SIMPLE_MATH.matcher(q).matches()) {
            return true;
        }
        return GREETING_OR_TEST.matcher(q).matches();
    }

    public static String offTopicResponse() {
        return """
                Answer:
                This assistant only answers strategic questions about the selected competitor news item—not general trivia, math drills, or unrelated chat. Rephrase your question in terms of the headline, competitor move, or market impact.

                Strategic implications:
                • Off-topic prompts are not analysed against the article.

                Recommended actions:
                • Ask how this news affects your positioning, risks, or response options.""";
    }
}
