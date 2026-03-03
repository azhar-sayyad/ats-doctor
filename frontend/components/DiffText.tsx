'use client';

import { useMemo } from 'react';
import { diffWords } from '../lib/diff';

interface DiffTextProps {
  original: string;
  tailored: string;
  /** full → show strikethrough removed words; effective → show only the kept text (added words highlighted). */
  mode?: 'full' | 'effective';
}

export default function DiffText({ original, tailored, mode = 'full' }: DiffTextProps) {
  const tokens = useMemo(() => diffWords(original, tailored), [original, tailored]);

  return (
    <>
      {tokens.map((token, i) => {
        if (mode === 'effective' && token.type === 'removed') {
          return null;
        }
        return (
          <span
            key={i}
            className={
              token.type === 'added'
                ? 'rounded-[3px] bg-brand/10 text-brand font-semibold'
                : token.type === 'removed'
                  ? 'text-muted line-through decoration-black/30'
                  : ''
            }
          >
            {token.text}
          </span>
        );
      })}
    </>
  );
}
