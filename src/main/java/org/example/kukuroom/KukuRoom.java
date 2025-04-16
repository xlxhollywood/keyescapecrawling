package org.example.kukuroom;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.UpdateOptions;
import org.bson.Document;
import org.example.config.MongoConfig;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.*;


public class KukuRoom {

    private final WebDriver driver;
    private final MongoCollection<Document> reservationCollection;

    private static class ThemeMapping {
        int id;
        String brand;
        String location;
        String branch;
        String title;

        ThemeMapping(int id, String brand, String location, String branch, String title) {
            this.id = id;
            this.brand = brand;
            this.location = location;
            this.branch = branch;
            this.title = title;
        }
    }


    private static final List<ThemeMapping> THEME_MAPPINGS = Arrays.asList(
            new ThemeMapping(209, "쿠쿠룸1089", "강남", "강남점", "신비로운 직원생활"),
            new ThemeMapping(210, "쿠쿠룸1089", "강남", "강남점", "실직요정 비상대책위원회"),
            new ThemeMapping(208, "쿠쿠룸1089", "강남", "강남점", "백순대-셀레스트"),
            new ThemeMapping(211, "쿠쿠룸1089", "강남", "강남점", "쿠쿠마스터")
    );

    private static final Map<String, ThemeMapping> THEME_MAP = new HashMap<>();
    static {
        for (ThemeMapping tm : THEME_MAPPINGS) {
            THEME_MAP.put(tm.title, tm);
        }
    }

    private static final Map<String, String> THEME_URLS = Map.of(
            "신비로운 직원생활", "https://m.booking.naver.com/booking/12/bizes/1073255/items/5626256?startDateTime=",
            "실직요정 비상대책위원회", "https://m.booking.naver.com/booking/12/bizes/1073255/items/5641334?startDateTime=",
            "백순대-셀레스트", "https://m.booking.naver.com/booking/12/bizes/1073255/items/5654295?startDateTime=",
            "쿠쿠마스터-2025윈터시즌-", "https://m.booking.naver.com/booking/12/bizes/1073255/items/6480799?startDateTime="
    );

    public KukuRoom(WebDriver driver) {
        this.driver = driver;

        MongoClient mongoClient = MongoConfig.getMongoClient();
        MongoDatabase database = mongoClient.getDatabase("scrd");
        this.reservationCollection = database.getCollection("reservation");
    }


    public void crawlAllDates() {
        try {


            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            Calendar calendar = Calendar.getInstance();

            for (int i = 0; i < 7; i++) {
                String date = sdf.format(calendar.getTime());

                boolean isFirstThemeOfDay = true;

                // 테마별 순회
                for (Map.Entry<String, String> entry : THEME_URLS.entrySet()) {
                    String themeName = entry.getKey();
                    String urlWithDate = entry.getValue() + date + "T00%3A00%3A00%2B09%3A00";

                    driver.get(urlWithDate);

                    try {
                        new WebDriverWait(driver, Duration.ofSeconds(10))
                                .until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".calendar_area")));


                        Thread.sleep(1000);

                        List<WebElement> timeSlots = driver.findElements(
                                By.cssSelector("li.time_item button.btn_time:not(.unselectable)")
                        );

                        if (isFirstThemeOfDay) {
                            System.out.println("\n📍 " + date);
                            isFirstThemeOfDay = false;
                        }

                        if (timeSlots.isEmpty()) {
                            System.out.println(" - " + themeName + " : 없음");
                            saveToDatabase(themeName, date, Collections.emptyList());
                        } else {
                            List<String> availableTimes = parseTimeSlots(timeSlots);
                            if (availableTimes.isEmpty()) {
                                System.out.println(" - " + themeName + " : 없음");
                            } else {
                                System.out.println(" - " + themeName + " : " + availableTimes);
                            }
                            saveToDatabase(themeName, date, availableTimes);
                        }
                    } catch (TimeoutException e) {
                        if (isFirstThemeOfDay) {
                            System.out.println("\n📍 " + date);
                            isFirstThemeOfDay = false;
                        }
                        System.out.println(" - " + themeName + " : 없음 (페이지 로드 실패)");
                        saveToDatabase(themeName, date, Collections.emptyList());
                    }
                }
                calendar.add(Calendar.DAY_OF_YEAR, 1);
            }
        } catch (Exception e) {
            System.err.println("네이버 크롤링 중 오류: " + e.getMessage());
        }
    }


    private List<String> parseTimeSlots(List<WebElement> timeSlots) {
        List<String> availableTimes = new ArrayList<>();
        for (WebElement slot : timeSlots) {
            String timeText = slot.getText()
                    .replaceAll("\\s*\\d+매", "") // '4매' 제거
                    .trim();
            if (!timeText.isEmpty()) {
                availableTimes.add(convertTo24Hour(timeText));
            }
        }
        return availableTimes;
    }

    private String convertTo24Hour(String timeText) {
        // "오전", "오후", 혹은 "AM", "PM" 인지 확인
        boolean isKoreanAm = timeText.contains("오전");
        boolean isKoreanPm = timeText.contains("오후");
        boolean isEnglishAm = timeText.toUpperCase().contains("AM");
        boolean isEnglishPm = timeText.toUpperCase().contains("PM");

        // 먼저 한글/영문 표기 제거
        String cleaned = timeText
                .replace("오전", "")
                .replace("오후", "")
                .replaceAll("(?i)AM", "") // 대소문자 구분 없이 AM 제거
                .replaceAll("(?i)PM", "") // PM 제거
                .replaceAll("\\s*\\d+\\s*tickets", "") // "1 tickets" 제거
                .trim();

        // 예: "8:30" 형태
        String[] parts = cleaned.split(":");
        if (parts.length != 2) {
            return cleaned; // 포맷 예외 시 그냥 반환
        }
        int hour = Integer.parseInt(parts[0].trim());
        int minute = Integer.parseInt(parts[1].trim());

        // 오전 12시 -> 00시
        if ((isKoreanAm || isEnglishAm) && hour == 12) {
            hour = 0;
        }
        // 오후/PM & 시 < 12 -> 시 + 12
        else if ((isKoreanPm || isEnglishPm) && hour < 12) {
            hour += 12;
        }

        return String.format("%02d:%02d", hour, minute);
    }


    private void saveToDatabase(String themeName, String date, List<String> availableTimes) {
        try {
            ThemeMapping tm = THEME_MAP.get(themeName);

            String brand = (tm != null) ? tm.brand : "기타";
            String location = (tm != null) ? tm.location : "기타";
            String branch = (tm != null) ? tm.branch : "기타";
            String title = (tm != null) ? tm.title : themeName;
            int id = (tm != null) ? tm.id : 0;

            Document filter = new Document("brand", brand)
                    .append("title", title)
                    .append("date", date);

            Document docToSave = new Document("brand", brand)
                    .append("location", location)
                    .append("branch", branch)
                    .append("title", title)
                    .append("id", id)
                    .append("date", date)
                    .append("availableTimes", availableTimes)
                    .append("updatedAt", new Date())
                    .append("expireAt", new Date(System.currentTimeMillis() + 24L * 60 * 60 * 1000));

            reservationCollection.updateOne(filter, new Document("$set", docToSave), new UpdateOptions().upsert(true));
        } catch (Exception e) {
            System.err.println("❌ DB 저장 중 오류: " + e.getMessage());
        }
    }

    public static void main(String[] args) {

        // Chromedriver 경로 설정 (Docker에서 chromedriver가 /usr/local/bin에 있음)
        System.setProperty("webdriver.chrome.driver", "/usr/local/bin/chromedriver");

        // Chrome 옵션 설정
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless");
        options.addArguments("--lang=ko"); // 한국어 로케일 지정
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--remote-allow-origins=*");
        options.addArguments("--disable-background-networking");
        options.addArguments("--user-data-dir=/dev/shm/chrome-user-data");


        WebDriver driver = null;
        try {
            driver = new ChromeDriver(options);

            KukuRoom crawler = new KukuRoom(driver);
            crawler.crawlAllDates();
        } catch (Exception e) {
            System.err.println("[네이버] 크롤링 오류: " + e.getMessage());
        } finally {
            if (driver != null) driver.quit();
        }
    }
}
