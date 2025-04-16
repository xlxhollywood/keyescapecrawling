package org.example.goldenkey;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.UpdateOptions;
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

public class GoldenkeyCrawling {

    private final MongoCollection<Document> reservationCollection;

    /**
     * 매장별 URL 호출을 위한 정보
     */
    private static class StoreInfo {
        String sZizum;
        String location;
        String branch;
        StoreInfo(String sZizum, String location, String branch) {
            this.sZizum = sZizum;
            this.location = location;
            this.branch = branch;
        }
    }
    // URL 호출 시 s_zizum 값에 따른 매장 정보 (브랜드 "황금열쇠" 대상)
    private static final Map<String, StoreInfo> STORE_INFO_MAP = new HashMap<>();
    static {
        STORE_INFO_MAP.put("1", new StoreInfo("1", "대구", "동성로점"));
        STORE_INFO_MAP.put("11", new StoreInfo("11", "대구", "동성로 2호점"));
        STORE_INFO_MAP.put("5", new StoreInfo("5", "강남", "강남 (타임스퀘어)"));
        STORE_INFO_MAP.put("6", new StoreInfo("6", "강남", "강남점 (플라워로드)"));
        STORE_INFO_MAP.put("7", new StoreInfo("7", "건대", "건대점"));
    }

    /**
     * 미리 정의한 테마 매핑 정보
     */
    private static class ThemeInfo {
        int id;
        String brand;
        String location;
        String branch;
        String title;
        ThemeInfo(int id, String brand, String location, String branch, String title) {
            this.id = id;
            this.brand = brand;
            this.location = location;
            this.branch = branch;
            this.title = title;
        }
    }
    // 테이블의 항목들을 리스트로 정의
    private static final List<ThemeInfo> GOLDEN_KEY_THEME_INFO = new ArrayList<>();
    static {
        GOLDEN_KEY_THEME_INFO.add(new ThemeInfo(283, "황금열쇠", "대구", "동성로 2호점", "냥탐정 셜록켓"));
        GOLDEN_KEY_THEME_INFO.add(new ThemeInfo(284, "황금열쇠", "강남", "강남 플라워로드점", "BACK화점 (범죄)"));
        GOLDEN_KEY_THEME_INFO.add(new ThemeInfo(285, "황금열쇠", "건대", "건대점", "fl[ae]sh"));
        GOLDEN_KEY_THEME_INFO.add(new ThemeInfo(286, "황금열쇠", "건대", "건대점", "NOW HERE"));
        GOLDEN_KEY_THEME_INFO.add(new ThemeInfo(287, "황금열쇠", "대구", "동성로점", "경산 (스릴러)"));
        GOLDEN_KEY_THEME_INFO.add(new ThemeInfo(288, "황금열쇠", "대구", "동성로점", "가이아 기적의 땅"));
        GOLDEN_KEY_THEME_INFO.add(new ThemeInfo(289, "황금열쇠", "대구", "동성로점", "JAIL.O"));
        GOLDEN_KEY_THEME_INFO.add(new ThemeInfo(290, "황금열쇠", "대구", "동성로점", "타임스틸러"));
        GOLDEN_KEY_THEME_INFO.add(new ThemeInfo(291, "황금열쇠", "대구", "동성로점", "X됐다"));
        GOLDEN_KEY_THEME_INFO.add(new ThemeInfo(292, "황금열쇠", "대구", "동성로 2호점", "BAD ROB BAD"));
        GOLDEN_KEY_THEME_INFO.add(new ThemeInfo(293, "황금열쇠", "대구", "동성로 2호점", "2Ways"));
        GOLDEN_KEY_THEME_INFO.add(new ThemeInfo(294, "황금열쇠", "대구", "동성로 2호점", "LAST"));
        GOLDEN_KEY_THEME_INFO.add(new ThemeInfo(295, "황금열쇠", "대구", "동성로 2호점", "PILGRIM"));
        GOLDEN_KEY_THEME_INFO.add(new ThemeInfo(296, "황금열쇠", "대구", "동성로 2호점", "지옥 (미스터리)"));
        GOLDEN_KEY_THEME_INFO.add(new ThemeInfo(297, "황금열쇠", "대구", "동성로 2호점", "다시, 너에게"));
        GOLDEN_KEY_THEME_INFO.add(new ThemeInfo(298, "황금열쇠", "대구", "동성로 2호점", "HEAVEN"));
        GOLDEN_KEY_THEME_INFO.add(new ThemeInfo(299, "황금열쇠", "강남", "강남 플라워로드점", "ANOTHER (스릴러)"));
        GOLDEN_KEY_THEME_INFO.add(new ThemeInfo(300, "황금열쇠", "강남", "강남 타임스퀘어점", "NOMON : THE ORDEAL (판타지)"));
        GOLDEN_KEY_THEME_INFO.add(new ThemeInfo(301, "황금열쇠", "강남", "강남 타임스퀘어점", "섬 : 잊혀진 이야기 (미스터리)"));
    }

    public GoldenkeyCrawling() {
        MongoClient mongoClient = MongoConfig.getMongoClient();
        MongoDatabase database = mongoClient.getDatabase("scrd");
        this.reservationCollection = database.getCollection("reservation");
    }


    private static class ParsedTheme {
        String extractedTitle; // HTML에서 추출한 제목
        List<String> availableTimes;
        ParsedTheme(String extractedTitle, List<String> availableTimes) {
            this.extractedTitle = extractedTitle;
            this.availableTimes = availableTimes;
        }
    }


    public void crawlAllDates() {
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

        // 매장별로 순회
        for (StoreInfo store : STORE_INFO_MAP.values()) {
            // 오늘 날짜부터 7일 반복
            Calendar tempCal = (Calendar) cal.clone();
            for (int i = 0; i < 7; i++) {
                String dateStr = dateFormat.format(tempCal.getTime());

                // 해당 날짜의 페이지 HTML 요청
                String html = requestDateHtml(store.sZizum, dateStr);
                if (html == null) {
                    // 요청 실패
                    tempCal.add(Calendar.DAY_OF_MONTH, 1);
                    continue;
                }

                // JSoup으로 HTML 파싱
                List<ParsedTheme> themes = parseThemes(html);

                // 날짜별로 첫 번째 테마가 나오기 전까진 "📍 지점 (날짜)"를 출력하기 위해 사용
                boolean isFirstTheme = true;

                // 테마 목록을 순회
                for (ParsedTheme pt : themes) {
                    // 미리 정의된 ThemeInfo 매핑 찾기 (유사도 매칭)
                    ThemeInfo mapping = findThemeInfo(pt.extractedTitle, store.branch);

                    // 매핑이 없으면 임시 정보 사용
                    String finalTitle = (mapping != null) ? mapping.title : pt.extractedTitle;
                    int finalId = (mapping != null) ? mapping.id : 0;
                    String finalBrand = (mapping != null) ? mapping.brand : "황금열쇠";
                    String finalLocation = (mapping != null) ? mapping.location : store.location;
                    String finalBranch = (mapping != null) ? mapping.branch : store.branch;

                    // 첫 테마라면 "📍 지점 (yyyy-MM-dd)" 출력
                    if (isFirstTheme) {
                        System.out.println("\n📍7번쨰 트라이" + finalBranch + " (" + dateStr + ")");
                        isFirstTheme = false;
                    }

                    // 시간 출력 (없으면 "없음")
                    if (pt.availableTimes.isEmpty()) {
                        System.out.println(" - " + finalTitle + " : 없음");
                    } else {
                        System.out.println(" - " + finalTitle + " : " + pt.availableTimes);
                    }

                    // MongoDB Upsert (DB 저장)
                    saveToDatabase(finalBrand, finalLocation, finalBranch, finalTitle, finalId, dateStr, pt.availableTimes);
                }

                tempCal.add(Calendar.DAY_OF_MONTH, 1);
            }
        }

    }


    private String requestDateHtml(String sZizum, String dateStr) {
        try {
            OkHttpClient client = new OkHttpClient();
            String url = "http://xn--jj0b998aq3cptw.com/layout/res/home.php?rev_days=" + dateStr
                    + "&s_zizum=" + sZizum + "&go=rev.make";
            Request request = new Request.Builder().url(url).get().build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    System.err.println("❌ 요청 실패: " + response);
                    return null;
                }
                return response.body().string();
            }
        } catch (Exception e) {
            System.err.println("❌ requestDateHtml() 오류: " + e.getMessage());
            return null;
        }
    }


    private List<ParsedTheme> parseThemes(String html) {
        List<ParsedTheme> themeList = new ArrayList<>();
        try {
            org.jsoup.nodes.Document doc = Jsoup.parse(html);
            Elements themeBoxes = doc.select("div.theme_box");

            for (Element box : themeBoxes) {
                Element titleEl = box.selectFirst("h3.h3_theme");
                if (titleEl == null) continue;

                String extractedTitle = titleEl.text().trim();

                // ✅ 원본 제목 로그 출력
                System.out.println("🔎 원본 추출 제목 (HTML에서): \"" + extractedTitle + "\"");

                List<String> availableTimes = new ArrayList<>();
                Elements liElements = box.select("div.time_Area ul.reserve_Time li");
                for (Element li : liElements) {
                    Element aTag = li.selectFirst("a");
                    if (aTag == null) continue;
                    if (aTag.hasAttr("href") && aTag.selectFirst("span.possible") != null) {
                        Element timeEl = aTag.selectFirst("span.time");
                        if (timeEl != null) {
                            String timeText = timeEl.text().trim();
                            availableTimes.add(timeText);
                        }
                    }
                }

                themeList.add(new ParsedTheme(extractedTitle, availableTimes));
            }

        } catch (Exception e) {
            System.err.println("parseThemes() 오류: " + e.getMessage());
        }
        return themeList;
    }


    private ThemeInfo findThemeInfo(String extractedTitle, String storeBranch) {
        String normalizedExtracted = normalize(extractedTitle);
        ThemeInfo bestMatch = null;
        double bestSimilarity = 0.0;

        for (ThemeInfo info : GOLDEN_KEY_THEME_INFO) {
            String normalizedMapping = normalize(info.title);
            double similarity = jaroWinklerSimilarity(normalizedExtracted, normalizedMapping);

            // 🔍 로그 출력
            System.out.println("🟡 매칭 시도: " + extractedTitle + " → normalize: " + normalizedExtracted);
            System.out.println("    ↪️ 비교 대상: " + info.title + " → normalize: " + normalizedMapping + " → 유사도: " + similarity);

            if (similarity > bestSimilarity) {
                bestSimilarity = similarity;
                bestMatch = info;
            }
        }

        if (bestMatch != null) {
            System.out.println("✅ 선택된 매핑: " + bestMatch.title + " (id=" + bestMatch.id + "), 유사도: " + bestSimilarity);
            if (bestSimilarity >= 0.05) {
                return bestMatch;
            }
        } else {
            System.out.println("❌ 매칭 실패: " + extractedTitle);
        }

        return null;
    }




    private String normalize(String s) {
        return s.toLowerCase().replaceAll("[^가-힣a-z0-9]", "");
    }


    private double jaroWinklerSimilarity(String s, String t) {
        if (s.equals(t)) {
            return 1.0;
        }
        int sLen = s.length();
        int tLen = t.length();
        if (sLen == 0 || tLen == 0) {
            return 0.0;
        }
        int matchDistance = Math.max(sLen, tLen) / 2 - 1;
        boolean[] sMatches = new boolean[sLen];
        boolean[] tMatches = new boolean[tLen];
        int matches = 0;
        for (int i = 0; i < sLen; i++) {
            int start = Math.max(0, i - matchDistance);
            int end = Math.min(i + matchDistance + 1, tLen);
            for (int j = start; j < end; j++) {
                if (tMatches[j]) continue;
                if (s.charAt(i) != t.charAt(j)) continue;
                sMatches[i] = true;
                tMatches[j] = true;
                matches++;
                break;
            }
        }
        if (matches == 0) {
            return 0.0;
        }
        double transpositions = 0;
        int k = 0;
        for (int i = 0; i < sLen; i++) {
            if (!sMatches[i]) continue;
            while (!tMatches[k]) {
                k++;
            }
            if (s.charAt(i) != t.charAt(k)) {
                transpositions++;
            }
            k++;
        }
        transpositions /= 2.0;
        double jaro = ((double) matches / sLen +
                (double) matches / tLen +
                ((double) (matches - transpositions) / matches)) / 3.0;
        // Jaro-Winkler: 공통 접두사 최대 4자, 스케일 0.1
        int prefix = 0;
        for (int i = 0; i < Math.min(4, Math.min(sLen, tLen)); i++) {
            if (s.charAt(i) == t.charAt(i)) {
                prefix++;
            } else {
                break;
            }
        }
        return jaro + prefix * 0.1 * (1 - jaro);
    }


    private void saveToDatabase(String brand,
                                String location,
                                String branch,
                                String title,
                                int id,
                                String date,
                                List<String> availableTimes) {
        try {
            Document filter = new Document("title", title)
                    .append("date", date)
                    .append("brand", brand);

            Document docToSave = new Document()
                    .append("brand", brand)
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
            System.err.println("DB 저장 중 오류: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        GoldenkeyCrawling crawler = new GoldenkeyCrawling();
        crawler.crawlAllDates();
    }
}
