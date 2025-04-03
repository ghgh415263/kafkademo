package org.example;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.CooperativeStickyAssignor;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Properties;

public class ConsumerMainAssignment {

    private static final Logger log = LoggerFactory.getLogger(ProducerMain.class.getName());

    public static void main(String[] args) {
        Properties props = new Properties();
        props.put("bootstrap.servers", "127.0.0.1:9092");
        props.put("key.deserializer", StringDeserializer.class.getName());
        props.put("value.deserializer", StringDeserializer.class.getName());
        props.put("group.id", "group-a");
        props.put("auto.offset.reset", "earliest");

        props.put("partition.assignment.strategy", CooperativeStickyAssignor.class.getName());
        // 컨슈머가 하나 더 추가되면 파티션 하나면 revoke한다. -> 나머지 파티션에서는 데이터를 읽는다.
        /*
        *  리밸런싱 전략
        *  1. Eager : 모든 컨슈머가 자신에게 할당된 파티션을 모두 반환(revoke) -> 재할당 (재할당 시간동안 모든 데이터처리 중단.) -> 재할당은 랜덤하다. Consumer1이 partition0을 처리했었더라도 다시 재할당 받는 것은 보장할 수 없다.
        *     RangeAssignor (기본값, 토픽별 연속된 파티션을 그룹별로 할당)
        *     RoundRobinAssignor (순차적으로 파티션을 고르게 분배)
        *     StickyAssignor (이전 할당을 최대한 유지하면서 할당)
        *
        *  2. Incremental Cooperative : 기존 파티션 할당을 유지하면서, 필요한 파티션만 재분배 -> 리밸런싱 중에도 데이터 처리가 계속 이루어질 수 있어, 중단 시간을 최소화 -> 가장 권장되는 방법
        *     CooperativeStickyAssignor (StickyAssignor에서 발전된 것것)
        */

        /**
         * Kafka Consumer의 주요 내부 쓰레드
         *
         * Application Thread → poll()을 실행하는 메인 쓰레드
         * Heartbeat Thread → 컨슈머 그룹 유지
         * Coordinator Communication Thread → 리밸런싱 및 오프셋 커밋 처리
         *
         *
         * Kafka Consumer는 thread safe하지 않다.
         * 내부적으로 하나의 TCP 연결과 세션을 유지하기 때문에 여러 스레드에서 동시에 접근하면 Race Condition이 발생
         *
         */

        KafkaConsumer<String,String> consumer = new KafkaConsumer<>(props);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            consumer.wakeup();
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

                ConsumerRecords<String,String> records = consumer.poll(Duration.ofMillis(500));  // 기본 auto.commit.interval.ms = 5000로 설정되어 있어서, poll하면 타이머시작 5초이상이 지나고 poll가 또 호출되면 async로 오토 커밋

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
