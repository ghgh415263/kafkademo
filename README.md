<h1>kafka</h1>

1. Bootstrap Server
   - Kafka 클러스터와 클라이언트(예: Producer나 Consumer) 간의 초기 연결을 설정하는 데 필요한 서버 정보를 제공합니다.
   - 클라이언트는 Bootstrap Server로 초기 연결을 시도하고, 클러스터의 전체 메타데이터(브로커 ID, 파티션 정보 등)를 받아옵니다.
   - bootstrap.servers=broker1:9092,broker2:9092,broker3:9092 같이 설정한다.
   - 여기서 broker1, broker2, broker3는 Kafka 브로커의 주소이며, 클라이언트는 이들 중 하나에 연결해 클러스터 메타데이터를 얻습니다.
  
2. Zookeeper
   - 분산 시스템을 위한 코디네이션 서비스로, Kafka의 초기 버전에서 메타데이터 관리와 클러스터 조정에 사용되었습니다.
   - Kafka는 Zookeeper를 통해 클러스터의 상태를 공유하고, Producer와 Consumer가 필요한 메타데이터를 가져가게끔 합니다
   - 브로커 관리 : Kafka 클러스터의 브로커 상태(등록 및 해제)를 추적
   - 파티션 리더 정보 저장 : 파티션마다 어떤 브로커가 리더인지 메타데이터 관리
   - 컨트롤러 선출 : Kafka 클러스터에서 컨트롤러 브로커를 선출
   - ACL 관리 : Kafka 클러스터의 인증 및 권한 관리
   - Kafka의 2.8.0 이후 버전에서는 KRaft라는 자체 분산 코디네이션 메커니즘이 도입되었습니다. 현재는 점점 없어지는 추세.
  
3. Topic
   - 어떤 데이터 형태든 저장할 수 있는 특정 데이터 스트림 (메시지의 시퀀스)
   - db의 테이블과 비슷한 개념
   - topic name으로 구분지어짐
   - producer로 특정 토픽에 데이터를 넣고 consumer로 토픽에서 데이터를 읽는다.
   - 예시 : 트럭이 카프카 서버로 자신의 위치 정보를 보냄 -> 위치 정보에는 TRUCK ID와 TRUCK POSITON에 대한 정보가 담겨있음 -> 이걸 trucks_gps 라는 토픽을 만들어서 거기에다가 적재함
  
4. Partition
   - topic은 여러개의 파티션으로 구성될 수 있다.
   - 각각의 파티션들은 각자의 순서로 정렬되어 있다.
   - 파티션들은 각자 offest이라고 부르는 incremental id가 있다.
   - offset은 재사용되지 않는다. 이전 메시지가 전부 삭제되어도...
   - 파티션에 쓰여진 데이터는 변경 불가능하다. (IMMUTABLE)
   - 데이터는 특정 기간까지는 유지한다. (디폴트 7일)
  
5. Producer
   - 토픽에 데이터를 씀
   - 카프카 메시지 형태 {key-binary, value -binary, 압축 타입 gzip 등등, 헤더 (옵셔널) 키벨류 형태, 파티션과 offset, 타임스탬프}
   - 생산자는 어느 파티션에 쓸지를 알고 있다. 즉 파티션 결정은 생산자가 한다. (프로듀서 내부에 파티셔너가 결정함)
     + key를 설정해서 보내면 해시값을 계산해서 어느 파티션에 넣을지 정함. 키가 같은 메시지는 한 파티션내에서 순서가 유지된다.
     + key를 설정하지 않으면 round robin 방식으로 파티션에 넣음
   - Recovery
     + 브로커 일시적 다운:	retries 설정을 활용하여 자동 재시도
     + 리더 브로커 장애:	메타데이터 업데이트 후 새로운 리더로 전환
     + 네트워크 장애:	delivery.timeout.ms와 request.timeout.ms로 타임아웃 설정
     + 성능 최적화:	linger.ms와 batch.size를 조정하여 메시지 배치 처리
     + 데이터 유실 방지:	acks=all 및 min.insync.replicas 설정으로 안전한 저장 보장

6. Consumer
   - 토픽에서 데이터가져옴
   - 파티션마다 읽기 offset이 있음 (같은 파티션 안에서만 순서 보장)
   - 키와 벨류를 deserializer 한다. deserializer는 컨슈머에서 설정해야하고 설정하려면 메시지 포멧에 대한 정보가 있어야 한다.
   - Recovery
     + 리더 브로커 장애:	메타데이터 업데이트 후 새로운 리더로 전환
     + Consumer 장애:	Consumer Group 리밸런싱 후 자동 복구 (session.timeout.ms와 heartbeat.interval.ms)
     + 데이터 유실 방지 : commit을 통해서 어디까지 처리했는지
    <br>
    
<h1>Consumer Group</h1>

1. Bootstrap Server
   - 같은 그룹이면 각각 다른 파티션의 데이터를 읽는다.
     + consumer1은 partition0  , consumer2은 partition1 결국, 그룹 하나는 하나의 토픽에 속한 파티션들을 전부 읽는다.
   - 컨수머 그룹의 컨슈머가 파티션보다 많으면 컨슈머 하나는 아무것도 안함
   - 하나의 토픽을 여러 컨슈머 그룹이 읽을 수 있다.
   - 그룹당 offset들을 __consumer_offsets 이라는 토픽에 저장한다.
   - 커밋 전략
     + at least once: 메시지를 처리하면 커밋
     + at most once  메시지 받으면 커밋
     + exactly once
