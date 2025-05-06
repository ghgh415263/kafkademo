package org.example;

import org.apache.kafka.clients.producer.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;

public class ProducerMain {

    private static final Logger log = LoggerFactory.getLogger(ProducerMain.class.getName());
    public static void main(String[] args) {

        Properties props = new Properties();
        props.put("bootstrap.servers", "127.0.0.1:9092"); // Kafka 브로커 주소 (local 카프카에 연결)
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        // 배치사이즈나 파티셔너 지정가능

        Producer<String, String> producer = new KafkaProducer<>(props); // 설정을 통해서 생산자 생성

        for (int i=0; i<200; i++) {
            ProducerRecord<String, String> record = new ProducerRecord<>("dev_topic", "test"+i);  // key가 없는 메시지 생성
            //ProducerRecord<String, String> record = new ProducerRecord<>("dev_topic", "test2");  // key가 있으면 같은 파티션으로 들어감.

            producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    System.out.println("Error sending message: " + exception.getMessage());
                }
                log.info("topic: {}      offset: {}      partition: {}      timestamp={}", metadata.topic(), metadata.offset(), metadata.partition(), metadata.timestamp());
            });
            /* producer 쓰레드
             * 1. main 쓰레드 (Producer의 호출 스레드)
             *      Kafka Producer의 send() 메서드를 호출하는 쓰레드입니다.
             *      이 쓰레드는 메시지를 전송하는 요청을 담당하고, 비동기적으로 처리되기 때문에, 전송이 완료될 때까지 기다리지 않습니다.
             *      send() 호출 후, 콜백을 통해 전송 결과를 받을 수 있습니다.
             * 2. Sender 쓰레드
             *      Kafka Producer 내부에서 메시지 전송을 담당하는 주요 쓰레드입니다.
             *      sender 스레드는 RecordAccumulator에 저장된 배치들을 모니터링합니다. RecordAccumulator는 파티션 별로 배치를 모은 자료구조입니다.
             *      sender 스레드는 이 배치들이 **batch.size**에 도달하거나, linger.ms 시간이 경과하는 등 전송 가능한 조건이 되면, 해당 배치를 Kafka 브로커로 전송합니다.
             *      기본적으로 1개의 Sender 쓰레드가 생성됩니다.
             *
             *
             *
             * max.in.flight.requests.per.connection 값이 5일 때, sender 스레드는 동시에 전송할 수 있는 배치 수가 5개로 제한되므로, 그 이상의 배치가 전송 중이라면 추가적인 배치는 대기하게 됩니다.
             *
             * Topic-A에 2개의 파티션이 있다고 가정: Partition-0: 메시지 5개, Partition-1: 메시지 7개
             * 각 파티션에 대해 프로듀서는 배치 크기(예: batch.size = 16384 바이트)에 맞춰서 메시지를 모읍니다.
             * Partition-0의 메시지가 16KB에 도달하면 배치가 전송됩니다.
             * Partition-1도 마찬가지로 16KB를 채우면 전송됩니다.
             * 여러 파티션의 배치는 병렬로 전송되지만, 각 파티션별로 독립적인 배치로 전송됩니다.
             */

            /*
             * Kafka Producer의 배치(batch)
             *
             * 1. send() 메서드를 호출하면, 메시지가 RecordAccumulator에 추가되어 배치로 묶입니다. 그리고 future 받음
             * 2. RecordAccumulator는 이 배치를 batches 큐에 저장하고, 이를 관리합니다. (RecordAccumulator는 batches라는 Map을 가지고 있는데, 이 Map의 Key는 TopicPartition이고, Value는 Deque<RecordBatch>이다.)
             * 3. 큐에 저장된 배치는 전송 조건이 만족되면 브로커로 전송됩니다.
             *      배치 크기(batch.size): 배치가 설정된 크기를 초과하면 전송됩니다.
             *      대기 시간(linger.ms): 배치가 설정된 시간 동안 완전히 채워지지 않아도, 기다린 후 전송됩니다.
             */

            /** 콜백이 호출되는 시점
             * 1.send() 호출
             * 메시지는 RecordAccumulator에 추가되어 배치로 묶여 큐에 저장됩니다.
             * Producer는 이 배치를 준비하고, 브로커에 배치를 전송합니다.
             *
             * 2.브로커에 배치 전송
             * Producer는 메시지를 포함하는 배치를 브로커에 전송합니다.
             * acks 설정에 따라, 브로커에서 전송 확인이 이루어지며, acks=all이면 모든 복제본에 메시지가 기록될 때까지 기다리게 됩니다.
             *
             * 3.브로커의 응답 처리
             * 브로커가 메시지를 받고 처리한 후 응답을 Producer로 보냅니다. 이 응답은 메시지가 성공적으로 처리되었는지 아니면 오류가 발생했는지를 포함합니다.
             * 브로커가 응답을 보내면, Sender thread는 그 응답을 받아 콜백 함수를 호출합니다.
             *
             * 4.콜백 호출
             * 응답을 받은 후, 콜백이 호출됩니다.
             * 만약 전송이 성공했다면, callback.onCompletion(null, metadata)가 호출됩니다.
             * 전송에 실패한 경우에는 callback.onCompletion(exception, null) 형태로 호출되며, 예외 객체가 전달됩니다.
             */

        }

        producer.flush();
        // 현재 쌓인 모든 메시지를 보내고 전송완료까지 대기함.

        producer.close();
    }
}
