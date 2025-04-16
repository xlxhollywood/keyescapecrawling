package org.example.keyescape;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.UpdateOptions;
import org.bson.Document;
import org.example.config.MongoConfig;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.WebDriver;


import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.*;

public class KeyEscapeCrawling {
    private final MongoCollection<Document> reservationCollection;

    private static class ThemeMapping {
        int id;
        String brand;
        String location;
        String branch;
        String title;
        String url;

        ThemeMapping(int id, String brand, String location, String branch, String title, String url) {
            this.id = id;
            this.brand = brand;
            this.location = location;
            this.branch = branch;
            this.title = title;
            this.url = url;
        }
    }

    private static final List<ThemeMapping> THEME_MAPPINGS = Arrays.asList(
            // 스테이션점
            new ThemeMapping(223, "키이스케이프", "강남", "스테이션점", "NOSTALGIA VISTA", "https://www.keyescape.com/reservation1.php?zizum_num=22&theme_num=67&theme_info_num=45"),
            new ThemeMapping(218, "키이스케이프", "강남", "스테이션점", "내 방", "https://www.keyescape.com/reservation1.php?zizum_num=22&theme_num=66&theme_info_num=44"),
            new ThemeMapping(212, "키이스케이프", "강남", "스테이션점", "머니머니부동산", "https://www.keyescape.com/reservation1.php?zizum_num=22&theme_num=65&theme_info_num=43"),

            // 로그인1
            new ThemeMapping(216, "키이스케이프", "강남", "LOG_IN 1", "FOR FREE", "https://www.keyescape.com/reservation1.php?zizum_num=19&theme_num=63&theme_info_num=41"),
            new ThemeMapping(226, "키이스케이프", "강남", "LOG_IN 1", "머니머니패키지", "https://www.keyescape.com/reservation1.php?zizum_num=19&theme_num=60&theme_info_num=38"),

            // 로그인2
            new ThemeMapping(222, "키이스케이프", "강남", "LOG_IN 2", "A GENTLE MONDAY", "https://www.keyescape.com/reservation1.php?zizum_num=20&theme_num=64&theme_info_num=42"),
            new ThemeMapping(217, "키이스케이프", "강남", "LOG_IN 2", "BACK TO THE SCENE+", "https://www.keyescape.com/reservation1.php?zizum_num=20&theme_num=61&theme_info_num=40"),

            // 메모리컴퍼니
            new ThemeMapping(213, "키이스케이프", "강남", "메모리컴퍼니", "FILM BY BOB", "https://www.keyescape.com/reservation1.php?zizum_num=18&theme_num=59&theme_info_num=36"),
            new ThemeMapping(214, "키이스케이프", "강남", "메모리컴퍼니", "FILM BY STEVE", "https://www.keyescape.com/reservation1.php?zizum_num=18&theme_num=58&theme_info_num=35"),
            new ThemeMapping(215, "키이스케이프", "강남", "메모리컴퍼니", "FILM BY EDDY", "https://www.keyescape.com/reservation1.php?zizum_num=18&theme_num=57&theme_info_num=34"),

            // 우주라이크점
            new ThemeMapping(219, "키이스케이프", "강남", "우주라이크점", "WANNA GO HOME", "https://www.keyescape.com/reservation1.php?zizum_num=16&theme_num=56&theme_info_num=33"),
            new ThemeMapping(221, "키이스케이프", "강남", "우주라이크점", "US", "https://www.keyescape.com/reservation1.php?zizum_num=16&theme_num=55&theme_info_num=31"),

            // 더오름점
            new ThemeMapping(224, "키이스케이프", "강남", "더오름점", "엔제리오", "https://www.keyescape.com/reservation1.php?zizum_num=14&theme_num=51&theme_info_num=27"),
            new ThemeMapping(220, "키이스케이프", "강남", "더오름점", "네드", "https://www.keyescape.com/reservation1.php?zizum_num=14&theme_num=48&theme_info_num=26"),

            // 강남점
            new ThemeMapping(228, "키이스케이프", "강남", "강남점", "살랑살랑연구소", "https://www.keyescape.com/reservation1.php?zizum_num=3&theme_num=6&theme_info_num=6"),
            new ThemeMapping(225, "키이스케이프", "강남", "강남점", "그카지말라캤자나", "https://www.keyescape.com/reservation1.php?zizum_num=3&theme_num=7&theme_info_num=7"),
            new ThemeMapping(227, "키이스케이프", "강남", "강남점", "월야애담", "https://www.keyescape.com/reservation1.php?zizum_num=3&theme_num=5&theme_info_num=5"),

            // 홍대
            new ThemeMapping(230, "키이스케이프", "홍대", "홍대점", "홀리데이", "https://www.keyescape.com/reservation1.php?zizum_num=10&theme_num=45&theme_info_num=22"),
            new ThemeMapping(231, "키이스케이프", "홍대", "홍대점", "고백", "https://www.keyescape.com/reservation1.php?zizum_num=10&theme_num=43&theme_info_num=23"),
            new ThemeMapping(229, "키이스케이프", "홍대", "홍대점", "삐릿-뽀", "https://www.keyescape.com/reservation1.php?zizum_num=10&theme_num=41&theme_info_num=21"),

            // 부산
            new ThemeMapping(235, "키이스케이프", "부산", "부산점", "셜록 죽음의 전화", "https://www.keyescape.com/reservation1.php?zizum_num=9&theme_num=39&theme_info_num=11"),
            new ThemeMapping(233, "키이스케이프", "부산", "부산점", "파파라치", "https://www.keyescape.com/reservation1.php?zizum_num=9&theme_num=38&theme_info_num=18"),
            new ThemeMapping(232, "키이스케이프", "부산", "부산점", "정신병동", "https://www.keyescape.com/reservation1.php?zizum_num=9&theme_num=37&theme_info_num=16"),
            new ThemeMapping(236, "키이스케이프", "부산", "부산점", "신비의숲 고대마법의 비밀", "https://www.keyescape.com/reservation1.php?zizum_num=9&theme_num=36&theme_info_num=9"),
            new ThemeMapping(234, "키이스케이프", "부산", "부산점", "난쟁이의 장난", "https://www.keyescape.com/reservation1.php?zizum_num=9&theme_num=35&theme_info_num=10"),

            // 전주
            new ThemeMapping(240, "키이스케이프", "전주", "전주점", "산장: 사라진 목격자", "https://www.keyescape.com/reservation1.php?zizum_num=7&theme_num=33&theme_info_num=8"),
            new ThemeMapping(237, "키이스케이프", "전주", "전주점", "난쟁이의 장난", "https://www.keyescape.com/reservation1.php?zizum_num=7&theme_num=32&theme_info_num=10"),
            new ThemeMapping(238, "키이스케이프", "전주", "전주점", "혜화잡화점", "https://www.keyescape.com/reservation1.php?zizum_num=7&theme_num=31&theme_info_num=17"),
            new ThemeMapping(241, "키이스케이프", "전주", "전주점", "살랑살랑연구소", "https://www.keyescape.com/reservation1.php?zizum_num=7&theme_num=30&theme_info_num=6"),
            new ThemeMapping(239, "키이스케이프", "전주", "전주점", "월야애담", "https://www.keyescape.com/reservation1.php?zizum_num=7&theme_num=29&theme_info_num=5")
    );


    public KeyEscapeCrawling() {
        MongoClient mongoClient = MongoConfig.getMongoClient();
        MongoDatabase database = mongoClient.getDatabase("scrd");
        this.reservationCollection = database.getCollection("reservation");
    }

    private void saveToDatabase(ThemeMapping mapping, String date, List<String> availableTimes, boolean isFirstDate) {
        try {
            Document filter = new Document("title", mapping.title).append("date", date);
            Document docToSave = new Document("brand", mapping.brand)
                    .append("location", mapping.location)
                    .append("branch", mapping.branch)
                    .append("title", mapping.title)
                    .append("id", mapping.id)
                    .append("date", date)
                    .append("availableTimes", availableTimes)
                    .append("updatedAt", new Date())
                    .append("expireAt", new Date(System.currentTimeMillis() + 24L * 60 * 60 * 1000));

            reservationCollection.updateOne(filter, new Document("$set", docToSave), new UpdateOptions().upsert(true));

            if (isFirstDate) {
                System.out.println("\n📍 " + mapping.branch + " (" + date + ")");
            }
            System.out.println(" - " + mapping.title + " : " + (availableTimes.isEmpty() ? "없음" : availableTimes));
        } catch (Exception e) {
            System.err.println("DB 저장 오류: " + e.getMessage());
        }
    }

    public void crawlReservations(int days) {

        // Chromedriver 경로 설정 (Docker에서 chromedriver가 /usr/local/bin에 있음)
        System.setProperty("webdriver.chrome.driver", "/usr/local/bin/chromedriver");

        // Chrome 옵션 설정
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--remote-allow-origins=*");
        options.addArguments("--disable-background-networking"); // 추가
        options.addArguments("--user-data-dir=/dev/shm/chrome-user-data");

        WebDriver driver = new ChromeDriver(options);



        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        try {
            for (ThemeMapping mapping : THEME_MAPPINGS) {
                driver.get(mapping.url);

                for (int i = 0; i < days; i++) {
                    Calendar calendar = Calendar.getInstance();
                    calendar.add(Calendar.DATE, i);
                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
                    String targetDate = dateFormat.format(calendar.getTime());

                    String dateSelector = "td.selDate.available[data-date='" + targetDate + "']";
                    boolean isFirstDate = true;
                    try {
                        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(dateSelector)));
                        WebElement dateElement = driver.findElement(By.cssSelector(dateSelector));
                        dateElement.click();

                        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".timepicker-ui")));

                        List<WebElement> timeElements = driver.findElements(By.cssSelector(".timepicker-ui .timeList li label input:not([disabled]) + span"));
                        List<String> availableTimes = new ArrayList<>();

                        for (WebElement timeElement : timeElements) {
                            String timeText = timeElement.getText().trim().replaceAll("\\s*\\(할인\\)", "");
                            availableTimes.add(timeText);
                        }

                        saveToDatabase(mapping, targetDate, availableTimes, isFirstDate);
                        isFirstDate = false;

                        WebElement backButton = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("back_btn")));
                        backButton.click();

                        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".datepicker-ui")));
                    } catch (Exception e) {
                        System.out.println("❌ 날짜 " + targetDate + " 선택 불가 또는 예약 시간 없음.");
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            driver.quit();
        }
    }

    public static void main(String[] args) {
        KeyEscapeCrawling crawler = new KeyEscapeCrawling();
        crawler.crawlReservations(7);
    }
}