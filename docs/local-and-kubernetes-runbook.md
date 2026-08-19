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

Grafana 內有兩套 dashboard：

| Dashboard | 觀察角度 | 主要內容 |
|---|---|---|
| `k6 Load Testing Results` | client／壓測產生器 | VUs、RPS、failure、checks、mean、max、median、min、p90、p95 |
| `Order System Load Test` | server／系統內部 | Gateway、各應用副本、HTTP、JVM、CPU、Hikari、Outbox、Redis fallback |

k6 使用 `experimental-prometheus-rw` output 將 metrics 即時寫入 Prometheus `/api/v1/write`。Prometheus 必須以 `--web.enable-remote-write-receiver` 啟動；延遲 Trend 會輸出 `avg,min,max,med,p(90),p(95)`，因此測試結束後仍可在 Grafana 比較這些統計值。

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

## 11. Kubernetes 到底替這個系統做什麼

Docker Compose 解決的是「在一台電腦把多個容器一起啟動」。Kubernetes 多處理三件事：持續維持指定副本數、替會變動的 Pod 提供穩定網路入口，以及根據健康狀態與資源使用量調整流量和容量。

Kubernetes 採宣告式管理。YAML 寫的是期望狀態，例如「Order Service 必須有 3 個副本」；controller 會不斷比較實際狀態。如果只剩 2 個，它會建立新的 Pod，而不是等待人員手動重啟。

### 11.1 名詞和本專案的對應

| Kubernetes 物件 | 白話用途 | 本專案實例 |
|---|---|---|
| Namespace | 將一組資源隔離及分類 | 全部放在 `order-system` |
| Pod | Kubernetes 最小執行單位，裡面跑 container | 一個 Order Service Pod 跑一個 Java container |
| Deployment | 管理無狀態 Pod 的副本、更新與自動補回 | Gateway、Order Service、Prometheus、Grafana |
| Service | 提供不隨 Pod 更換而改變的 DNS 和虛擬 IP | `order-service:8080`、`redis:6379` |
| NodePort | 將 cluster 內 Service 暴露到節點連接埠 | API `30080`、Grafana `30300` |
| ConfigMap | 保存非機密設定 | DB URL、Redis host、Kafka broker |
| Secret | 保存敏感設定值 | MySQL 帳號與密碼；目前只適合本機示範 |
| PVC | 要求持久儲存空間 | MySQL 的 `/var/lib/mysql` |
| HPA | 按 metrics 調整 Deployment 副本數 | Order Service 3～10 個副本 |
| PDB | 限制主動維護時可同時中斷的 Pod 數量 | Order Service 至少保留 2 個、Gateway 至少 1 個 |
| ServiceAccount/RBAC | 限制 Pod 可讀取的 Kubernetes API | Prometheus 只能在 namespace 內查看 Pod |
| Job | 執行一次完成後結束的工作 | `k6-smoke` 壓力測試 |
| Kustomize | 將多份 YAML 組成一套可套用資源 | `kubectl apply -k k8s` |

Pod 是短暫資源。Pod 被刪除後，新 Pod 的名稱和 IP 都可能不同，所以其他元件不能記住某個 Pod IP，而要連接 Service DNS。

### 11.2 一個 API 請求怎麼走

```text
瀏覽器或 k6
  → kind host port 8080
  → NodePort 30080
  → order-gateway Service
  → 其中一個 Gateway Pod
  → Redis rate-limit bucket
  → order-service Service
  → 其中一個 Ready 的 Order Service Pod
  → Hikari 取得 MySQL connection
  → MySQL transaction 原子扣庫存、建立訂單及 outbox
  → HTTP 201 回到 client
  → 背景 relay 再將 outbox event 送往 Kafka
```

Gateway 在 Kubernetes 裡設定 `ORDER_SERVICE_URI=http://order-service:8080`。`order-service` 不是主機名稱，而是 Kubernetes Service 的 DNS。Service selector `app: order-service` 會找到相同 label 且 Ready 的 Pod，並透過 EndpointSlice 維護當下可用位址。

因此這裡有兩層分流：外層 Gateway Service 分到 2 個 Gateway Pod；內層 Order Service 分到 3～10 個 Order Pod。與 Docker Compose 不同，Gateway 不需要知道每個 Order Pod 的 IP。

### 11.3 Deployment 如何維持三個副本

`k8s/order-service.yaml` 的 `replicas: 3` 表示最低正常狀態先維持三個副本。Deployment 建立 ReplicaSet，再由 ReplicaSet 建立 Pod。

如果 Java process crash，container restart policy 先嘗試重啟；如果整個 Pod 或 node 消失，ReplicaSet 會另外建立 Pod。這叫 reconciliation：控制器持續把實際狀態拉回 YAML 描述的期望狀態。

RollingUpdate 設為：

```yaml
rollingUpdate:
  maxUnavailable: 0
  maxSurge: 1
```

更新映像時，Kubernetes 最多暫時多建立 1 個新 Pod，而且不主動讓可用副本低於原數量。新 Pod readiness 通過後，才逐步移除舊 Pod。這降低更新期間中斷服務的機率，但叢集必須有足夠資源容納額外 Pod。

### 11.4 三種 Probe 的差異

- `startupProbe`：只負責確認應用完成啟動。在它成功前不執行另外兩種 probe，避免 Spring Boot 啟動較慢時被誤殺。
- `readinessProbe`：判斷能不能接流量。失敗時 container 不一定重啟，但該 Pod 會從 Service 的可用 endpoints 排除。
- `livenessProbe`：判斷 process 是否卡死。連續失敗後 kubelet 會重啟 container。

本專案使用 Spring Boot Actuator 的 `/actuator/health/liveness` 與 `/actuator/health/readiness`。readiness 不應因短暫外部依賴波動而過度敏感，否則所有 Pod 可能同時被移出，形成更嚴重的流量集中或完全無可用 endpoint。

### 11.5 HPA 怎麼擴容

Order Service 每個 Pod 宣告 CPU request `500m`，HPA 目標為平均 CPU utilization 60%。這裡的 60% 是相對 request 計算：大致等於每 Pod 平均使用 `300m` CPU 時達到目標。

```text
期望副本數 ≈ 目前副本數 × 目前平均 CPU / 60%
```

例如目前 3 個 Pod 平均 120%，估算會希望變成 `3 × 120 / 60 = 6` 個。實際結果還會受取樣、容忍區間與 scaling policy 影響。`minReplicas: 3`、`maxReplicas: 10` 防止縮到低於基本可用容量或無限制增加。

HPA 並不是瞬間擋住尖峰的防火牆。建立 Pod、啟動 JVM、通過 startup/readiness 都需要時間，所以短尖峰仍由 Gateway rate limit、應用 bulkhead 與現有副本承受。HPA 更適合持續一段時間的負載。

HPA 依賴 Metrics Server。kind 預設不一定包含它；若 `kubectl get hpa` 的 TARGETS 顯示 `<unknown>`，代表 YAML 存在但沒有 CPU metrics，必須另外安裝 Metrics Server。

### 11.6 PDB 能保護什麼、不能保護什麼

PDB 的 `minAvailable: 2` 表示執行 node drain 等主動中斷操作時，Kubernetes 不應同意同時中斷到只剩 1 個 Order Pod。它不保證硬體突然壞掉時仍有兩個 Pod，也不會替應用建立副本；建立副本是 Deployment 的工作。

簡單區分：

- Deployment：我希望一直有幾個 Pod。
- HPA：負載改變時應該調成幾個 Pod。
- PDB：主動維護時一次可以少掉幾個 Pod。

### 11.7 資料為什麼不能只放 Pod

Pod filesystem 隨 Pod 消失，因此 MySQL 掛載 PVC。PVC 是對儲存的需求，實際 volume 由 kind 或正式環境的 StorageClass 提供。刪除 MySQL Pod 後，只要 PVC 和 volume 還在，新 Pod 仍可掛回資料。

但目前 Kubernetes YAML 的 MySQL、Redis、Kafka 都只有單副本。PVC 只解決資料跟 Pod 分離，沒有解決資料庫高可用、備份、跨節點複寫或災難復原。正式環境通常改用託管服務或成熟 operator，而不是直接把單節點資料服務當成高可用。

### 11.8 Prometheus 如何找到新 Pod

Gateway 與 Order Pod template 有以下 annotations：

```yaml
prometheus.io/scrape: "true"
prometheus.io/path: /actuator/prometheus
prometheus.io/port: "8080"
```

Prometheus 透過 Kubernetes service discovery 查詢 Pod，再以 relabel 規則只保留 `scrape: true` 的目標。HPA 新增 Pod 後，Prometheus 會自動發現，不必手動修改 IP。RBAC 只授權 Prometheus 在 `order-system` namespace 內 `get/list/watch pods`。

Grafana 本身不收集 metrics；它查詢 Prometheus 並畫圖。各線條的 `pod` 或 `instance` label 可用來辨認流量是否平均，以及哪一個副本的 CPU、heap 或 Hikari 特別高。

### 11.9 這套 Kubernetes 目前沒有做到的事

- kind 是本機開發叢集，不代表雲端多機房容錯。
- NodePort 是本機入口；正式環境通常使用 cloud Load Balancer、Ingress 或 Kubernetes Gateway API。
- 沒有安裝 Metrics Server，所以 HPA YAML 可建立，但必須補上 metrics provider 才會真的依 CPU 擴縮。
- 沒有 NetworkPolicy，namespace 內網路目前未做最小權限隔離。
- Secret 只是 base64 編碼的 Kubernetes 物件，不等於完整密鑰保護；正式環境需加密 at rest 或外部 secret manager。
- MySQL、Redis、Kafka 都是本機單節點版本。
- Prometheus 沒有持久 volume，Pod 重建後歷史 metrics 會消失。
- 目前沒有 TLS、使用者認證、集中式 log、distributed tracing 與正式告警通知。

## 12. Kubernetes YAML 檔案地圖

| 檔案 | 內容 |
|---|---|
| `namespace.yaml` | 建立 `order-system` namespace |
| `config.yaml` | 應用 ConfigMap 與本機 Secret |
| `infrastructure.yaml` | MySQL、Redis、Kafka 的 Deployment、Service、PVC |
| `order-service.yaml` | Order Deployment、Service、HPA、PDB |
| `gateway.yaml` | Gateway Deployment、NodePort Service、PDB |
| `monitoring.yaml` | Prometheus/Grafana、ServiceAccount、RBAC、Service |
| `monitoring/prometheus.yml` | Kubernetes Pod discovery 與 relabel 規則 |
| `k6-test.yaml` | cluster 內執行的一次性 k6 Job |
| `kind-config.yaml` | 三節點 kind cluster 與 host port mapping |
| `kustomization.yaml` | 組合以上資源並產生監控 ConfigMap |

讀 YAML 時建議順序是 Namespace → Config/Secret → Infrastructure → Order Service → Gateway → Monitoring → k6 Job。這與系統依賴方向一致，比直接逐行閱讀容易理解。

## 13. 常用排查指令與判讀

```bash
# 總覽：READY 例如 1/1、STATUS 應為 Running
kubectl -n order-system get pods -o wide

# Pod 為什麼起不來、被排程到哪裡、probe 是否失敗
kubectl -n order-system describe pod POD_NAME

# 看 Java 或 Gateway log
kubectl -n order-system logs POD_NAME --tail=200

# container 重啟後查看上一次的 log
kubectl -n order-system logs POD_NAME --previous

# Service 目前實際連到哪些 Ready Pod
kubectl -n order-system get service,endpointslice

# Deployment 是否完成更新
kubectl -n order-system rollout status deployment/order-service

# HPA 是否取得 metrics、目前副本數
kubectl -n order-system describe hpa order-service

# 直接查看事件，通常能找到 image pull、排程或 volume 問題
kubectl -n order-system get events --sort-by=.lastTimestamp
```

常見狀況：

| 現象 | 優先檢查 |
|---|---|
| `ImagePullBackOff` | 是否先執行 `kind load docker-image`、image 名稱是否相同 |
| `CrashLoopBackOff` | `kubectl logs` 與 `--previous`，常見為 DB 尚未可連線或設定錯誤 |
| `0/1 Ready` | readiness endpoint、port、依賴初始化 |
| Service 連不到 | selector 與 Pod label 是否一致、EndpointSlice 是否有位址 |
| HPA `<unknown>` | Metrics Server 是否安裝、Pod 是否有 CPU requests |
| Pending | node CPU/記憶體不足、PVC 無法綁定、taint 不允許排程 |
| Grafana 無資料 | Prometheus targets health、Pod annotations、RBAC 與 `/actuator/prometheus` |

## 14. 正式環境仍需補強

- MySQL、Redis、Kafka 改用多節點或託管服務，並建立備份與復原演練。
- Gateway 至少兩個副本，入口前使用雲端 Load Balancer 或 Ingress。
- HPA 需要 Metrics Server；更精準的擴縮可加入 request rate、queue depth 等自訂 metrics。
- 設定 TLS、認證授權、Secret 管理、NetworkPolicy、集中 log、trace 與告警。
- 依整體 MySQL `max_connections` 分配每個副本的 Hikari pool，不能只靠增加 Pod 無限擴容。
