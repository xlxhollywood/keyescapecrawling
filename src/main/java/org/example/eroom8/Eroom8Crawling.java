package org.example.eroom8;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.UpdateOptions;
import org.bson.Document;
import org.example.config.MongoConfig;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.*;

public class Eroom8Crawling {
    private final MongoCollection<Document> reservationCollection;

    private static class ThemeMapping {
        int id;
        String title;
        ThemeMapping(int id, String title) {
            this.id = id;
            this.title = title;
        }
    }

    private static final List<ThemeMapping> THEME_MAPPINGS = Arrays.asList(
            new ThemeMapping(187, "고인"),
            new ThemeMapping(188, "민초"),
            new ThemeMapping(189, "나의계획은"),
            new ThemeMapping(190, "고령화사회"),
            new ThemeMapping(191, "아이엠유튜버"),
            new ThemeMapping(192, "스테이시"),
            new ThemeMapping(193, "낙원"),
            new ThemeMapping(194, "죽지않아")
    );

    public Eroom8Crawling() {
        MongoClient mongoClient = MongoConfig.getMongoClient();
        MongoDatabase database = mongoClient.getDatabase("scrd");
        this.reservationCollection = database.getCollection("reservation");
    }

    private void saveToDatabase(ThemeMapping mapping, String date, List<String> availableTimes) {
        try {
            Document filter = new Document("title", mapping.title).append("date", date);
            Document docToSave = new Document("brand", "이룸에이트")
                    .append("location", "강남")
                    .append("branch", "강남점")
                    .append("title", mapping.title)
                    .append("id", mapping.id)
                    .append("date", date)
                    .append("availableTimes", availableTimes)
                    .append("updatedAt", new Date())
                    .append("expireAt", new Date(System.currentTimeMillis() + 24L * 60 * 60 * 1000));

            reservationCollection.updateOne(filter, new Document("$set", docToSave), new UpdateOptions().upsert(true));
            System.out.println(" - " + mapping.title + " : " + (availableTimes.isEmpty() ? "없음" : availableTimes));
        } catch (Exception e) {
            System.err.println("DB 저장 오류: " + e.getMessage());
        }
    }

    public void crawlReservations(String startDate, int days) {

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
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));



        try {
            for (int i = 0; i < days; i++) {
                Calendar cal = Calendar.getInstance();
                cal.add(Calendar.DATE, i);
                String date = new SimpleDateFormat("yyyy-MM-dd").format(cal.getTime());
                String url = "https://eroom8.co.kr/layout/res/home.php?rev_days=" + date + "&go=rev.make";
                driver.get(url);
                System.out.println("\n📍 강남점 (" + date + ")");

                wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".theme_box")));
                Thread.sleep(2000);
                List<WebElement> themes = driver.findElements(By.cssSelector(".theme_box"));

                for (WebElement theme : themes) {
                    String rawTitle = theme.findElement(By.cssSelector(".h3_theme")).getText();
                    String title = rawTitle.split("[ (]")[0].trim().replaceAll("\\s+", "");

                    Optional<ThemeMapping> mappingOpt = THEME_MAPPINGS.stream()
                            .filter(t -> t.title.replaceAll("\\s+", "").equals(title))
                            .findFirst();
                    if (!mappingOpt.isPresent()) continue;

                    List<WebElement> times = theme.findElements(By.cssSelector(".time_Area ul.reserve_Time li a:not(.end) .time"));
                    List<String> availableTimes = new ArrayList<>();

                    if (times.isEmpty()) {
                        System.out.println("⚠ 예약 가능한 시간이 없음.");
                    } else {
                        for (WebElement time : times) {
                            availableTimes.add(time.getText().trim());
                        }
                    }

                    saveToDatabase(mappingOpt.get(), date, availableTimes);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            driver.quit();
        }
    }

    public static void main(String[] args) {
        Eroom8Crawling crawler = new Eroom8Crawling();
        crawler.crawlReservations(new SimpleDateFormat("yyyy-MM-dd").format(new Date()), 7);
    }
}