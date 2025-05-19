package org.example.signescape;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.UpdateOptions;
import okhttp3.OkHttpClient;
import org.bson.Document;
import org.example.config.MongoConfig;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.text.SimpleDateFormat;
import java.util.*;

public class SignescapeCrawling {

    private final MongoCollection<Document> reservationCollection;
    private final OkHttpClient client = new OkHttpClient();
    private final Set<String> processedDates = new HashSet<>(); // 중복 실행 방지

    private static class ThemeMapping {
        String themeCode;
        String title;
        int id;
        ThemeMapping(String themeCode, String title, int id) {
            this.themeCode = themeCode;
            this.title = title;
            this.id = id;
        }
    }

    private static class BranchMapping {
        String branchCode;
        String location;
        String branch;
        List<ThemeMapping> themes;
        BranchMapping(String branchCode, String location, String branch, List<ThemeMapping> themes) {
            this.branchCode = branchCode;
            this.location = location;
            this.branch = branch;
            this.themes = themes;
        }
    }

    private static final List<BranchMapping> BRANCH_MAPPINGS = new ArrayList<>();
    static {
        BRANCH_MAPPINGS.add(new BranchMapping("S6", "강남", "강남시티점", Arrays.asList(
                new ThemeMapping("A", "러너웨이", 171),
                new ThemeMapping("C", "EXPRESS", 173),
                new ThemeMapping("B", "MUST", 172)
        )));
        BRANCH_MAPPINGS.add(new BranchMapping("S5", "홍대", "홍대점", Arrays.asList(
                new ThemeMapping("A", "거상", 179),
                new ThemeMapping("B", "졸업", 181),
                new ThemeMapping("C", "하이팜", 180)
        )));
        BRANCH_MAPPINGS.add(new BranchMapping("S4", "수원", "인계점", Arrays.asList(
                new ThemeMapping("D", "신비의 베이커리", 183),
                new ThemeMapping("C", "악은 어디에나 존재한다", 185),
                new ThemeMapping("B", "트라이 위저드", 184),
                new ThemeMapping("E", "GATE : CCZ (episode 1)", 182),
                new ThemeMapping("A", "NEW", 186)
        )));
        BRANCH_MAPPINGS.add(new BranchMapping("S2", "수원", "성대역점", Arrays.asList(
                new ThemeMapping("B", "각성(Awakening)", 175),
                new ThemeMapping("E", "고시텔(3층)", 178),
                new ThemeMapping("A", "우울증(Depression)", 174),
                new ThemeMapping("C", "인턴(Intern)", 176),
                new ThemeMapping("D", "자멜신부의 비밀", 177)
        )));
    }

    public SignescapeCrawling() {
        MongoClient mongoClient = MongoConfig.getMongoClient();
        MongoDatabase database = mongoClient.getDatabase("scrd");
        this.reservationCollection = database.getCollection("reservation");
    }

    private Map<String, List<String>> fetchBranchData(BranchMapping branchMapping, String dateStr) {
        Map<String, List<String>> branchData = new LinkedHashMap<>();
        for (ThemeMapping themeMapping : branchMapping.themes) {
            try {
                String url = "http://www.signescape.com/sub/sub03_1.html?R_JIJEM="
                        + branchMapping.branchCode
                        + "&chois_date=" + dateStr
                        + "&R_THEMA=" + themeMapping.themeCode
                        + "&DIS_T=";
                org.jsoup.nodes.Document doc = Jsoup.connect(url).get();
                Elements timeElements = doc.select("div#reser4 ul.list li.timeOn");
                List<String> availableTimes = new ArrayList<>();
                for (Element timeEl : timeElements) {
                    String timeText = timeEl.text().replace("☆", "").trim();
                    availableTimes.add(timeText);
                }
                branchData.put(themeMapping.title, availableTimes);
                saveToDatabase("싸인 이스케이프", branchMapping.location, branchMapping.branch,
                        themeMapping.title, themeMapping.id, dateStr, availableTimes);
            } catch (Exception e) {
                System.err.println("fetchAndStore() 오류: " + e.getMessage());
            }
        }
        return branchData;
    }

    private void saveToDatabase(String brand, String location, String branch, String title, int id, String date, List<String> availableTimes) {
        try {
            Document filter = new Document("title", title)
                    .append("date", date)
                    .append("brand", brand);
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


    public void crawlFromToday(int numDays) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            Calendar cal = Calendar.getInstance();
            for (int i = 0; i < numDays; i++) {
                String currentDate = sdf.format(cal.getTime());
                if (processedDates.contains(currentDate)) {
                    continue;
                }
                for (BranchMapping branchMapping : BRANCH_MAPPINGS) {
                    Map<String, List<String>> branchData = fetchBranchData(branchMapping, currentDate);
                    if (!branchData.isEmpty()) {
                        System.out.println("\n📍 " + branchMapping.branch + " (" + currentDate + ")");
                        for (Map.Entry<String, List<String>> entry : branchData.entrySet()) {
                            System.out.println(" - " + entry.getKey() + " : " + (entry.getValue().isEmpty() ? "없음" : entry.getValue()));
                        }
                    }
                }
                processedDates.add(currentDate);
                cal.add(Calendar.DAY_OF_MONTH, 1);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        SignescapeCrawling crawler = new SignescapeCrawling();
        crawler.crawlFromToday(7);
    }
}