ИНСТРУКЦИЯ ПО СОЗДАНИЮ СКРИНШОТОВ ДЛЯ КУРСОВОЙ РАБОТЫ
=====================================================

НЕОБХОДИМО: 7 скриншотов (сохранить как PNG в эту папку)

====== ПОДГОТОВКА ======

Открыть PowerShell или CMD и выполнить:

1. Собрать лаб. работу №4:
   cd C:\Users\Quonix\Downloads\papka\sotnichenko-lab-4
   mvn clean install -DskipTests

2. Собрать курсовую:
   cd C:\Users\Quonix\Downloads\papka\sotnichenko-coursework
   mvn clean package -DskipTests

====== СКРИНШОТ 1: tests_results.png ======

Команда:
   mvn test

Что снять: Консоль с "BUILD SUCCESS" и "Tests run: 83, Failures: 0"

====== СКРИНШОТ 2: server_running.png ======

Команда:
   java -jar target/sentiment-analysis-server-1.0.0.jar

Что снять: Консоль с сообщением:
   === Sentiment Analysis Server ===
   Server started on port 8080
   Available endpoints:
   ...

НЕ ЗАКРЫВАТЬ! Сервер должен работать для следующих скриншотов.

====== СКРИНШОТЫ 3-6: API (в НОВОМ терминале!) ======

Открыть ВТОРОЙ терминал (сервер продолжает работать в первом).

--- СКРИНШОТ 3: api_analyze.png ---
Команда (Windows CMD):
   curl -X POST http://localhost:8080/api/analyze -H "Content-Type: application/json" -d "{\"text\":\"Отличный продукт, очень доволен!\"}"

Команда (PowerShell):
   Invoke-RestMethod -Uri http://localhost:8080/api/analyze -Method POST -ContentType "application/json" -Body '{"text":"Отличный продукт, очень доволен!"}'

Что снять: JSON ответ с "sentiment": "positive"

--- СКРИНШОТ 4: api_analyze_negative.png ---
Команда (Windows CMD):
   curl -X POST http://localhost:8080/api/analyze -H "Content-Type: application/json" -d "{\"text\":\"Ужасное качество, полное разочарование\"}"

Что снять: JSON ответ с "sentiment": "negative"

--- СКРИНШОТ 5: api_batch.png ---
Команда (Windows CMD):
   curl -X POST http://localhost:8080/api/analyze/batch -H "Content-Type: application/json" -d "[\"Отличный сервис!\", \"Плохое качество\", \"Обычный товар\"]"

Что снять: JSON с массивом results (3 элемента)

--- СКРИНШОТ 6: api_stats.png ---
Команда:
   curl http://localhost:8080/api/stats

Что снять: JSON со статистикой (totalRequests, cache, classifier)

====== СКРИНШОТ 7: load_test_results.png ======

Сервер должен продолжать работать!

Команда:
   java -cp target/sentiment-analysis-server-1.0.0.jar ru.sotnichenko.sentiment.LoadTester --port 8080 --threads 10 --requests 5000

Что снять: Результаты нагрузки:
   - Throughput (req/sec)
   - Avg latency (ms)
   - Success rate (%)

====== ПОСЛЕ СКРИНШОТОВ ======

Вернуться в первый терминал и нажать Enter для остановки сервера.

====== ПРОВЕРКА ======

Убедиться что в папке screenshots есть:
   1. tests_results.png
   2. server_running.png
   3. api_analyze.png
   4. api_analyze_negative.png
   5. api_batch.png
   6. api_stats.png
   7. load_test_results.png

====== ОПЦИОНАЛЬНО ======

8. model_evaluation.png - если делаете обучение на своих данных
