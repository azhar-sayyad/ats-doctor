'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { ChevronDown, LayoutGrid } from 'lucide-react';

const PAGE_LABELS: Record<string, string> = {
  '/dashboard': 'Dashboard Hub',
  '/resume': 'Master Resume',
  '/jobs': 'Target Jobs',
  '/analyses': 'Match Analyses',
  '/tailored': 'Tailored Resumes',
  '/job-scan': 'Job Scan',
  '/profile': 'Profile & Settings',
  '/tailor': 'Tailor Wizard',
};

export default function DashboardTopBar() {
  const pathname = usePathname();

  const label = Object.entries(PAGE_LABELS)
    .sort((a, b) => b[0].length - a[0].length)
    .find(([href]) => pathname === href || pathname.startsWith(href + '/'))?.[1];

  return (
    <header className="sticky top-0 z-40 border-b border-black/10 bg-white/90 backdrop-blur-xl">
      <div className="flex h-16 items-center justify-between gap-3 px-4 sm:px-6">
        <div className="flex min-w-0 items-center gap-2.5">
          <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-md bg-brand-soft text-brand">
            <LayoutGrid className="h-3.5 w-3.5" />
          </span>
          <span className="font-mono text-xs font-semibold uppercase tracking-[0.2em] text-faint">
            Workspace
          </span>
          {label && (
            <>
              <span className="text-faint">/</span>
              <span className="truncate text-sm font-semibold text-foreground">{label}</span>
            </>
          )}
        </div>

        <Link
          href="/profile"
          className="group inline-flex shrink-0 items-center gap-2.5 rounded-full border border-black/10 bg-surface py-1.5 pl-1.5 pr-3 shadow-2xs transition hover:border-black/20 hover:bg-white"
          title="Account profile"
        >
          <span className="flex h-8 w-8 items-center justify-center rounded-full bg-foreground text-xs font-bold text-white">
            JD
          </span>
          <span className="hidden text-left sm:block">
            <span className="block text-xs font-bold leading-tight text-foreground">Jane Doe</span>
            <span className="block font-mono text-[10px] leading-tight text-faint">jane.doe@example.com</span>
          </span>
          <ChevronDown className="h-3.5 w-3.5 text-faint transition group-hover:text-foreground" />
        </Link>
      </div>
    </header>
  );
}
