# Load-test results

All runs were executed locally with Docker Compose. Results are retained to show both the capacity limit and the effect of each tuning step; failed runs are intentionally not hidden.

| Run | Virtual users | Orders | HTTP 201 | Failed | p95 | Throughput | Finding |
|---|---:|---:|---:|---:|---:|---:|---|
| Baseline | 100 | 1,000 | 1,000 | 0 | 295 ms | 93 req/s | Correct stock and no overselling |
| Bulkhead protected | 1,000 | 10,000 | 8,714 | 1,286 | 1.43 s | 526 req/s | 500-concurrent bulkhead shed excess load |
| DB pool bottleneck | 1,000 | 10,000 | 8,997 | 1,003 | 1.34 s | 629 req/s | Hikari 1-second timeout caused transaction acquisition failures |
| Tuned capacity | 1,000 | 10,000 | 10,000 | 0 | 659 ms | 718 req/s | All checks passed; MySQL stock reached zero |
| Gateway + 3 replicas | 1,000 | 10,000 | 10,000 | 0 | 1.71 s | 420 req/s | All checks passed through Gateway; traffic spread across three instances and stock reached zero |

The single-instance tuned run uses a 1,000-request bulkhead and 100 Hikari connections. The multi-instance run uses three 350-request bulkheads and three pools of 30 connections, with a 10-second connection timeout and a 3-second p95 threshold. These values describe this local test environment, not universal production defaults.

The outbox backlog increased during the burst because Kafka delivery is intentionally decoupled from order creation. The relay drains it after the request spike; this keeps Kafka latency and outages out of the checkout transaction.

## Screenshots

- `order-system-dashboard.png`: original 100-user / 1,000-order baseline.
- `order-system-1000-users-10000-orders.png`: 1,000-user tuning runs and resilience panels.
- `load-test-before-tuning.png`: 1,000-user run before DB pool tuning, including HTTP 500s.
- `load-test-after-tuning.png`: 1,000-user run after tuning, with all 10,000 requests successful.
- `order-system-gateway-1000-users-10000-orders.png`: latest Gateway and three-replica run; all requests succeeded with p95 1.71 seconds.
