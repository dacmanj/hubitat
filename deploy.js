require('dotenv').config();
const chokidar = require('chokidar');
const fs = require('fs');
const path = require('path');
const http = require('http');
const { URL } = require('url');

const HUB_URL = (process.env.HUBITAT_URL || '').replace(/\/$/, '');
if (!HUB_URL) {
  console.error('HUBITAT_URL not set in .env');
  process.exit(1);
}

const STATE_FILE = path.join(__dirname, '.hubitat.json');

const HUB_HOST = new URL(HUB_URL).hostname;

function findEntry(relpath) {
  const hub = JSON.parse(fs.readFileSync(STATE_FILE, 'utf8'))[HUB_HOST];
  if (!hub) { console.error(`No .hubitat.json entry for hub ${HUB_HOST}`); return null; }
  const entry = hub[relpath];
  if (!entry) return null;
  const type = relpath.split('/')[1].replace(/s$/, ''); // "apps" → "app", "drivers" → "driver"
  return { type, id: entry.id };
}

function get(endpoint) {
  return new Promise((resolve, reject) => {
    const parsed = new URL(endpoint);
    http.get({ hostname: parsed.hostname, port: parsed.port || 80, path: parsed.pathname }, (res) => {
      let buf = '';
      res.on('data', chunk => buf += chunk);
      res.on('end', () => resolve({ status: res.statusCode, body: buf }));
    }).on('error', reject);
  });
}

function post(endpoint, body) {
  return new Promise((resolve, reject) => {
    const parsed = new URL(endpoint);
    const data = Buffer.from(body, 'utf8');
    const req = http.request({
      hostname: parsed.hostname,
      port:     parsed.port || 80,
      path:     parsed.pathname,
      method:   'POST',
      headers:  { 'Content-Type': 'application/json', 'Content-Length': data.length },
    }, (res) => {
      let buf = '';
      res.on('data', chunk => buf += chunk);
      res.on('end', () => resolve({ status: res.statusCode, body: buf }));
    });
    req.on('error', reject);
    req.write(data);
    req.end();
  });
}

async function deploy(filepath) {
  const relpath = path.relative(__dirname, filepath).replace(/\\/g, '/');
  const found = findEntry(relpath);
  if (!found) return;

  const { type, id } = found;

  try {
    const cur = await get(`${HUB_URL}/${type}/list/single/data/${id}`);
    if (cur.status !== 200) {
      console.error(`[${type}] ${relpath} → could not fetch current version (HTTP ${cur.status})`);
      return;
    }
    const { version } = JSON.parse(cur.body)[0];
    const source  = fs.readFileSync(filepath, 'utf8');
    const payload = JSON.stringify({ id, source, version });

    const res = await post(`${HUB_URL}/${type}/saveOrUpdateJson`, payload);
    if (res.status >= 200 && res.status < 300) {
      console.log(`[${type}] ${relpath} → v${version + 1} deployed`);
    } else {
      console.error(`[${type}] ${relpath} → FAILED HTTP ${res.status}: ${res.body}`);
    }
  } catch (err) {
    console.error(`[${type}] ${relpath} → ERROR: ${err.message}`);
  }
}

const pending = new Map();

chokidar.watch('**/*.groovy', { ignoreInitial: true }).on('change', filepath => {
  clearTimeout(pending.get(filepath));
  pending.set(filepath, setTimeout(() => deploy(filepath), 300));
});

console.log(`Watching **/*.groovy — hub: ${HUB_URL}`);
