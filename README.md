# Order System

Hello, I'm Bonita. This is a project that simulates a **high-concurrency product order system** based on **Spring Boot**, **Redis**, **Kafka**, and **MySQL**.  
It supports **distributed locking** to prevent overselling and uses Kafka for **asynchronous stock updates** in MySQL.

## Technologies
- **Spring Boot 3.x** - Backend framework
- **Spring Data JPA** - Database access
- **Spring Data Redis** - Caching mechanism
- **Apache Kafka** - Message queue for order processing
- **Redisson** - Distributed locking to ensure concurrency safety
- **MySQL** - Database for products and orders
- **JMeter** - Used for stress testing

## Project Features

### 1. Product Management
- **Create a new product**
- **Retrieve product details**
- **Check product stock (using Redis for faster response)**

### 2. Order Management
- **Place an order** (deduct stock from Redis and send Kafka message)
- **Kafka consumer updates MySQL stock**
- **Ensure stock consistency and prevent overselling**

### 3. Concurrency Safety Measures
- **Redis caches stock data to reduce MySQL queries**
- **Redisson distributed lock ensures only one thread deducts stock at a time**
- **Kafka message queue asynchronously processes orders to prevent main thread blocking**

## API Overview

### **Product APIs**
| Method | Endpoint | Description |
|--------|---------|-------------|
| `POST` | `/products/create` | Create a new product |
| `GET`  | `/products/{productId}` | Get product details |
| `GET`  | `/products/{productId}/stock` | Get product stock |

### **Order APIs**
| Method | Endpoint | Description |
|--------|---------|-------------|
| `POST` | `/orders/create/{productId}` | Place an order (using Redis + Kafka) |

##  How to Start the Project

### 1️.Set Up Database (MySQL)
```sql
CREATE DATABASE order_system;
USE order_system;
```

**Ensure `application.properties` is configured correctly:**
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/order_system?serverTimezone=UTC
spring.datasource.username=root
spring.datasource.password=password
```

### 2️.Start Kafka
On Windows, download Kafka and start it:
```bash
# Start Zookeeper
{kafka_path}\bin\windows\zookeeper-server-start.bat 

# Start Kafka
{kafka_path}\bin\windows\kafka-server-start.bat D:\kafka_2.13-3.9.0\config\server.properties

# Create Kafka topic
{kafka_path}\bin\windows\kafka-topics.bat --create --topic order-topic --bootstrap-server localhost:9092 --partitions 1 --replication-factor 1
```

### 3.Start Spring Boot Project
```bash
mvn spring-boot:run
```

### 4️. Test APIs (Using Postman or CURL)
#### **Create a New Product (POST)**
```bash
POST "http://localhost:8080/products/create" -d "name={product_name} &stock={stock_quantity}"
```

#### **Place an Order (POST)**
```bash
POST "http://localhost:8080/orders/create/{product_id}"
```

##  How to Perform High-Concurrency Testing with JMeter
1. **Open JMeter** and create a new test plan
2. **Add `Thread Group`** and configure:
   - **Number of Threads (simulated users): 1000**
   - **Ramp-up Period: 10s** (1000 users start over 10 seconds)
   - **Loop Count: 1** (Each user sends one request)
3. **Add `HTTP Request`** and configure:
   - **Method**: `POST`
   - **Path**: `/orders/create/{productId}`
   - **Parameters**: None
4. **Add `View Results Tree`** to monitor request results
5. **Run the test** → **Monitor Redis stock & MySQL order processing**

## Future Optimizations
- **Add Kafka delayed processing to prevent stock inconsistencies during burst orders**
- **Implement `database sharding` to improve MySQL throughput**
- **Use ELK (Elasticsearch + Logstash + Kibana) for monitoring high-concurrency request data**

---

# Order System

您好我是Bonita，這是一個基於 **Spring Boot**、**Redis**、**Kafka** 和 **MySQL** 來模擬高併發商品訂單的project，
支援 **分布式鎖** 來避免超賣，並透過 Kafka **異步更新** MySQL 庫存。

## 技術
- **Spring Boot 3.x** - 後端框架
- **Spring Data JPA** - 資料庫存取
- **Spring Data Redis** - 緩存機制
- **Apache Kafka** - 訂單處理的消息佇列
- **Redisson** - 分布式鎖，確保併發安全
- **MySQL** - 產品與訂單數據庫
- **JMeter** - 用於壓力測試

## 專案功能
### 1.商品管理
- **新增商品**
- **查詢商品**
- **商品庫存查詢（透過 Redis 加快響應速度）**

### 2.訂單管理
- **用戶下單**（扣除 Redis 庫存並發送 Kafka 消息）
- **Kafka 消費者更新 MySQL 庫存**
- **確保庫存一致性，避免超賣問題**

### 3.併發安全措施
- **Redis 快取庫存數據，減少 MySQL 查詢負擔**
- **Redisson 分布式鎖確保同時只允許一個執行緒扣庫存**
- **Kafka 消息隊列異步處理訂單，避免主線程阻塞**

##  API 一覽

###  **商品相關 API**
| Method | Endpoint | Description |
|--------|---------|-------------|
| `POST` | `/products/create` | 新增商品 |
| `GET`  | `/products/{productId}` | 查詢商品資訊 |
| `GET`  | `/products/{productId}/stock` | 查詢商品庫存 |

### **訂單相關 API**
| Method | Endpoint | Description |
|--------|---------|-------------|
| `POST` | `/orders/create/{productId}` | 用戶下單（使用 Redis + Kafka） |

##  如何啟動專案

### 1.設定資料庫（MySQL）
```sql
CREATE DATABASE order_system;
USE order_system;
```
**確認 `application.properties` 設定 MySQL 資訊：**
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/order_system?serverTimezone=UTC
spring.datasource.username=root
spring.datasource.password=password
```

### 2️.啟動 Kafka
在windows中下載 Kafka：
```
# 啟動 Zookeeper
{kafka路徑下}\bin\windows\zookeeper-server-start.bat 

# 啟動 Kafka
{kafka路徑下}\bin\windows\kafka-server-start.bat D:\kafka_2.13-3.9.0\config\server.properties

# 建立 Kafka topic
{kafka路徑下}\bin\windows\kafka-topics.bat --create --topic order-topic --bootstrap-server localhost:9092 --partitions 1 --replication-factor 1
```

### 3. 啟動 Spring Boot 專案
```bash
mvn spring-boot:run
```

### 4️.測試 API（使用 Postman 或 CURL）
####  **新增商品POST**
```bash
 POST "http://localhost:8080/products/create" -d "name={商品名稱} &stock={庫存數量}"
```

####  **用戶下訂單POST**
```bash
POST "http://localhost:8080/orders/create/{商品id}"
```

##  如何使用 JMeter 進行高併發測試
1. **打開 JMeter**，建立一個測試計畫
2. **新增 `Thread Group`**，設定:
   - **Number of Threads (Threads模擬使用者數量)：1000**
   - **Ramp-up Period：10s**（10 秒啟動 1000 個使用者）
   - **Loop Count：1**（每個使用者請求一次）
3. **新增 `HTTP Request`**，設定：
   - **Method**：`POST`
   - **Path**：`/orders/create/{productId}`
   - **Parameters**：無
4. **新增 `View Results Tree`** 來觀察請求結果
5. **執行測試** → **觀察 Redis 庫存 & MySQL 訂單情況**

##  未來優化方向
- **增加 Kafka 消息的延遲處理機制，避免短時間大量請求導致的 Redis 與 MySQL 數據不一致**
- **實作 `分庫分表` 優化 MySQL 的吞吐量**
- **透過 ELK（Elasticsearch + Logstash + Kibana）來監控併發請求數據**

---
📌 **Author作者: Bonita**  
📌 **GitHub Repo: [Bonita's Repository Link](https://github.com/dinyutin)**

