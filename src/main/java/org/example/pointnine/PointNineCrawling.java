package org.example.pointnine;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.UpdateOptions;
import org.bson.Document;
import org.example.config.MongoConfig;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.*;


public class PointNineCrawling {

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
            new ThemeMapping(242, "포인트나인", "강남", "강남점", "EP1 : 시간이 멈춘 마을"),
            new ThemeMapping(243, "포인트나인", "강남", "강남점", "EP2 : 열쇠공의 이중생활"),
            new ThemeMapping(244, "포인트나인", "강남", "강남점", "EP3 : 눈 먼 귀금속상인의 후회"),
            new ThemeMapping(245, "포인트나인", "강남", "강남점", "EP4 : 주인 없는 낡은 서점"),

            // 건대점
            new ThemeMapping(248, "포인트나인", "건대", "건대점", "JACK IN THE SHOW"),
            new ThemeMapping(249, "포인트나인", "건대", "건대점", "RETURN"),
            new ThemeMapping(250, "포인트나인", "건대", "건대점", "ALBA"),

            // 홍대점
            new ThemeMapping(251, "포인트나인", "홍대", "홍대점", "SILENT"),
            new ThemeMapping(252, "포인트나인", "홍대", "홍대점", "LISTEN")
    );


    private static final Map<String, ThemeMapping> THEME_MAP = new HashMap<>();
    static {
        for (ThemeMapping tm : THEME_MAPPINGS) {
            THEME_MAP.put(tm.title, tm);
        }
    }

    public PointNineCrawling(WebDriver driver) {
        this.driver = driver;
        MongoClient mongoClient = MongoConfig.getMongoClient();
        MongoDatabase database = mongoClient.getDatabase("scrd");
        this.reservationCollection = database.getCollection("reservation");
    }


    public void crawlThemesForDate(String date) {

        Map<String, String> zizumList = new LinkedHashMap<>();
        zizumList.put("1", "강남점");
        zizumList.put("4", "강남2호점");
        zizumList.put("5", "건대점");
        zizumList.put("6", "홍대점");


        boolean isFirstStoreOfDay = true;


        for (Map.Entry<String, String> entry : zizumList.entrySet()) {
            String zizumId = entry.getKey();
            String zizumName = entry.getValue();

            try {
                driver.get("https://point-nine.com/layout/res/home.php?go=rev.make&s_zizum=1");

                if (isFirstStoreOfDay) {
                    System.out.println("\n📍 " + date);
                    isFirstStoreOfDay = false;
                }

                selectDate(date);

                selectZizum(zizumId);

                Map<String, List<String>> themeTimesMap = fetchAvailableTimesWithTheme();

                for (Map.Entry<String, List<String>> themeEntry : themeTimesMap.entrySet()) {
                    String rawThemeName = themeEntry.getKey();
                    String themeName = normalizeThemeName(rawThemeName);
                    List<String> availableTimes = themeEntry.getValue();

                    if (availableTimes.isEmpty()) {
                        System.out.println(" - " + themeName + " : 없음");
                    } else {
                        System.out.println(" - " + themeName + " : " + availableTimes);
                    }


                    ThemeMapping mapping = THEME_MAP.get(themeName);
                    String brand = (mapping != null) ? mapping.brand : "포인트나인";
                    String location = (mapping != null) ? mapping.location : "기타";
                    String branch = (mapping != null) ? mapping.branch : zizumName;
                    String title = (mapping != null) ? mapping.title : themeName;
                    int id = (mapping != null) ? mapping.id : 0;

                    saveToDatabase(brand, location, branch, title, id, date, availableTimes);
                }

            } catch (TimeoutException e) {
                System.out.println(" - (지점: " + zizumName + ") : 없음 (페이지 로드 실패)");
            } catch (Exception e) {
                System.err.println("Error while crawling " + zizumName + " on " + date + ": " + e.getMessage());
            }
        }
    }


    private void selectDate(String date) {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        js.executeScript(String.format("document.querySelector('input[name=\"rev_days\"]').value = '%s';", date));
        js.executeScript("fun_rev_change();");

        new WebDriverWait(driver, Duration.ofSeconds(10))
                .until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".theme_box")));
    }


    private void selectZizum(String zizumId) {
        WebElement zizumSelect = driver.findElement(By.name("s_zizum"));
        new WebDriverWait(driver, Duration.ofSeconds(5))
                .until(ExpectedConditions.elementToBeClickable(zizumSelect));

        JavascriptExecutor js = (JavascriptExecutor) driver;
        js.executeScript(String.format("document.querySelector('select[name=\"s_zizum\"]').value = '%s';", zizumId));
        js.executeScript("fun_rev_change();");

        new WebDriverWait(driver, Duration.ofSeconds(10))
                .until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".theme_box")));
    }


    private Map<String, List<String>> fetchAvailableTimesWithTheme() {
        Map<String, List<String>> themeTimesMap = new LinkedHashMap<>();
        try {
            List<WebElement> themeBoxes = driver.findElements(By.cssSelector(".theme_box"));
            for (WebElement themeBox : themeBoxes) {
                String rawThemeName = themeBox.findElement(By.cssSelector(".h3_theme")).getText().trim();
                // normalize 함수를 적용하여 괄호 등을 제거
                String themeName = normalizeThemeName(rawThemeName);

                List<WebElement> availableTimeElements = themeBox.findElements(By.cssSelector(".reserve_Time li a:not(.end) .time"));
                List<String> availableTimes = new ArrayList<>();
                for (WebElement timeElement : availableTimeElements) {
                    String time = timeElement.getText().trim();
                    availableTimes.add(time);
                }
                themeTimesMap.put(themeName, availableTimes);
            }
        } catch (Exception e) {
            System.err.println("Error while fetching theme times: " + e.getMessage());
        }
        return themeTimesMap;
    }


    private String normalizeThemeName(String rawTheme) {
        return rawTheme.replaceAll("\\(.*?\\)", "").trim();
    }


    private void saveToDatabase(String brand,
                                String location,
                                String branch,
                                String title,
                                int id,
                                String date,
                                List<String> availableTimes) {
        try {
            Document filter = new Document("brand", brand)
                    .append("title", title)
                    .append("date", date);

            Date expireAt = new Date(System.currentTimeMillis() + 24L * 60L * 60L * 1000);

            Document docToSave = new Document("brand", brand)
                    .append("location", location)
                    .append("branch", branch)
                    .append("title", title)
                    .append("id", id)
                    .append("date", date)
                    .append("availableTimes", availableTimes)
                    .append("updatedAt", new Date())
                    .append("expireAt", expireAt);

            reservationCollection.updateOne(filter, new Document("$set", docToSave),
                    new UpdateOptions().upsert(true));

        } catch (Exception e) {
            System.err.println("Error saving theme data: " + e.getMessage());
        }
    }


    public static void main(String[] args) {

        // 크롬드라이버 경로 (Docker: /usr/local/bin/chromedriver)
        System.setProperty("webdriver.chrome.driver", "/usr/local/bin/chromedriver");
        org.openqa.selenium.chrome.ChromeOptions options = new org.openqa.selenium.chrome.ChromeOptions();
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
            driver = new org.openqa.selenium.chrome.ChromeDriver(options);
            PointNineCrawling crawler = new PointNineCrawling(driver);

            Calendar cal = Calendar.getInstance();
            for (int i = 0; i < 7; i++) {
                String date = String.format("%tF", cal); // yyyy-MM-dd
                crawler.crawlThemesForDate(date);
                cal.add(Calendar.DATE, 1);
            }

        } catch (Exception e) {
            System.err.println("[포인트나인] 크롤링 오류: " + e.getMessage());
        } finally {
            if (driver != null) driver.quit();
        }

    }
}
