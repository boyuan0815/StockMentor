import type { BasicAuthCredentials } from '@/types/auth';

const BASE64_ALPHABET = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';

function toUtf8Bytes(value: string) {
  if (typeof TextEncoder !== 'undefined') {
    return Array.from(new TextEncoder().encode(value));
  }

  const bytes: number[] = [];

  for (const character of value) {
    const codePoint = character.codePointAt(0) ?? 0;

    if (codePoint <= 0x7f) {
      bytes.push(codePoint);
    } else if (codePoint <= 0x7ff) {
      bytes.push(0xc0 | (codePoint >> 6), 0x80 | (codePoint & 0x3f));
    } else if (codePoint <= 0xffff) {
      bytes.push(
        0xe0 | (codePoint >> 12),
        0x80 | ((codePoint >> 6) & 0x3f),
        0x80 | (codePoint & 0x3f),
      );
    } else {
      bytes.push(
        0xf0 | (codePoint >> 18),
        0x80 | ((codePoint >> 12) & 0x3f),
        0x80 | ((codePoint >> 6) & 0x3f),
        0x80 | (codePoint & 0x3f),
      );
    }
  }

  return bytes;
}

function encodeBase64(value: string) {
  const bytes = toUtf8Bytes(value);
  let output = '';

  for (let index = 0; index < bytes.length; index += 3) {
    const first = bytes[index];
    const second = bytes[index + 1];
    const third = bytes[index + 2];

    output += BASE64_ALPHABET[first >> 2];
    output += BASE64_ALPHABET[((first & 3) << 4) | ((second ?? 0) >> 4)];
    output += second === undefined ? '=' : BASE64_ALPHABET[((second & 15) << 2) | ((third ?? 0) >> 6)];
    output += third === undefined ? '=' : BASE64_ALPHABET[third & 63];
  }

  return output;
}

export function createBasicAuthHeader(credentials: BasicAuthCredentials) {
  return `Basic ${encodeBase64(`${credentials.username}:${credentials.password}`)}`;
}

export function applyAuthHeaders(
  headers: Headers,
  credentials?: BasicAuthCredentials | null,
  adminToken?: string | null,
) {
  if (credentials) {
    headers.set('Authorization', createBasicAuthHeader(credentials));
  }

  if (adminToken) {
    headers.set('X-Admin-Token', adminToken);
  }
}
