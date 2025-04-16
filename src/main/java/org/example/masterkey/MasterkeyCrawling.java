package org.example.masterkey;

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

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.NoSuchElementException;
import java.util.*;
import java.util.stream.Collectors;

public class MasterkeyCrawling {
    private final MongoCollection<Document> reservationCollection;

    private static class ThemeMapping {
        int id;
        String brand;
        String location;
        String branch;
        String title;
        int bid;
        ThemeMapping(int id, String brand, String location, String branch, String title, int bid) {
            this.id = id;
            this.brand = brand;
            this.location = location;
            this.branch = branch;
            this.title = title;
            this.bid = bid;
        }
    }
    private static final List<ThemeMapping> THEME_MAPPINGS = Arrays.asList(
            // 노바홍대 , 플포랩 강남, 프라임신촌퍼블릭, 노원점, 건대점, 잠실점,홍대점
            // 신촌 (bid=32)
            new ThemeMapping(42, "마스터키", "신촌", "프라임 신촌 퍼블릭점", "SCENE : 404 NOT FOUND", 32),
            new ThemeMapping(43, "마스터키", "신촌", "프라임 신촌 퍼블릭점", "그도... 그럴 것이다", 32),
            new ThemeMapping(46, "마스터키", "신촌", "프라임 신촌 퍼블릭점", "인투더와일드", 32),
            // 노바홍대 (bid=41)
            new ThemeMapping(62, "마스터키", "홍대", "노바홍대점", "검은의사", 41),
            new ThemeMapping(66, "마스터키", "홍대", "노바홍대점", "NOEXIT", 41),
            // 건대점 (bid=26)
            new ThemeMapping(44, "마스터키", "건대", "건대점", "DELIVER", 26),
            new ThemeMapping(45, "마스터키", "건대", "건대점", "D-Day", 26),
            new ThemeMapping(58, "마스터키", "건대", "건대점", "교생실습", 26),
            // 마스터키강남점 (bid=35)
            new ThemeMapping(254, "마스터키", "강남", "마스터키강남점", "위로", 35),
            new ThemeMapping(135, "마스터키", "강남", "마스터키강남점", "리허설", 35),
            new ThemeMapping(136, "마스터키", "강남", "마스터키강남점", "갱생", 35),
            new ThemeMapping(137, "마스터키", "강남", "마스터키강남점", "더맨", 35),
            new ThemeMapping(138, "마스터키", "강남", "마스터키강남점", "STAFF ONLY", 35),
            new ThemeMapping(139, "마스터키", "강남", "마스터키강남점", "작은창고", 35),
            //  잠실점 (bid=21)
            new ThemeMapping(52, "마스터키", "잠실", "잠실점", "이스케이프플랜", 21),
            new ThemeMapping(69, "마스터키", "잠실", "잠실점", "어게인", 21),
            new ThemeMapping(53, "마스터키", "잠실", "잠실점", "그리고 아무도 없었다", 21),
            new ThemeMapping(55, "마스터키", "잠실", "잠실점", "블랙룸:쉽게 만들어진 방", 21),
            new ThemeMapping(67, "마스터키", "잠실", "잠실점", "샵보이스", 21),
            new ThemeMapping(65, "마스터키", "잠실", "잠실점", "더매치:마지막전쟁", 21),
            //  홍대점 (bid=11)
            new ThemeMapping(68, "마스터키", "홍대", "홍대점", "온칼로", 11),
            new ThemeMapping(60, "마스터키", "홍대", "홍대점", "연애조작단", 11),
            new ThemeMapping(61, "마스터키", "홍대", "홍대점", "B미술학원13호실", 11),
            // 노원점 (bid=31)
            new ThemeMapping(63, "마스터키", "노원", "노원점", "통제구역", 31),
            new ThemeMapping(64, "마스터키", "노원", "노원점", "일탈", 31),
            new ThemeMapping(70, "마스터키", "노원", "노원점", "타임크랙 (TIME CRACK)", 31),
            new ThemeMapping(71, "마스터키", "노원", "노원점", "이모션 (EMOTION)", 31)


    );

    private static final Map<String, Integer> TITLE_TO_ID_MAP = THEME_MAPPINGS.stream()
            .collect(Collectors.toMap(m -> m.title, m -> m.id));

    public MasterkeyCrawling() {
        MongoClient mongoClient = MongoConfig.getMongoClient();
        MongoDatabase database = mongoClient.getDatabase("scrd");
        this.reservationCollection = database.getCollection("reservation");
    }

    private void saveToDatabase(ThemeMapping mapping, String date, List<String> availableTimes, boolean isFirstDate) {
        try {
            Document filter = new Document("title", mapping.title)
                    .append("date", date)
                    .append("brand", mapping.brand)
                    .append("branch", mapping.branch);

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
            // ✅ 지점명과 날짜를 한 번만 출력하도록 개선
            if (isFirstDate) {
                System.out.println("\n📍 " + mapping.branch + " (" + date + ")");
            }
            System.out.println(" - " + mapping.title + " : " + (availableTimes.isEmpty() ? "없음" : availableTimes));
        } catch (Exception e) {
            System.err.println("DB 저장 오류: " + e.getMessage());
        }
    }

    public void crawlReservations(String startDate, int days) {
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
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));

        try {
            Set<Integer> visitedBids = new HashSet<>();
            for (ThemeMapping mapping : THEME_MAPPINGS) {
                if (visitedBids.contains(mapping.bid)) continue;
                visitedBids.add(mapping.bid);

                String url = "https://www.master-key.co.kr/booking/bk_detail?bid=" + mapping.bid;
                driver.get(url);

                wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".date_click_div1 p")));
                List<WebElement> dateElements = driver.findElements(By.cssSelector(".date_click_div1 p"));

                for (int i = 0; i < Math.min(dateElements.size(), days); i++) {
                    WebElement dateElement = dateElements.get(i);
                    String date = dateElement.getAttribute("data-dd");

                    ((JavascriptExecutor) driver).executeScript("arguments[0].click();", dateElement);
                    Thread.sleep(2000);

                    wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#booking_list .box2-inner")));
                    List<WebElement> rooms = driver.findElements(By.cssSelector("#booking_list .box2-inner"));

                    Map<String, List<String>> themeAvailability = new LinkedHashMap<>();
                    for (WebElement room : rooms) {
                        try {
                            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".title")));
                            String title = room.findElement(By.cssSelector(".title")).getText().trim();

                            wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".right")));
                            List<WebElement> availableTimesElements = room.findElements(By.cssSelector(".right p.col.true a"));

                            List<String> availableTimes = new ArrayList<>();
                            for (WebElement timeElement : availableTimesElements) {
                                availableTimes.add(timeElement.getText().trim().replace("예약가능", "").trim());
                            }

                            themeAvailability.put(title, availableTimes);
                        } catch (TimeoutException | NoSuchElementException e) {
                            System.out.println("⚠ 예약 가능한 시간이 없음.");
                        }
                    }

                    boolean isFirstDate = true;
                    for (Map.Entry<String, List<String>> entry : themeAvailability.entrySet()) {
                        String themeTitle = entry.getKey();
                        List<String> availableTimes = entry.getValue();

                        // ✅ [여기부터] 내가 정의한 title -> id 매핑 적용
                        Integer predefinedId = TITLE_TO_ID_MAP.get(themeTitle);
                        if (predefinedId == null) {
                            System.out.println("⚠ 정의되지 않은 테마: " + themeTitle);
                            continue; // 무시하거나 로그 찍고 패스
                        }

                        ThemeMapping fixedMapping = new ThemeMapping(
                                predefinedId,
                                mapping.brand,
                                mapping.location,
                                mapping.branch,
                                themeTitle,
                                mapping.bid
                        );
                        // ✅ [여기까지] 내가 정의한 id로 저장되게 처리

                        saveToDatabase(fixedMapping, date, availableTimes, isFirstDate);
                        isFirstDate = false;
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
        MasterkeyCrawling crawler = new MasterkeyCrawling();
        crawler.crawlReservations(new SimpleDateFormat("yyyy-MM-dd").format(new Date()), 7);
    }
}
