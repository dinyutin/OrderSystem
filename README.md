# Order System

以 Spring Boot 建置的高併發商品訂單服務。請求先經 API Gateway 分流到三個應用副本，再由 Redis 原子預扣庫存、Kafka 削峰排隊、MySQL 保存訂單，並透過逾時補償與 Transactional Outbox 保持最終一致。

## 核心設計

![Local architecture](docs/architecture-local.svg)

所有外部流量先進入 API Gateway。Gateway 負責 request ID、每個 client 的 Redis 限流與輪詢分流；三個 Order Service 副本共同使用 MySQL、Redis 與 Kafka。任一應用副本停止時，其餘副本仍可繼續服務。

同步 API 的庫存正確性由 MySQL 原子條件更新保證；高流量非同步 API 則先在 Redis 執行 Lua 原子預扣，再將命令依 `requestId` 分散到 Kafka 的六個 partition，由三個副本共六個 consumer 平行寫入 MySQL。

```sql
UPDATE products
SET stock = stock - :quantity
WHERE product_id = :productId
  AND stock >= :quantity;
```

非同步提交立即回傳 `202 Accepted` 與 `requestId`，前端以漸進退避查詢狀態，避免固定高頻輪詢形成第二波流量。Kafka 無法接收時會回補 Redis；consumer 最終失敗則進入 DLT 並執行補償。付款成功、失敗與五分鐘未付款自動過期都會留下 lifecycle event。

## 從原版到目前版本

| 原版問題 | 目前處理方式 |
|---|---|
| Producer 只送 `orderId`，consumer 卻期待 `orderId:productId` | 統一使用 `OrderCreatedEvent` JSON schema |
| Consumer 再扣一次庫存、再建立一次訂單 | 庫存與訂單只在 MySQL transaction 修改一次；consumer 不碰核心資料 |
| Kafka 非同步 send 被當成已成功 | 訂單 transaction 同時寫入 outbox；relay 等待 broker ack，失敗保留並重試 |
| Redis 或 Kafka 失敗可能讓已成功訂單回 500 | 外部依賴移出 HTTP transaction；Redis 失敗 fallback MySQL，Kafka 由 outbox 補送 |
| 客戶端逾時重送可能重複扣庫存 | 支援 `Idempotency-Key`，相同 key 回傳既有訂單 |
| 固定 Redis TTL 可能同時大量失效 | TTL 隨機分散於 25～35 分鐘 |
| 無過載隔離 | 訂單 bulkhead 限制同時執行數，超過容量快速回傳 `429` |
| 無 consumer 死信處理 | 指數退避重試後送至 `order-created.DLT` |
| 尖峰請求直接占用 HTTP 與 DB 連線 | Redis 預扣後送 Kafka，HTTP 快速回 `202`，consumer 依系統能力平行消化 |
| 同商品訊息全部落在單一 Kafka partition | 改用 `requestId` 作為 key，平均分散到六個 partition |
| 固定 100 ms 輪詢放大流量 | 改為 0.5 秒起跳的漸進退避，最高每 2 秒查詢一次 |
| 動態 URL 造成 Prometheus 高基數 | k6 將 requestId、orderId 路徑統一成固定 metric name |
| 無 schema 管理、測試與可觀測性 | Flyway、JUnit、k6、Prometheus、Grafana 與容錯 metrics |

## 技術

- Java 17、Spring Boot 3.4
- Spring Web、Validation、Data JPA
- MySQL 8、Flyway
- Redis
- Apache Kafka
- Actuator、Prometheus metrics
- JUnit 5、Mockito
- Spring Cloud Gateway、LoadBalancer
- Docker Compose、Kubernetes、HPA、PDB
- React、Nginx、k6 測試控制介面

## 啟動

需求：Docker 與 Docker Compose。

```bash
docker compose up -d --build
```

確認服務：

```bash
curl http://localhost:8080/actuator/health
```

停止服務：

```bash
docker compose down
```

若要同時移除本機資料庫 volume：

```bash
docker compose down -v
```

## API

### 建立商品

```bash
curl -i -X POST http://localhost:8080/api/products \
  -H 'Content-Type: application/json' \
  -d '{"name":"Keyboard","stock":100}'
```

### 查詢商品與 cache 庫存

```bash
curl http://localhost:8080/api/products/1
curl http://localhost:8080/api/products/1/stock
```

### 建立訂單

```bash
curl -i -X POST http://localhost:8080/api/orders \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: checkout-20260818-0001' \
  -d '{"productId":1,"quantity":1}'
```

成功回傳 `201 Created`；相同 `Idempotency-Key` 重送會取得同一筆訂單且不會再次扣庫存；商品不存在回傳 `404`；庫存不足回傳 `409`；bulkhead 滿載回傳 `429`。

### 非同步提交、查詢與付款

```bash
# 快速排入 Kafka
curl -i -X POST http://localhost:8080/api/orders/requests \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: async-20260819-0001' \
  -d '{"productId":1,"quantity":1}'

# 使用回傳的 requestId 查詢排隊結果
curl http://localhost:8080/api/orders/requests/{requestId}

# 狀態為 RESERVED 後完成付款
curl -X POST http://localhost:8080/api/orders/{orderId}/payments \
  -H 'Content-Type: application/json' \
  -d '{"result":"SUCCESS"}'
```

完整狀態流程為 `QUEUED → RESERVED → COMPLETED`。五分鐘內未付款會轉為 `EXPIRED` 並回補庫存；Kafka 最終處理失敗會標為 `FAILED` 並補償 Redis 預扣數量。

### 查詢訂單

```bash
curl http://localhost:8080/api/orders/{orderId}
```

## 測試

```bash
./mvnw test
```

測試涵蓋：

- 訂單建立與查詢服務
- 原子扣庫存成功後建立訂單及發布事件
- 商品不存在與庫存不足
- 商品名稱 setter regression

## Prometheus 與 Grafana

啟動應用與監控 profile：

```bash
docker compose --profile observability up -d --build
```

| 服務 | 位址 |
|---|---|
| Gateway health | http://localhost:8080/actuator/health |
| Order Service 1 / 2 / 3 | http://localhost:8082 / 8083 / 8084 |
| Prometheus | http://localhost:9091 |
| Grafana | http://localhost:3001（`admin` / `admin`） |

Grafana 會自動載入 `Order System Load Test` dashboard，包含：

- 訂單 API 每秒請求數與 HTTP status
- p95 latency
- 成功與拒絕訂單 business metrics
- JVM heap、CPU 與 Hikari connection pool
- Outbox pending/dead backlog、Redis fallback 與 bulkhead 拒絕數

另外保留獨立的 `k6 Load Testing Results` dashboard，顯示壓測端的即時 VUs、每秒請求數、HTTP failure、checks pass rate、平均／最大／中位數／最小延遲、p90、p95 與延遲趨勢。k6 透過 Prometheus Remote Write 傳送 metrics，原本的伺服器端 dashboard 不會被取代。

### 同一組測試的改前／改後

條件固定為 1,000 VUs、10,000 筆完整訂單流程（提交、等待保留、模擬付款）。改前與改後都保留 k6 用戶端視角及服務端視角，共四張 Grafana 圖。

| 視角 | 改前 | 改後 |
|---|---|---|
| k6：VUs、RPS、錯誤率與回應時間 | ![Current before k6 dashboard](docs/current-before-k6.png) | ![Current after k6 dashboard](docs/current-after-k6.png) |
| 服務端：API、非同步處理、JVM、Hikari 與 CPU | ![Current before server dashboard](docs/current-before-server.png) | ![Current after server dashboard](docs/current-after-server.png) |

改前因固定高頻輪詢及高基數監控資料壓垮本機監控服務，執行 4 分 36 秒後有 9,559 筆完成；改後以 Kafka partition 分散、漸進退避與固定 metric name 完成 10,000 筆提交，其中 9,998 筆在 27.8 秒測試時間內完成，2 筆稍後由逾時機制回補庫存。送單 p95 為 156.96 ms、完整 HTTP p95 為 98.24 ms、排隊 p95 為 4.16 秒，HTTP 失敗率為 0.04%。詳細數據見 [`docs/load-test-results.md`](docs/load-test-results.md)。

## k6 高併發測試

先啟動應用與監控，再執行一次性的 k6 profile：

```bash
docker compose --profile observability up -d --build
docker compose --profile loadtest up -d --build frontend loadtest-controller
```

開啟 http://localhost:5173，在「負載測試」區塊輸入使用者數與訂單數後即可執行。介面會顯示執行狀態、測試摘要及 Grafana 連結；Grafana 的 `k6 Load Testing Results` 顯示負載端 metrics，`Order System Load Test` 顯示 Gateway、應用與基礎設施 metrics。

預設壓測設定為 1,000 VUs、共 10,000 筆訂單。k6 會建立庫存為 10,000 的獨立商品，並驗證：

- 非同步提交回傳 `202`
- 至少 99.9% 訂單在測試期限內完成
- 送單 p95 小於 2 秒、排隊 p95 小於 30 秒
- 未付款保留單會在五分鐘後過期並回補庫存

腳本位於 `load-test/async-checkout-load.js`，測試期間可直接在 Grafana 觀察延遲、吞吐、JVM 與連線池變化。

可透過環境變數覆寫規模，例如：

```bash
VUS=200 TOTAL_ORDERS=2000 docker compose --profile loadtest run --rm \
  k6 run --out experimental-prometheus-rw /scripts/async-checkout-load.js
```

## 設定

所有外部連線皆可透過環境變數覆寫：

| 變數 | 預設值 |
|---|---|
| `DB_URL` | `jdbc:mysql://localhost:3306/order_system...` |
| `DB_USERNAME` | `order_app` |
| `DB_PASSWORD` | `order_password` |
| `REDIS_HOST` | `localhost` |
| `REDIS_PORT` | `6379` |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` |

資料表由 Flyway migration 建立，JPA 使用 `ddl-auto=validate` 驗證 schema。

## 故障行為與防雪崩

| 故障 | 系統行為 |
|---|---|
| Redis 停止 | 讀取回退 MySQL，寫 cache 失敗只記錄 metric/log；訂單仍正常建立 |
| Kafka 停止 | 訂單與 outbox 一起 commit；relay 指數退避，Kafka 恢復後補送 |
| Consumer 持續失敗 | 指數退避重試，超過期限送入 `order-created.DLT` |
| 瞬間請求超量 | bulkhead 快速回 `429`，避免 HTTP threads 和 DB pool 被拖垮 |
| 大量 cache 同時過期 | Redis TTL 加入 25～35 分鐘 jitter，降低同時回源 MySQL 的尖峰 |
| 用戶端重試 | `Idempotency-Key` 防止重複訂單與重複扣庫存 |

MySQL 是唯一 source of truth。Outbox delivery 採 at-least-once，因此正式下游 consumer 仍必須以 `orderId` 做冪等；DLT 也需要監控及人工／自動重放流程。

Docker Compose 的壓測環境將 `ORDER_MAX_CONCURRENT` 提高為 1,000，以測試 1,000 VUs 全數完成；正式部署應依 CPU、DB pool 與壓測容量下修，而不是無限制放大。

壓測環境的三個應用副本各配置 Hikari pool 30（合計最多 90）、connection timeout 10 秒，讓超過資料庫即時容量的請求短暫排隊，而不是在 1 秒內回傳 500；正式值需配合 MySQL `max_connections` 與實際服務副本數計算。

## Gateway、負載平衡與 Kubernetes

![Kubernetes architecture](docs/architecture-kubernetes.svg)

本機 Docker Compose 會啟動 1 個 Gateway 與 3 個 Order Service；Kubernetes 版本使用 2 個 Gateway、3 個 Order Service，並以 Service 做負載平衡。HPA 可依 CPU 將 Order Service 從 3 個擴到 10 個副本，PDB 在節點維護時至少保留 2 個可用副本，readiness probe 不會把未就緒 Pod 放入流量路徑。

完整啟動、驗證分流、限流、故障切換、kind 部署與清理指令請看 [`docs/local-and-kubernetes-runbook.md`](docs/local-and-kubernetes-runbook.md)。文件也逐項說明 Pod、Deployment、Service、Probe、HPA、PDB、PVC、RBAC、Prometheus discovery、完整流量路徑與排錯方式。

### 故障演練

```bash
# Redis 掛掉：API 仍應從 MySQL 回應
docker compose stop redis
curl http://localhost:8080/api/products/1
docker compose start redis

# Kafka 掛掉：訂單仍回 201，outbox_pending 上升；恢復後下降為 0
docker compose stop kafka
curl -i -X POST http://localhost:8080/api/orders \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: kafka-failure-demo' \
  -d '{"productId":1,"quantity":1}'
docker compose start kafka
```
