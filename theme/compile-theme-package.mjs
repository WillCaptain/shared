#!/usr/bin/env node

/**
 * Deterministically compiles a theme source directory into a stored ZIP with
 * the .ones-theme extension. Validation remains the Host/ThemePackageSpec's
 * responsibility; this compiler never makes its own output trusted.
 *
 * Usage:
 *   node shared/theme/compile-theme-package.mjs <source-dir> [output.ones-theme]
 */

import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';

const [, , inputArg, outputArg] = process.argv;
if (!inputArg) {
  console.error('Usage: compile-theme-package.mjs <source-dir> [output.ones-theme]');
  process.exit(2);
}

const inputDir = path.resolve(inputArg);
if (!fs.statSync(inputDir, { throwIfNoEntry: false })?.isDirectory()) {
  throw new Error(`Theme source directory does not exist: ${inputDir}`);
}

function comparePaths(left, right) {
  return left < right ? -1 : left > right ? 1 : 0;
}

function canonicalValue(value) {
  if (Array.isArray(value)) return value.map(canonicalValue);
  if (value && typeof value === 'object') {
    return Object.fromEntries(
      Object.keys(value).sort().map((key) => [key, canonicalValue(value[key])]),
    );
  }
  return value;
}

function canonicalJson(bytes, relativePath) {
  try {
    const parsed = JSON.parse(bytes.toString('utf8'));
    return Buffer.from(`${JSON.stringify(canonicalValue(parsed))}\n`, 'utf8');
  } catch (error) {
    throw new Error(`Invalid JSON in ${relativePath}: ${error.message}`);
  }
}

function safeRelativePath(relativePath) {
  if (!relativePath || relativePath.startsWith('/') || relativePath.includes('\\')) return false;
  return relativePath.split('/').every((part) => part && part !== '.' && part !== '..');
}

function collectFiles(directory, prefix = '') {
  const files = new Map();
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })
    .sort((a, b) => comparePaths(a.name, b.name))) {
    const relativePath = prefix ? `${prefix}/${entry.name}` : entry.name;
    if (!safeRelativePath(relativePath)) throw new Error(`Unsafe package path: ${relativePath}`);
    const absolutePath = path.join(directory, entry.name);
    if (entry.isSymbolicLink()) throw new Error(`Symbolic links are forbidden: ${relativePath}`);
    if (entry.isDirectory()) {
      for (const [nestedPath, content] of collectFiles(absolutePath, relativePath)) {
        files.set(nestedPath, content);
      }
      continue;
    }
    if (!entry.isFile()) throw new Error(`Non-regular file is forbidden: ${relativePath}`);
    if (relativePath === 'integrity.json') continue;
    if (relativePath === 'signature.ed25519') {
      throw new Error('Signing is a separate market operation; source signatures are forbidden');
    }
    const raw = fs.readFileSync(absolutePath);
    files.set(relativePath, relativePath.endsWith('.json')
      ? canonicalJson(raw, relativePath)
      : raw);
  }
  return files;
}

function sha256(bytes) {
  return crypto.createHash('sha256').update(bytes).digest('hex');
}

function makeIntegrity(files) {
  const listed = {};
  for (const [relativePath, content] of [...files.entries()]
    .sort(([a], [b]) => comparePaths(a, b))) {
    listed[relativePath] = { sha256: sha256(content), size: content.length };
  }
  return Buffer.from(`${JSON.stringify(canonicalValue({
    schema_version: 1,
    algorithm: 'sha256',
    files: listed,
  }))}\n`, 'utf8');
}

const CRC_TABLE = (() => {
  const table = new Uint32Array(256);
  for (let n = 0; n < 256; n += 1) {
    let value = n;
    for (let bit = 0; bit < 8; bit += 1) {
      value = (value & 1) ? (0xedb88320 ^ (value >>> 1)) : (value >>> 1);
    }
    table[n] = value >>> 0;
  }
  return table;
})();

function crc32(bytes) {
  let value = 0xffffffff;
  for (const byte of bytes) value = CRC_TABLE[(value ^ byte) & 0xff] ^ (value >>> 8);
  return (value ^ 0xffffffff) >>> 0;
}

function localHeader(name, content, checksum) {
  const nameBytes = Buffer.from(name, 'utf8');
  const header = Buffer.alloc(30);
  header.writeUInt32LE(0x04034b50, 0);
  header.writeUInt16LE(20, 4);
  header.writeUInt16LE(0x0800, 6);
  header.writeUInt16LE(0, 8);
  header.writeUInt16LE(0, 10);
  header.writeUInt16LE(0x21, 12);
  header.writeUInt32LE(checksum, 14);
  header.writeUInt32LE(content.length, 18);
  header.writeUInt32LE(content.length, 22);
  header.writeUInt16LE(nameBytes.length, 26);
  header.writeUInt16LE(0, 28);
  return Buffer.concat([header, nameBytes, content]);
}

function centralHeader(name, content, checksum, localOffset) {
  const nameBytes = Buffer.from(name, 'utf8');
  const header = Buffer.alloc(46);
  header.writeUInt32LE(0x02014b50, 0);
  header.writeUInt16LE(20, 4);
  header.writeUInt16LE(20, 6);
  header.writeUInt16LE(0x0800, 8);
  header.writeUInt16LE(0, 10);
  header.writeUInt16LE(0, 12);
  header.writeUInt16LE(0x21, 14);
  header.writeUInt32LE(checksum, 16);
  header.writeUInt32LE(content.length, 20);
  header.writeUInt32LE(content.length, 24);
  header.writeUInt16LE(nameBytes.length, 28);
  header.writeUInt16LE(0, 30);
  header.writeUInt16LE(0, 32);
  header.writeUInt16LE(0, 34);
  header.writeUInt16LE(0, 36);
  header.writeUInt32LE(0, 38);
  header.writeUInt32LE(localOffset, 42);
  return Buffer.concat([header, nameBytes]);
}

function createZip(files) {
  const localParts = [];
  const centralParts = [];
  let localOffset = 0;
  for (const [name, content] of [...files.entries()]
    .sort(([a], [b]) => comparePaths(a, b))) {
    const checksum = crc32(content);
    const local = localHeader(name, content, checksum);
    localParts.push(local);
    centralParts.push(centralHeader(name, content, checksum, localOffset));
    localOffset += local.length;
  }
  const central = Buffer.concat(centralParts);
  const end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50, 0);
  end.writeUInt16LE(0, 4);
  end.writeUInt16LE(0, 6);
  end.writeUInt16LE(files.size, 8);
  end.writeUInt16LE(files.size, 10);
  end.writeUInt32LE(central.length, 12);
  end.writeUInt32LE(localOffset, 16);
  end.writeUInt16LE(0, 20);
  return Buffer.concat([...localParts, central, end]);
}

const files = collectFiles(inputDir);
if (!files.has('manifest.json')) throw new Error('manifest.json is required');
if (!files.has('LICENSE.txt')) throw new Error('LICENSE.txt is required');
files.set('integrity.json', makeIntegrity(files));

const manifest = JSON.parse(files.get('manifest.json').toString('utf8'));
const defaultName = `${manifest.package_id}-${manifest.version}.ones-theme`;
const outputPath = path.resolve(outputArg || path.join(path.dirname(inputDir), defaultName));
if (!outputPath.endsWith('.ones-theme')) throw new Error('Output must use .ones-theme extension');

const packageBytes = createZip(files);
fs.writeFileSync(outputPath, packageBytes, { flag: 'w' });
console.log(JSON.stringify({
  output: outputPath,
  package_id: manifest.package_id,
  version: manifest.version,
  size: packageBytes.length,
  sha256: sha256(packageBytes),
}));
