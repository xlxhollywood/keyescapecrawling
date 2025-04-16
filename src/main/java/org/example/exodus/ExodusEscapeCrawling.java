package org.example.exodus;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.UpdateOptions;
import org.bson.Document;
import org.example.config.MongoConfig;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.text.SimpleDateFormat;
import java.util.*;

public class ExodusEscapeCrawling {

    /**
     * 테마 매핑용 클래스
     */
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

    /**
     * 실제 테마 매핑 정보
     * - 여기서 예시로 2개만 등록 (CLAIM, WISH)
     * - 필요에 따라 추가/수정 가능
     */
    private static final List<ThemeMapping> THEME_MAPPINGS = Arrays.asList(
            new ThemeMapping(187, "엑소더스이스케이프", "강남", "강남 1호점", "CLAIM"),
            new ThemeMapping(188, "엑소더스이스케이프", "강남", "강남 1호점", "WISH")
    );

    // 엑소더스이스케이프 예약 페이지
    private static final String BASE_URL = "https://exodusescape.co.kr/layout/res/home.php?go=rev.make";

    private final MongoCollection<Document> reservationCollection;

    public ExodusEscapeCrawling() {
        MongoClient mongoClient = MongoConfig.getMongoClient();
        MongoDatabase database = mongoClient.getDatabase("scrd");
        this.reservationCollection = database.getCollection("reservation");
    }

    /**
     * HTML에서 테마명별 예약가능 시간 목록을 추출한다.
     *  - key: 테마명 (예: "CLAIM")
     *  - value: 해당 테마의 예약 가능 시작시간 리스트
     */
    private Map<String, List<String>> parseThemeBoxes(org.jsoup.nodes.Document doc) {
        Map<String, List<String>> result = new HashMap<>();

        Elements themeBoxes = doc.select("div.theme_box");
        for (Element box : themeBoxes) {
            // 테마명 추출
            Element titleEl = box.selectFirst("div.theme_Title h3.h3_theme");
            if (titleEl == null) continue;

            // 예: "CLAIM", "WISH" ...
            String themeTitle = titleEl.text().trim().toUpperCase();

            // 예약 가능한 시간 <li> 파싱
            List<String> times = new ArrayList<>();
            Elements liElements = box.select("div.time_Area ul.reserve_Time li");
            for (Element li : liElements) {
                // span.possible가 있으면 예약가능
                if (li.select("span.possible").size() > 0) {
                    Element timeEl = li.selectFirst("span.time");
                    if (timeEl != null) {
                        String timeText = timeEl.text().replace("☆", "").trim();
                        times.add(timeText);
                    }
                }
            }
            result.put(themeTitle, times);
        }
        return result;
    }

    /**
     * MongoDB에 저장 + 콘솔에 출력
     */
    private void saveToDatabase(ThemeMapping mapping, String date, List<String> availableTimes, boolean isFirstDate) {
        try {
            // MongoDB에서 title+date+brand 기준으로 upsert
            Document filter = new Document("title", mapping.title)
                    .append("date", date)
                    .append("brand", mapping.brand);

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

            // KeyEscapeCrawling 스타일 출력
            if (isFirstDate) {
                System.out.println("\n📍 " + mapping.branch + " (" + date + ")");
            }
            System.out.println(" - " + mapping.title + " : " + (availableTimes.isEmpty() ? "없음" : availableTimes));
        } catch (Exception e) {
            System.err.println("DB 저장 오류: " + e.getMessage());
        }
    }

    /**
     * 지정한 일수(days)만큼, 오늘부터 순차적으로 날짜를 돌며 예약 페이지를 파싱한다.
     */
    public void crawlReservations(int days) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            Calendar cal = Calendar.getInstance();

            for (int i = 0; i < days; i++) {
                String targetDate = sdf.format(cal.getTime());

                // 페이지 호출 (예: ...?go=rev.make&rev_days=2025-03-10&s_theme_num=)
                String url = BASE_URL + "&rev_days=" + targetDate + "&s_theme_num=";
                try {
                    org.jsoup.nodes.Document doc = Jsoup.connect(url).get();

                    // 전체 테마 파싱
                    Map<String, List<String>> themeTimeMap = parseThemeBoxes(doc);

                    // 날짜별로 첫 테마 로그 출력을 위한 플래그
                    boolean isFirstTheme = true;

                    // 우리가 관리하는 THEME_MAPPINGS 순회
                    for (ThemeMapping mapping : THEME_MAPPINGS) {
                        // HTML 상의 테마명이 대문자라 가정
                        String key = mapping.title.toUpperCase();

                        List<String> availableTimes = themeTimeMap.getOrDefault(key, Collections.emptyList());
                        saveToDatabase(mapping, targetDate, availableTimes, isFirstTheme);

                        // 한 번이라도 출력하면 false로 변경
                        isFirstTheme = false;
                    }
                } catch (Exception e) {
                    // 날짜별 에러: 페이지가 없거나 기타 오류
                    System.out.println("❌ 날짜 " + targetDate + " 파싱 오류: " + e.getMessage());
                }

                // 날짜 1일 증가
                cal.add(Calendar.DAY_OF_MONTH, 1);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 메인 실행부
     */
    public static void main(String[] args) {
        ExodusEscapeCrawling crawler = new ExodusEscapeCrawling();
        // 7일치 크롤링 예시
        crawler.crawlReservations(7);
    }
}
