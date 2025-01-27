package org.example;

import org.apache.kafka.clients.producer.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class.getName());
    public static void main(String[] args) {

        Properties props = new Properties();
        props.put("bootstrap.servers", "127.0.0.1:9092"); // Kafka 브로커 주소 (local 카프카에 연결)
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        // 배치사이즈나 파티셔너 지정가능

        Producer<String, String> producer = new KafkaProducer<>(props); // 설정을 통해서 생산자 생성

        ProducerRecord<String, String> record = new ProducerRecord<>("dev_topic", "test2");  // key가 없는 메시지 생성
        //ProducerRecord<String, String> record = new ProducerRecord<>("dev_topic", "test2");  // key가 있으면 같은 파티션으로 들어감.

        producer.send(record, (metadata, exception) -> {
            if (exception != null) {
                System.out.println("Error sending message: " + exception.getMessage());
            }
            log.info("topic: {}      offset: {}      partition: {}      timestamp={}", metadata.topic(), metadata.offset(), metadata.partition(), metadata.timestamp());
        });
        // 읽을려면 bin\windows\kafka-console-consumer.bat --bootstrap-server localhost:9092 --topic dev_topic --group my_consumer_group --from-beginning 사용.
        // 배치 크기만큼 쌓이면 카프카로 보낸다.
        // 시간을 설정하여 일정 시간마다 쌓인 메시지들을 보낼 수도 있다.
        // 비동기 함수임.
        // 콜백을 줘서, 카프카에 전송시 어떤 걸할지 설정할 수 있다.

        producer.flush();
        // 현재 쌓인 모든 메시지를 보내고 전송완료까지 대기함.

        producer.close();
    }
}