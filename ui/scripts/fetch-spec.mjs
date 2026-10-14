// Saves a running service's live OpenAPI document, pretty-printed so changes show up clearly in diffs.
// Usage: node scripts/fetch-spec.mjs <spec url> <output file>
import { writeFile } from 'node:fs/promises';

const [url, outFile] = process.argv.slice(2);
if (!url || !outFile) {
  console.error('Usage: node scripts/fetch-spec.mjs <spec url> <output file>');
  process.exit(1);
}

const response = await fetch(url);
if (!response.ok) {
  console.error(`GET ${url} failed with ${response.status}; is the service running?`);
  process.exit(1);
}

await writeFile(outFile, JSON.stringify(await response.json(), null, 2) + '\n');
console.log(`Saved ${url} to ${outFile}`);
