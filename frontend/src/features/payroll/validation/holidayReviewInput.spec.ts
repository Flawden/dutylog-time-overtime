import { describe, expect, it } from 'vitest';
import { reviewHundredths } from './holidayReviewInput';
describe('reviewed holiday money and additional percentages', () => {
  it('accepts a decimal comma without losing a kopeck', () => {
    expect(reviewHundredths(' 125,01 ', 1e12, true)).toBe(12501);
    expect(reviewHundredths('0.29', 1e12, true)).toBe(29);
  });
  it('100 percent means 10000 additional basis points', () => {
    expect(reviewHundredths('100', 1e7)).toBe(10000);
    expect(reviewHundredths('0', 1e7)).toBe(0);
    expect(reviewHundredths('12.5', 1e7)).toBe(1250);
  });
  it('rejects rounded, negative, exponential, blank and partially parsed values', () => {
    for (const value of ['', '-1', '1e2', '0.001', '12abc', '1,2,3', 'NaN']) {
      expect(() => reviewHundredths(value, 1e12)).toThrow();
    }
  });
  it('requires a positive source amount while allowing zero additional premium', () => {
    expect(() => reviewHundredths('0', 1e12, true)).toThrow();
    expect(reviewHundredths('0', 1e7)).toBe(0);
  });
  it('enforces the backend maxima exactly', () => {
    expect(reviewHundredths('10000000000.00', 1e12, true)).toBe(1e12);
    expect(() => reviewHundredths('10000000000.01', 1e12, true)).toThrow();
    expect(() => reviewHundredths('100000.01', 1e7)).toThrow();
  });
});
