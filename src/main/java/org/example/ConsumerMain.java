package org.example;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Properties;

public class ConsumerMain {

    private static final Logger log = LoggerFactory.getLogger(ProducerMain.class.getName());

    // 2개를 같은 컨슈머 그룹으로 실행하면 각각 파티션이 할당된다.

    /* Graceful Shutdown 해야하는 이유 -> 제데로된 커밋, 리소스 정리 등..
    *
    * -단계-
    * 현재 메시지 처리 완료: 종료 신호를 감지한 후, 처리 중인 메시지 작업을 완료.
    * 오프셋 커밋: 마지막으로 처리된 오프셋을 Kafka에 안전하게 커밋.
    * 컨슈머 그룹 탈퇴: 그룹 코디네이터에 Leave Group 요청을 보내 리밸런싱이 원활히 진행되도록 함.
    * 리소스 정리: 네트워크 연결, 소켓, 메모리 등 리소스 해제.
    *
    * -일어날 수 있는 문제-
    * 데이터 손실: 메시지가 처리되지 않고 손실될 위험.
    * 중복 처리: 동일한 메시지가 반복 처리되어 시스템 오류 가능성.
    * 리소스 누수: 네트워크 연결이나 메모리 누수가 발생.
    * 리밸런싱 불안정: 컨슈머 그룹의 리밸런싱 과정에서 불필요한 지연 발생.
    */
    public static void main(String[] args) {
        Properties props = new Properties();
        props.put("bootstrap.servers", "127.0.0.1:9092"); // Kafka 브로커 주소 (local 카프카에 연결)
        props.put("key.deserializer", StringDeserializer.class.getName());
        props.put("value.deserializer", StringDeserializer.class.getName());
        props.put("group.id", "group-a");
        props.put("auto.offset.reset", "earliest");  //두 번쨰로 실행하면 무시하고 이전에 저장한 offset을 따라간다.

        KafkaConsumer<String,String> consumer = new KafkaConsumer<>(props);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            consumer.wakeup(); // poll() 호출하면 WeakUpException 터지게 하는 거다.
            log.info("============= shutDownHook =================");
            try {
                Thread.currentThread().join();
            } catch (InterruptedException e) {
                log.info("====== InterruptedException ============", e);
            }
        }));

        consumer.subscribe(List.of("dev_topic"));

        try {
            while (true) {

                log.info("============= polling =================");

                // 처음 연결하면 4번의 폴링이 지나야 그떄서야 메시지를 읽어온다. -> 그룹에 조인하고 파티션을 할당 받고 처음 연결한거면 offset도 없다.
                ConsumerRecords<String,String> records = consumer.poll(Duration.ofMillis(500));

                //폴링 방식임.
                for (ConsumerRecord<String,String> record : records) {
                    log.info("key={}, value={}", record.key(), record.value());
                    log.info("partition={}, offset={}", record.partition(), record.offset());
                }

            }
        }
        catch (WakeupException e) {
            log.info("====== WakeupException ============", e);
        }
        finally {
            consumer.close(); // 자원 정리
        }
    }
}
