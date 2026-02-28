export type DiffTokenType = 'added' | 'removed' | 'same';

export interface DiffToken {
  text: string;
  type: DiffTokenType;
}

const normalize = (token: string): string => {
  const trimmed = token.trim();
  return trimmed === '' ? '\u0000' : trimmed.toLowerCase();
};

/** Word-level LCS diff between two strings, preserving whitespace runs. */
export function diffWords(original: string, tailored: string): DiffToken[] {
  const a = tokenize(original);
  const b = tokenize(tailored);
  if (a.length === 0 && b.length === 0) return [];
  if (a.length === 0) return [{ text: tailored, type: 'added' }];
  if (b.length === 0) return [{ text: original, type: 'removed' }];

  const m = a.length;
  const n = b.length;
  const lcs: number[][] = Array.from({ length: m + 1 }, () => new Array(n + 1).fill(0));
  for (let i = m - 1; i >= 0; i--) {
    for (let j = n - 1; j >= 0; j--) {
      lcs[i][j] =
        normalize(a[i]) === normalize(b[j])
          ? lcs[i + 1][j + 1] + 1
          : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
    }
  }

  const chunks: DiffToken[] = [];
  let i = 0;
  let j = 0;
  while (i < m && j < n) {
    if (normalize(a[i]) === normalize(b[j])) {
      push(chunks, a[i], 'same');
      i++;
      j++;
    } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
      push(chunks, a[i], 'removed');
      i++;
    } else {
      push(chunks, b[j], 'added');
      j++;
    }
  }
  while (i < m) {
    push(chunks, a[i], 'removed');
    i++;
  }
  while (j < n) {
    push(chunks, b[j], 'added');
    j++;
  }
  return chunks;
}

function push(chunks: DiffToken[], token: string, type: DiffTokenType) {
  const last = chunks[chunks.length - 1];
  if (last && last.type === type) {
    last.text += token;
  } else {
    chunks.push({ text: token, type });
  }
}

function tokenize(text: string): string[] {
  const matches = text.match(/\S+|\s+/g);
  return matches ?? [];
}
