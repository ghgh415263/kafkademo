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

                /*
                Consumer가 poll()을 호출하면 Broker는 해당 Consumer가 구독한 파티션에서 데이터를 찾음.
                가져올 데이터가 있으면 즉시 반환 (이 경우 fetch.min.bytes 조건도 충족해야 함).
                가져올 데이터가 없으면 Broker는 일정 시간 동안 대기 (fetch.max.wait.ms 설정에 따라).
                기다리는 동안 새로운 메시지가 오면 즉시 반환하고, 설정된 시간이 지나도 데이터가 없으면 빈 응답을 반환.
                */
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
    /**
     * 1. Kafka 브로커와 연결
     * KafkaConsumer 객체를 생성하고 subscribe() 또는 assign()을 호출하면 Kafka 브로커와 연결을 수립합니다.
     * 컨슈머는 GroupCoordinator (Kafka 브로커 내 리더 역할)에게 컨슈머 그룹 가입 요청을 보냅니다.
     * 그룹 ID가 설정된 경우 컨슈머 그룹 관리에 따라 파티션을 할당받습니다.
     * 주기적으로 하트비트(heartbeat)를 전송하여 그룹이 유지됩니다.
     *
     * 2. 파티션 할당 (Partition Assignment)
     * 같은 그룹 ID를 가진 여러 KafkaConsumer가 존재할 경우, Kafka는 파티션을 컨슈머들에게 분배합니다.
     * Rebalance 과정을 통해 컨슈머가 추가되거나 제거될 때 동적으로 파티션을 다시 분배합니다.
     *
     * 3. 메시지 가져오기 (poll())
     * poll()을 주기적으로 호출하지 않으면 컨슈머가 죽은 것으로 간주되어 리밸런스가 발생합니다.
     * 컨슈머는 poll() 메서드를 주기적으로 호출하여 Kafka 브로커에서 배치(batch) 단위로 메시지를 가져옵니다.
     * 가져오는 데이터는 ConsumerRecords 객체에 저장됩니다.
     *
     * 4. 메시지 처리
     * 가져온 ConsumerRecords는 컨슈머 애플리케이션이 처리합니다.
     * 비즈니스 로직에 따라 메시지를 가공하거나 데이터베이스에 저장하는 등의 작업을 수행합니다.
     *
     * 5. 오프셋 커밋 (Offset Commit)
     * Kafka는 메시지를 가져간 컨슈머가 어디까지 읽었는지를 **오프셋(offset)**으로 관리합니다.
     * enable.auto.commit=true이면 Kafka가 자동으로 커밋하지만, 보통 수동 커밋을 사용하여 안정성을 확보합니다.
     *
     * 6. 컨슈머 종료 및 리밸런스
     * 컨슈머가 종료되거나 장애가 발생하면, Rebalance가 발생하여 다른 컨슈머가 해당 파티션을 다시 할당받습니다.
     *
     *
     */
}
