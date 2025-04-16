package org.example.playtheworld;

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
import java.util.AbstractMap;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.*;

public class PlayTheWorldCrawling {

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
            // 강남점
            new ThemeMapping(259, "플레이더월드", "강남", "강남점", "먹루마블"),
            new ThemeMapping(256, "플레이더월드", "강남", "강남점", "이웃집 또도와"),
            new ThemeMapping(258, "플레이더월드", "강남", "강남점", "이웃집 또털어"),
            new ThemeMapping(260, "플레이더월드", "강남", "강남점", "두근두근 러브대작전"),
            new ThemeMapping(261, "플레이더월드", "강남", "강남점", "조선피자몰"),
            new ThemeMapping(262, "플레이더월드", "강남", "강남점", "이상한 나라로 출두요"),

            // 건대점
            new ThemeMapping(263, "플레이더월드", "건대", "건대점", "선고"),
            new ThemeMapping(264, "플레이더월드", "건대", "건대점", "뱀파이어 헌터"),
            new ThemeMapping(265, "플레이더월드", "건대", "건대점", "사악한 악마와 달콤한 공장"),
            new ThemeMapping(266, "플레이더월드", "건대", "건대점", "전지적 교수님 시점"),
            new ThemeMapping(267, "플레이더월드", "건대", "건대점", "개수작"),

            // 부평점
            new ThemeMapping(268, "플레이더월드", "인천", "부평점", "구해줘 햄즈"),
            new ThemeMapping(269, "플레이더월드", "인천", "부평점", "별점테러 짜장나요"),
            new ThemeMapping(270, "플레이더월드", "인천", "부평점", "호스피스"),
            new ThemeMapping(271, "플레이더월드", "인천", "부평점", "세이브 더 월드"),
            new ThemeMapping(272, "플레이더월드", "인천", "부평점", "노 웨이 아웃"),

            // 평택점
            new ThemeMapping(273, "플레이더월드", "평택", "평택점", "시간전당포"),
            new ThemeMapping(274, "플레이더월드", "평택", "평택점", "사귀"),
            new ThemeMapping(275, "플레이더월드", "평택", "평택점", "대탈출2"),
            new ThemeMapping(276, "플레이더월드", "평택", "평택점", "MSI 미제사건 전담반"),
            new ThemeMapping(277, "플레이더월드", "평택", "평택점", "집으로"),
            new ThemeMapping(278, "플레이더월드", "평택", "평택점", "쌩얼")
    );


    private static final Map<String, ThemeMapping> THEME_MAP = new HashMap<>();
    static {
        for (ThemeMapping tm : THEME_MAPPINGS) {
            THEME_MAP.put(tm.title, tm);
        }
    }


    private static final Map<String, String> THEME_URLS = Map.ofEntries(
            // 강남점
            new AbstractMap.SimpleEntry<>("먹루마블", "https://m.booking.naver.com/booking/12/bizes/999864/items/5576524?startDateTime="),
            new AbstractMap.SimpleEntry<>("이웃집 또도와", "https://m.booking.naver.com/booking/12/bizes/999864/items/5399654?startDateTime="),
            new AbstractMap.SimpleEntry<>("이웃집 또털어", "https://m.booking.naver.com/booking/12/bizes/999864/items/5399727?area=ple&lang=ko&startDateTime="),
            new AbstractMap.SimpleEntry<>("두근두근 러브대작전", "https://m.booking.naver.com/booking/12/bizes/999864/items/5566404?area=ple&lang=ko&startDateTime="),
            new AbstractMap.SimpleEntry<>("조선피자몰", "https://m.booking.naver.com/booking/12/bizes/999864/items/5399783?startDateTime="),
            new AbstractMap.SimpleEntry<>("이상한 나라로 출두요", "https://m.booking.naver.com/booking/12/bizes/999864/items/5399819?startDateTime="),

            // 건대점
            new AbstractMap.SimpleEntry<>("선고", "https://m.booking.naver.com/booking/12/bizes/1061698/items/5588095?startDateTime="),
            new AbstractMap.SimpleEntry<>("뱀파이어 헌터", "https://m.booking.naver.com/booking/12/bizes/1061698/items/5588104?startDateTime="),
            new AbstractMap.SimpleEntry<>("사악한 악마와 달콤한 공장", "https://m.booking.naver.com/booking/12/bizes/1061698/items/5588103?startDateTime="),
            new AbstractMap.SimpleEntry<>("전지적 교수님 시점", "https://m.booking.naver.com/booking/12/bizes/1061698/items/5588106?startDateTime="),
            new AbstractMap.SimpleEntry<>("개수작", "https://m.booking.naver.com/booking/12/bizes/1061698/items/5588105?startDateTime="),

            // 부평점
            new AbstractMap.SimpleEntry<>("구해줘 햄즈", "https://m.booking.naver.com/booking/12/bizes/1061688/items/5588081?startDateTime="),
            new AbstractMap.SimpleEntry<>("별점테러 짜장나요", "https://m.booking.naver.com/booking/12/bizes/1061688/items/5588080?startDateTime="),
            new AbstractMap.SimpleEntry<>("호스피스", "https://m.booking.naver.com/booking/12/bizes/1061688/items/5588079?startDateTime="),
            new AbstractMap.SimpleEntry<>("세이브 더 월드", "https://m.booking.naver.com/booking/12/bizes/1061688/items/5588078?startDateTime="),
            new AbstractMap.SimpleEntry<>("노 웨이 아웃", "https://m.booking.naver.com/booking/12/bizes/1061688/items/5588066?startDateTime="),

            // 평택점
            new AbstractMap.SimpleEntry<>("시간전당포", "https://m.booking.naver.com/booking/12/bizes/1106066/items/5739972?startDateTime="),
            new AbstractMap.SimpleEntry<>("사귀", "https://m.booking.naver.com/booking/12/bizes/1106066/items/5739965?startDateTime="),
            new AbstractMap.SimpleEntry<>("대탈출2", "https://m.booking.naver.com/booking/12/bizes/1106066/items/5739923?startDateTime="),
            new AbstractMap.SimpleEntry<>("MSI 미제사건 전담반", "https://m.booking.naver.com/booking/12/bizes/1106066/items/5739961?startDateTime="),
            new AbstractMap.SimpleEntry<>("집으로", "https://m.booking.naver.com/booking/12/bizes/1106066/items/5739956?startDateTime="),
            new AbstractMap.SimpleEntry<>("쌩얼", "https://m.booking.naver.com/booking/12/bizes/1106066/items/5739943?startDateTime=")
    );


    public PlayTheWorldCrawling(WebDriver driver) {
        this.driver = driver;
        MongoClient mongoClient = MongoConfig.getMongoClient();
        MongoDatabase database = mongoClient.getDatabase("scrd");
        this.reservationCollection = database.getCollection("reservation");
    }

    public void crawlAllDates() {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            Calendar calendar = Calendar.getInstance();

            // 7일 반복
            for (int i = 0; i < 7; i++) {
                String date = sdf.format(calendar.getTime());

                // 날짜별 첫 테마 직전에 "📍 yyyy-MM-dd"를 한 번만 출력하기 위한 플래그
                boolean isFirstThemeOfDay = true;

                // 테마별 순회
                for (Map.Entry<String, String> entry : THEME_URLS.entrySet()) {
                    String themeName = entry.getKey();
                    String urlWithDate = entry.getValue() + date + "T00%3A00%3A00%2B09%3A00";
                    driver.get(urlWithDate);

                    try {
                        // .calendar_area가 나타날 때까지 최대 10초 대기
                        new WebDriverWait(driver, Duration.ofSeconds(10))
                                .until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".calendar_area")));

                        // 추가 로딩 고려 (1초)
                        Thread.sleep(1000);

                        // 예약 가능한 시간 슬롯
                        List<WebElement> timeSlots = driver.findElements(
                                By.cssSelector("li.time_item button.btn_time:not(.unselectable)")
                        );

                        // 날짜별 첫 테마면 "📍 yyyy-MM-dd" 출력
                        if (isFirstThemeOfDay) {
                            System.out.println("\n📍 " + date);
                            isFirstThemeOfDay = false;
                        }

                        if (timeSlots.isEmpty()) {
                            System.out.println(" - " + themeName + " : 없음");
                            saveToDatabase(themeName, date, Collections.emptyList());
                        } else {
                            // 시간대 목록 추출 (24시간 변환)
                            List<String> availableTimes = parseTimeSlots(timeSlots);

                            if (availableTimes.isEmpty()) {
                                System.out.println(" - " + themeName + " : 없음");
                            } else {
                                System.out.println(" - " + themeName + " : " + availableTimes);
                            }

                            saveToDatabase(themeName, date, availableTimes);
                        }
                    } catch (TimeoutException e) {
                        // 페이지 로드 실패 → 없음 처리
                        if (isFirstThemeOfDay) {
                            System.out.println("\n📍 " + date);
                            isFirstThemeOfDay = false;
                        }
                        System.out.println(" - " + themeName + " : 없음 (페이지 로드 실패)");
                        saveToDatabase(themeName, date, Collections.emptyList());
                    }
                }
                // 다음 날짜
                calendar.add(Calendar.DAY_OF_YEAR, 1);
            }
        } catch (Exception e) {
            System.err.println("네이버 크롤링 중 오류: " + e.getMessage());
        }
    }


    private List<String> parseTimeSlots(List<WebElement> timeSlots) {
        List<String> result = new ArrayList<>();
        for (WebElement timeSlot : timeSlots) {
            String rawText = timeSlot.getText().trim();
            if (!rawText.isEmpty()) {
                // "4매" / "tickets" 등 제거 + 오전/오후/AM/PM 변환
                String converted = convertTo24Hour(rawText);
                result.add(converted);
            }
        }
        return result;
    }


    private String convertTo24Hour(String timeText) {
        // 오전/오후/AM/PM 확인
        boolean isKoreanAm = timeText.contains("오전");
        boolean isKoreanPm = timeText.contains("오후");
        boolean isEnglishAm = timeText.toUpperCase().contains("AM");
        boolean isEnglishPm = timeText.toUpperCase().contains("PM");

        // 한글/영문 표기 제거
        String cleaned = timeText
                .replace("오전", "")
                .replace("오후", "")
                .replaceAll("(?i)AM", "")
                .replaceAll("(?i)PM", "")
                .replaceAll("\\s*\\d+\\s*tickets", "") // 예: "1 tickets"
                .replaceAll("\\s*\\d+매", "")          // 예: "4매"
                .trim();

        // "8:30" 형태 기대
        String[] parts = cleaned.split(":");
        if (parts.length != 2) {
            return cleaned; // 예외 포맷은 그대로
        }

        int hour = Integer.parseInt(parts[0].trim());
        int minute = Integer.parseInt(parts[1].trim());

        // 오전 12시 → 00시
        if ((isKoreanAm || isEnglishAm) && hour == 12) {
            hour = 0;
        }
        // 오후/PM & 시 < 12 → 시 + 12
        else if ((isKoreanPm || isEnglishPm) && hour < 12) {
            hour += 12;
        }

        return String.format("%02d:%02d", hour, minute);
    }


    private void saveToDatabase(String themeName, String date, List<String> availableTimes) {
        try {
            // ThemeMapping 찾기
            ThemeMapping tm = THEME_MAP.get(themeName);

            String brand = (tm != null) ? tm.brand : "기타";
            String location = (tm != null) ? tm.location : "기타";
            String branch = (tm != null) ? tm.branch : "기타";
            String title = (tm != null) ? tm.title : themeName;
            int id = (tm != null) ? tm.id : 0;

            // Upsert 키: (brand, title, date)
            Document filter = new Document("brand", brand)
                    .append("title", title)
                    .append("date", date);

            // 저장 내용
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


        // 크롬드라이버 경로 (Docker: /usr/local/bin/chromedriver)
        System.setProperty("webdriver.chrome.driver", "/usr/local/bin/chromedriver");

        // 크롬 옵션
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless");
        options.addArguments("--lang=ko"); // 한국어 로케일
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--remote-allow-origins=*");
        options.addArguments("--disable-background-networking");
        options.addArguments("--user-data-dir=/dev/shm/chrome-user-data");

        WebDriver driver = null;
        try {
            driver = new ChromeDriver(options);
            PlayTheWorldCrawling crawler = new PlayTheWorldCrawling(driver);
            crawler.crawlAllDates();
        } catch (Exception e) {
            System.err.println("[네이버] 크롤링 오류: " + e.getMessage());
        } finally {
            if (driver != null) driver.quit();
        }
    }
}
