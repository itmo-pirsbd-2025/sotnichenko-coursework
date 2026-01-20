package ru.sotnichenko.sentiment.dictionary;

/**
 * Словарь тональности для английского языка.
 * Содержит часто используемые слова с эмоциональной окраской.
 */
public class EnglishSentimentDictionary extends SentimentDictionary {

    public EnglishSentimentDictionary() {
        super("English");
        loadDefaultWords();
    }

    /**
     * Загружает слова по умолчанию.
     */
    private void loadDefaultWords() {
        // Сильно позитивные слова (0.8 - 1.0)
        addWord("excellent", 0.95);
        addWord("amazing", 0.95);
        addWord("wonderful", 0.9);
        addWord("fantastic", 0.9);
        addWord("outstanding", 0.9);
        addWord("brilliant", 0.9);
        addWord("superb", 0.9);
        addWord("perfect", 0.95);
        addWord("awesome", 0.85);
        addWord("incredible", 0.85);
        addWord("magnificent", 0.9);
        addWord("exceptional", 0.85);
        addWord("marvelous", 0.85);
        addWord("phenomenal", 0.85);
        addWord("spectacular", 0.85);
        addWord("love", 0.8);
        addWord("adore", 0.85);

        // Позитивные слова (0.4 - 0.8)
        addWord("great", 0.75);
        addWord("good", 0.6);
        addWord("nice", 0.55);
        addWord("pleasant", 0.55);
        addWord("enjoyable", 0.6);
        addWord("lovely", 0.65);
        addWord("beautiful", 0.7);
        addWord("delightful", 0.7);
        addWord("impressive", 0.65);
        addWord("remarkable", 0.65);
        addWord("terrific", 0.75);
        addWord("fabulous", 0.75);
        addWord("cool", 0.55);
        addWord("fine", 0.45);
        addWord("decent", 0.4);
        addWord("satisfactory", 0.45);
        addWord("positive", 0.5);
        addWord("useful", 0.5);
        addWord("helpful", 0.55);
        addWord("valuable", 0.55);
        addWord("quality", 0.5);
        addWord("recommend", 0.6);
        addWord("recommended", 0.6);
        addWord("happy", 0.65);
        addWord("pleased", 0.6);
        addWord("satisfied", 0.6);
        addWord("glad", 0.55);
        addWord("thankful", 0.6);
        addWord("grateful", 0.65);
        addWord("like", 0.45);
        addWord("enjoy", 0.55);
        addWord("best", 0.75);
        addWord("better", 0.5);
        addWord("easy", 0.4);
        addWord("fast", 0.4);
        addWord("reliable", 0.55);
        addWord("efficient", 0.5);
        addWord("effective", 0.5);
        addWord("success", 0.6);
        addWord("successful", 0.6);
        addWord("winner", 0.6);

        // Слабо негативные слова (-0.4 - -0.1)
        addWord("okay", 0.1);
        addWord("ok", 0.1);
        addWord("mediocre", -0.3);
        addWord("average", -0.1);
        addWord("boring", -0.4);
        addWord("slow", -0.3);
        addWord("difficult", -0.3);
        addWord("complicated", -0.3);
        addWord("confusing", -0.35);
        addWord("disappointing", -0.5);
        addWord("disappointed", -0.5);
        addWord("frustrating", -0.5);
        addWord("frustrated", -0.5);
        addWord("annoying", -0.5);
        addWord("annoyed", -0.45);
        addWord("problem", -0.4);
        addWord("issue", -0.35);
        addWord("bug", -0.45);
        addWord("error", -0.45);
        addWord("concern", -0.3);

        // Негативные слова (-0.8 - -0.4)
        addWord("bad", -0.65);
        addWord("poor", -0.6);
        addWord("terrible", -0.8);
        addWord("awful", -0.8);
        addWord("horrible", -0.85);
        addWord("dreadful", -0.8);
        addWord("dislike", -0.55);
        addWord("hate", -0.85);
        addWord("worst", -0.9);
        addWord("worse", -0.6);
        addWord("useless", -0.7);
        addWord("worthless", -0.75);
        addWord("waste", -0.65);
        addWord("trash", -0.75);
        addWord("garbage", -0.75);
        addWord("junk", -0.7);
        addWord("scam", -0.85);
        addWord("fraud", -0.85);
        addWord("fake", -0.7);
        addWord("broken", -0.65);
        addWord("defective", -0.65);
        addWord("faulty", -0.6);
        addWord("failed", -0.6);
        addWord("failure", -0.65);
        addWord("disaster", -0.8);
        addWord("catastrophe", -0.85);
        addWord("nightmare", -0.8);
        addWord("ruined", -0.7);
        addWord("destroyed", -0.75);
        addWord("damaged", -0.6);
        addWord("angry", -0.6);
        addWord("upset", -0.5);
        addWord("unhappy", -0.55);
        addWord("sad", -0.5);
        addWord("regret", -0.55);
        addWord("sorry", -0.3);

        // Сильно негативные слова (-1.0 - -0.8)
        addWord("disgusting", -0.9);
        addWord("repulsive", -0.9);
        addWord("revolting", -0.9);
        addWord("vile", -0.85);
        addWord("hideous", -0.85);
        addWord("atrocious", -0.9);
        addWord("abysmal", -0.9);
        addWord("pathetic", -0.75);
        addWord("despise", -0.9);
        addWord("loathe", -0.9);

        // Позитивные фразы
        addPhrase("highly recommend", 0.85);
        addPhrase("strongly recommend", 0.85);
        addPhrase("love it", 0.85);
        addPhrase("must have", 0.75);
        addPhrase("top notch", 0.85);
        addPhrase("first class", 0.8);
        addPhrase("world class", 0.85);
        addPhrase("best ever", 0.9);
        addPhrase("five stars", 0.9);
        addPhrase("5 stars", 0.9);
        addPhrase("well done", 0.7);
        addPhrase("thumbs up", 0.7);
        addPhrase("good job", 0.65);
        addPhrase("great job", 0.75);
        addPhrase("very good", 0.7);
        addPhrase("so good", 0.75);
        addPhrase("really good", 0.7);

        // Негативные фразы
        addPhrase("do not buy", -0.8);
        addPhrase("don't buy", -0.8);
        addPhrase("waste of money", -0.85);
        addPhrase("waste of time", -0.8);
        addPhrase("not worth", -0.7);
        addPhrase("stay away", -0.8);
        addPhrase("avoid at all costs", -0.9);
        addPhrase("one star", -0.85);
        addPhrase("1 star", -0.85);
        addPhrase("zero stars", -0.95);
        addPhrase("0 stars", -0.95);
        addPhrase("rip off", -0.8);
        addPhrase("very bad", -0.8);
        addPhrase("so bad", -0.8);
        addPhrase("really bad", -0.8);
        addPhrase("complete garbage", -0.9);
        addPhrase("total waste", -0.85);
        addPhrase("never again", -0.75);
        addPhrase("does not work", -0.7);
        addPhrase("doesn't work", -0.7);

        // Модификаторы
        addWord("very", 0.0);
        addWord("really", 0.0);
        addWord("quite", 0.0);
        addWord("extremely", 0.0);
        addWord("absolutely", 0.0);
        addWord("completely", 0.0);
        addWord("totally", 0.0);
        addWord("somewhat", 0.0);
        addWord("slightly", 0.0);

        // Отрицания
        addWord("not", 0.0);
        addWord("no", 0.0);
        addWord("never", 0.0);
        addWord("neither", 0.0);
        addWord("nothing", 0.0);
        addWord("none", 0.0);
    }

    /**
     * Создаёт расширенный словарь с дополнительными словами.
     */
    public static EnglishSentimentDictionary extended() {
        EnglishSentimentDictionary dict = new EnglishSentimentDictionary();

        // Сленг и неформальные выражения
        dict.addWord("lit", 0.7);
        dict.addWord("dope", 0.65);
        dict.addWord("sick", 0.6); // В позитивном смысле
        dict.addWord("fire", 0.7);
        dict.addWord("goat", 0.8); // Greatest Of All Time
        dict.addWord("epic", 0.75);
        dict.addWord("legendary", 0.8);
        dict.addWord("insane", 0.6); // В позитивном смысле
        dict.addWord("killer", 0.6); // В позитивном смысле
        dict.addWord("bomb", 0.65); // В позитивном смысле

        dict.addWord("meh", -0.3);
        dict.addWord("cringe", -0.6);
        dict.addWord("lame", -0.55);
        dict.addWord("sucks", -0.7);
        dict.addWord("crap", -0.65);
        dict.addWord("bs", -0.7);
        dict.addWord("sus", -0.4);
        dict.addWord("toxic", -0.6);
        dict.addWord("overrated", -0.45);
        dict.addWord("underwhelming", -0.5);

        // Эмотиконы
        dict.addWord(":)", 0.5);
        dict.addWord(":D", 0.7);
        dict.addWord(";)", 0.4);
        dict.addWord(":(", -0.5);
        dict.addWord(":/", -0.3);
        dict.addWord(":P", 0.3);
        dict.addWord("<3", 0.7);

        return dict;
    }
}
