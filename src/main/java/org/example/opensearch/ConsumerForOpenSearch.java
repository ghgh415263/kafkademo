package org.example.opensearch;

import com.google.gson.JsonParser;
import org.apache.http.HttpHost;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.errors.WakeupException;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.bulk.BulkResponse;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.client.indices.CreateIndexRequest;
import org.opensearch.client.indices.GetIndexRequest;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;

/*
 * Heartbeat는 컨슈머가 컨슈머 그룹에서 정상적으로 동작하고 있음을 Kafka 안에 Consumer Group Coordinator에 주기적으로 알리는 신호입니다.
 *
 * 컨슈머가 정상적으로 동작 중인지 체크
 * 컨슈머가 중단되거나 장애가 발생하면, 일정 시간 후 자동으로 컨슈머 그룹에서 제거됨.
 *
 * Heartbeat 관련 주요 설정값
 *  - heartbeat.interval.ms	3초	   하트비트 전송 간격
 *  - session.timeout.ms	45초   이 시간 동안 하트비트가 없으면 컨슈머 그룹에서 제거 (heartbeat.interval.ms 보다 커야함)
 *  - max.poll.interval.ms	5분	  이 시간 동안 poll() 호출이 없으면 리밸런스 발생
 */


/* Consumer Poll Thread
 * poll()의 호출 간격을 적절하게 설정하는 것이 성능에 중요하며, 소비자는 일정한 간격으로 poll()을 호출해야 리밸런스 이벤트를 처리하고 세션 타임아웃을 방지할 수 있습니다.
 *
 * 설정값
 *  - max.poll.records: 한 번에 가져올 수 있는 최대 메시지 수를 설정합니다.
 *  - fetch.max.wait.ms: 소비자가 데이터를 가져오기 위해 대기할 최대 시간을 설정합니다.   -> 데이터 없으면 빈값 리턴
 *  - max.poll.interval.ms: poll() 호출 사이의 최대 시간 간격을 설정하며, 이 시간이 초과되면 소비자는 비정상적으로 종료된 것으로 간주되어 리밸런싱이 일어납니다. -> 프로세싱이 오래걸리면 poll 사이의 간격이 커질 수 있다. -> 컨슈머가 비정상인 것으로 간주
 *  - fetch.min.bytes는 Kafka Consumer에서 데이터를 가져올 때, 최소한으로 가져올 데이터 크기를 설정하는 옵션입니다. 이 값은 소비자가 한 번의 fetch 요청에서 최소한 얼마나 많은 바이트의 데이터를 가져올 것인지를 결정합니다. (max 옵션도 있음)
 */

public class ConsumerForOpenSearch {

    private static final Logger log = LoggerFactory.getLogger(ConsumerForOpenSearch.class.getSimpleName());

    public static void main(String[] args) throws IOException {
        RestHighLevelClient openSearchClient = createOpenSearchClient();

        /* Kafka 컨슈머 하나당 기본적으로 하나의 메인 쓰레드가 실행됨(데이터 가져오는 쓰레드) 하지만
         * Auto Commit, Rebalance, heartbeat 등으로 인해 추가적인 백그라운드 쓰레드가 있을 수 있음
         * KafkaConsumer 객체 자체는 멀티 쓰레드에서 안전하지 않음 (Thread-Safe하지 않음)
         * 멀티 쓰레드 처리가 필요하면, poll()은 단일 쓰레드에서 실행하고, 메시지 처리는 별도 쓰레드에서 실행해야 함
         */
        KafkaConsumer<String, String> consumer = createKafkaConsumer();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("===========shutdown=========");
            consumer.wakeup();

            // join the main thread to allow the execution of the code in the main thread
            try {
                Thread.currentThread().join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }));

        try(openSearchClient; consumer) {

            // index 존여 여부 확인
            boolean indexExists = openSearchClient.indices().exists(new GetIndexRequest("wikimedia"), RequestOptions.DEFAULT);

            if (!indexExists) {
                CreateIndexRequest createIndexRequest = new CreateIndexRequest("wikimedia");
                openSearchClient.indices().create(createIndexRequest, RequestOptions.DEFAULT);
                log.info("The Wikimedia Index has been created!");
            } else {
                log.info("The Wikimedia Index already exits");
            }

            consumer.subscribe(Collections.singleton("wikimedia"));

            while(true) {

                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(3000));

                int recordCount = records.count();
                log.info("Received " + recordCount + " record(s)");

                BulkRequest bulkRequest = new BulkRequest();

                for (ConsumerRecord<String, String> record : records) {

                    // we extract the ID from the JSON value
                    String id = extractId(record.value());

                    IndexRequest indexRequest = new IndexRequest("wikimedia")
                            .source(record.value(), XContentType.JSON)
                            .id(id); // id를 넣으면 멱등하게 변함. 만약 중복된 id가 존재하면 update로 작동함.

                    bulkRequest.add(indexRequest);

                }

                if (bulkRequest.numberOfActions() > 0){
                    BulkResponse bulkResponse = openSearchClient.bulk(bulkRequest, RequestOptions.DEFAULT);
                    log.info("Inserted " + bulkResponse.getItems().length + " record(s).");

                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    // commit offsets after the batch is consumed
                    consumer.commitSync();
                    log.info("Offsets have been committed!");
                }
                // Kafka에서 데이터를 가져옴. OpenSearch에 Bulk API로 한 번에 여러 건 저장


            }

        } catch (WakeupException e) {
            log.error("WakeupException 발생");
        }


    }

    private static String extractId(String json){
        return JsonParser.parseString(json)
                .getAsJsonObject()
                .get("meta")
                .getAsJsonObject()
                .get("id")
                .getAsString();
    }

    public static RestHighLevelClient createOpenSearchClient() {
        String connString = "http://localhost:9200";

        RestHighLevelClient restHighLevelClient;
        URI connUri = URI.create(connString);

        restHighLevelClient = new RestHighLevelClient(RestClient.builder(new HttpHost(connUri.getHost(), connUri.getPort(), "http")));

        return restHighLevelClient;
    }

    // 컨슈머 생성
    private static KafkaConsumer<String, String> createKafkaConsumer(){

        /* Offset Reset이 필요한 상황
         *  - 새로운 컨슈머 그룹이 처음 실행될 때 (저장된 오프셋이 없기 때문에 어디서부터 읽을지 결정해야 함)
         *  - 컨슈머 그룹의 오프셋 정보가 만료되었을 때 (Kafka에서 log.retention.hours(기본 168시간 = 7일)를 초과하면 토픽의 record가 사라짐, 오프셋 자체는 Kafka의 __consumer_offsets 토픽에 저장됨)
         *
         * Offset 유의사항
         *  - Offset Reset은 "오프셋 정보가 없을 때만" 적용됨 (기존 컨슈머 그룹이 이미 오프셋을 저장하고 있다면, auto.offset.reset 값은 무시됨)
         *  - 오프셋을 강제로 변경하려면 seek_to_beginning() 사용 가능
         *  - consumer.seek(partition, offset) (오프셋을 특정 값으로 직접 설정할 수도 있음)
         *
         * Offset 옵션
         *  - earliest	가장 처음 메시지부터 읽기
         *  - latest	현재부터 생성되는 메시지부터 읽기
         *  - none	오프셋이 없으면 예외 발생
         */

        /* 1. auto commit
         *      설정한 시간만큼 지난 후 poll()을 호출하면 commit 함
         *      At Least Once랑은 같이 안쓰는 경우가 많다.
         *
         * 2. commit
         *      가져온 데이터를 다 처리하고 commit 하게 만듬.
         */


        /*  1. At Most Once
         *      컨슈머가 메시지를 가져오자마자 오프셋을 커밋합니다.
         *      이후 메시지 처리가 실패해도 다시 읽을 기회가 없음 → 데이터 유실 가능
         *      성능이 가장 빠름 (재처리 로직 불필요)
         *      금융, 결제 시스템 등에서는 부적합하지만, 로그 수집 같은 경우 적합할 수 있음
         *      auto 커밋이랑 쓰면 자동으로 특정 시간마다 커밋
         *
         *  2. At Least Once
         *      데이터 유실 없음: 메시지가 적어도 한 번 이상 처리됨
         *      중복 가능성 있음: 컨슈머가 같은 메시지를 두 번 읽을 수 있음 → Idempotent(멱등) 처리 필요   (처리하다 실패하면 커밋을 안해서 다시 읽어올 수 있으니..)
         *      재처리 가능: 장애 발생 시 마지막 커밋된 오프셋부터 다시 읽음
         *      auto랑 쓰지말고 수동 commit 해야함
         *      대부분의 경우, 이 옵션이 선호됨
         *
         *  3. Exactly Once 특징
         *      데이터 유실 없음 (At Least Once와 동일)
         *      중복 처리 없음 (At Least Once의 단점 보완)
         *      추가 설정 필요 (기본 Kafka 설정만으로는 불가능)
         *      트랜잭션 또는 Idempotent Producer 필요
         *          - Kafka 트랜잭션 사용 (Transactional Producer + Consumer) -> 프로듀서가 데이터 넣기전에 트랜잭션 얻고 커밋 -> 소비자는 read commited 옵션 사용
         *          - Idempotent Producer (멱등 프로듀서) 사용 -> Kafka가 자동으로 중복된 메시지를 감지하고 제거 -> 컨슈머는 At Least Once나 혹은 다른 방법으로 멱등하게 만들어야함. (여기서는 opensearch id를 사용했다. 일반적으로는 merge하는 방식 같은 게 있다.)
         */

        /*
        * Kafka에서 record가 멱등하려면 producer만 멱등하면 됨 그러나 전체 처리 과정이 멱등해야 한다면 consumer 측의 로직도 멱등하게 작성해야 함
        */
        String groupId = "consumer-opensearch-demo";

        Properties properties = new Properties();
        properties.setProperty(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, "127.0.0.1:9092");
        properties.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        properties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

        return new KafkaConsumer<>(properties);
    }
}
