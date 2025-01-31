package org.example.wikimedia;

import com.launchdarkly.eventsource.EventSource;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;

import java.net.URI;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class WikimediaProducer {
    public static void main(String[] args) throws InterruptedException {

        Properties props = new Properties();
        props.put("bootstrap.servers", "127.0.0.1:9092");
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");

        /* safe producer 설정하는 법 (kafka 3.0 미만 버전에만 safe 모드가 아니다.)
         * 1. ack = all
         * 2. min.insync.replicas = 2
         * 3. enable.idempotence=true      요청1을 보내고 ack가 정상적으로 오다가 소실되면 retry되는데... 그걸 kafka가 알아채고 바로 ack 다시 날려줌
         * 4. retries=Max
         * 5. delivery.timeout.ms=120000
         * 6. max.in.flight.requests.per.connection=5    ack를 받지 않고 한번에 보낼 수 있는 최대 메시지 배치의 갯수
         */
        props.put("enable.idempotence", "true");
        props.put("acks", "all");
        props.put("retries", Integer.MAX_VALUE);

        Producer<String, String> producer = new KafkaProducer<>(props);

        //이 객체는 클라이언트가 서버와 지속적으로 연결을 유지하면서 서버에서 전송하는 이벤트를 수신 -> 수신한 데이터를 카프카에 보냄
        EventSource eventSource = new EventSource.Builder(new WikimediaEventHandler("wikimedia", producer), URI.create("https://stream.wikimedia.org/v2/stream/recentchange"))
                .build();

        /* 생산 ack 옵션
         * ack = 0 : ack 안기다림 (데이터 손실) -> 브로커가 잠깐 끊기면 데이터 손실 -> 당연히 프로듀싱 성능이 매우 좋음
         *
         * ack = 1 : leader의 ack만 기다림 (제한된 데이터 손실) -> 카프카 2.8까지는 디폴트옵션이였음 -> leader의 ack가 안오면 producer는 retry -> 데이터를 받은 leader가 잠깐 끊기면 replica들은 데이터를 못받는다
         *
         * ack = all : leader와 repilca ack 다 기다림 -> 카프카 3부터는 이게 디폴트옵션 -> leader에게 데이터 보내면 leader가 replica에게 데이터 보냄 -> replica에게 ack를 받은 leader가 최종 ack를 보냄
         *             min.insync.replicas=1 하면 leader만 받으면 바로 ack, min.insync.replicas=2 하면 leader와 replica 최소1개가 받으면 ack (만약 replica factor가 3일때는 2개에만 저장하면 되니.. 파티션을 가진 브로커 하나가 죽는건 걍 넘어간다.)
         *             가장 추천하는건 replica factor=3 + min.insync.replicas=2
         */
        eventSource.start();

        TimeUnit.SECONDS.sleep(120);
    }
}
