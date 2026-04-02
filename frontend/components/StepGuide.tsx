'use client';

import Link from 'next/link';
import { ArrowRight } from 'lucide-react';

interface Props {
  title: string;
  description: string;
  nextHref: string;
  nextLabel: string;
}

export default function StepGuide({ title, description, nextHref, nextLabel }: Props) {
  return (
    <div className="mb-8 rounded-[20px] border border-black/10 bg-surface p-5 sm:p-6 shadow-2xs">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div className="min-w-0 flex-1">
          <h2 className="text-lg font-bold text-foreground">{title}</h2>
          <p className="mt-1 text-xs text-muted leading-relaxed">{description}</p>
        </div>

        <Link
          href={nextHref}
          className="inline-flex items-center gap-1.5 shrink-0 rounded-xl bg-foreground px-4 py-2.5 text-xs font-bold text-white transition hover:bg-brand hover:-translate-y-0.5 shadow-xs"
        >
          <span>{nextLabel}</span>
          <ArrowRight className="h-3.5 w-3.5" />
        </Link>
      </div>
    </div>
  );
}
