package org.example.beatphobia;

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

public class BeatphobiaCrawling {
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
            //  홍대던전
            new ThemeMapping(149, "비트포비아", "홍대", "홍대던전", "사라진 보물 : 대저택의 비밀", "https://xdungeon.net/layout/res/home.php?go=rev.main&s_zizum=3"),
            new ThemeMapping(150, "비트포비아", "홍대", "홍대던전", "날씨의 신", "https://xdungeon.net/layout/res/home.php?go=rev.main&s_zizum=3"),
            new ThemeMapping(151, "비트포비아", "홍대", "홍대던전", "꿈의 공장", "https://xdungeon.net/layout/res/home.php?go=rev.main&s_zizum=3"),
            new ThemeMapping(152, "비트포비아", "홍대", "홍대던전", "오늘 나는", "https://xdungeon.net/layout/res/home.php?go=rev.main&s_zizum=3"),
            //  던전101
            new ThemeMapping(300, "비트포비아", "홍대", "던전101", "화생설화 : Blooming", "https://xdungeon.net/layout/res/home.php?go=rev.main&s_zizum=1"),
            new ThemeMapping(157, "비트포비아", "홍대", "던전101", "MST 엔터테인먼트", "https://xdungeon.net/layout/res/home.php?go=rev.main&s_zizum=1"),
            new ThemeMapping(158, "비트포비아", "홍대", "던전101", "LET’S PLAY TOGETHER", "https://xdungeon.net/layout/res/home.php?go=rev.main&s_zizum=1"),
            new ThemeMapping(159, "비트포비아", "홍대", "던전101", "전래동 자살사건", "https://xdungeon.net/layout/res/home.php?go=rev.main&s_zizum=1"),
            //  홍대던전3
            new ThemeMapping(153, "비트포비아", "홍대", "홍대던전3", "그달동네", "https://xdungeon.net/layout/res/home.php?go=rev.main&s_zizum=5"),
            new ThemeMapping(154, "비트포비아", "홍대", "홍대던전3", "이미지 세탁소", "https://xdungeon.net/layout/res/home.php?go=rev.main&s_zizum=5"),
            new ThemeMapping(155, "비트포비아", "홍대", "홍대던전3", "경성 연쇄실종사건", "https://xdungeon.net/layout/res/home.php?go=rev.main&s_zizum=5"),
            new ThemeMapping(156, "비트포비아", "홍대", "홍대던전3", "And I met E", "https://xdungeon.net/layout/res/home.php?go=rev.main&s_zizum=5"),
            //  강남던전
            new ThemeMapping(143, "비트포비아", "강남", "강남던전", "강남목욕탕", "https://xdungeon.net/layout/res/home.php?go=rev.main&s_zizum=2"),
            new ThemeMapping(144, "비트포비아", "강남", "강남던전", "대호시장 살인사건", "https://xdungeon.net/layout/res/home.php?go=rev.main&s_zizum=2"),
            new ThemeMapping(145, "비트포비아", "강남", "강남던전", "LOST KINGDOM : 잊혀진 전설", "https://xdungeon.net/layout/res/home.php?go=rev.main&s_zizum=2"),
            new ThemeMapping(146, "비트포비아", "강남", "강남던전", "마음을 그려드립니다", "https://xdungeon.net/layout/res/home.php?go=rev.main&s_zizum=2"),

            //  강남던전2
            new ThemeMapping(145, "비트포비아", "강남", "강남던전2", "LOST KINGDOM2 : 대탐험의 시작", "https://xdungeon.net/layout/res/home.php?go=rev.main&s_zizum=4"),
            new ThemeMapping(141, "비트포비아", "강남", "강남던전2", "MAYDAY", "https://xdungeon.net/layout/res/home.php?go=rev.main&s_zizum=4"),
            //  던전루나
            new ThemeMapping(142, "비트포비아", "강남", "던전루나", "검은 운명의 밤", "https://xdungeon.net/layout/res/home.php?go=rev.main&s_zizum=6"),
            new ThemeMapping(147, "비트포비아", "강남", "던전루나", "3일", "https://xdungeon.net/layout/res/home.php?go=rev.main&s_zizum=6"),
            //  던전스텔라
            new ThemeMapping(138, "비트포비아", "강남", "던전스텔라", "響 : 향", "https://xdungeon.net/layout/res/home.php?go=rev.main&s_zizum=9"),
            new ThemeMapping(139, "비트포비아", "강남", "던전스텔라", "데스티니 앤드 타로", "https://xdungeon.net/layout/res/home.php?go=rev.main&s_zizum=9"),
            new ThemeMapping(140, "비트포비아", "강남", "던전스텔라", "TIENTANG CITY", "https://xdungeon.net/layout/res/home.php?go=rev.main&s_zizum=9"),
            // 서면던전
            new ThemeMapping(160, "비트포비아", "서면", "서면던전", "오늘 나는", "https://xdungeon.net/layout/res/home.php?go=rev.main&s_zizum=7"),
            new ThemeMapping(161, "비트포비아", "서면", "서면던전", "꿈의 공장", "https://xdungeon.net/layout/res/home.php?go=rev.main&s_zizum=7"),
            new ThemeMapping(162, "비트포비아", "서면", "서면던전", "날씨의 신", "https://xdungeon.net/layout/res/home.php?go=rev.main&s_zizum=7"),
            // 서면던전 레드
            new ThemeMapping(163, "비트포비아", "서면", "서면던전 레드", "어느 수집가의 집", "https://xdungeon.net/layout/res/home.php?go=rev.main&s_zizum=10"),
            new ThemeMapping(164, "비트포비아", "서면", "서면던전 레드", "AMEN", "https://xdungeon.net/layout/res/home.php?go=rev.main&s_zizum=10"),
            new ThemeMapping(165, "비트포비아", "서면", "서면던전 레드", "고시원 살인사건", "https://xdungeon.net/layout/res/home.php?go=rev.main&s_zizum=10"),
            new ThemeMapping(166, "비트포비아", "서면", "서면던전 레드", "당감동 정육점", "https://xdungeon.net/layout/res/home.php?go=rev.main&s_zizum=10"),
            new ThemeMapping(167, "비트포비아", "서면", "서면던전 레드", "부적", "https://xdungeon.net/layout/res/home.php?go=rev.main&s_zizum=10"),
            new ThemeMapping(168, "비트포비아", "서면", "서면던전 레드", "산장으로의 초대", "https://xdungeon.net/layout/res/home.php?go=rev.main&s_zizum=10")


    );

    public BeatphobiaCrawling() {
        MongoClient mongoClient = MongoConfig.getMongoClient();
        MongoDatabase database = mongoClient.getDatabase("scrd");
        this.reservationCollection = database.getCollection("reservation");
    }

    private void saveToDatabase(ThemeMapping mapping, String date, List<String> availableTimes) {
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
            // 지점별 그룹화하여 URL당 한 번만 요청
            Map<String, List<ThemeMapping>> locationMap = new HashMap<>();
            for (ThemeMapping mapping : THEME_MAPPINGS) {
                locationMap.computeIfAbsent(mapping.url, k -> new ArrayList<>()).add(mapping);
            }

            for (int i = 0; i < days; i++) {
                Calendar calendar = Calendar.getInstance();
                calendar.add(Calendar.DATE, i);
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
                String targetDate = dateFormat.format(calendar.getTime());

                // 날짜별로 크롤링 결과를 임시 저장해서, 나중에 한 번에 출력할 수 있도록 준비
                // branch(지점)마다 테마별로 시간을 모아두는 구조
                // key: branch, value: ( key: themeTitle, value: List<String> times )
                Map<String, Map<String, List<String>>> branchThemeMap = new LinkedHashMap<>();

                for (Map.Entry<String, List<ThemeMapping>> entry : locationMap.entrySet()) {
                    String url = entry.getKey();
                    List<ThemeMapping> mappings = entry.getValue();
                    // 어떤 branch 인지(홍대던전, 강남던전, ...)를 꺼낸다.
                    String branchName = mappings.get(0).branch;

                    // branch에 해당하는 테마-시간 저장용
                    branchThemeMap.putIfAbsent(branchName, new LinkedHashMap<>());

                    driver.get(url);
                    try {
                        WebElement dateInput = driver.findElement(By.name("rev_days"));
                        ((JavascriptExecutor) driver).executeScript("arguments[0].value = arguments[1];", dateInput, targetDate);
                        ((JavascriptExecutor) driver).executeScript("fun_search();");
                        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(".thm_box")));

                        List<WebElement> themeBoxes = driver.findElements(By.cssSelector(".thm_box .box"));

                        // (테마제목 -> ThemeMapping) 매핑
                        Map<String, ThemeMapping> themeMap = new HashMap<>();
                        for (ThemeMapping mapping : mappings) {
                            themeMap.put(mapping.title, mapping);
                        }

                        // HTML에서 테마박스를 돌며, 실제 시간을 추출
                        for (WebElement themeBox : themeBoxes) {
                            String themeName = themeBox.findElement(By.cssSelector(".img_box .tit")).getText().trim();

                            List<WebElement> timeElements = themeBox.findElements(By.cssSelector(".time_box ul li.sale:not(.dead) a"));
                            List<String> availableTimes = new ArrayList<>();

                            for (WebElement timeElement : timeElements) {
                                availableTimes.add(timeElement.getText().replace("SALE", "").trim());
                            }

                            // themeMap에 있는 테마명과 일치(혹은 포함)하면 DB 저장 및 임시 리스트에 추가
                            for (String key : themeMap.keySet()) {
                                // 테마명이 "강남목욕탕" 같이 완전히 일치하지 않고
                                // "사라진 보물 : 대저택의 비밀"처럼 포함만 되어도 처리하도록 하고 싶다면
                                // if (themeName.contains(key)) 로 사용
                                if (themeName.equals(key) || themeName.contains(key)) {
                                    saveToDatabase(themeMap.get(key), targetDate, availableTimes);
                                    // branchThemeMap에 테마명으로 시간 정보 저장
                                    branchThemeMap.get(branchName).put(key, availableTimes);
                                }
                            }
                        }
                    } catch (Exception e) {
                        // 실패한 경우에도 branchThemeMap에 넣어주어야, 출력 시 빠지지 않는다 (없음으로 처리)
                        for (ThemeMapping mapping : mappings) {
                            branchThemeMap.get(branchName)
                                    .putIfAbsent(mapping.title, new ArrayList<>()); // 빈 리스트로 세팅
                        }
                    }
                }

                // 이제 branchThemeMap 에 날짜(targetDate)에 대한 모든 branch별 정보가 모였으니 출력
                for (String branch : branchThemeMap.keySet()) {
                    System.out.println("\n📍 " + branch + " (" + targetDate + ")");
                    // branch에 해당하는 (테마 -> times)
                    Map<String, List<String>> themeInfo = branchThemeMap.get(branch);

                    // 테마별로 출력 (입력 순서 유지 위해 LinkedHashMap 사용)
                    for (Map.Entry<String, List<String>> entry : themeInfo.entrySet()) {
                        String themeTitle = entry.getKey();
                        List<String> times = entry.getValue();
                        if (times == null || times.isEmpty()) {
                            System.out.println(themeTitle + " : 없음");
                        } else {
                            System.out.println(themeTitle + " : " + times);
                        }
                    }
                }
            }
        } finally {
            driver.quit();
        }
    }

    public static void main(String[] args) {
        BeatphobiaCrawling crawler = new BeatphobiaCrawling();
        crawler.crawlReservations(7);
    }
}
