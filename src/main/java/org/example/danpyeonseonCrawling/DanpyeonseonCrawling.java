package org.example.danpyeonseonCrawling;

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

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class DanpyeonseonCrawling {

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

    // 원하는 컨벤션대로 ID, 브랜드명, 테마명, URL 등을 배치
    private static final List<ThemeMapping> THEME_MAPPINGS = Arrays.asList(
            // 강남점
            new ThemeMapping(8,"단편선","강남","강남점","상자","https://www.dpsnnn.com/reserve_g"),
            new ThemeMapping(9,"단편선","강남","강남점","행복","https://www.dpsnnn.com/reserve_g"),
            // 성수점
            new ThemeMapping(10,"단편선","성수","성수점","자격","https://dpsnnn-s.imweb.me/reserve_ss"),
            new ThemeMapping(11,"단편선","성수","성수점","문장","https://dpsnnn-s.imweb.me/reserve_ss"),
            new ThemeMapping(12,"단편선","성수","성수점","별","https://dpsnnn-s.imweb.me/reserve_ss"),
            new ThemeMapping(13,"단편선","성수","성수점","쥐","https://dpsnnn-s.imweb.me/reserve_ss")

    );

    private final WebDriver driver;
    private final MongoCollection<Document> reservationCollection;

    // 날짜 -> (branch -> (theme -> times))
    private final Map<String, Map<String, Map<String, List<String>>>> finalMap;

    public DanpyeonseonCrawling(WebDriver driver) {
        this.driver = driver;
        MongoClient mongoClient = MongoConfig.getMongoClient();
        MongoDatabase database = mongoClient.getDatabase("scrd");
        this.reservationCollection = database.getCollection("reservation");
        this.finalMap = new LinkedHashMap<>();
    }

    public void crawlAllDates(String referenceDate) {
        // URL별로 ThemeMapping을 묶어서 한 번만 페이지 로드
        Map<String,List<ThemeMapping>> urlMap = new LinkedHashMap<>();
        for(ThemeMapping tm : THEME_MAPPINGS) {
            urlMap.putIfAbsent(tm.url, new ArrayList<>());
            urlMap.get(tm.url).add(tm);
        }
        // 각 URL(=지점 페이지)에 접속
        for(String url : urlMap.keySet()) {
            List<ThemeMapping> list = urlMap.get(url);
            String branchName = list.get(0).branch;

            try {
                driver.get(url);
                new WebDriverWait(driver, Duration.ofSeconds(10))
                        .until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".booking_view_container")));
                Thread.sleep(2000);

                // 페이지에서 달력 정보를 수집
                parseCalendar(list);
            } catch(Exception e) {
                System.err.println("["+branchName+"] 크롤링 오류: "+e.getMessage());
                // 만약 전체 페이지 로딩 자체가 실패한 경우, branchName에 해당하는 모든 테마를 '없음' 처리
                for (ThemeMapping tm : list) {
                    saveToFinalMap(tm, referenceDate, Collections.emptyList());
                }
            }
        }

        // 크롤링 후 최종 결과 출력
        printFinalResults();
    }

    /**
     * 예약 달력(td.booking_day)을 파싱하여, theme별로 예약 가능한 times를 찾는다.
     */
    private void parseCalendar(List<ThemeMapping> themeMappings) {
        try {
            // 오늘 날짜 구하기 (yyyy-MM-dd 형식)
            String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

            List<WebElement> dateCells = driver.findElements(By.cssSelector("td.booking_day"));
            for (WebElement dateCell : dateCells) {
                String dataDateAttr = dateCell.getAttribute("data-date");
                if (dataDateAttr == null || dataDateAttr.isEmpty()) continue;

                // ⏩ 오늘 이전 날짜는 무시
                if (dataDateAttr.compareTo(today) < 0) {
                    continue;
                }

                // 예약 가능 리스트
                List<WebElement> bookingLists = dateCell.findElements(By.cssSelector("div.booking_list"));
                if (bookingLists.isEmpty()) {
                    for (ThemeMapping tm : themeMappings) {
                        saveToDB(tm, dataDateAttr, Collections.emptyList());
                        saveToFinalMap(tm, dataDateAttr, Collections.emptyList());
                    }
                    continue;
                }

                Map<String, List<String>> themeTimesMap = new LinkedHashMap<>();

                for (WebElement bookingItem : bookingLists) {
                    String classAttr = bookingItem.getAttribute("class");
                    boolean isClosed = classAttr.contains("closed") || classAttr.contains("disable");
                    if (isClosed) continue;

                    WebElement aTag = bookingItem.findElement(By.tagName("a"));
                    String rawText = aTag.getText().trim();
                    if (rawText.isEmpty() || rawText.equals("-")) continue;

                    String[] splitted = rawText.split("/");
                    if (splitted.length < 2) continue;

                    String themeName = splitted[0].trim();
                    String timePart = splitted[1].trim();
                    if (themeName.isEmpty() || timePart.isEmpty()) continue;

                    themeTimesMap.putIfAbsent(themeName, new ArrayList<>());
                    themeTimesMap.get(themeName).add(timePart);
                }

                for (ThemeMapping tm : themeMappings) {
                    if (themeTimesMap.containsKey(tm.title)) {
                        List<String> times = themeTimesMap.get(tm.title);
                        saveToDB(tm, dataDateAttr, times);
                        saveToFinalMap(tm, dataDateAttr, times);
                    } else {
                        saveToDB(tm, dataDateAttr, Collections.emptyList());
                        saveToFinalMap(tm, dataDateAttr, Collections.emptyList());
                    }
                }
            }
        } catch (TimeoutException e) {
            System.out.println("❌ 예약 정보 못찾음 (TimeoutException).");
        } catch (Exception e) {
            System.err.println("❌ parseCalendar 오류: " + e.getMessage());
        }
    }

    /**
     * DB 저장 (없으면 빈 리스트)
     */
    private void saveToDB(ThemeMapping tm, String date, List<String> times) {
        try {
            Document filter = new Document("themeName", tm.title)
                    .append("zizum", tm.branch)
                    .append("date", date);

            long expireTime = System.currentTimeMillis() + (24L*60*60*1000);
            Document update = new Document("$set", new Document("availableTimes", times)
                    .append("store", "danpyeonseon")
                    .append("zizum", tm.branch)
                    .append("location", tm.location)
                    .append("brand", tm.brand)
                    .append("themeName", tm.title)
                    .append("date", date)
                    .append("expireAt", new Date(expireTime))
                    .append("id", tm.id));
            reservationCollection.updateOne(filter, update, new UpdateOptions().upsert(true));
        } catch(Exception e) {
            System.err.println("DB 저장 에러: " + e.getMessage());
        }
    }

    /**
     * finalMap에도 테마별 times를 저장
     * times가 비어 있으면 '없음' 출력될 것임
     */
    private void saveToFinalMap(ThemeMapping tm, String date, List<String> times) {
        finalMap.putIfAbsent(date, new LinkedHashMap<>());
        finalMap.get(date).putIfAbsent(tm.branch, new LinkedHashMap<>());
        finalMap.get(date).get(tm.branch).put(tm.title, times);
    }

    /**
     * 마지막에 최종 출력
     * (날짜 -> 지점 -> 테마 순서로, times가 비어 있으면 "없음")
     */
    private void printFinalResults() {
        for(String date : finalMap.keySet()) {
            Map<String, Map<String, List<String>>> branchMap = finalMap.get(date);
            for(String branch : branchMap.keySet()) {
                System.out.println("\n📍 " + branch + " (" + date + ")");
                Map<String, List<String>> themeMap = branchMap.get(branch);
                for(Map.Entry<String, List<String>> e : themeMap.entrySet()) {
                    String themeName = e.getKey();
                    List<String> times = e.getValue();
                    if(times == null || times.isEmpty()) {
                        System.out.println(themeName + " : 없음");
                    } else {
                        System.out.println(themeName + " : " + times);
                    }
                }
            }
        }
    }

    public static void main(String[] args) {
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

        try {
            DanpyeonseonCrawling crawler = new DanpyeonseonCrawling(driver);

            String todayDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

            crawler.crawlAllDates(todayDate);
        } finally {
            driver.quit();
        }
    }
}
