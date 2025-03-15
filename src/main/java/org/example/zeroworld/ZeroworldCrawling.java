package org.example.zeroworld;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.UpdateOptions;
import org.bson.Document;
import org.example.config.MongoConfig;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.*;

public class ZeroworldCrawling {

    // 사용자 정의 ID 매핑 (테마명 → ID)
    private static final Map<String, Integer> THEME_ID_MAP = new HashMap<>();
    static {
        THEME_ID_MAP.put("링", 195);
        THEME_ID_MAP.put("포레스트(FORREST)", 196);
        THEME_ID_MAP.put("DONE", 197);
        THEME_ID_MAP.put("아이엠", 198);
        THEME_ID_MAP.put("헐!", 199);
        THEME_ID_MAP.put("제로호텔L", 200);
        THEME_ID_MAP.put("어느 겨울밤2", 201);
        THEME_ID_MAP.put("콜러", 202);
        THEME_ID_MAP.put("나비효과", 203);
    }

    // 고정 정보 (원하는 출력/DB 저장 형식에 맞춰 수정)
    private static final String BRAND = "제로월드";
    // 예를 들어, 홍대점으로 출력하고 싶다면:
    private static final String LOCATION = "강남"; // 이 값은 DB에 저장되는 값입니다.
    private static final String BRANCH = "강남점";

    private final WebDriver driver;
    private final MongoCollection<Document> reservationCollection;

    public ZeroworldCrawling(WebDriver driver) {
        this.driver = driver;
        MongoClient client = MongoConfig.getMongoClient();
        MongoDatabase db = client.getDatabase("scrd");
        this.reservationCollection = db.getCollection("reservation");
    }

    /**
     * 테마명 정규화: 예를 들어 "[강남] 링" → "링", "포레스트 (FORREST)" → "포레스트(FORREST)"
     */
    private String normalizeTitle(String raw) {
        String t = raw.replaceAll("\\[.*?\\]\\s*", ""); // 대괄호 내용 제거
        t = t.replaceAll("\\s*\\(\\s*", "(").replaceAll("\\s*\\)\\s*", ")");
        return t.trim();
    }

    /**
     * 오늘부터 7일간 크롤링
     */
    public void crawlNext7Days() {
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        for (int i = 0; i < 7; i++) {
            String dateStr = sdf.format(cal.getTime());
            crawlOneDay(dateStr);
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }
    }

    /**
     * 지정된 날짜(dateStr)에 대해 예약 페이지에서 테마와 예약 가능 시간 추출 후 출력 및 DB 저장
     */
    public void crawlOneDay(String dateStr) {
        try {
            // 1) 예약 페이지 접속
            driver.get("https://zerogangnam.com/reservation");

            // 2) 달력이 로드될 때까지 대기
            new WebDriverWait(driver, Duration.ofSeconds(10))
                    .until(ExpectedConditions.visibilityOfElementLocated(By.id("calendar")));

            // 3) 달력에서 해당 날짜 클릭
            selectDateOnCalendar(dateStr);

            // 4) 테마 목록 (#themeChoice) 로드 대기
            new WebDriverWait(driver, Duration.ofSeconds(10))
                    .until(ExpectedConditions.visibilityOfElementLocated(By.id("themeChoice")));
            new WebDriverWait(driver, Duration.ofSeconds(10))
                    .until(ExpectedConditions.numberOfElementsToBeMoreThan(
                            By.cssSelector("#themeChoice label.hover2"), 0
                    ));

            // 5) 헤더 출력: "📍 [지점] ([날짜])"
            System.out.println("\n📍 " + BRANCH + " (" + dateStr + ")");

            // 6) 테마 라디오 버튼들 찾기
            List<WebElement> themeLabels = driver.findElements(By.cssSelector("#themeChoice label.hover2"));

            // 7) 각 테마 처리
            for (WebElement themeLabel : themeLabels) {
                WebElement radio = themeLabel.findElement(By.cssSelector("input[type='radio']"));
                // radio value는 참고용
                String themeValue = radio.getAttribute("value");
                String rawThemeTitle = themeLabel.getText().trim();
                String processedTitle = normalizeTitle(rawThemeTitle);

                // 테마 클릭 (스크롤 후)
                ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", themeLabel);
                themeLabel.click();

                // 8) 예약 가능한 시간 목록 대기 (#themeTimeWrap)
                new WebDriverWait(driver, Duration.ofSeconds(10))
                        .until(ExpectedConditions.visibilityOfElementLocated(By.id("themeTimeWrap")));
                new WebDriverWait(driver, Duration.ofSeconds(10))
                        .until(ExpectedConditions.numberOfElementsToBeMoreThan(
                                By.cssSelector("#themeTimeWrap label.hover2"), 0
                        ));

                // 9) 예약 가능한 시간 파싱
                List<String> availableTimes = fetchAvailableTimes();

                // 10) DB 저장: 사용자 정의 ID 적용
                int customId = getUserDefinedId(processedTitle);
                saveToDatabase(BRAND, LOCATION, BRANCH, processedTitle, customId, dateStr, availableTimes);

                // 11) 콘솔 출력: " -> [테마명] ([radio value]) : [예약시간들]"
                System.out.println( processedTitle +  " : " + availableTimes);
            }
        } catch (Exception e) {
            System.err.println("[오류] " + dateStr + " 처리 중: " + e.getMessage());
        }
    }


//    private void selectDateOnCalendar(String dateStr) {
//        try {
//            String[] parts = dateStr.split("-");
//            int year = Integer.parseInt(parts[0]);
//            int month = Integer.parseInt(parts[1]); // 1~12
//            int day = Integer.parseInt(parts[2]);
//            int dataMonth = month - 1; // 예: 3월 -> data-month="2"
//
//            List<WebElement> dayCells = driver.findElements(By.cssSelector(".datepicker--cell.datepicker--cell-day"));
//
//            for (WebElement cell : dayCells) {
//                String cellYear = cell.getAttribute("data-year");
//                String cellMonth = cell.getAttribute("data-month");
//                String cellDate = cell.getAttribute("data-date");
//                boolean isDisabled = cell.getAttribute("class").contains("-disabled-");
//
//                // 로그 추가 (디버깅 목적)
//                System.out.println("날짜 검토: " + cellYear + "-" + (Integer.parseInt(cellMonth) + 1) + "-" + cellDate
//                        + " | 비활성화 여부: " + isDisabled);
//
//                if (isDisabled) continue;
//
//                if (String.valueOf(year).equals(cellYear)
//                        && String.valueOf(dataMonth).equals(cellMonth)
//                        && String.valueOf(day).equals(cellDate)) {
//                    ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", cell);
//
//                    // 클릭 가능한 상태인지 확인 후 클릭
//                    new WebDriverWait(driver, Duration.ofSeconds(5))
//                            .until(ExpectedConditions.elementToBeClickable(cell))
//                            .click();
//
//                    return;
//                }
//            }
//            System.out.println("❌ 날짜 클릭 실패 또는 해당 날짜가 비활성화됨: " + dateStr);
//        } catch (Exception e) {
//            System.err.println("⚠ selectDateOnCalendar() 예외 발생: " + e.getMessage());
//        }
//    }
private void selectDateOnCalendar(String dateStr) {
    try {
        String[] parts = dateStr.split("-");
        int year = Integer.parseInt(parts[0]);
        int month = Integer.parseInt(parts[1]); // 1~12
        int day = Integer.parseInt(parts[2]);
        int dataMonth = month - 1; // 예: 3월 -> data-month="2"

        List<WebElement> dayCells = driver.findElements(By.cssSelector(".datepicker--cell.datepicker--cell-day"));

        for (WebElement cell : dayCells) {
            String cellYear = cell.getAttribute("data-year");
            String cellMonth = cell.getAttribute("data-month");
            String cellDate = cell.getAttribute("data-date");
            boolean isDisabled = cell.getAttribute("class").contains("-disabled-");

            System.out.println("날짜 검토: " + cellYear + "-" + (Integer.parseInt(cellMonth) + 1) + "-" + cellDate
                    + " | 비활성화 여부: " + isDisabled);

            if (isDisabled) continue;

            if (String.valueOf(year).equals(cellYear)
                    && String.valueOf(dataMonth).equals(cellMonth)
                    && String.valueOf(day).equals(cellDate)) {

                // 스크롤하여 날짜 보이도록 함
                ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", cell);
                Thread.sleep(500); // 스크롤 후 0.5초 대기

                // JavaScript로 강제 클릭
                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", cell);
                Thread.sleep(1000); // 클릭 후 1초 대기

                // themeChoice 요소가 나타날 때까지 최대 15초 대기
                new WebDriverWait(driver, Duration.ofSeconds(15))
                        .until(ExpectedConditions.visibilityOfElementLocated(By.id("themeChoice")));

                System.out.println("✅ 날짜 클릭 성공: " + dateStr);
                return;
            }
        }
        System.out.println("❌ 날짜 클릭 실패 또는 해당 날짜가 비활성화됨: " + dateStr);
    } catch (Exception e) {
        System.err.println("⚠ selectDateOnCalendar() 예외 발생: " + e.getMessage());
    }
}


    /**
     * #themeTimeWrap 내의 label들을 스캔하여 예약 가능한 시간 추출
     */
    private List<String> fetchAvailableTimes() {
        List<String> result = new ArrayList<>();
        try {
            // 1️⃣ 요소가 나타날 때까지 20초 기다리기
            new WebDriverWait(driver, Duration.ofSeconds(20))
                    .until(ExpectedConditions.visibilityOfElementLocated(By.id("themeTimeWrap")));

            new WebDriverWait(driver, Duration.ofSeconds(20))
                    .until(ExpectedConditions.numberOfElementsToBeMoreThan(
                            By.cssSelector("#themeTimeWrap label.hover2"), 0
                    ));

            // 2️⃣ 예약 가능한 시간 목록 가져오기
            List<WebElement> timeLabels = driver.findElements(By.cssSelector("#themeTimeWrap label.hover2"));
            for (WebElement lbl : timeLabels) {
                WebElement input = lbl.findElement(By.cssSelector("input[name='reservationTime']"));
                boolean isDisabled = (input.getAttribute("disabled") != null);
                boolean hasActiveClass = lbl.getAttribute("class").contains("active");

                // 3️⃣ 클릭 가능하면 리스트에 추가
                if (!isDisabled && !hasActiveClass) {
                    result.add(lbl.getText().trim());
                }
            }

            // 4️⃣ 예약 가능한 시간이 없으면 "예약불가" 반환
            if (result.isEmpty()) {
                result.add("예약불가");
            }
        } catch (Exception e) {
            System.err.println("fetchAvailableTimes() 오류: " + e.getMessage());
            result.add("오류");
        }
        return result;
    }


    /**
     * 사용자 정의 ID 매핑: processedTitle에 따라 반환
     */
    private int getUserDefinedId(String processedTitle) {
        // 사용자 정의 문자열 포함 여부로 ID 반환
        if (processedTitle.contains("링")) {
            return THEME_ID_MAP.get("링");
        } else if (processedTitle.contains("포레스트")) {
            return THEME_ID_MAP.get("포레스트(FORREST)");
        } else if (processedTitle.contains("DONE")) {
            return THEME_ID_MAP.get("DONE");
        } else if (processedTitle.contains("아이엠")) {
            return THEME_ID_MAP.get("아이엠");
        } else if (processedTitle.contains("헐!")) {
            return THEME_ID_MAP.get("헐!");
        } else if (processedTitle.contains("제로호텔L")) {
            return THEME_ID_MAP.get("제로호텔L");
        } else if (processedTitle.contains("어느 겨울밤2")) {
            return THEME_ID_MAP.get("어느 겨울밤2");
        } else if (processedTitle.contains("콜러")) {
            return THEME_ID_MAP.get("콜러");
        } else if (processedTitle.contains("나비효과")) {
            return THEME_ID_MAP.get("나비효과");
        }
        return -1;
    }

    /**
     * DB에 예약 가능 시간 저장
     * 저장 구조: { brand, location, branch, title, id, date, availableTimes, updatedAt, expireAt }
     */
    private void saveToDatabase(String brand, String location, String branch, String title, int id, String dateStr, List<String> times) {
        try {
            Document filter = new Document("date", dateStr)
                    .append("brand", brand)
                    .append("title", title);
            long expireTime = System.currentTimeMillis() + 24L * 60 * 60 * 1000;
            Date expireAt = new Date(expireTime);
            Document doc = new Document("brand", brand)
                    .append("location", location)
                    .append("branch", branch)
                    .append("title", title)
                    .append("id", id)
                    .append("date", dateStr)
                    .append("availableTimes", times)
                    .append("updatedAt", new Date())
                    .append("expireAt", expireAt);
            Document update = new Document("$set", doc);
            reservationCollection.updateOne(filter, update, new UpdateOptions().upsert(true));
        } catch (Exception e) {
            System.err.println("DB 저장 오류: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        // System.setProperty("webdriver.chrome.driver", "/Users/pro/Downloads/chromedriver-mac-x64/chromedriver");
        System.setProperty("webdriver.chrome.driver", "/usr/local/bin/chromedriver");

        // Chrome 옵션 설정
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--remote-allow-origins=*");
        options.addArguments("--disable-background-networking"); // 추가
//        options.addArguments("--user-data-dir=/dev/shm/chrome-user-data");

        WebDriver driver = new ChromeDriver(options);

        try {
            ZeroworldCrawling crawler = new ZeroworldCrawling(driver);
            crawler.crawlNext7Days();
        } finally {
            driver.quit();
        }
    }
}
