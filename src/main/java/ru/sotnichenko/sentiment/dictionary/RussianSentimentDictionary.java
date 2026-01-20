package ru.sotnichenko.sentiment.dictionary;

/**
 * Словарь тональности для русского языка.
 * Содержит часто используемые слова с эмоциональной окраской.
 */
public class RussianSentimentDictionary extends SentimentDictionary {

    public RussianSentimentDictionary() {
        super("Russian");
        loadDefaultWords();
    }

    /**
     * Загружает слова по умолчанию.
     */
    private void loadDefaultWords() {
        // Сильно позитивные слова (0.8 - 1.0)
        addWord("превосходно", 1.0);
        addWord("великолепно", 1.0);
        addWord("замечательно", 0.95);
        addWord("потрясающе", 0.95);
        addWord("восхитительно", 0.95);
        addWord("прекрасно", 0.9);
        addWord("отлично", 0.9);
        addWord("идеально", 0.9);
        addWord("шикарно", 0.9);
        addWord("безупречно", 0.9);
        addWord("блестяще", 0.85);
        addWord("изумительно", 0.85);
        addWord("обожаю", 0.85);
        addWord("люблю", 0.8);
        addWord("восторг", 0.8);

        // Позитивные слова (0.4 - 0.8)
        addWord("хорошо", 0.7);
        addWord("классно", 0.7);
        addWord("здорово", 0.7);
        addWord("круто", 0.7);
        addWord("супер", 0.7);
        addWord("чудесно", 0.7);
        addWord("красиво", 0.65);
        addWord("приятно", 0.6);
        addWord("радость", 0.6);
        addWord("счастье", 0.65);
        addWord("удовольствие", 0.6);
        addWord("нравится", 0.55);
        addWord("интересно", 0.5);
        addWord("удобно", 0.5);
        addWord("полезно", 0.5);
        addWord("качественно", 0.55);
        addWord("надёжно", 0.5);
        addWord("надежно", 0.5);
        addWord("быстро", 0.4);
        addWord("легко", 0.4);
        addWord("просто", 0.35);
        addWord("спасибо", 0.5);
        addWord("благодарю", 0.55);
        addWord("рекомендую", 0.6);
        addWord("советую", 0.5);
        addWord("доволен", 0.6);
        addWord("довольна", 0.6);
        addWord("довольны", 0.6);
        addWord("рад", 0.55);
        addWord("рада", 0.55);
        addWord("радует", 0.55);
        addWord("успех", 0.6);
        addWord("победа", 0.65);
        addWord("достижение", 0.5);
        addWord("улучшение", 0.4);

        // Слабо негативные слова (-0.4 - -0.1)
        addWord("неудобно", -0.35);
        addWord("долго", -0.25);
        addWord("сложно", -0.3);
        addWord("непонятно", -0.3);
        addWord("скучно", -0.35);
        addWord("странно", -0.2);
        addWord("неприятно", -0.4);
        addWord("разочарован", -0.4);
        addWord("разочарована", -0.4);
        addWord("проблема", -0.35);
        addWord("недостаток", -0.35);
        addWord("минус", -0.3);

        // Негативные слова (-0.8 - -0.4)
        addWord("плохо", -0.7);
        addWord("ужасно", -0.85);
        addWord("отвратительно", -0.9);
        addWord("кошмар", -0.85);
        addWord("ненавижу", -0.9);
        addWord("дрянь", -0.8);
        addWord("мусор", -0.75);
        addWord("хлам", -0.7);
        addWord("отстой", -0.8);
        addWord("жесть", -0.6);
        addWord("ерунда", -0.5);
        addWord("фигня", -0.55);
        addWord("бред", -0.6);
        addWord("обман", -0.7);
        addWord("разочарование", -0.6);
        addWord("провал", -0.7);
        addWord("неудача", -0.55);
        addWord("катастрофа", -0.8);
        addWord("кризис", -0.6);
        addWord("ошибка", -0.45);
        addWord("баг", -0.5);
        addWord("глюк", -0.5);
        addWord("тормозит", -0.5);
        addWord("виснет", -0.55);
        addWord("сломано", -0.6);
        addWord("сломался", -0.6);
        addWord("сломалось", -0.6);
        addWord("испорчено", -0.6);
        addWord("брак", -0.65);
        addWord("дефект", -0.55);

        // Сильно негативные слова (-1.0 - -0.8)
        addWord("ужас", -0.9);
        addWord("кошмарно", -0.9);
        addWord("омерзительно", -0.95);
        addWord("отвратительный", -0.9);
        addWord("мерзко", -0.85);
        addWord("гадость", -0.85);
        addWord("позор", -0.8);
        addWord("стыд", -0.7);
        addWord("катастрофически", -0.85);

        // Позитивные фразы
        addPhrase("очень хорошо", 0.8);
        addPhrase("очень нравится", 0.75);
        addPhrase("всё отлично", 0.85);
        addPhrase("всем доволен", 0.8);
        addPhrase("от души", 0.7);
        addPhrase("на высоте", 0.75);
        addPhrase("высший класс", 0.85);
        addPhrase("лучший выбор", 0.8);
        addPhrase("настоятельно рекомендую", 0.85);
        addPhrase("горячо рекомендую", 0.9);

        // Негативные фразы
        addPhrase("очень плохо", -0.85);
        addPhrase("не работает", -0.7);
        addPhrase("не рекомендую", -0.7);
        addPhrase("зря потратил", -0.75);
        addPhrase("зря купил", -0.75);
        addPhrase("деньги на ветер", -0.8);
        addPhrase("худший опыт", -0.9);
        addPhrase("полный отстой", -0.9);
        addPhrase("полное разочарование", -0.85);
        addPhrase("никому не советую", -0.8);

        // Нейтральные модификаторы (для будущего улучшения)
        addWord("очень", 0.0);
        addWord("весьма", 0.0);
        addWord("довольно", 0.0);
        addWord("немного", 0.0);
        addWord("слегка", 0.0);

        // Отрицания (для будущей обработки инверсии)
        addWord("не", 0.0);
        addWord("нет", 0.0);
        addWord("никак", 0.0);
        addWord("никогда", 0.0);
        addWord("ничего", 0.0);
    }

    /**
     * Создаёт расширенный словарь с дополнительными словами.
     */
    public static RussianSentimentDictionary extended() {
        RussianSentimentDictionary dict = new RussianSentimentDictionary();

        // Дополнительные позитивные слова
        dict.addWord("топ", 0.7);
        dict.addWord("огонь", 0.75);
        dict.addWord("бомба", 0.7);
        dict.addWord("вау", 0.7);
        dict.addWord("ого", 0.5);
        dict.addWord("класс", 0.65);
        dict.addWord("прелесть", 0.7);
        dict.addWord("умница", 0.6);
        dict.addWord("молодец", 0.6);
        dict.addWord("браво", 0.7);
        dict.addWord("шедевр", 0.85);
        dict.addWord("гениально", 0.9);
        dict.addWord("божественно", 0.9);

        // Дополнительные негативные слова
        dict.addWord("фуфло", -0.7);
        dict.addWord("барахло", -0.65);
        dict.addWord("дно", -0.75);
        dict.addWord("зашквар", -0.7);
        dict.addWord("кринж", -0.6);
        dict.addWord("трэш", -0.6);
        dict.addWord("хрень", -0.65);
        dict.addWord("фейк", -0.6);
        dict.addWord("развод", -0.7);
        dict.addWord("лохотрон", -0.8);

        return dict;
    }
}
