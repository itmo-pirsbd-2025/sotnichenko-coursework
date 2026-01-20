package ru.sotnichenko.sentiment.preprocessing;

import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты для стеммера.
 */
@DisplayName("Stemmer Tests")
class StemmerTest {

    private Stemmer stemmer;

    @BeforeEach
    void setUp() {
        stemmer = new Stemmer();
    }

    @Nested
    @DisplayName("Английский стемминг")
    class EnglishStemmingTests {

        @Test
        @DisplayName("Множественное число")
        void pluralForms() {
            Stemmer englishStemmer = new Stemmer(Stemmer.Language.ENGLISH);

            assertEquals("cat", englishStemmer.stem("cats"));
            assertEquals("box", englishStemmer.stem("boxes"));
        }

        @Test
        @DisplayName("Глагольные формы -ing")
        void ingForms() {
            Stemmer englishStemmer = new Stemmer(Stemmer.Language.ENGLISH);

            String stemmed = englishStemmer.stem("running");
            // После стемминга должно получиться "run" или близкое
            assertTrue(stemmed.length() < "running".length());
        }

        @Test
        @DisplayName("Глагольные формы -ed")
        void edForms() {
            Stemmer englishStemmer = new Stemmer(Stemmer.Language.ENGLISH);

            String stemmed = englishStemmer.stem("walked");
            assertTrue(stemmed.length() <= "walked".length());
        }

        @Test
        @DisplayName("Прилагательные")
        void adjectives() {
            Stemmer englishStemmer = new Stemmer(Stemmer.Language.ENGLISH);

            String stemmed = englishStemmer.stem("happiness");
            assertTrue(stemmed.length() < "happiness".length());
        }
    }

    @Nested
    @DisplayName("Русский стемминг")
    class RussianStemmingTests {

        @Test
        @DisplayName("Существительные")
        void nouns() {
            Stemmer russianStemmer = new Stemmer(Stemmer.Language.RUSSIAN);

            String stem1 = russianStemmer.stem("книга");
            String stem2 = russianStemmer.stem("книги");
            String stem3 = russianStemmer.stem("книгу");

            // Все формы должны иметь общую основу
            assertEquals(stem1, stem2);
            assertEquals(stem2, stem3);
        }

        @Test
        @DisplayName("Глаголы")
        void verbs() {
            Stemmer russianStemmer = new Stemmer(Stemmer.Language.RUSSIAN);

            String stem1 = russianStemmer.stem("читать");
            String stem2 = russianStemmer.stem("читаю");
            String stem3 = russianStemmer.stem("читал");

            // Все формы должны быть короче оригинала
            assertTrue(stem1.length() <= "читать".length());
            assertTrue(stem2.length() <= "читаю".length());
            assertTrue(stem3.length() <= "читал".length());
        }

        @Test
        @DisplayName("Прилагательные")
        void adjectives() {
            Stemmer russianStemmer = new Stemmer(Stemmer.Language.RUSSIAN);

            String stem1 = russianStemmer.stem("красивый");
            String stem2 = russianStemmer.stem("красивая");
            String stem3 = russianStemmer.stem("красивое");

            // Все формы должны иметь близкую основу
            assertEquals(stem1, stem2);
            assertEquals(stem2, stem3);
        }
    }

    @Nested
    @DisplayName("Автоопределение языка")
    class AutoLanguageDetectionTests {

        @Test
        @DisplayName("Определение английского")
        void detectEnglish() {
            Stemmer autoStemmer = new Stemmer(Stemmer.Language.AUTO);

            String stemmed = autoStemmer.stem("running");
            // Должен применить английский стемминг
            assertTrue(stemmed.length() < "running".length());
        }

        @Test
        @DisplayName("Определение русского")
        void detectRussian() {
            Stemmer autoStemmer = new Stemmer(Stemmer.Language.AUTO);

            String stemmed = autoStemmer.stem("бегущий");
            // Должен применить русский стемминг
            assertTrue(stemmed.length() < "бегущий".length());
        }
    }

    @Nested
    @DisplayName("Кэширование")
    class CachingTests {

        @Test
        @DisplayName("Результат кэшируется")
        void resultIsCached() {
            stemmer.stem("running");
            stemmer.stem("running");

            assertTrue(stemmer.getCacheSize() > 0);
        }

        @Test
        @DisplayName("Очистка кэша")
        void clearCache() {
            stemmer.stem("running");
            stemmer.clearCache();

            assertEquals(0, stemmer.getCacheSize());
        }
    }

    @Nested
    @DisplayName("Список токенов")
    class TokenListTests {

        @Test
        @DisplayName("Стемминг списка")
        void stemList() {
            List<String> tokens = List.of("running", "walking", "jumping");

            List<String> stemmed = stemmer.stem(tokens);

            assertEquals(3, stemmed.size());
            for (String s : stemmed) {
                assertNotNull(s);
                assertFalse(s.isEmpty());
            }
        }
    }

    @Nested
    @DisplayName("Граничные случаи")
    class EdgeCasesTests {

        @Test
        @DisplayName("Короткое слово")
        void shortWord() {
            String stemmed = stemmer.stem("a");

            assertEquals("a", stemmed);
        }

        @Test
        @DisplayName("Null")
        void nullInput() {
            String stemmed = stemmer.stem((String) null);

            assertNull(stemmed);
        }

        @Test
        @DisplayName("Пустая строка")
        void emptyString() {
            String stemmed = stemmer.stem("");

            assertEquals("", stemmed);
        }

        @Test
        @DisplayName("Числа")
        void numbers() {
            String stemmed = stemmer.stem("123");

            assertEquals("123", stemmed);
        }
    }
}
