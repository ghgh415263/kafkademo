<h1>kafka</h1>

1. 브로커
   - 카프카 클러스터는 여러개의 브로커 (서버)
   - 각각의 브로커는 id가 있다
   - 컨슈머나 프로듀서가 어느 브로커든 연결되면 나머지 클러스터의 브로커들과 연결된다.
   - 최소 추천 브로커는 3개 -> 백개 넘개 만들 수 도 있다
   - 토픽의 파티션들은 broker들에 나뉜다 =>  파티션과 브로커가 많아질 수록 여러 곳에 나뉜다.
![image](https://github.com/user-attachments/assets/8c222cbf-2dd5-4f4d-bf42-ea493e64c6bb)
<br><br>


2. Bootstrap Server
   - Kafka 클러스터와 클라이언트(예: Producer나 Consumer) 간의 초기 연결을 설정하는 데 필요한 서버 정보를 제공합니다.
   - 클라이언트는 Bootstrap Server로 초기 연결을 시도하고, 클러스터의 전체 메타데이터(브로커 ID, 파티션 정보, 토픽 정보 등)를 받아옵니다.
   - bootstrap.servers=broker1:9092,broker2:9092,broker3:9092 같이 설정한다.
   - 여기서 broker1, broker2, broker3는 Kafka 브로커의 주소이며, 클라이언트는 이들 중 하나에 연결해 클러스터 메타데이터를 얻습니다.
![image](https://github.com/user-attachments/assets/2a33fbf5-22e5-4088-acb4-b8159bf64ee0)


3. Zookeeper
   - 분산 시스템을 위한 코디네이션 서비스로, Kafka의 초기 버전에서 메타데이터 관리와 클러스터 조정에 사용되었습니다.
   - Kafka는 Zookeeper를 통해 클러스터의 상태를 공유하고, Producer와 Consumer가 필요한 메타데이터를 가져가게끔 합니다
   - 카프카에 변경사항이 있으며 브로커들에게 notify한다. (브로커 추가, 토픽 추가, 토픽 삭제 , 브로커 사망 등등)
   - 브로커 관리 : Kafka 클러스터의 브로커 상태(등록 및 해제)를 추적
   - 파티션 리더 정보 저장 : 파티션마다 어떤 브로커가 리더인지 메타데이터 관리
   - 컨트롤러 선출 : Kafka 클러스터에서 컨트롤러 브로커를 선출
   - ACL 관리 : Kafka 클러스터의 인증 및 권한 관리
   - Kafka의 2.8.0 이후 버전에서는 KRaft라는 자체 분산 코디네이션 메커니즘이 도입되었습니다. 현재는 점점 없어지는 추세.

4. Zookeeper 기반 vs KRaft(Kafka Raft) 기반
   - Kafka Controller는 클러스터를 관리하는 중앙 노드이며, Zookeeper를 사용할 수도 있고 KRaft 방식을 사용할 수도 있음.
   - Zookeeper 기반 Kafka (기존 방식)
     + Zookeeper가 클러스터의 메타데이터를 관리
     + 브로커 중 하나가 Controller로 선출되어 메타데이터 변경을 담당
     + 만약 컨트롤러가 다운되면 Zookeeper가 새로운 컨트롤러를 선출
     + 단점: Zookeeper와의 통신이 필요하여 복잡성이 증가하고 지연 시간이 발생
   - KRaft
     + Zookeeper 없이 Kafka 내부에서 컨트롤러를 관리
     + 여러 개의 Controller 노드가 존재하며, 이 중 하나가 Quorum Leader(최고 관리자)가 됨. [controller1, controller2, broker1, broker2]
     + Raft Consensus 알고리즘을 사용하여 다수결 방식으로 클러스터 상태를 동기화.
     + 장점: Zookeeper 없이 Kafka만으로 클러스터를 관리하므로 더 빠르고 안정적.
<br>

<h1>Topic & Partition</h1>

1. Topic
   - 어떤 데이터 형태든 저장할 수 있는 특정 데이터 스트림 (메시지의 시퀀스)
   - db의 테이블과 비슷한 개념
   - topic name으로 구분지어짐
   - producer로 특정 토픽에 데이터를 넣고 consumer로 토픽에서 데이터를 읽는다.
   - 예시 : 트럭이 카프카 서버로 자신의 위치 정보를 보냄 -> 위치 정보에는 TRUCK ID와 TRUCK POSITON에 대한 정보가 담겨있음 -> 이걸 trucks_gps 라는 토픽을 만들어서 거기에다가 적재함

2. Topic 복제
   - factor는 보통 2,3으로 설정
   - 하나의 파티션이 리더와 isr로 나뉘어 각각 다른 브로커에 저장됨
   - 리더를 가진 브로커가 죽으면 다른 브로커의 isr을 리더로 바꿈
   - 카프카 2.4부터는 프로듀서는 leader에 쓰고 컨슈머는 isr(팔로워)에서 읽는 형태도 등장했다.  만약 leader보다 isr에서 읽는 게 더 효율적이면 사용.
![image](https://github.com/user-attachments/assets/f7d87325-58df-4043-9a44-fad8bc0e528f)


3. Partition
   - topic은 여러개의 파티션으로 구성될 수 있다.
   - 각각의 파티션들은 각자의 순서로 정렬되어 있다.
   - 파티션들은 각자 offest이라고 부르는 incremental id가 있다.
   - offset은 재사용되지 않는다. 이전 메시지가 전부 삭제되어도...
   - 파티션에 쓰여진 데이터는 변경 불가능하다. (IMMUTABLE)
   - 데이터는 특정 기간까지는 유지한다. (디폴트 7일)

4. Partitioner
   - DefaultPartitioner (기본값)	Key가 있으면 해시 기반, 없으면 Round Robin	키 기반 순서 유지, 부하 분산	특정 키에 트래픽 집중 가능
   - UniformStickyPartitioner	키 없는 메시지를 특정 파티션에 일정량 묶어서 전송	배치 최적화, 순서 유지 가능	특정 파티션 과부하 가능
   - Custom Partitioner	비즈니스 로직에 따라 직접 파티션 지정	로드 밸런싱 최적화 가능	구현이 복잡할 수 있음


<br>

<h1>Producer</h1>

1. Producer
   - 토픽에 데이터를 씀
   - 카프카 메시지 형태 {key-binary, value -binary, 압축 타입 gzip 등등, 헤더 (옵셔널) 키벨류 형태, 파티션과 offset, 타임스탬프}
   - 생산자는 어느 파티션에 쓸지를 알고 있다. 즉 파티션 결정은 생산자가 한다. (프로듀서 내부에 파티셔너가 결정함)
     + key를 설정해서 보내면 해시값을 계산해서 어느 파티션에 넣을지 정함. 키가 같은 메시지는 한 파티션내에서 순서가 유지된다.
     + key를 설정하지 않으면 round robin 방식으로 파티션에 넣음. 현재는 sticky 방식을 사용함.
   - Recovery
     + 브로커 일시적 다운:	retries 설정을 활용하여 자동 재시도
     + 리더 브로커 장애:	메타데이터 업데이트 후 새로운 리더로 전환
     + 네트워크 장애:	delivery.timeout.ms와 request.timeout.ms로 타임아웃 설정
     + 성능 최적화:	linger.ms와 batch.size를 조정하여 메시지 배치 처리
     + 데이터 유실 방지:	acks=all 및 min.insync.replicas 설정으로 안전한 저장 보장

2. 데이터 전송
   - properties로 연결할 카프카에 대한 정보 설정
   - send로 데이터를 보내면 알아서 배치로 보냄 -> 어느정도 데이터를 모아서 보낸다.
   - flush하면 데이터를 보낸다. close 직전에 해줘야함.

3. Retry
   - Producer가 메시지를 브로커에 전송할 때 실패하면 자동으로 재시도
   - 메시지 전송 실패 시 즉시 재시도하지 않고 retry.backoff.ms 만큼 대기
   - retries 횟수만큼 재시도 후에도 실패하면 예외 발생 (TimeoutException)
   - acks=all을 사용하면 리더 브로커가 변경된 경우에도 재시도 가능
   - retries 값이 크면 중복 메시지가 전송될 가능성이 있음 → Idempotence 설정 (enable.idempotence=true) 추천
   - max.in.flight.requests.per.connection 값이 너무 크면 순서 보장이 어려울 수 있음

<br>

<h1>Consumer</h1>

1. Consumer
   - 토픽에서 데이터가져옴
   - 파티션마다 읽기 offset이 있음 (같은 파티션 안에서만 순서 보장)
   - 키와 벨류를 deserializer 한다. deserializer는 컨슈머에서 설정해야하고 설정하려면 메시지 포멧에 대한 정보가 있어야 한다.
   - Recovery
     + 리더 브로커 장애:	메타데이터 업데이트 후 새로운 리더로 전환
     + Consumer 장애:	Consumer Group 리밸런싱 후 자동 복구 (session.timeout.ms와 heartbeat.interval.ms)
     + 데이터 유실 방지 : commit을 통해서 어디까지 처리했는지
      
2. Consumer Group
   - 같은 그룹이면 각각 다른 파티션의 데이터를 읽는다.
     + consumer1은 partition0  , consumer2은 partition1 결국, 그룹 하나는 하나의 토픽에 속한 파티션들을 전부 읽는다.
   - 컨수머 그룹의 컨슈머가 파티션보다 많으면 컨슈머 하나는 아무것도 안함
   - 하나의 토픽을 여러 컨슈머 그룹이 읽을 수 있다.
   - 그룹당 offset들을 __consumer_offsets 이라는 토픽에 저장한다.
   - 커밋 전략
     + at least once: 메시지를 처리하면 커밋
     + at most once  메시지 받으면 커밋
     + exactly once

3. Static Group Membership
   - 일시적인 네트워크 장애 또는 컨슈머 재시작 시에도 불필요한 리밸런싱이 발생하면 성능이 저하될 수 있음
   - 컨슈머가 재시작되거나 일시적으로 연결이 끊어져도 기존 파티션 할당을 유지할 수 있음
   - 기본 Dynamic Group Membership
     + 컨슈머가 poll()을 호출하면 그룹에 참여 (Group Coordinator가 관리).
     + 컨슈머가 세션 타임아웃(session.timeout.ms) 내에 응답하지 않으면 제거되고 리밸런싱 발생.
     + 컨슈머가 다시 연결되면 새로운 멤버로 등록되며 리밸런싱이 다시 발생.
   - Static Group Membership
     + 컨슈머가 그룹에 참여할 때 고유한 멤버 ID (group.instance.id)를 설정.
     + 컨슈머가 일시적으로 연결이 끊겨도 Group Coordinator가 기존 멤버로 유지.
     + 같은 멤버 ID를 가진 컨슈머가 다시 연결되면 기존 파티션 할당 유지.
     + 완전히 새로운 컨슈머가 추가되거나, 기존 멤버 ID가 없는 컨슈머만 리밸런싱 발생.

4. Retry
   - 오프셋을 Commit하지 않고 재처리
     + 자동 커밋을 비활성화 (enable.auto.commit=false)
     + 예외 발생 시 오프셋을 Commit하지 않으면 같은 메시지를 다시 소비할 수 있음
     + 무한 루프 발생 가능 → 실패한 메시지만 계속 가져올 수 있음
   - Dead Letter Queue (DLQ) 사용
     + Kafka에서 실패한 메시지를 별도의 토픽(DLQ)으로 보내고, 나중에 재처리하는 방법.
     + 무한 루프 방지책이다
<br>

<h1>Command</h1>

1. topic
   - 토픽 생성 (파티션 설정 가능, replica 갯수 설정 가능 -> 다만 브로커가 1개면 replica 갯수 설정 불가능)
   - 토픽 리스트 보기
   - 특정 토픽 상세보기 -> 파티션과 레플리카 들이 나옴, 몇번 브로커에 있는지도 나옴
   - 토픽 삭제 가능

2. producer
   - 메시지 보내기 (ack 옵션 사용가능)
   - 없는 토픽으로 연결해서 메시지보내면 타임아웃남 -> 특정 옵션에서는 리더가 없다는 경고가 나오고 토픽이 자동생성됨
   - 파티셔너를 설정할 수 있다. 예를들어 roundrobin (prd에서는 쓰면 안됨)

3. Consumer
   - 이전 메시지 전부 확인하도록 설정할 수 도 있고 앞으로 오는 메시지들만 확인하게 할 수도 있다
   - 메시지의 키, 벨류, 타임스탬프, 파티션 정보를 같이 나오게 consume 할 수 있다.
   - 현재 컨슈머 그룹을 볼 수 있다.
   - 컨슈머 그룹 상세를 볼 수 있다. 파티션마다 offset 정보가 다 나옴, 연결된 컨슈머들 정보도 나오고    lag도 나옴 (마지막 메시지 오프셋 - 먹은 메시지 오프셋)
   - 특정 컨슈머그룹의 오프셋을 초기화 혹은 변경할 수 있다. (--reset-offsets)

4. 임시 실행결과 보기
   - `--reset-offsets` 옵션으로 실행
   - Kafka Consumer 오프셋을 재설정하려면 `--reset-offsets`와 함께 적절한 옵션을 사용합니다. 이 명령은 데이터를 다시 읽거나 건너뛰도록 설정하는 데 영향을 미치므로, 적용 전 `--dry-run`으로 확인하는 것이 좋습니다.
<br>

<h1>Kafka Connect</h1>

1. 역할
   - Kafka와 외부 시스템(데이터베이스, 파일 시스템, 클라우드 서비스 등)을 쉽게 연결할 수 있도록 도와주는 프레임워크
   - 코드를 작성하지 않고 다양한 데이터 소스를 연동
   - 대량의 데이터를 안정적으로 처리할 수 있도록 병렬 처리 및 오류 복구 기능을 제공
   - 이미 저장된 데이터를 카프카로 활용하려고 할때 사용한다.

2. 컴포넌트
   - Source Connector (소스 커넥터)
     + 외부 시스템 → Kafka로 데이터를 가져오는 역할
     + 예: MySQL, PostgreSQL, MongoDB, S3, HTTP API 등의 데이터를 Kafka로 전송
   - Sink Connector (싱크 커넥터)
     + Kafka → 외부 시스템으로 데이터를 전송하는 역할
     + 예: Kafka 데이터를 MySQL, Elasticsearch, HDFS, S3 등에 저장

3. 케넉트 클러스터
   - 여러개의 커넥터로 구성되어 있음.
   - Worker: Kafka Connect 프로세스 (Connector 실행 담당). Standalone 모드에서는 1개의 Worker, Distributed 모드에서는 여러 개의 Worker가 클러스터로 동작
   - Connector: 데이터 소스 ↔ Kafka 연결 (Source/Sink)
   - Task: Connector 내부에서 병렬 처리되는 작업
![image](https://github.com/user-attachments/assets/085dafab-a941-4824-a14d-3782781b43aa)

4. 실행 (예시 Elasticsearch)
   - Kafka + Zookeeper 실행 중
   - Elasticsearch 실행 중
   - Kafka Connect 실행 가능
   - Kafka Connect Elasticsearch 플러그인 설치 후 connect에 등록
<br>

<h1>Kafka Streams</h1>

1. 역할
   - Kafka 내에서 실시간으로 데이터(스트림)를 처리하고 변환할 수 있도록 도와주는 분산형 스트리밍 애플리케이션을 개발할 때 사용
   - 실시간 스트리밍 처리: Kafka에서 지속적으로 데이터를 읽고 처리하여 빠른 실시간 데이터 변환 및 분석 가능.
   - Kafka와 강력한 통합: Kafka의 Producer & Consumer 역할을 동시에 수행하며, 별도의 메시지 큐 없이 직접 데이터를 처리 가능.
   - Streams API 제공: 데이터를 변환, 필터링, 그룹화하는 등의 처리를 위한 다양한 API 제공 (map(), filter(), groupBy(), join(), windowing() 등).

2. 동작
   - Kafka Streams는 Producer + Consumer 역할을 동시에 수행하며, 토픽(Topic)으로부터 데이터를 가져와 가공한 후, 다시 Kafka 토픽에 저장하는 방식으로 동작

3. 예시
   - 데이터 통계치: 데이터를 읽어와서 SUM이나 COUNT 토픽에 결과값을 넣는다.
   - 실시간 로그 분석
   - IoT 센서 데이터 처리
<br>

<h1>Kafka Schema Registry</h1>

1. 역할
   - Kafka 메시지의 데이터 구조(Schema)를 중앙에서 관리하는 서비스 (카프카와 같이 쓰는 독립된 서비스) -> 이것을 통해서 카프카는 데이터의 형태에 대해서 신경쓰지 않고 데이터를 저장한다.
   - Producer와 Consumer가 같은 데이터 구조(Schema)를 공유하도록 강제할 수 있음
   - 새로운 필드 추가 등 Schema 변경 시에도 호환성 유지 가능
   - Producer가 잘못된 데이터 형식을 보낼 경우, Schema Registry에서 거부하여 데이터 무결성을 유지
   - API를 통해 Schema 등록, 업데이트, 버전 관리 가능

2. 동작
   - Producer가 데이터를 Kafka로 전송 -> 메시지를 보내기 전에 Schema Registry에서 Schema ID 확인 -> 데이터를 Schema에 맞춰 직렬화(Serialize) 후 Kafka로 전송
   - Schema Registry가 Schema 저장 및 관리 -> 새로운 Schema가 등록되면 버전 관리 -> 기존 Schema와 비교하여 호환성 검사
   - Consumer가 데이터를 읽고 역직렬화(Deserialize) -> Kafka에서 받은 데이터를 Schema Registry에서 가져온 Schema를 이용해 변환
  
3. 장점
   - Kafka 브로커에 부하를 줄이기 위해 -> Schema를 Kafka 내부에서 관리하면 브로커가 부담을 더 가지게 됨 -> Schema Registry를 분리하면 Kafka 브로커의 성능을 유지할 수 있음.
   - 독립적인 Schema 관리 가능 -> Kafka 이외의 시스템에서도 Schema를 사용할 수 있음 -> 예를 들어, Kafka가 아닌 다른 데이터베이스나 API 서비스에서도 Schema Registry를 활용 가능.
