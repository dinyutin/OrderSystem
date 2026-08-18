# 本機與 Kubernetes 操作手冊

本文件涵蓋本機啟動、Gateway 分流、限流、高流量測試、服務故障切換，以及 kind Kubernetes 部署。所有指令都在專案根目錄執行。

## 1. 架構與連接埠

外部請求只進入 `localhost:8080` 的 Gateway。Gateway 加入 `X-Request-Id`，以 `X-Client-Id` 做 Redis token-bucket 限流，再把 `/api/**` 輪詢分配給三個 Order Service。

| 元件 | 本機位址 | 用途 |
|---|---|---|
| Gateway | `localhost:8080` | 唯一 API 入口 |
| Order Service 1～3 | `localhost:8082`～`8084` | 三個應用副本 |
| MySQL | `localhost:3306` | 商品、訂單、outbox 的唯一真實來源 |
| Redis | `localhost:6379` | cache 與 Gateway 限流 bucket |
| Kafka | `localhost:9092` | 訂單事件 |
| Prometheus | `localhost:9091` | metrics 收集 |
| Grafana | `localhost:3001` | dashboard，帳密 `admin/admin` |

## 2. Docker Compose 本機啟動

需求為 Docker Desktop。啟動完整環境：

```bash
docker compose --profile observability up -d --build
docker compose ps
curl -i http://localhost:8080/actuator/health
```

`200 OK` 代表 Gateway 可用。直接呼叫 API 時建議帶上 client ID：

```bash
curl -i http://localhost:8080/api/products/1 -H 'X-Client-Id: local-user-1'
```

回應中的 `X-Order-Instance` 顯示實際處理請求的副本，`X-Request-Id` 可串起同一請求的 log。

## 3. 驗證負載平衡

```bash
docker compose --profile loadtest run --rm k6 run /scripts/load-balance-test.js
```

輸出會依序出現 `order-app-1`、`order-app-2`、`order-app-3`。三者反覆出現代表 Gateway 正在輪詢分流，而不是永遠打到單一副本。

## 4. 驗證 Gateway 限流

先暫時把單一 client 的速率改成每秒 5 個、突發容量 10 個：

```bash
export GATEWAY_RATE_LIMIT=5 GATEWAY_RATE_BURST=10
docker compose up -d --force-recreate gateway
curl --retry 30 --retry-delay 1 --retry-all-errors --fail http://localhost:8080/actuator/health
docker compose --profile loadtest run --rm k6 run /scripts/rate-limit-test.js
```

測試必須看到 `gateway_429` 大於 0，且檢查全部通過。測完恢復預設容量：

```bash
unset GATEWAY_RATE_LIMIT GATEWAY_RATE_BURST
docker compose up -d --force-recreate gateway
```

Gateway 是入口保護，Order Service 的 bulkhead 是第二層保護。前者限制單一來源的速率，後者限制每個副本同時進行的訂單工作，避免 HTTP thread 與資料庫連線被耗盡。

## 5. 一千個 VU、共一萬筆訂單

```bash
docker compose --profile loadtest run --rm k6
```

k6 會先建立庫存 10,000 的獨立商品，再以 1,000 VUs 送出 10,000 筆訂單。完成條件為訂單全數 `201`、p95 小於 3 秒、最後庫存剛好為 0。每個 VU 使用不同 `X-Client-Id`，模擬 1,000 個獨立使用者通過 Gateway。

測試期間開啟 `http://localhost:3001`，選擇最近 15 分鐘並等待資料穩定後截圖。舊截圖不會被覆蓋，方便前後比對。

## 6. 驗證副本故障切換

```bash
docker compose stop app1
docker compose --profile loadtest run --rm k6 run /scripts/load-balance-test.js
docker compose start app1
```

停止 `app1` 後，請求仍應由另外兩個副本處理。若 Gateway 在短時間內尚未移除失效節點，個別請求可能失敗；正式環境由 Kubernetes readiness probe 與 Service endpoints 更快地排除未就緒 Pod。

Redis 停止時庫存讀取回退 MySQL；Kafka 停止時訂單與 outbox 仍一起寫入 MySQL，broker 恢復後 relay 補送。MySQL 目前是單點，本機組態不等於正式高可用資料庫。

## 7. kind Kubernetes 本機部署

需求：Docker Desktop、`kubectl`、`kind`，建議 Docker 至少配置 8 GB 記憶體。

先建置映像並建立叢集：

```bash
docker build -t order-system:local .
docker build -t order-gateway:local gateway
kind create cluster --name order-system --config k8s/kind-config.yaml
kind load docker-image order-system:local order-gateway:local --name order-system
```

套用所有資源並等待啟動：

```bash
kubectl apply -k k8s
kubectl -n order-system rollout status deployment/mysql --timeout=180s
kubectl -n order-system rollout status deployment/redis --timeout=180s
kubectl -n order-system rollout status deployment/kafka --timeout=180s
kubectl -n order-system rollout status deployment/order-service --timeout=300s
kubectl -n order-system rollout status deployment/order-gateway --timeout=180s
kubectl -n order-system rollout status deployment/prometheus --timeout=180s
kubectl -n order-system rollout status deployment/grafana --timeout=180s
kubectl -n order-system get pods,service,hpa,pdb
curl -i http://localhost:8080/actuator/health
```

kind 將 host `8080` 映射到 Gateway 的 NodePort `30080`，並將 host `3001` 映射到 Grafana 的 NodePort `30300`。因此 API 與 Grafana 位址都和 Docker Compose 相同。Grafana 會自動載入 dashboard；Prometheus 透過 Kubernetes API 自動發現帶有 scrape annotation 的 Gateway 與 Order Pod，新擴出的 Pod 不需手動加入 target。

## 8. Kubernetes 的流量與復原機制

流量路徑為 `NodePort → Gateway Service → 2 個 Gateway Pod → Order Service → 3～10 個 Order Pod`。Service 只把流量送到 readiness 通過的 Pod；Deployment 在 Pod 掛掉時補足副本；HPA 以平均 CPU 60% 為目標自動擴縮；PDB 保護主動維護期間的最低可用數。

觀察狀態：

```bash
kubectl -n order-system get pods -w
kubectl -n order-system get hpa -w
kubectl -n order-system top pods
```

刪除一個 Pod 驗證自動補回：

```bash
kubectl -n order-system delete pod -l app=order-service --field-selector=status.phase=Running
kubectl -n order-system get pods -w
```

執行 Kubernetes 內部 smoke load：

```bash
kubectl apply -f k8s/k6-test.yaml
kubectl -n order-system logs -f job/k6-smoke
kubectl -n order-system delete job k6-smoke
```

## 9. 更新程式後重新部署

kind 不會自動讀取本機新映像。每次改碼後：

```bash
docker build -t order-system:local .
docker build -t order-gateway:local gateway
kind load docker-image order-system:local order-gateway:local --name order-system
kubectl -n order-system rollout restart deployment/order-service deployment/order-gateway
kubectl -n order-system rollout status deployment/order-service
kubectl -n order-system rollout status deployment/order-gateway
```

## 10. 清理

保留資料、只停止 Compose：

```bash
docker compose down
```

連 MySQL volume 一起刪除會永久移除本機訂單資料：

```bash
docker compose down -v
```

刪除整個 kind 叢集：

```bash
kind delete cluster --name order-system
```

## 11. 正式環境仍需補強

- MySQL、Redis、Kafka 改用多節點或託管服務，並建立備份與復原演練。
- Gateway 至少兩個副本，入口前使用雲端 Load Balancer 或 Ingress。
- HPA 需要 Metrics Server；更精準的擴縮可加入 request rate、queue depth 等自訂 metrics。
- 設定 TLS、認證授權、Secret 管理、NetworkPolicy、集中 log、trace 與告警。
- 依整體 MySQL `max_connections` 分配每個副本的 Hikari pool，不能只靠增加 Pod 無限擴容。
