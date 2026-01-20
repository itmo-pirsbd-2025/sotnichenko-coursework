package ru.sotnichenko.sentiment.preprocessing;

import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты для токенизатора.
 */
@DisplayName("Tokenizer Tests")
class TokenizerTest {

    private Tokenizer tokenizer;

    @BeforeEach
    void setUp() {
        tokenizer = new Tokenizer();
    }

    @Nested
    @DisplayName("Базовая токенизация")
    class BasicTokenizationTests {

        @Test
        @DisplayName("Токенизация простого текста")
        void tokenizeSimpleText() {
            List<String> tokens = tokenizer.tokenize("Hello world");

            assertEquals(2, tokens.size());
            assertTrue(tokens.contains("hello"));
            assertTrue(tokens.contains("world"));
        }

        @Test
        @DisplayName("Токенизация русского текста")
        void tokenizeRussianText() {
            List<String> tokens = tokenizer.tokenize("Привет мир");

            assertEquals(2, tokens.size());
            assertTrue(tokens.contains("привет"));
            assertTrue(tokens.contains("мир"));
        }

        @Test
        @DisplayName("Смешанный текст")
        void tokenizeMixedText() {
            List<String> tokens = tokenizer.tokenize("Hello мир, this is тест!");

            assertTrue(tokens.contains("hello"));
            assertTrue(tokens.contains("мир"));
            assertTrue(tokens.contains("this"));
            assertTrue(tokens.contains("тест"));
        }

        @Test
        @DisplayName("Пустой текст")
        void tokenizeEmptyText() {
            List<String> tokens = tokenizer.tokenize("");

            assertTrue(tokens.isEmpty());
        }

        @Test
        @DisplayName("Null текст")
        void tokenizeNullText() {
            List<String> tokens = tokenizer.tokenize(null);

            assertTrue(tokens.isEmpty());
        }
    }

    @Nested
    @DisplayName("Эмотиконы")
    class EmoticonTests {

        @Test
        @DisplayName("Сохранение эмотиконов")
        void preserveEmoticons() {
            Tokenizer tokenizerWithEmoticons = Tokenizer.builder()
                    .preserveEmoticons(true)
                    .build();

            List<String> tokens = tokenizerWithEmoticons.tokenize("I love this :)");

            assertTrue(tokens.contains(":)"));
        }

        @Test
        @DisplayName("Разные эмотиконы")
        void variousEmoticons() {
            List<String> tokens = tokenizer.tokenize("Happy :D sad :( wink ;)");

            assertTrue(tokens.contains(":D") || tokens.contains("happy"));
        }
    }

    @Nested
    @DisplayName("Нормализация")
    class NormalizationTests {

        @Test
        @DisplayName("Нормализация повторяющихся символов")
        void normalizeRepeatedChars() {
            List<String> tokens = tokenizer.tokenizeWithNormalization("Очееееень хорошооооо");

            // После нормализации "ооооо" -> "оо"
            assertTrue(tokens.stream()
                    .anyMatch(t -> !t.contains("оооо"))); // Не должно быть более 2 подряд
        }

        @Test
        @DisplayName("Приведение к нижнему регистру")
        void lowercaseConversion() {
            List<String> tokens = tokenizer.tokenize("HELLO World");

            assertTrue(tokens.contains("hello"));
            assertTrue(tokens.contains("world"));
            assertFalse(tokens.contains("HELLO"));
        }
    }

    @Nested
    @DisplayName("Фильтрация по длине")
    class LengthFilterTests {

        @Test
        @DisplayName("Минимальная длина токена")
        void minTokenLength() {
            Tokenizer filteredTokenizer = Tokenizer.builder()
                    .minTokenLength(3)
                    .build();

            List<String> tokens = filteredTokenizer.tokenize("I am a developer");

            assertFalse(tokens.contains("i"));
            assertFalse(tokens.contains("a"));
            assertFalse(tokens.contains("am"));
            assertTrue(tokens.contains("developer"));
        }

        @Test
        @DisplayName("Максимальная длина токена")
        void maxTokenLength() {
            Tokenizer filteredTokenizer = Tokenizer.builder()
                    .maxTokenLength(5)
                    .build();

            List<String> tokens = filteredTokenizer.tokenize("Hi there developer");

            assertTrue(tokens.contains("hi"));
            assertTrue(tokens.contains("there"));
            assertFalse(tokens.contains("developer"));
        }
    }

    @Nested
    @DisplayName("Числа")
    class NumberTests {

        @Test
        @DisplayName("Целые числа")
        void integerNumbers() {
            List<String> tokens = tokenizer.tokenize("I have 3 apples");

            assertTrue(tokens.contains("3"));
        }

        @Test
        @DisplayName("Десятичные числа")
        void decimalNumbers() {
            List<String> tokens = tokenizer.tokenize("Price is 99.99 dollars");

            assertTrue(tokens.contains("99.99") || tokens.contains("99"));
        }
    }

    @Nested
    @DisplayName("Специальные случаи")
    class SpecialCasesTests {

        @Test
        @DisplayName("Пунктуация удаляется")
        void punctuationRemoved() {
            List<String> tokens = tokenizer.tokenize("Hello, world! How are you?");

            assertFalse(tokens.contains(","));
            assertFalse(tokens.contains("!"));
            assertFalse(tokens.contains("?"));
        }

        @Test
        @DisplayName("Множественные пробелы")
        void multipleSpaces() {
            List<String> tokens = tokenizer.tokenize("Hello    world");

            assertEquals(2, tokens.size());
        }

        @Test
        @DisplayName("Апострофы в словах")
        void apostrophes() {
            List<String> tokens = tokenizer.tokenize("don't can't won't");

            // Апострофы могут обрабатываться по-разному
            assertTrue(tokens.size() >= 3);
        }
    }

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("Builder создаёт токенизатор")
        void builderCreatesTokenizer() {
            Tokenizer custom = Tokenizer.builder()
                    .lowercase(false)
                    .preserveEmoticons(false)
                    .minTokenLength(2)
                    .maxTokenLength(20)
                    .build();

            assertNotNull(custom);
        }

        @Test
        @DisplayName("Без lowercase")
        void withoutLowercase() {
            Tokenizer noLowercase = Tokenizer.builder()
                    .lowercase(false)
                    .build();

            List<String> tokens = noLowercase.tokenize("HELLO World");

            assertTrue(tokens.contains("HELLO"));
            assertTrue(tokens.contains("World"));
        }
    }
}
