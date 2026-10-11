/** Exact decimal input for minor currency units or percentage basis points. */
export function reviewHundredths(value: string, max: number, positive = false): number {
  const normalized = value.trim().replace(',', '.');
  if (!/^\d+(\.\d{1,2})?$/.test(normalized)) throw new Error('DECIMAL_REQUIRED');
  const [whole, fraction = ''] = normalized.split('.');
  const n = Number(whole) * 100 + Number(fraction.padEnd(2, '0'));
  if (!Number.isSafeInteger(n) || n > max || (positive && n <= 0)) throw new Error('VALUE_OUT_OF_RANGE');
  return n;
}
