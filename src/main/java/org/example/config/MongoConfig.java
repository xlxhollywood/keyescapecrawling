package org.example.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.ServerApi;
import com.mongodb.ServerApiVersion;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import org.bson.Document;

import java.util.concurrent.TimeUnit;

public class MongoConfig {
    // MongoDB 연결 문자열 - MongoDB Atlas 클러스터에 연결하기 위한 URI
    private static final String CONNECTION_STRING = "mongodb+srv://admin:wntkfkd11!@cluster0.fvzvl.mongodb.net/scrd?retryWrites=true&w=majority&tls=true";

    // MongoClient 인스턴스를 관리하기 위한 정적 변수
    private static MongoClient mongoClient;

    // MongoClient를 가져오는 메서드 (싱글톤 패턴으로 구현)
    public static MongoClient getMongoClient() {
        if (mongoClient == null) { // mongoClient가 아직 생성되지 않았을 경우
            synchronized (MongoConfig.class) { // 동기화 블록으로 동시성 문제 방지
                if (mongoClient == null) { // 다시 확인 후 MongoClient 생성
                    // MongoDB 서버 API 설정 (버전 V1 사용)
                    ServerApi serverApi = ServerApi.builder()
                            .version(ServerApiVersion.V1) // MongoDB 서버 API 버전 설정
                            .build();

                    // MongoClient 설정 구성
                    MongoClientSettings settings = MongoClientSettings.builder()
                            .applyConnectionString(new ConnectionString(CONNECTION_STRING)) // 연결 문자열 적용
                            .applyToConnectionPoolSettings(builder ->
                                    builder.maxConnectionIdleTime(60, TimeUnit.SECONDS) // 연결 풀에서의 최대 유휴 시간
                            )
                            .applyToSocketSettings(builder ->
                                    builder.connectTimeout(10, TimeUnit.SECONDS) // 소켓 연결 제한 시간
                            )
                            .serverApi(serverApi) // API 설정 추가
                            .build();

                    // MongoClient 생성
                    mongoClient = MongoClients.create(settings);

                    // TTL 인덱스 생성 메서드 호출
                    createTTLIndex();

                    // 애플리케이션 종료 시 MongoClient를 닫는 작업 추가
                    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                        if (mongoClient != null) {
                            mongoClient.close(); // MongoClient 종료
                            System.out.println("MongoClient closed."); // 종료 로그 출력
                        }
                    }));
                }
            }
        }
        return mongoClient; // MongoClient 반환
    }

    // TTL(Time-To-Live) 인덱스를 생성하는 메서드
    private static void createTTLIndex() {
        // MongoDB 데이터베이스와 컬렉션에 접근
        MongoDatabase database = getMongoClient().getDatabase("scrd"); // scrd 데이터베이스 선택
        MongoCollection<Document> reservationCollection = database.getCollection("reservation"); // reservation 컬렉션 선택

        // TTL 인덱스 옵션 설정 (expireAt 필드 기준, 데이터는 expireAt 시각 이후 삭제)
        IndexOptions indexOptions = new IndexOptions().expireAfter(0L, TimeUnit.SECONDS); // 0초 이후 삭제
        reservationCollection.createIndex(new Document("expireAt", 1), indexOptions); // 인덱스 생성

        // TTL 인덱스 생성 완료 로그 출력
        System.out.println("TTL index created for 'expireAt' field.");
    }
}
