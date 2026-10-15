import { newUuid } from './uuid';

describe('newUuid', () => {
  const pattern = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

  it('returns a v4 UUID', () => {
    expect(newUuid()).toMatch(pattern);
  });

  it('falls back to getRandomValues when randomUUID is unavailable (insecure context)', () => {
    const original = crypto.randomUUID;
    (crypto as { randomUUID?: unknown }).randomUUID = undefined;
    try {
      const first = newUuid(),
        second = newUuid();
      expect(first).toMatch(pattern);
      expect(second).toMatch(pattern);
      expect(first).not.toBe(second);
    } finally {
      crypto.randomUUID = original;
    }
  });
});
