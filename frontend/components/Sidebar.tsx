'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useEffect, useState } from 'react';
import {
  apiGet,
  getCurrentResume,
  getJobs,
  getAnalyses,
  getTailoredResumes,
  type AiConfig,
  type ResumeVersion,
  type Job,
  type Analysis,
  type TailoredResume,
} from '../lib/api';
import {
  FileText,
  Briefcase,
  Target,
  Sparkles,
  LayoutDashboard,
  ShieldCheck,
  Search,
  Wand2,
  UserCircle2,
} from 'lucide-react';

export default function Sidebar() {
  const pathname = usePathname();
  const [ai, setAi] = useState<AiConfig | null>(null);
  const [resume, setResume] = useState<ResumeVersion | null>(null);
  const [jobs, setJobs] = useState<Job[] | null>(null);
  const [analyses, setAnalyses] = useState<Analysis[] | null>(null);
  const [tailored, setTailored] = useState<TailoredResume[] | null>(null);

  useEffect(() => {
    apiGet<AiConfig>('/ai/config')
      .then(setAi)
      .catch(() => setAi(null));

    getCurrentResume()
      .then(setResume)
      .catch(() => setResume(null));

    getJobs({ size: 1000 })
      .then((r) => setJobs(r.items))
      .catch(() => setJobs(null));

    getAnalyses({ size: 1000 })
      .then((r) => setAnalyses(r.items))
      .catch(() => setAnalyses(null));

    getTailoredResumes({ size: 1000 })
      .then((r) => setTailored(r.items))
      .catch(() => setTailored(null));
  }, [pathname]);

  const isActive = (href: string) =>
    href === '/'
      ? pathname === '/'
      : href === '/tailored'
        ? pathname === '/tailored' || pathname.startsWith('/tailored/')
        : pathname === href || pathname.startsWith(href + '/');

  const navItems = [
    {
      label: 'Master Resume',
      href: '/resume',
      icon: FileText,
      badge: resume ? `v${resume.version} ${resume.state}` : 'Upload required',
      ready: Boolean(resume && resume.state === 'READY'),
    },
    {
      label: 'Target Jobs',
      href: '/jobs',
      icon: Briefcase,
      badge: jobs === null ? '…' : `${jobs.length} saved`,
      ready: Boolean(jobs && jobs.length > 0),
    },
    {
      label: 'Match Analyses',
      href: '/analyses',
      icon: Target,
      badge: analyses === null ? '…' : `${analyses.length} runs`,
      ready: Boolean(analyses && analyses.length > 0),
    },
    {
      label: 'Tailored Resumes',
      href: '/tailored',
      icon: Sparkles,
      badge: tailored === null ? '…' : `${tailored.length} runs`,
      ready: Boolean(tailored && tailored.length > 0),
    },
    {
      label: 'Job Scan',
      href: '/job-scan',
      icon: Search,
      badge: null,
      ready: false,
    },
    {
      label: 'Tailor Wizard',
      href: '/tailor',
      icon: Wand2,
      badge: null,
      ready: false,
    },
  ];

  const providerLabel = ai
    ? ai.mode === 'stub'
      ? 'Dev Stub Mode'
      : ai.mode === 'omniroute'
        ? `OmniRoute · ${ai.provider ?? 'gateway'}`
        : `${ai.mode} · ${ai.provider ?? ''}`
    : 'AI Provider';

  return (
    <aside className="w-64 shrink-0 border-r border-black/10 bg-surface flex flex-col min-h-screen">
      {/* Brand Header */}
      <div className="p-6 border-b border-black/10">
        <Link href="/" className="flex items-center gap-2.5">
          <span className="relative inline-flex h-8 w-8 items-center justify-center rounded-md bg-foreground text-sm font-bold text-white shadow-sm">
            A
            <span className="absolute inset-y-0 right-1 w-0.5 bg-proof" />
          </span>
          <span className="text-lg font-bold tracking-[-0.04em]">ATSDoctor</span>
        </Link>
        <p className="mt-1 font-mono text-[10px] uppercase tracking-wider text-faint">
          Precision Workspace v1.41
        </p>
      </div>

      {/* Main Workspace Navigation */}
      <div className="flex-1 px-4 py-6 space-y-6 overflow-y-auto">
        <div>
          <p className="px-3 font-mono text-[10px] font-bold uppercase tracking-widest text-faint mb-2">
            Overview
          </p>
          <Link
            href="/dashboard"
            className={`flex items-center gap-2.5 rounded-xl px-3 py-2 text-xs font-semibold transition ${
              isActive('/dashboard')
                ? 'bg-foreground text-white shadow-xs'
                : 'text-muted hover:text-foreground hover:bg-black/[0.04]'
            }`}
          >
            <LayoutDashboard className="h-4 w-4" />
            <span>Dashboard Hub</span>
          </Link>
        </div>

        <div>
          <p className="px-3 font-mono text-[10px] font-bold uppercase tracking-widest text-faint mb-2">
            Workspace
          </p>
          <nav className="space-y-1">
            {navItems.map((s) => {
              const active = isActive(s.href);
              const Icon = s.icon;
              return (
                <Link
                  key={s.href}
                  href={s.href}
                  className={`group relative flex items-center justify-between rounded-xl px-3 py-2.5 transition ${
                    active
                      ? 'bg-brand text-white shadow-xs font-semibold'
                      : 'text-muted hover:text-foreground hover:bg-black/[0.04]'
                  }`}
                >
                  <div className="flex items-center gap-2.5 min-w-0">
                    <Icon className="h-4 w-4 shrink-0" />
                    <span className="truncate text-xs">{s.label}</span>
                  </div>
                  {s.badge !== null && (
                    <span
                      className={`rounded-full px-2 py-0.5 font-mono text-[9px] font-bold shrink-0 ${
                        active
                          ? 'bg-white/20 text-white'
                          : s.ready
                            ? 'bg-proof text-proof-ink'
                            : 'bg-black/[0.05] text-faint'
                      }`}
                    >
                      {s.badge}
                    </span>
                  )}
                </Link>
              );
            })}
          </nav>
        </div>

        <div>
          <p className="px-3 font-mono text-[10px] font-bold uppercase tracking-widest text-faint mb-2">
            Account
          </p>
          <Link
            href="/profile"
            className={`flex items-center gap-2.5 rounded-xl px-3 py-2.5 text-xs transition ${
              isActive('/profile')
                ? 'bg-brand text-white shadow-xs font-semibold'
                : 'text-muted hover:text-foreground hover:bg-black/[0.04]'
            }`}
          >
            <UserCircle2 className="h-4 w-4 shrink-0" />
            <span>Profile</span>
          </Link>
        </div>
      </div>

      {/* AI Privacy & System Status Footer */}
      <div className="p-4 border-t border-black/10 bg-white/50 space-y-3">
        <div className="flex items-center justify-between font-mono text-[11px]">
          <span className="text-faint">AI Engine:</span>
          <span className="font-semibold text-foreground flex items-center gap-1.5">
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
            {providerLabel}
          </span>
        </div>

        <div className="flex items-center gap-1.5 text-[10px] text-muted font-medium bg-surface p-2 rounded-lg border border-black/10">
          <ShieldCheck className="h-3.5 w-3.5 text-brand shrink-0" />
          <span>
            {ai?.data_leaves_machine ? 'Cloud Provider Connected' : 'Private-by-design · Local Only'}
          </span>
        </div>
      </div>
    </aside>
  );
}
