package org.example.wikimedia;

import com.launchdarkly.eventsource.EventSource;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.example.ProducerMain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/* 파티셔너 설정
 * 1. key 있을때, 같은 파티션으로 보내진다. 다만 토픽의 파티션이 증가하면 같은 키여도 같은 파티션으로 가는 것을 보장해주지 않는다. (key.hashCode() % numPartitions로 파티션 결정)
 * 2. key 없을떄, 카프카 2.3이하에서는 라운드 로빈, 2.4부터는 sticky
 *               one batch per partition이라 라운드 로빈 방식은 여러 개의 작은 배치가 만들어 진다. 보내는 데이터가 5개고 파티션 갯수가 5개면 배치가 5개가 만들어질 수 있다.
 *               sticky는 배치를 파티션0부터 꽉꽉 채우는 방식이므로 배치 한방에 가능하다.
 */

public class WikimediaProducerHighThroughput {

    private static final Logger log = LoggerFactory.getLogger(ProducerMain.class.getName());
    public static void main(String[] args) throws InterruptedException {

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "127.0.0.1:9092");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);



        /* 데이터 압축 (한 메시지 배치를 압축해서 브로커로 보낸다.)
         * 1. 더 적은 데이터량으로 보냄
         * 2. 카프카 디스크를 더 적게 차지한다.
         * 3. producer나 consumer는 압축에 대한 cpu 사용량이 증가한다.
         * 4. prd에서는 무조건 사용해라
         * 5. compression.type = producer or none or gzip 등등 producer의 경우 producer가 압축해서 보낸다고 가정해서 동작하는 것이다. 만약 gzip으로 압축 포맷을 주면 브로커에서 압축포맷을 확인하고 gzip이 아니면 브로커가 다시 압축한다. (브로커 cpu사용량 증가)
         */

        /* Smart Batching이 적용되는 주요 시점
         * 동일한 파티션으로 가는 메시지를 모아 하나의 배치로 묶음.          batch.size
         * 배치 크기나 대기 시간(linger.ms) 조건이 충족되었을 때 메시지를 전송.     linger.ms
         * 배치는 파티션 당으로 묶으니.. 너무 많이 모아서 보내게하면 메모리를 많이 잡아먹는다.
         */
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, Integer.toString(32 * 1024));
        props.put(ProducerConfig.LINGER_MS_CONFIG, "20");
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");

        Producer<String, String> producer = new KafkaProducer<>(props);

        EventSource eventSource = new EventSource.Builder(new WikimediaEventHandler("topic_dev", producer), URI.create("https://stream.wikimedia.org/v2/stream/recentchange"))
                .build();

        eventSource.start();

        TimeUnit.SECONDS.sleep(120);
    }
}
