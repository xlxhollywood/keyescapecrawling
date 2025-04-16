package org.example.fantastrick;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.UpdateOptions;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.bson.Document;
import org.example.config.MongoConfig;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.text.SimpleDateFormat;
import java.util.*;

public class FantastrickCrawling {

    private final MongoCollection<Document> reservationCollection;

    private static final String BRAND = "판타스트릭";
    private static final String LOCATION = "강남";
    private static final String BRANCH = "강남점";

    private final Map<String, Map<String, Map<String, List<String>>>> finalMap;


    private static class ThemeInfo {
        String calendarId;
        String title;
        int id;
        ThemeInfo(String calendarId, String title, int id) {
            this.calendarId = calendarId;
            this.title = title;
            this.id = id;
        }
    }

    private static final Map<String, ThemeInfo> THEME_INFO_MAP = new LinkedHashMap<>();
    static {
        THEME_INFO_MAP.put("사자의 서 : 북오브 두아트", new ThemeInfo("23", "사자의 서 : 북오브 두아트", 243));
        THEME_INFO_MAP.put("태초의 신부 : 이브 프로젝트", new ThemeInfo("17", "태초의 신부 : 이브 프로젝트", 242));

    }

    public FantastrickCrawling() {
        MongoClient mongoClient = MongoConfig.getMongoClient();
        MongoDatabase database = mongoClient.getDatabase("scrd");
        this.reservationCollection = database.getCollection("reservation");

        // 최종 결과를 담는 구조
        this.finalMap = new LinkedHashMap<>();
    }

    private String convertTo24HourFormat(String time) {
        if (time.contains("오전")) {
            return time.replace("오전 ", "");
        } else if (time.contains("오후")) {
            String[] parts = time.replace("오후 ", "").split(":");
            int hour = Integer.parseInt(parts[0]) + 12;
            if (hour == 24) hour = 12; // 오후 12시는 그대로 12 유지
            return hour + ":" + parts[1];
        }
        return time; // 변환 실패 시 원본 유지
    }

    public void crawlAllDates() {

        Calendar cal = Calendar.getInstance();
        SimpleDateFormat dateAjaxFmt = new SimpleDateFormat("yyyy-MM-d");
        SimpleDateFormat dateStoreFmt = new SimpleDateFormat("yyyy-MM-dd");

        for (int i = 0; i < 7; i++) {
            // 날짜 문자열
            String dateStrAjax = dateAjaxFmt.format(cal.getTime());
            String dateStrStore = dateStoreFmt.format(cal.getTime());
            Map<String, Map<String, List<String>>> branchThemeMap = new LinkedHashMap<>();
            branchThemeMap.put(BRANCH, new LinkedHashMap<>());

            for (Map.Entry<String, ThemeInfo> entry : THEME_INFO_MAP.entrySet()) {
                String themeKey = entry.getKey();
                ThemeInfo info = entry.getValue();

                String html = requestDateHtml(info.calendarId, dateStrAjax);
                if (html == null) {
                    System.out.println("   - HTML 응답이 null. 요청 실패 -> 없음 처리");
                    branchThemeMap.get(BRANCH).put(info.title, Collections.emptyList());
                    saveToDB(info, dateStrStore, Collections.emptyList());
                } else {
                    List<String> availableTimes = parseAvailableTimes(html);
                    if (availableTimes.isEmpty()) {
                        branchThemeMap.get(BRANCH).put(info.title, Collections.emptyList());
                        saveToDB(info, dateStrStore, Collections.emptyList());
                    } else {
                        branchThemeMap.get(BRANCH).put(info.title, availableTimes);
                        saveToDB(info, dateStrStore, availableTimes);
                    }
                }
            }

            finalMap.putIfAbsent(dateStrStore, new LinkedHashMap<>());
            finalMap.get(dateStrStore).putAll(branchThemeMap);

            cal.add(Calendar.DAY_OF_MONTH, 1);
        }

        printFinalResults();
    }

    private String requestDateHtml(String calendarId, String dateStr) {
        try {
            OkHttpClient client = new OkHttpClient();
            FormBody formBody = new FormBody.Builder()
                    .add("action", "booked_calendar_date")
                    .add("date", dateStr)       // ex "2025-03-2"
                    .add("calendar_id", calendarId)
                    .build();

            Request request = new Request.Builder()
                    .url("http://fantastrick.co.kr/wp-admin/admin-ajax.php")
                    .post(formBody)
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    System.err.println("   - 요청 실패: " + response);
                    return null;
                }
                return response.body().string();
            }
        } catch (Exception e) {
            System.err.println("   - requestDateHtml() 오류: " + e.getMessage());
            return null;
        }
    }

    private List<String> parseAvailableTimes(String html) {
        List<String> availableTimes = new ArrayList<>();
        try {
            org.jsoup.nodes.Document doc = Jsoup.parse(html);
            Elements timeSlots = doc.select(".timeslot.bookedClearFix");

            for (Element slot : timeSlots) {
                // 시간 (ex "오전 11:30", "오후 9:40")
                Element timeEl = slot.selectFirst(".timeslot-range");
                if (timeEl == null) continue;
                String timeText = timeEl.text().trim();

                // 24시간 형식으로 변환
                String convertedTime = convertTo24HourFormat(timeText);

                // spots-available => "예약완료" or "예약가능" ...
                Element spotEl = slot.selectFirst(".spots-available");
                String spotText = (spotEl != null) ? spotEl.text().trim() : "";

                // 버튼 disabled => 예약불가
                Element btnEl = slot.selectFirst(".new-appt.button");
                boolean isDisabled = (btnEl != null && btnEl.hasAttr("disabled"));

                boolean isCompleted = "예약완료".equals(spotText) || isDisabled;
                if (isCompleted) {
                    continue;
                }
                // 예약 가능
                availableTimes.add(convertedTime);
            }
        } catch (Exception e) {
            System.err.println("parseAvailableTimes() 오류: " + e.getMessage());
        }
        return availableTimes;
    }


    private void saveToDB(ThemeInfo info, String date, List<String> availableTimes) {
        try {
            Document filter = new Document("title", info.title)
                    .append("date", date)
                    .append("brand", BRAND);

            Document docToSave = new Document()
                    .append("brand", BRAND)
                    .append("location", LOCATION)
                    .append("branch", BRANCH)
                    .append("title", info.title)
                    .append("id", info.id)
                    .append("date", date)
                    .append("availableTimes", availableTimes)
                    .append("updatedAt", new Date())
                    .append("expireAt", new Date(System.currentTimeMillis() + 24L * 60L * 60L * 1000));

            Document update = new Document("$set", docToSave);
            reservationCollection.updateOne(filter, update, new UpdateOptions().upsert(true));

            // System.out.println("   >> DB 저장 완료: " + date + " / " + info.title + " / times=" + availableTimes);
        } catch (Exception e) {
            System.err.println("DB 저장 중 오류: " + e.getMessage());
        }
    }

    private void printFinalResults() {
        for (String date : finalMap.keySet()) {
            Map<String, Map<String, List<String>>> branchMap = finalMap.get(date);

            for (String branch : branchMap.keySet()) {
                System.out.println("\n📍 " + branch + " (" + date + ")");

                Map<String, List<String>> themeMap = branchMap.get(branch);
                for (Map.Entry<String, List<String>> e : themeMap.entrySet()) {
                    String themeName = e.getKey();
                    List<String> times = e.getValue();

                    if (times == null || times.isEmpty()) {
                        System.out.println(themeName + " : 없음");
                    } else {
                        System.out.println(themeName + " : " + times);
                    }
                }
            }
        }
    }

    public static void main(String[] args) {
        FantastrickCrawling crawler = new FantastrickCrawling();
        // 7일치 크롤링
        crawler.crawlAllDates();
    }
}
