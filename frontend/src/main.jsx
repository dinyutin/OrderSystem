import React, { useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import './styles.css';

const api = async (path, options = {}) => {
  const response = await fetch(path, {
    ...options,
    headers: {'Content-Type': 'application/json', 'X-Client-Id': 'order-console', ...(options.headers || {})},
  });
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(body.message || `HTTP ${response.status}`);
  return body;
};

const statusText = {
  RESERVED: '訂單已建立，等待付款',
  COMPLETED: '訂單已完成',
  PAYMENT_FAILED: '付款失敗，庫存已回補',
  EXPIRED: '訂單逾時，庫存已回補',
};

function App() {
  const [newProduct, setNewProduct] = useState({name: `測試商品-${Date.now()}`, stock: 100});
  const [products, setProducts] = useState([]);
  const [selectedId, setSelectedId] = useState('');
  const [quantity, setQuantity] = useState(1);
  const [order, setOrder] = useState(null);
  const [message, setMessage] = useState('請先從商品清單選擇一項商品');
  const [busy, setBusy] = useState(false);
  const [loadConfig, setLoadConfig] = useState({vus: 10, totalOrders: 100});
  const [loadJob, setLoadJob] = useState({state: 'idle', output: ''});

  const selectedProduct = products.find(item => String(item.productId) === selectedId);

  const loadProducts = async () => {
    try { setProducts(await api('/api/products')); }
    catch (error) { setMessage(`讀取商品失敗：${error.message}`); }
  };

  useEffect(() => { loadProducts(); }, []);
  useEffect(() => {
    const refresh = () => api('/loadtest/status').then(setLoadJob).catch(() => {});
    refresh();
    const timer = setInterval(refresh, 2000);
    return () => clearInterval(timer);
  }, []);

  const run = async (label, action) => {
    setBusy(true);
    try { const result = await action(); setMessage(`${label}成功`); return result; }
    catch (error) { setMessage(`${label}失敗：${error.message}`); }
    finally { setBusy(false); }
  };

  const createProduct = () => run('新增商品', async () => {
    const result = await api('/api/products', {
      method: 'POST', body: JSON.stringify({...newProduct, stock: Number(newProduct.stock)}),
    });
    await loadProducts();
    setSelectedId(String(result.productId));
    return result;
  });

  const createOrder = () => run('建立訂單', async () => {
    const result = await api('/api/orders/reservations', {
      method: 'POST', headers: {'Idempotency-Key': crypto.randomUUID()},
      body: JSON.stringify({productId: Number(selectedId), quantity: Number(quantity)}),
    });
    setOrder(result);
    await loadProducts();
    return result;
  });

  const pay = result => run(result === 'SUCCESS' ? '付款' : '模擬付款失敗', async () => {
    const updated = await api(`/api/orders/${order.orderId}/payments`, {
      method: 'POST', body: JSON.stringify({result}),
    });
    setOrder(updated);
    await loadProducts();
    return updated;
  });

  const startLoadTest = async () => {
    if (Number(loadConfig.vus) >= 500 && !window.confirm(`確定要啟動 ${loadConfig.vus} 人、${loadConfig.totalOrders} 筆訂單的大型壓力測試嗎？`)) return;
    try {
      const result = await api('/loadtest/run', {method: 'POST', body: JSON.stringify({vus: Number(loadConfig.vus), totalOrders: Number(loadConfig.totalOrders)})});
      setLoadJob(result); setMessage('壓力測試已啟動，可到 Grafana 查看即時指標');
    } catch (error) { setMessage(`啟動壓力測試失敗：${error.message}`); }
  };

  return <main>
    <header>
      <span className="eyebrow">ORDER SYSTEM</span>
      <h1>訂單管理操作台</h1>
      <p>依序選擇商品、填寫數量、建立訂單，再測試付款結果。</p>
    </header>

    <div className="flow"><span className="active">1 選擇商品</span><i>→</i><span>2 建立訂單</span><i>→</i><span>3 處理付款</span></div>

    <section className="panel">
      <div className="panel-title"><div><span className="step">01</span><h2>選擇商品</h2></div><button className="secondary compact" onClick={loadProducts}>重新整理</button></div>
      <p className="hint">點擊一列商品進行選擇。庫存為 0 的商品不能建立訂單。</p>
      <div className="table-wrap"><table><thead><tr><th>選擇</th><th>商品編號</th><th>商品名稱</th><th>剩餘庫存</th></tr></thead><tbody>
        {products.map(item => <tr key={item.productId} className={String(item.productId) === selectedId ? 'selected' : ''} onClick={() => item.stock > 0 && setSelectedId(String(item.productId))}>
          <td><input type="radio" checked={String(item.productId) === selectedId} disabled={item.stock <= 0} onChange={() => setSelectedId(String(item.productId))}/></td>
          <td>#{item.productId}</td><td>{item.name}</td><td className={item.stock <= 0 ? 'empty' : ''}>{item.stock}</td>
        </tr>)}
      </tbody></table></div>
    </section>

    <section className="grid actions-grid">
      <article><span className="step">02</span><h2>建立訂單</h2>
        <p>已選商品：<strong>{selectedProduct?.name || '尚未選擇'}</strong></p>
        <label>購買數量<input type="number" min="1" max={selectedProduct?.stock || 1} value={quantity} onChange={event => setQuantity(event.target.value)}/></label>
        <button disabled={busy || !selectedProduct || quantity < 1} onClick={createOrder}>建立訂單</button>
        <small>建立後會先保留庫存五分鐘。</small>
      </article>
      <article><span className="step">03</span><h2>處理付款</h2>
        <p>{order ? statusText[order.status] || order.status : '請先建立一筆訂單。'}</p>
        <div className="actions"><button disabled={busy || order?.status !== 'RESERVED'} onClick={() => pay('SUCCESS')}>付款成功</button><button className="secondary" disabled={busy || order?.status !== 'RESERVED'} onClick={() => pay('FAILURE')}>付款失敗</button></div>
      </article>
      <article><span className="step">＋</span><h2>新增測試商品</h2>
        <label>商品名稱<input value={newProduct.name} onChange={event => setNewProduct({...newProduct, name: event.target.value})}/></label>
        <label>初始庫存<input type="number" min="1" value={newProduct.stock} onChange={event => setNewProduct({...newProduct, stock: event.target.value})}/></label>
        <button className="secondary" disabled={busy} onClick={createProduct}>新增商品</button>
      </article>
    </section>

    <section className="order-summary"><div><span>訂單狀態</span><strong className={`state ${order?.status || ''}`}>{order ? statusText[order.status] || order.status : '尚未建立'}</strong></div><div><span>訂單編號</span><code>{order?.orderId || '—'}</code></div><div><span>庫存保留期限</span><code>{order?.expiresAt ? new Date(order.expiresAt).toLocaleString() : '—'}</code></div></section>

    <section className="panel load-panel">
      <div className="panel-title"><div><span className="step">⚡</span><h2>高併發壓力測試</h2></div><strong className={`job ${loadJob.state}`}>{loadJob.state === 'running' ? '執行中' : loadJob.state === 'completed' ? '已完成' : loadJob.state === 'failed' ? '失敗' : '尚未執行'}</strong></div>
      <p className="hint">設定 k6 虛擬使用者與訂單總數。請求會經過 Gateway，由 Redis 原子預扣、Kafka 排隊，再由背景服務寫入資料庫及付款。</p>
      <div className="presets">
        <button className="secondary" onClick={() => setLoadConfig({vus: 10, totalOrders: 100})}>小型：10 人／100 筆</button>
        <button className="secondary" onClick={() => setLoadConfig({vus: 100, totalOrders: 1000})}>中型：100 人／1000 筆</button>
        <button className="secondary" onClick={() => setLoadConfig({vus: 1000, totalOrders: 10000})}>大型：1000 人／10000 筆</button>
      </div>
      <div className="load-form"><label>虛擬使用者數（1–1000）<input type="number" min="1" max="1000" value={loadConfig.vus} onChange={event => setLoadConfig({...loadConfig, vus: event.target.value})}/></label><label>訂單總數（最多 10000）<input type="number" min="1" max="10000" value={loadConfig.totalOrders} onChange={event => setLoadConfig({...loadConfig, totalOrders: event.target.value})}/></label><button disabled={loadJob.state === 'running'} onClick={startLoadTest}>開始壓力測試</button><a href="http://localhost:3001" target="_blank" rel="noreferrer">開啟 Grafana</a></div>
      {loadJob.output && <details><summary>查看 k6 執行結果</summary><pre>{loadJob.output}</pre></details>}
    </section>
    <footer>{message}</footer>
  </main>;
}

createRoot(document.getElementById('root')).render(<React.StrictMode><App/></React.StrictMode>);
