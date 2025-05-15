package org.example.config;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Properties;

public class KafkaProducerConfig {

    private static KafkaProducer<String, String> producer;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    static {
        Properties props = new Properties();

        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "43.200.236.67:9092");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        producer = new KafkaProducer<>(props);
    }

    public static void send(String topic, Object data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            producer.send(new ProducerRecord<>(topic, json));
        } catch (Exception e) {
            System.err.println("Kafka 전송 실패: " + e.getMessage());
        }
    }

    public static void close() {
        producer.close();
    }
}
