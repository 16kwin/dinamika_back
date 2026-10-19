#!/usr/bin/env node
// video-load-test.mjs — нагрузочная проверка видео: несколько зрителей живого потока одновременно
// с записью по событию станции. Каждый зритель — отдельное подключение к серверу, как отдельный хост в ЛВС.
//
// Что проверяется:
//   • у каждого зрителя живой поток идёт не реже 25 к/с (считаются кадры-фрагменты потока);
//   • запись по событию, идущая в это же время, содержит не меньше 25 к/с и не имеет провалов.
//
// Запуск (Node 18+; ffprobe нужен для проверки записанных файлов):
//   node tools/video-load-test.mjs --base http://localhost:8084 --user director_test --password <пароль> \
//        --viewers 4 --seconds 60 --record
//   Пароль можно взять из файла: --credentials C:/AWMS/local-test-credentials.txt
//
// Параметры:
//   --base         адрес сервера (по умолчанию http://localhost:8084)
//   --user         учётная запись администратора или директора
//   --password     пароль; или --credentials <файл> со строками username=/password=
//   --viewers      зрителей на каждую камеру (по умолчанию 3)
//   --seconds      длительность проверки (по умолчанию 60)
//   --record       параллельно записать событие «Выдача» на станции камер
//   --min-fps      порог частоты кадров (по умолчанию 25; допуск 1% на округление времени)
//   --ffprobe      путь к ffprobe (по умолчанию C:/ffmpeg/bin/ffprobe.exe или ffprobe из PATH)

import { execFileSync } from 'node:child_process';
import { existsSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const args = Object.fromEntries(
  process.argv.slice(2).reduce((pairs, arg, i, all) => {
    if (arg.startsWith('--')) pairs.push([arg.slice(2), all[i + 1] && !all[i + 1].startsWith('--') ? all[i + 1] : true]);
    return pairs;
  }, []),
);

const BASE = String(args.base ?? 'http://localhost:8084').replace(/\/$/, '');
const VIEWERS = Number(args.viewers ?? 3);
const SECONDS = Number(args.seconds ?? 60);
const MIN_FPS = Number(args['min-fps'] ?? 25);
const TOLERANCE = 0.99;
const WARMUP_MS = 3000;
const FFPROBE = args.ffprobe ?? (existsSync('C:/ffmpeg/bin/ffprobe.exe') ? 'C:/ffmpeg/bin/ffprobe.exe' : 'ffprobe');

const credentials = () => {
  if (args.password) return { username: args.user, password: args.password };
  if (!args.credentials) throw new Error('Нужен --password или --credentials <файл>');
  const lines = readFileSync(args.credentials, 'utf8').split(/\r?\n/);
  const user = args.user ?? 'director_test';
  const at = lines.findIndex(line => line.trim() === `username=${user}`);
  const password = at >= 0 ? lines.slice(at + 1).find(line => line.startsWith('password='))?.slice('password='.length) : null;
  if (!password) throw new Error(`В ${args.credentials} нет пароля для ${user}`);
  return { username: user, password };
};

// ==================== Сессия: вход, куки, CSRF ====================

const cookies = new Map();
const keepCookies = response => {
  for (const header of response.headers.getSetCookie?.() ?? []) {
    const [pair] = header.split(';');
    const eq = pair.indexOf('=');
    cookies.set(pair.slice(0, eq).trim(), pair.slice(eq + 1).trim());
  }
};
const cookieHeader = () => [...cookies].map(([k, v]) => `${k}=${v}`).join('; ');
let csrf = '';

const api = async (path, { method = 'GET', body } = {}) => {
  const response = await fetch(BASE + path, {
    method,
    headers: {
      Cookie: cookieHeader(),
      ...(body ? { 'Content-Type': 'application/json' } : {}),
      ...(method !== 'GET' ? { 'X-XSRF-TOKEN': csrf } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  keepCookies(response);
  if (!response.ok) throw new Error(`${method} ${path}: ${response.status} ${await response.text()}`);
  return response;
};

const login = async () => {
  const { username, password } = credentials();
  const response = await fetch(`${BASE}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  });
  keepCookies(response);
  if (!response.ok) throw new Error(`Вход не удался: ${response.status}`);
  csrf = (await (await api('/csrf')).json()).token;
  return username;
};

// ==================== Зритель живого потока ====================

/** Один зритель: читает фрагментированный MP4 и отмечает время прихода каждого кадра (moof) */
const watch = async (camera, index, durationMs, signal) => {
  const viewer = { camera: camera.name, index, frames: [], bytes: 0, firstFrameMs: null, error: null };
  const started = performance.now();
  try {
    const response = await fetch(`${BASE}/api/video/cameras/${camera.id}/live`, { headers: { Cookie: cookieHeader() }, signal });
    if (!response.ok) throw new Error(`${response.status} ${await response.text()}`);
    viewer.codec = response.headers.get('x-video-codec');
    const reader = response.body.getReader();
    let buffer = Buffer.alloc(0);
    const deadline = started + durationMs;
    while (performance.now() < deadline) {
      const { done, value } = await reader.read();
      if (done) {
        viewer.error = 'поток закончился раньше времени';
        break;
      }
      viewer.bytes += value.length;
      buffer = buffer.length ? Buffer.concat([buffer, value]) : Buffer.from(value);
      // Разбор боксов верхнего уровня: на каждый кадр — moof + mdat
      for (;;) {
        if (buffer.length < 8) break;
        let size = buffer.readUInt32BE(0);
        if (size === 1) {
          if (buffer.length < 16) break;
          size = Number(buffer.readBigUInt64BE(8));
        }
        if (size < 8 || buffer.length < size) break;
        if (buffer.toString('latin1', 4, 8) === 'moof') {
          const at = performance.now() - started;
          viewer.frames.push(at);
          if (viewer.firstFrameMs === null) viewer.firstFrameMs = at;
        }
        buffer = buffer.subarray(size);
      }
    }
    reader.cancel().catch(() => undefined);
  } catch (error) {
    if (!signal.aborted) viewer.error = error.message;
  }
  return viewer;
};

/** Частота кадров после разгона: новый зритель сначала получает пачку кадров от последнего опорного */
const steadyStats = frames => {
  const steady = frames.filter(t => t >= WARMUP_MS);
  if (steady.length < 2) return { fps: 0, minWindowFps: 0, maxGapMs: 0, frames: steady.length };
  const fps = ((steady.length - 1) * 1000) / (steady[steady.length - 1] - steady[0]);
  let maxGapMs = 0;
  for (let i = 1; i < steady.length; i++) maxGapMs = Math.max(maxGapMs, steady[i] - steady[i - 1]);
  // Самая медленная секунда
  let minWindowFps = Infinity;
  for (let from = steady[0]; from + 1000 <= steady[steady.length - 1]; from += 1000) {
    minWindowFps = Math.min(minWindowFps, steady.filter(t => t >= from && t < from + 1000).length);
  }
  return { fps, minWindowFps: Number.isFinite(minWindowFps) ? minWindowFps : 0, maxGapMs, frames: steady.length };
};

// ==================== Запись по событию ====================

const probeFile = path => {
  const out = execFileSync(FFPROBE, ['-v', 'error', '-select_streams', 'v:0', '-show_entries', 'packet=pts_time', '-of', 'csv=p=0', path], {
    encoding: 'utf8',
    maxBuffer: 64 * 1024 * 1024,
  });
  const times = out.split(/\r?\n/).map(Number).filter(Number.isFinite).sort((a, b) => a - b);
  if (times.length < 2) return { frames: times.length, duration: 0, fps: 0, maxGapMs: 0 };
  let maxGap = 0;
  for (let i = 1; i < times.length; i++) maxGap = Math.max(maxGap, times[i] - times[i - 1]);
  const duration = times[times.length - 1] - times[0];
  return { frames: times.length, duration, fps: (times.length - 1) / duration, maxGapMs: maxGap * 1000 };
};

// ==================== Проверка ====================

const pad = (value, width) => String(value).padEnd(width);
const num = (value, digits = 2) => Number(value).toFixed(digits).replace('.', ',');

const main = async () => {
  const username = await login();
  const cameras = await (await api('/api/video/cameras')).json();
  if (cameras.length === 0) throw new Error('На сервере нет камер');
  console.log(`Сервер ${BASE}, пользователь ${username}`);
  console.log(`Камеры: ${cameras.map(c => `${c.name} (${c.rtspUrl})`).join('; ')}`);
  console.log(`Зрителей на камеру: ${VIEWERS}, длительность ${SECONDS} с${args.record ? ', параллельно запись по событию «Выдача»' : ''}\n`);

  const abort = new AbortController();
  const durationMs = SECONDS * 1000;
  const viewers = cameras.flatMap(camera => Array.from({ length: VIEWERS }, (_, i) => watch(camera, i + 1, durationMs, abort.signal)));

  let record = null;
  if (args.record) {
    const stationId = cameras.find(c => c.stationId !== null)?.stationId;
    if (!stationId) throw new Error('Камеры не привязаны к станции — записывать нечего');
    await new Promise(resolve => setTimeout(resolve, WARMUP_MS));
    await api('/api/video/events', { method: 'POST', body: { stationId, eventType: 'issue', phase: 'start' } });
    await new Promise(resolve => setTimeout(resolve, Math.max(5000, durationMs - WARMUP_MS - 4000)));
    record = await (await api('/api/video/events', { method: 'POST', body: { stationId, eventType: 'issue', phase: 'stop' } })).json();
  }

  const results = await Promise.all(viewers);
  abort.abort();
  const live = await (await api('/api/video/live')).json().catch(() => []);

  let passed = true;
  console.log('Живой поток у зрителей (после разгона 3 с):');
  console.log(`  ${pad('Камера', 8)}${pad('Зритель', 9)}${pad('Кадров', 8)}${pad('к/с', 8)}${pad('мин. за 1 с', 13)}${pad('макс. пауза', 13)}${pad('1-й кадр', 10)}Итог`);
  for (const viewer of results) {
    const stats = steadyStats(viewer.frames);
    const ok = !viewer.error && stats.fps >= MIN_FPS * TOLERANCE;
    passed &&= ok;
    console.log(
      `  ${pad(viewer.camera, 8)}${pad(viewer.index, 9)}${pad(stats.frames, 8)}${pad(num(stats.fps), 8)}${pad(stats.minWindowFps, 13)}` +
        `${pad(`${Math.round(stats.maxGapMs)} мс`, 13)}${pad(viewer.firstFrameMs === null ? '—' : `${Math.round(viewer.firstFrameMs)} мс`, 10)}` +
        `${ok ? 'OK' : `НЕТ${viewer.error ? `: ${viewer.error}` : ''}`}`,
    );
  }
  if (live.length) {
    console.log('\nСервер (одно подключение к каждой камере на всех зрителей):');
    for (const channel of live) {
      console.log(`  ${channel.cameraName}: ${num(channel.fps, 1)} к/с с камеры, ${num(channel.bitrateMbps)} Мбит/с, зрителей ${channel.viewers}, пропущено кадров ${channel.droppedFrames}`);
    }
  }

  if (record) {
    console.log(`\nЗапись по событию № ${record.number}: ${record.status}, ${num(record.durationSeconds ?? 0, 1)} с`);
    const dir = mkdtempSync(join(tmpdir(), 'video-load-'));
    try {
      for (const file of record.files) {
        if (file.status !== 'done') {
          passed = false;
          console.log(`  ${file.cameraName}: файл не записан — ${file.errorMessage ?? 'нет причины'}`);
          continue;
        }
        const path = join(dir, file.fileName);
        writeFileSync(path, Buffer.from(await (await api(file.downloadUrl)).arrayBuffer()));
        const probe = probeFile(path);
        const ok = probe.fps >= MIN_FPS * TOLERANCE && probe.maxGapMs < 100;
        passed &&= ok;
        console.log(
          `  ${pad(file.cameraName, 8)}кадров ${probe.frames}, ${num(probe.duration, 1)} с, ${num(probe.fps)} к/с, макс. пауза ${Math.round(probe.maxGapMs)} мс — ${ok ? 'OK' : 'НЕТ'}`,
        );
      }
    } finally {
      rmSync(dir, { recursive: true, force: true });
    }
  }

  console.log(`\nИтог: ${passed ? 'ПРОЙДЕНО' : 'НЕ ПРОЙДЕНО'} — порог ${MIN_FPS} к/с (допуск 1%)`);
  process.exit(passed ? 0 : 1);
};

main().catch(error => {
  console.error(`Ошибка: ${error.message}`);
  process.exit(2);
});
