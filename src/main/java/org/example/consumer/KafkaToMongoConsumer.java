package org.example.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.*;
import com.mongodb.client.model.UpdateOptions;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.bson.Document;
import org.example.config.MongoConfig;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;

public class KafkaToMongoConsumer {

    private static final String TOPIC = "reservation_topic";
    private static final String MONGO_DATABASE = "scrd";
    private static final String MONGO_COLLECTION = "reservation_kafka";

    public static void main(String[] args) {
        // Kafka Consumer 설정
        Properties props = new Properties();
        props.put("bootstrap.servers", "43.200.236.67:9092");  // ✅ EC2 공인 IP
        props.put("group.id", "reservation-consumer-group");
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("auto.offset.reset", "earliest");

        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Collections.singletonList(TOPIC));

        MongoClient mongoClient = MongoConfig.getMongoClient();
        MongoDatabase database = mongoClient.getDatabase("scrd");
        MongoCollection<Document> collection = database.getCollection("reservation_kafka");


        ObjectMapper objectMapper = new ObjectMapper();

        System.out.println("🔵 Kafka Consumer 시작!");

        while (true) {  // finally 블록 없이 그냥 무한루프
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));

            for (ConsumerRecord<String, String> record : records) {
                try {
                    Document doc = Document.parse(record.value());

                    Document filter = new Document("title", doc.getString("title"))
                            .append("date", doc.getString("date"));
                    collection.updateOne(filter, new Document("$set", doc), new UpdateOptions().upsert(true));

                    System.out.println("✅ 저장 성공: " + doc.getString("title") + " (" + doc.getString("date") + ")");
                } catch (Exception e) {
                    System.err.println("❌ MongoDB 저장 실패: " + e.getMessage());
                }
            }
        }

    }


}
