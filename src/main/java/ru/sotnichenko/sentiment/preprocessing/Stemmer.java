package ru.sotnichenko.sentiment.preprocessing;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Стеммер для русского и английского языков.
 * Приводит слова к их основе (стему).
 *
 * Реализует упрощённый алгоритм Портера для обоих языков.
 */
public class Stemmer {

    private final Language language;
    private final Map<String, String> cache;
    private final int cacheSize;

    public enum Language {
        RUSSIAN, ENGLISH, AUTO
    }

    // Русские окончания для удаления (от длинных к коротким)
    private static final String[] RUSSIAN_PERFECTIVE_GERUND_1 = {"вшись", "вши", "в"};
    private static final String[] RUSSIAN_PERFECTIVE_GERUND_2 = {"ившись", "ывшись", "ивши", "ывши", "ив", "ыв"};
    private static final String[] RUSSIAN_ADJECTIVE = {
            "ими", "ыми", "его", "ого", "ему", "ому", "ее", "ие", "ые", "ое",
            "ей", "ий", "ый", "ой", "ем", "им", "ым", "ом", "их", "ых", "ую",
            "юю", "ая", "яя", "ою", "ею"
    };
    private static final String[] RUSSIAN_PARTICIPLE_1 = {"ем", "нн", "вш", "ющ", "щ"};
    private static final String[] RUSSIAN_PARTICIPLE_2 = {"ивш", "ывш", "ующ"};
    private static final String[] RUSSIAN_REFLEXIVE = {"ся", "сь"};
    private static final String[] RUSSIAN_VERB_1 = {
            "ейте", "уйте", "ете", "йте", "ешь", "нно", "ей", "уй", "ет", "ют",
            "ны", "ть", "ешь", "ит", "им", "ят", "ем", "ишь", "ят"
    };
    private static final String[] RUSSIAN_VERB_2 = {
            "ейте", "уйте", "ила", "ыла", "ена", "ите", "или", "ыли", "ило",
            "ыло", "ено", "ят", "ует", "уют", "ит", "ыт", "ены", "ить", "ыть",
            "ишь", "ую", "ю", "ла", "на", "ли", "ем", "ло", "но", "ет", "ют",
            "ны", "ть", "й", "л", "н"
    };
    private static final String[] RUSSIAN_NOUN = {
            "иями", "ями", "ами", "ией", "ией", "ьями", "ями", "иям", "ием",
            "иях", "ях", "ов", "ев", "ей", "ий", "ья", "ье", "ьи", "ью", "ия",
            "ие", "ии", "ей", "ой", "ий", "ам", "ем", "ом", "ах", "их", "ью",
            "ия", "ья", "ью", "ов", "ев", "ом", "ем", "ах", "ях", "ам", "ям",
            "ой", "ей", "а", "е", "и", "о", "у", "ы", "ь", "й", "ю", "я"
    };
    private static final String[] RUSSIAN_SUPERLATIVE = {"ейш", "ейше"};
    private static final String[] RUSSIAN_DERIVATIONAL = {"ость", "ост"};

    // Английские суффиксы
    private static final String[] ENGLISH_STEP1A = {"sses", "ies", "ss", "s"};
    private static final String[] ENGLISH_STEP1B = {"eed", "ed", "ing"};
    private static final String[] ENGLISH_STEP2 = {
            "ational", "tional", "enci", "anci", "izer", "abli", "alli", "entli",
            "eli", "ousli", "ization", "ation", "ator", "alism", "iveness",
            "fulness", "ousness", "aliti", "iviti", "biliti", "logi"
    };
    private static final String[] ENGLISH_STEP3 = {
            "icate", "ative", "alize", "iciti", "ical", "ful", "ness"
    };
    private static final String[] ENGLISH_STEP4 = {
            "al", "ance", "ence", "er", "ic", "able", "ible", "ant", "ement",
            "ment", "ent", "ion", "ou", "ism", "ate", "iti", "ous", "ive", "ize"
    };

    public Stemmer() {
        this(Language.AUTO, 10000);
    }

    public Stemmer(Language language) {
        this(language, 10000);
    }

    public Stemmer(Language language, int cacheSize) {
        this.language = language;
        this.cacheSize = cacheSize;
        this.cache = new HashMap<>();
    }

    /**
     * Выполняет стемминг слова.
     */
    public String stem(String word) {
        if (word == null || word.length() < 2) {
            return word;
        }

        String lowerWord = word.toLowerCase();

        // Проверяем кэш
        String cached = cache.get(lowerWord);
        if (cached != null) {
            return cached;
        }

        // Определяем язык
        Language lang = (language == Language.AUTO) ? detectLanguage(lowerWord) : language;

        // Выполняем стемминг
        String stem = (lang == Language.RUSSIAN) ? stemRussian(lowerWord) : stemEnglish(lowerWord);

        // Кэшируем результат
        if (cache.size() < cacheSize) {
            cache.put(lowerWord, stem);
        }

        return stem;
    }

    /**
     * Выполняет стемминг списка токенов.
     */
    public List<String> stem(List<String> tokens) {
        return tokens.stream()
                .map(this::stem)
                .collect(Collectors.toList());
    }

    /**
     * Определяет язык слова.
     */
    private Language detectLanguage(String word) {
        for (char c : word.toCharArray()) {
            if (c >= 'а' && c <= 'я' || c == 'ё') {
                return Language.RUSSIAN;
            }
        }
        return Language.ENGLISH;
    }

    /**
     * Русский стемминг (упрощённый алгоритм Портера).
     */
    private String stemRussian(String word) {
        if (word.length() < 3) {
            return word;
        }

        // Находим RV (часть слова после первой гласной)
        int rv = findRV(word);
        if (rv >= word.length()) {
            return word;
        }

        String stem = word;

        // Шаг 1: Удаляем окончание
        stem = removeEnding(stem, rv, RUSSIAN_PERFECTIVE_GERUND_1);
        if (stem.equals(word)) {
            stem = removeEnding(stem, rv, RUSSIAN_PERFECTIVE_GERUND_2);
        }

        if (stem.equals(word)) {
            // Удаляем возвратную частицу
            String temp = removeEnding(stem, rv, RUSSIAN_REFLEXIVE);

            // Пробуем удалить прилагательное
            String adjRemoved = removeEnding(temp, rv, RUSSIAN_ADJECTIVE);
            if (!adjRemoved.equals(temp)) {
                stem = adjRemoved;
                // Пробуем причастие
                stem = removeEnding(stem, rv, RUSSIAN_PARTICIPLE_1);
                if (stem.equals(adjRemoved)) {
                    stem = removeEnding(stem, rv, RUSSIAN_PARTICIPLE_2);
                }
            } else {
                // Пробуем глагол
                stem = removeEnding(temp, rv, RUSSIAN_VERB_1);
                if (stem.equals(temp)) {
                    stem = removeEnding(temp, rv, RUSSIAN_VERB_2);
                }
                if (stem.equals(temp)) {
                    // Пробуем существительное
                    stem = removeEnding(temp, rv, RUSSIAN_NOUN);
                }
            }
        }

        // Шаг 2: Удаляем "и"
        if (stem.endsWith("и") && stem.length() > rv + 1) {
            stem = stem.substring(0, stem.length() - 1);
        }

        // Шаг 3: Удаляем деривационные суффиксы
        stem = removeEnding(stem, rv, RUSSIAN_DERIVATIONAL);

        // Шаг 4: Удаляем суперлатив
        stem = removeEnding(stem, rv, RUSSIAN_SUPERLATIVE);

        // Удаляем "нн" -> "н"
        if (stem.endsWith("нн")) {
            stem = stem.substring(0, stem.length() - 1);
        }

        // Удаляем мягкий знак
        if (stem.endsWith("ь")) {
            stem = stem.substring(0, stem.length() - 1);
        }

        return stem.length() >= 2 ? stem : word;
    }

    /**
     * Английский стемминг (упрощённый алгоритм Портера).
     */
    private String stemEnglish(String word) {
        if (word.length() < 3) {
            return word;
        }

        String stem = word;

        // Шаг 1a
        if (stem.endsWith("sses")) {
            stem = stem.substring(0, stem.length() - 2);
        } else if (stem.endsWith("ies")) {
            stem = stem.substring(0, stem.length() - 2);
        } else if (stem.endsWith("s") && !stem.endsWith("ss")) {
            stem = stem.substring(0, stem.length() - 1);
        }

        // Шаг 1b
        if (stem.endsWith("eed")) {
            if (measureEnglish(stem.substring(0, stem.length() - 3)) > 0) {
                stem = stem.substring(0, stem.length() - 1);
            }
        } else if (stem.endsWith("ed") && containsVowelEnglish(stem.substring(0, stem.length() - 2))) {
            stem = stem.substring(0, stem.length() - 2);
            stem = step1bCleanup(stem);
        } else if (stem.endsWith("ing") && containsVowelEnglish(stem.substring(0, stem.length() - 3))) {
            stem = stem.substring(0, stem.length() - 3);
            stem = step1bCleanup(stem);
        }

        // Шаг 1c: заменяем y на i
        if (stem.endsWith("y") && containsVowelEnglish(stem.substring(0, stem.length() - 1))) {
            stem = stem.substring(0, stem.length() - 1) + "i";
        }

        // Шаг 2
        stem = applyStep2English(stem);

        // Шаг 3
        stem = applyStep3English(stem);

        // Шаг 4
        stem = applyStep4English(stem);

        // Шаг 5a
        if (stem.endsWith("e")) {
            int m = measureEnglish(stem.substring(0, stem.length() - 1));
            if (m > 1 || (m == 1 && !endsWithCVC(stem.substring(0, stem.length() - 1)))) {
                stem = stem.substring(0, stem.length() - 1);
            }
        }

        // Шаг 5b
        if (measureEnglish(stem) > 1 && stem.endsWith("ll")) {
            stem = stem.substring(0, stem.length() - 1);
        }

        return stem.length() >= 2 ? stem : word;
    }

    private String step1bCleanup(String stem) {
        if (stem.endsWith("at") || stem.endsWith("bl") || stem.endsWith("iz")) {
            return stem + "e";
        }
        if (stem.length() >= 2) {
            char last = stem.charAt(stem.length() - 1);
            char secondLast = stem.charAt(stem.length() - 2);
            if (last == secondLast && "lsz".indexOf(last) < 0) {
                return stem.substring(0, stem.length() - 1);
            }
            if (measureEnglish(stem) == 1 && endsWithCVC(stem)) {
                return stem + "e";
            }
        }
        return stem;
    }

    private String applyStep2English(String stem) {
        Map<String, String> replacements = new HashMap<>();
        replacements.put("ational", "ate");
        replacements.put("tional", "tion");
        replacements.put("enci", "ence");
        replacements.put("anci", "ance");
        replacements.put("izer", "ize");
        replacements.put("abli", "able");
        replacements.put("alli", "al");
        replacements.put("entli", "ent");
        replacements.put("eli", "e");
        replacements.put("ousli", "ous");
        replacements.put("ization", "ize");
        replacements.put("ation", "ate");
        replacements.put("ator", "ate");
        replacements.put("alism", "al");
        replacements.put("iveness", "ive");
        replacements.put("fulness", "ful");
        replacements.put("ousness", "ous");
        replacements.put("aliti", "al");
        replacements.put("iviti", "ive");
        replacements.put("biliti", "ble");

        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            if (stem.endsWith(entry.getKey())) {
                String base = stem.substring(0, stem.length() - entry.getKey().length());
                if (measureEnglish(base) > 0) {
                    return base + entry.getValue();
                }
            }
        }
        return stem;
    }

    private String applyStep3English(String stem) {
        Map<String, String> replacements = new HashMap<>();
        replacements.put("icate", "ic");
        replacements.put("ative", "");
        replacements.put("alize", "al");
        replacements.put("iciti", "ic");
        replacements.put("ical", "ic");
        replacements.put("ful", "");
        replacements.put("ness", "");

        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            if (stem.endsWith(entry.getKey())) {
                String base = stem.substring(0, stem.length() - entry.getKey().length());
                if (measureEnglish(base) > 0) {
                    return base + entry.getValue();
                }
            }
        }
        return stem;
    }

    private String applyStep4English(String stem) {
        for (String suffix : ENGLISH_STEP4) {
            if (stem.endsWith(suffix)) {
                String base = stem.substring(0, stem.length() - suffix.length());
                if (measureEnglish(base) > 1) {
                    if (suffix.equals("ion")) {
                        if (base.endsWith("s") || base.endsWith("t")) {
                            return base;
                        }
                    } else {
                        return base;
                    }
                }
            }
        }
        return stem;
    }

    private int findRV(String word) {
        String vowels = "аеёиоуыэюя";
        for (int i = 0; i < word.length(); i++) {
            if (vowels.indexOf(word.charAt(i)) >= 0) {
                return i + 1;
            }
        }
        return word.length();
    }

    private String removeEnding(String word, int rv, String[] endings) {
        for (String ending : endings) {
            if (word.endsWith(ending) && word.length() - ending.length() >= rv) {
                return word.substring(0, word.length() - ending.length());
            }
        }
        return word;
    }

    private boolean containsVowelEnglish(String str) {
        return str.matches(".*[aeiou].*");
    }

    private int measureEnglish(String str) {
        str = str.replaceAll("^[^aeiou]+", "");
        return str.split("[aeiou]+").length - 1;
    }

    private boolean endsWithCVC(String str) {
        if (str.length() < 3) return false;
        char c1 = str.charAt(str.length() - 1);
        char c2 = str.charAt(str.length() - 2);
        char c3 = str.charAt(str.length() - 3);
        return "aeiou".indexOf(c1) < 0 && "aeiou".indexOf(c2) >= 0 &&
               "aeiou".indexOf(c3) < 0 && "wxy".indexOf(c1) < 0;
    }

    /**
     * Очищает кэш.
     */
    public void clearCache() {
        cache.clear();
    }

    /**
     * Возвращает размер кэша.
     */
    public int getCacheSize() {
        return cache.size();
    }
}
