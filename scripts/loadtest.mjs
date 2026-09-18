#!/usr/bin/env node
/**
 * SSE 并发压测脚本（零依赖，需 Node 18+）
 *
 * 压测目标：GET /ai/chat（面试对话 SSE 流式接口）
 *
 * 用法：
 *   node scripts/loadtest.mjs
 *   set LT_CONCURRENCY=1,5,10,20 & node scripts/loadtest.mjs
 *
 * 可选环境变量：
 *   LT_BASE          服务地址，默认 http://localhost:8123
 *   LT_TOKEN         现成 token（不填则自动注册压测账号）
 *   LT_MESSAGE       提问内容，默认 hello（ASCII 可避开 cmd 中文编码问题）
 *   LT_CONCURRENCY   并发梯度，逗号分隔，默认 1,5,10
 *   LT_PER_WORKER    每个虚拟用户的请求数，默认 3
 *   LT_TIMEOUT_MS    单请求超时，默认 120000
 *   LT_USER_PREFIX   自动注册的用户名前缀，默认 ltuser
 */
import { writeFileSync } from 'node:fs';

const BASE         = process.env.LT_BASE || 'http://localhost:8123';
const TOKEN        = process.env.LT_TOKEN || '';
const MESSAGE      = process.env.LT_MESSAGE || 'hello';
const PER_WORKER   = Number(process.env.LT_PER_WORKER || 3);
const TIMEOUT_MS   = Number(process.env.LT_TIMEOUT_MS || 120000);
const USER_PREFIX  = process.env.LT_USER_PREFIX || 'ltuser';
const USER_PASS    = process.env.LT_USER_PASS || 'lt123456';
const LEVELS = (process.env.LT_CONCURRENCY || '1,5,10')
    .split(',').map(s => Number(s.trim())).filter(n => n > 0);

// ===== 工具 =====
/** 显示宽度：CJK 字符按 2 列算，否则表格对不齐 */
const width = s => [...s].reduce((w, c) => w + (c.charCodeAt(0) > 0x2e80 ? 2 : 1), 0);
const pad = (s, n) => String(s) + ' '.repeat(Math.max(0, n - width(s)));
const fix = (v, d = 2) => Number(v).toFixed(d);

/** 百分位（nearest-rank） */
function stats(arr) {
    const s = arr.filter(v => v != null && Number.isFinite(v)).sort((a, b) => a - b);
    if (!s.length) return { n: 0, avg: 0, p50: 0, p95: 0, p99: 0, max: 0 };
    const at = p => s[Math.min(s.length - 1, Math.max(0, Math.ceil((p / 100) * s.length) - 1))];
    return { n: s.length, avg: s.reduce((a, b) => a + b, 0) / s.length, p50: at(50), p95: at(95), p99: at(99), max: s[s.length - 1] };
}

async function postJson(path, body) {
    const res = await fetch(BASE + path, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
    });
    return res.json();
}

/**
 * 创建一个压测账号并登录，返回 token。
 * 每个虚拟用户独立 token ⇒ 独立 userId ⇒ 独立答题游标，避免并发串台。
 * （QuizApp.cursorKey 在 userId 存在时只认 userId，不看 chatId）
 */
async function makeUser(i) {
    const username = `${USER_PREFIX}${i}`;
    // 已存在则注册失败，忽略即可，直接登录
    await postJson('/api/user/register', { username, password: USER_PASS, nickname: `lt${i}` }).catch(() => {});
    const r = await postJson('/api/user/login', { username, password: USER_PASS });
    if (r.code !== 0 || !r.data?.token) throw new Error(`登录失败 ${username}: ${JSON.stringify(r)}`);
    return r.data.token;
}

/**
 * 发一次 SSE 请求，测量首字节与总耗时。
 * 首字节 = 用户感知的"干等时间"；总耗时包含 AI 边想边吐的整个过程。
 */
async function oneRequest(token, chatId) {
    const url = `${BASE}/api/ai/chat?message=${encodeURIComponent(MESSAGE)}`
        + `&chatId=${encodeURIComponent(chatId)}&token=${encodeURIComponent(token)}`;
    const ac = new AbortController();
    const timer = setTimeout(() => ac.abort(), TIMEOUT_MS);
    const t0 = performance.now();
    let ttfb = null, bytes = 0, status = 0, sawDone = false, err = null;

    try {
        const res = await fetch(url, { signal: ac.signal });
        status = res.status;
        if (res.ok && res.body) {
            const reader = res.body.getReader();
            const dec = new TextDecoder();
            let tail = '';
            for (;;) {
                const { done, value } = await reader.read();
                if (done) break;
                if (ttfb === null) ttfb = performance.now() - t0;
                bytes += value.byteLength;
                // SSE 可能把 [DONE] 拆到两个 chunk，取尾部 64 字符兜住
                tail = (tail + dec.decode(value, { stream: true })).slice(-64);
                if (tail.includes('[DONE]')) sawDone = true;
            }
        } else {
            err = (await res.text().catch(() => '')).slice(0, 200) || `HTTP ${status}`;
        }
    } catch (e) {
        err = e.name === 'AbortError' ? `TIMEOUT(${TIMEOUT_MS}ms)` : e.message;
    } finally {
        clearTimeout(timer);
    }

    const total = performance.now() - t0;
    return { ok: status === 200 && sawDone, status, ttfb: ttfb ?? total, total, bytes, sawDone, err };
}

async function main() {
    const maxN = Math.max(...LEVELS);
    console.log('='.repeat(64));
    console.log('  SSE 并发压测 — GET /api/ai/chat');
    console.log('='.repeat(64));
    console.log(`  目标      : ${BASE}`);
    console.log(`  消息      : ${JSON.stringify(MESSAGE)}`);
    console.log(`  并发梯度  : ${LEVELS.join(' → ')}`);
    console.log(`  每人请求数: ${PER_WORKER}  (每级共 ${maxN} 个虚拟用户)`);
    console.log('');

    // 预热：把 JIT、连接池、RAG 索引缓存都喂热，避免首个请求污染数据
    process.stdout.write('  预热中 ... ');
    const warmToken = TOKEN || await makeUser(0);
    await oneRequest(warmToken, 'perf_warmup');
    console.log('完成\n');

    // 准备 token：显式传入则所有 VU 共用（答题游标会串台，仅测吞吐极限时用）
    const tokens = [];
    if (TOKEN) {
        console.log('  ⚠ 使用 LT_TOKEN：所有虚拟用户共用同一账号，答题进度会互相干扰\n');
        for (let i = 0; i < maxN; i++) tokens.push(TOKEN);
    } else {
        process.stdout.write(`  准备 ${maxN} 个压测账号 ... `);
        for (let i = 1; i <= maxN; i++) tokens.push(await makeUser(i));
        console.log('准备完成\n');
    }

    const rows = [];
    for (const n of LEVELS) {
        process.stdout.write(`  并发 ${pad(n, 4)} 压测中 ... `);
        const results = [];
        const t0 = performance.now();

        // 每个虚拟用户串行发自己的 PER_WORKER 次请求，VU 之间并发
        await Promise.all(Array.from({ length: n }, async (_, w) => {
            for (let k = 0; k < PER_WORKER; k++) {
                results.push(await oneRequest(tokens[w], `perf_vu${w}`));
            }
        }));

        const wall = (performance.now() - t0) / 1000;
        const ok = results.filter(r => r.ok);
        const fail = results.filter(r => !r.ok);
        const a = stats(ok.map(r => r.ttfb));
        const b = stats(ok.map(r => r.total));

        console.log(`完成  ${fix(wall, 1)}s   成功 ${ok.length}  失败 ${fail.length}`);
        if (fail.length) {
            const f = fail[0];
            console.log(`      ↳ 失败样例: status=${f.status} ${f.err}`);
        }
        rows.push({ n, wall, total: results.length, ok: ok.length, fail: fail.length, ttfb: a, dur: b });
    }

    // ===== 汇总表 =====
    console.log('\n' + '='.repeat(64));
    console.log('  压测汇总');
    console.log('='.repeat(64));
    const head = ['并发', '总数', '成功', '失败', '墙钟(s)', '吞吐(个/s)', '首字节p50', '首字节p95', '总耗时p50', '总耗时p95'];
    const cells = r => [
        r.n, r.total, r.ok, r.fail, fix(r.wall, 1), fix(r.total / r.wall, 2),
        fix(r.ttfb.p50) + 's', fix(r.ttfb.p95) + 's', fix(r.dur.p50) + 's', fix(r.dur.p95) + 's',
    ];
    const w = head.map((h, i) => Math.max(width(h), ...rows.map(r => width(cells(r)[i]))) + 2);
    console.log('  ' + head.map((h, i) => pad(h, w[i])).join(''));
    console.log('  ' + '-'.repeat(w.reduce((a, b) => a + b, 0)));
    for (const r of rows) console.log('  ' + cells(r).map((c, i) => pad(c, w[i])).join(''));

    console.log('\n  首字节 = 用户点发送后干等多久（体验指标）');
    console.log('  总耗时 = 整个流吐完（含 AI 逐字输出，不代表"卡"）');

    const out = { base: BASE, message: MESSAGE, perWorker: PER_WORKER, levels: rows };
    writeFileSync('loadtest-result.json', JSON.stringify(out, null, 2));
    console.log('\n  明细已写入 loadtest-result.json');
}

main().catch(e => {
    console.error('\n压测异常终止:', e.message);
    process.exit(1);
});
