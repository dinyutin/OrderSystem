import http from 'node:http';
import {spawn} from 'node:child_process';

let job = {state: 'idle', vus: 0, totalOrders: 0, startedAt: null, finishedAt: null, exitCode: null, output: ''};
let child = null;
const json = (res, status, body) => { res.writeHead(status, {'Content-Type': 'application/json; charset=utf-8'}); res.end(JSON.stringify(body)); };
const readBody = req => new Promise((resolve, reject) => { let body = ''; req.on('data', chunk => { body += chunk; if (body.length > 10000) req.destroy(); }); req.on('end', () => { try { resolve(JSON.parse(body || '{}')); } catch (error) { reject(error); } }); req.on('error', reject); });
const append = data => { job.output = (job.output + data.toString()).slice(-30000); };

http.createServer(async (req, res) => {
  if (req.method === 'GET' && req.url === '/status') return json(res, 200, job);
  if (req.method === 'POST' && req.url === '/run') {
    if (child) return json(res, 409, {message: '已有壓力測試正在執行'});
    try {
      const body = await readBody(req);
      const vus = Number(body.vus);
      const totalOrders = Number(body.totalOrders);
      if (!Number.isInteger(vus) || vus < 1 || vus > 1000) return json(res, 400, {message: '虛擬使用者必須介於 1 到 1000'});
      if (!Number.isInteger(totalOrders) || totalOrders < vus || totalOrders > 10000) return json(res, 400, {message: '訂單數必須介於使用者數與 10000 之間'});
      job = {state: 'running', vus, totalOrders, startedAt: new Date().toISOString(), finishedAt: null, exitCode: null, output: ''};
      child = spawn('/usr/bin/k6', ['run', '--address', '127.0.0.1:6566', '--out', 'experimental-prometheus-rw', '/scripts/async-checkout-load.js'], {env: {...process.env, VUS: String(vus), TOTAL_ORDERS: String(totalOrders)}});
      child.stdout.on('data', append); child.stderr.on('data', append);
      child.on('close', code => { job = {...job, state: code === 0 ? 'completed' : 'failed', exitCode: code, finishedAt: new Date().toISOString()}; child = null; });
      return json(res, 202, job);
    } catch { return json(res, 400, {message: '請求格式錯誤'}); }
  }
  json(res, 404, {message: 'Not found'});
}).listen(6565, '0.0.0.0');
