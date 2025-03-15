package org.example.portraiteller;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.UpdateOptions;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.bson.Document;
import org.example.config.MongoConfig;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

public class PortraitellerCrawling {

    private final MongoCollection<Document> reservationCollection;
    private final OkHttpClient client = new OkHttpClient();

    // 매핑 정보 클래스: 캘린더별로 저장할 필드들을 정의
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
    // 캘린더별 매핑 (키: API URL에 사용되는 캘린더 이름)
    private static final Map<String, ThemeMapping> CALENDAR_MAPPING = new HashMap<>();
    static {
        CALENDAR_MAPPING.put("lesportrait", new ThemeMapping(204, "초상화", "강남", "강남점", "Les portrait"));
        CALENDAR_MAPPING.put("hiraeth", new ThemeMapping(205, "초상화", "강남", "강남점", "Hiraeth"));
    }

    public PortraitellerCrawling() {
        MongoClient mongoClient = MongoConfig.getMongoClient();
        MongoDatabase database = mongoClient.getDatabase("scrd");
        this.reservationCollection = database.getCollection("reservation");
    }

    /**
     * API에서 가져온 데이터를 MongoDB에 upsert 방식으로 저장합니다.
     * 저장 구조는 다음과 같습니다.
     * { brand, location, branch, title, id, date, availableTimes, updatedAt, expireAt }
     */
    private void saveToDatabase(String brand, String location, String branch, String title, int id, String date, List<String> availableTimes) {
        try {
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
            System.err.println("DB 저장 오류: " + e.getMessage());
        }
    }

    /**
     * 지정한 캘린더와 날짜를 기반으로 API를 호출하여 예약 데이터를 가져오고, DB에 저장합니다.
     * @param calendar API에 사용할 캘린더 이름 ("lesportrait" 또는 "hiraeth")
     * @param dateParam 날짜 파라미터 (예: "2025-03-01")
     */
    private void fetchAndStore(String calendar, String dateParam) {
        try {
            String encodedTimeZone = URLEncoder.encode("Asia/Seoul", StandardCharsets.UTF_8);
            String baseUrl = "https://api-prod.whattime.co.kr/api/meeting/reservations/calendars/" + calendar + "/slots";
            // sync 파라미터 true로 설정
            String sync = "true";
            String url = baseUrl + "?slug=portraiteller&date=" + dateParam
                    + "&sync=" + sync
                    + "&time_zone=" + encodedTimeZone;
            Request request = new Request.Builder().url(url).build();
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String jsonResponse = response.body().string();
                    JSONObject jsonObject = new JSONObject(jsonResponse);
                    ThemeMapping mapping = CALENDAR_MAPPING.get(calendar);
                    // 날짜별로 API 응답을 처리 (키가 날짜)
                    for (String key : jsonObject.keySet()) {
                        if (key.equals("info")) continue;
                        JSONArray slotsArray = jsonObject.getJSONArray(key);
                        List<String> availableTimes = new ArrayList<>();
                        for (int i = 0; i < slotsArray.length(); i++) {
                            JSONObject slot = slotsArray.getJSONObject(i);
                            // 시작 시간(start_hour)만 저장
                            String startHour = slot.getString("start_hour");
                            availableTimes.add(startHour);
                        }
                        // 출력 형식: 헤더와 테마 라인
                        System.out.println("\n📍 " + key);
                        if (availableTimes.isEmpty()) {
                            System.out.println(" - " + mapping.title + " : 없음");
                        } else {
                            System.out.println(" - " + mapping.title + " : " + availableTimes);
                        }
                        saveToDatabase(mapping.brand, mapping.location, mapping.branch, mapping.title, mapping.id, key, availableTimes);
                    }
                } else {
                    System.err.println("API 요청 실패: " + response);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 지정한 시작 날짜부터 numDays 일간의 데이터를 크롤링합니다.
     * @param startDateStr 시작 날짜 (yyyy-MM-dd 형식)
     * @param numDays 일수
     */
    public void crawlDateRange(String startDateStr, int numDays) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            Calendar cal = Calendar.getInstance();
            cal.setTime(sdf.parse(startDateStr));
            for (int i = 0; i < numDays; i++) {
                String currentDate = sdf.format(cal.getTime());
                // 두 캘린더 모두에 대해 API 호출 후 저장
                fetchAndStore("lesportrait", currentDate);
                fetchAndStore("hiraeth", currentDate);
                cal.add(Calendar.DAY_OF_MONTH, 1);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        PortraitellerCrawling crawler = new PortraitellerCrawling();
        // 현재 날짜를 "yyyy-MM-dd" 형식으로 구해서, 7일간 크롤링하도록 함
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String todayStr = sdf.format(new Date());
        crawler.crawlDateRange(todayStr, 1);
    }
}
