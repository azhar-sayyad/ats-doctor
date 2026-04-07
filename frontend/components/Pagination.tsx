'use client';

import { ChevronLeft, ChevronRight } from 'lucide-react';

/**
 * Prev/next pagination controls for the list pages. Rendered only when the
 * total row count exceeds one page ({@code total > size}); otherwise the
 * single page shows everything and no controls are needed.
 */
export default function Pagination({
  page,
  size,
  total,
  onChange,
}: {
  /** 0-based current page. */
  page: number;
  size: number;
  total: number;
  onChange: (page: number) => void;
}) {
  if (total <= size) {
    return null;
  }
  const lastPage = Math.max(0, Math.ceil(total / size) - 1);
  const from = total === 0 ? 0 : page * size + 1;
  const to = Math.min(total, (page + 1) * size);

  const buttonCls =
    'inline-flex items-center gap-1.5 whitespace-nowrap rounded-lg border border-black/15 px-3 py-1.5 font-mono text-xs font-semibold text-muted transition hover:text-foreground disabled:opacity-40';

  return (
    <div className="mt-5 flex items-center justify-between gap-4">
      <span className="font-mono text-xs font-semibold rounded-full border border-black/10 bg-surface px-3 py-1 text-muted shadow-2xs">
        {from}–{to} of {total}
      </span>
      <div className="flex items-center gap-2">
        <button
          type="button"
          className={buttonCls}
          disabled={page === 0}
          onClick={() => onChange(page - 1)}
        >
          <ChevronLeft className="h-3.5 w-3.5" />
          Prev
        </button>
        <button
          type="button"
          className={buttonCls}
          disabled={page >= lastPage}
          onClick={() => onChange(page + 1)}
        >
          Next
          <ChevronRight className="h-3.5 w-3.5" />
        </button>
      </div>
    </div>
  );
}
