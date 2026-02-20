'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useEffect, useState } from 'react';
import { apiGet, ApiError, type AiConfig } from '../lib/api';
import { ArrowRight, Sparkles, ShieldCheck } from 'lucide-react';

const NAV_LINKS = [
  { href: '/resume', label: 'Resume' },
  { href: '/jobs', label: 'Jobs' },
  { href: '/analyses', label: 'Analyses' },
  { href: '/tailored', label: 'Tailored' },
];

export default function Navbar() {
  const pathname = usePathname();
  const [ai, setAi] = useState<AiConfig | null>(null);

  useEffect(() => {
    let active = true;
    apiGet<AiConfig>('/ai/config')
      .then((config) => {
        if (active) setAi(config);
      })
      .catch((err: unknown) => {
        if (!active) return;
        const status = err instanceof ApiError ? err.status : undefined;
        if (status !== 404) setAi(null);
      });
    return () => {
      active = false;
    };
  }, []);

  const isActive = (href: string) =>
    href === '/tailored'
      ? pathname === '/tailored' || pathname.startsWith('/tailored/')
      : pathname === href || pathname.startsWith(href + '/');

  const providerLabel = ai
    ? ai.mode === 'stub'
      ? 'Dev Stub'
      : ai.mode === 'omniroute'
        ? `OmniRoute · ${ai.provider ?? 'gateway'}`
        : `${ai.mode} · ${ai.provider ?? ''}`
    : 'AI Provider';

  if (pathname !== '/') {
    // Hide default top navbar on dashboard routes since Sidebar provides primary navigation
    return null;
  }

  return (
    <header className="sticky top-0 z-50 border-b border-black/[0.08] bg-white/90 backdrop-blur-xl">
      <nav className="mx-auto flex h-16 max-w-[1200px] items-center justify-between gap-3 px-4 sm:px-6">
        <Link href="/" className="flex items-center gap-2.5" aria-label="ATSDoctor home">
          <span
            aria-hidden="true"
            className="relative inline-flex h-8 w-8 items-center justify-center rounded-md bg-foreground text-sm font-bold text-white shadow-sm"
          >
            A
            <span className="absolute inset-y-0 right-1 w-0.5 bg-proof" />
          </span>
          <span className="text-lg font-bold tracking-[-0.04em]">ATSDoctor</span>
        </Link>

        {/* Navigation links */}
        <div className="hidden items-center gap-1 text-sm font-medium text-muted md:flex">
          <Link
            href="/"
            className={`rounded-lg px-3 py-1.5 transition ${
              pathname === '/' ? 'text-foreground font-semibold bg-black/[0.04]' : 'hover:text-foreground'
            }`}
          >
            Home
          </Link>
          {NAV_LINKS.map((link) => (
            <Link
              key={link.href}
              href={link.href}
              className={`rounded-lg px-3 py-1.5 transition ${
                isActive(link.href)
                  ? 'text-brand font-semibold bg-brand-soft'
                  : 'hover:text-foreground'
              }`}
            >
              {link.label}
            </Link>
          ))}
        </div>

        {/* Right side widgets & CTAs */}
        <div className="flex items-center gap-3">
          {/* AI Provider Status */}
          <div
            className="hidden sm:inline-flex items-center gap-2 rounded-full border border-black/10 bg-surface px-3 py-1 font-mono text-[11px] font-semibold text-muted shadow-2xs"
            title="Active AI provider profile — GET /api/v1/ai/config"
          >
            <span
              className={`h-2 w-2 rounded-full ${
                ai === null
                  ? 'bg-black/20 animate-pulse'
                  : ai.mode === 'stub'
                    ? 'bg-emerald-500'
                    : ai.data_leaves_machine
                      ? 'bg-amber-500'
                      : 'bg-brand'
              }`}
            />
            <span>{providerLabel}</span>
          </div>

          {ai && ai.data_leaves_machine && (
            <span
              className="hidden lg:inline-flex items-center gap-1 rounded-full border border-amber-200 bg-amber-50 px-2.5 py-0.5 font-mono text-[10px] font-semibold text-amber-700"
              title="AI requests leave this machine"
            >
              <ShieldCheck className="h-3 w-3 text-amber-600" />
              Cloud AI
            </span>
          )}

          <Link
            href="/analyses"
            className="inline-flex shrink-0 items-center gap-2 whitespace-nowrap rounded-[10px] bg-foreground px-4 py-2 text-sm font-semibold text-white transition hover:-translate-y-0.5 hover:bg-brand shadow-sm"
          >
            <Sparkles className="h-4 w-4 text-proof" />
            <span>Get started</span>
            <ArrowRight className="h-4 w-4" />
          </Link>
        </div>
      </nav>
    </header>
  );
}