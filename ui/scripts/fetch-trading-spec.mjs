// Saves the trading API's OpenAPI spec into contracts/, so clients can be generated without a running backend.
// Usage: npm run fetch:trading-spec   (the trading API must be running)
import { writeFile } from 'node:fs/promises';

const url = process.env.TRADING_API_DOCS ?? 'http://localhost:8081/api/v3/api-docs';
const out = new URL('../../contracts/trading-api.json', import.meta.url);

let response;
try {
  response = await fetch(url);
} catch {
  console.error(`Couldn't reach ${url}. Is the trading API running? (docker-compose up -d app)`);
  process.exit(1);
}
if (!response.ok) {
  console.error(`${url} answered ${response.status} ${response.statusText}`);
  process.exit(1);
}

const spec = await response.json();
await writeFile(out, JSON.stringify(spec, null, 2) + '\n');
console.log('Saved the trading API spec to contracts/trading-api.json');
