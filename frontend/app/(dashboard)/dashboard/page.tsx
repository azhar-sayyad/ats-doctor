'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import {
  getCurrentResume,
  getAnalyses,
  getTailoredResumes,
  deleteJob,
  type ResumeVersion,
  type Analysis,
  type TailoredResume,
  API_BASE_URL,
} from '../../../lib/api';
import {
  FileText,
  Briefcase,
  Target,
  Sparkles,
  ArrowRight,
  CheckCircle2,
  AlertCircle,
  UploadCloud,
  Plus,
  Search,
  Wand2,
  Download,
  Trash2,
  Eye,
  Award,
  Zap,
} from 'lucide-react';

export default function DashboardHubPage() {
  const [resume, setResume] = useState<ResumeVersion | null>(null);
  const [analyses, setAnalyses] = useState<Analysis[] | null>(null);
  const [tailored, setTailored] = useState<TailoredResume[] | null>(null);
  const [tailoredTotal, setTailoredTotal] = useState(0);

  useEffect(() => {
    getCurrentResume()
      .then(setResume)
      .catch(() => setResume(null));

    // Score stat averages over all runs; recent table needs only the latest 6.
    getAnalyses({ size: 1000 })
      .then((res) => setAnalyses(res.items))
      .catch(() => setAnalyses(null));

    getTailoredResumes({ size: 6 })
      .then((res) => {
        setTailored(res.items);
        setTailoredTotal(res.total);
      })
      .catch(() => setTailored(null));
  }, []);

  const totalTailored = tailoredTotal;
  const avgScore =
    analyses && analyses.length > 0
      ? Math.round(
          analyses
            .filter((a) => a.score !== null)
            .reduce((acc, curr) => acc + (curr.score ?? 0), 0) /
            (analyses.filter((a) => a.score !== null).length || 1),
        )
      : 0;

  return (
    <div className="mx-auto max-w-6xl px-4 py-10 sm:px-6 space-y-10">
      {/* Top Header Bar */}
      <div className="border-b border-black/10 pb-6 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <div className="inline-flex items-center gap-2 rounded-full border border-black/10 bg-surface px-3 py-1 font-mono text-[10px] font-semibold uppercase tracking-[0.16em] text-muted shadow-2xs">
            <span className="h-2 w-2 rounded-full bg-brand animate-pulse" />
            ATSDoctor Workspace Control Panel
          </div>
          <h1 className="mt-3 text-3xl font-bold tracking-[-0.04em]">Applications Dashboard</h1>
          <p className="mt-1 text-sm text-muted">
            Manage your master career resume, analyze target job postings, and export ATS-optimized applications.
          </p>
        </div>

      </div>

      {/* Hero Quick Action Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <Link
          href="/tailor"
          className="group rounded-[24px] border border-brand/30 bg-brand-soft/30 p-6 transition duration-200 hover:-translate-y-1 hover:border-brand shadow-xs flex items-center justify-between"
        >
          <div>
            <div className="inline-flex items-center gap-1.5 rounded-full bg-brand px-2.5 py-0.5 font-mono text-[10px] font-bold text-white uppercase tracking-wider">
              <Wand2 className="h-3 w-3" /> Core Action
            </div>
            <h2 className="mt-3 text-xl font-bold text-foreground">Tailor a Job Wizard</h2>
            <p className="mt-1 text-xs text-muted max-w-sm">
              Customize your Master Resume for a specific role in 3 simple steps with live diffs.
            </p>
          </div>
          <ArrowRight className="h-6 w-6 text-brand transition group-hover:translate-x-1 shrink-0" />
        </Link>

        <Link
          href="/job-scan"
          className="group rounded-[24px] border border-black/10 bg-surface p-6 transition duration-200 hover:-translate-y-1 hover:border-black/20 shadow-xs flex items-center justify-between"
        >
          <div>
            <div className="inline-flex items-center gap-1.5 rounded-full bg-foreground px-2.5 py-0.5 font-mono text-[10px] font-bold text-white uppercase tracking-wider">
              <Search className="h-3 w-3" /> Diagnostic Tool
            </div>
            <h2 className="mt-3 text-xl font-bold text-foreground">Job Scan Diagnostic</h2>
            <p className="mt-1 text-xs text-muted max-w-sm">
              Run instant ATS compatibility scan to detect missing hard skills and format issues.
            </p>
          </div>
          <ArrowRight className="h-6 w-6 text-foreground transition group-hover:translate-x-1 shrink-0" />
        </Link>
      </div>

      {/* Metrics & Analytics Bar */}
      <section className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div className="rounded-[24px] border border-black/10 bg-white p-6 shadow-xs">
          <p className="font-mono text-[10px] font-bold uppercase tracking-widest text-faint">
            Total Resumes Tailored
          </p>
          <p className="font-editorial mt-2 text-4xl font-normal text-foreground">
            {totalTailored}
          </p>
          <p className="mt-1 text-xs text-muted">Role-optimized rewrite runs</p>
        </div>

        <div className="rounded-[24px] border border-black/10 bg-white p-6 shadow-xs">
          <p className="font-mono text-[10px] font-bold uppercase tracking-widest text-brand">
            Average ATS Match Score
          </p>
          <p className="font-editorial mt-2 text-4xl font-normal text-brand">
            {avgScore}%
          </p>
          <p className="mt-1 text-xs text-muted">Across target job applications</p>
        </div>

        <div className="rounded-[24px] border border-black/10 bg-white p-6 shadow-xs">
          <p className="font-mono text-[10px] font-bold uppercase tracking-widest text-faint">
            User Credits & Mode
          </p>
          <p className="font-editorial mt-2 text-3xl font-normal text-foreground flex items-center gap-2">
            <Zap className="h-6 w-6 text-brand" />
            Unlimited
          </p>
          <p className="mt-1 text-xs text-muted">Local-first mode (Private)</p>
        </div>
      </section>

      {/* Recent Applications & Tailored Resumes Data Table */}
      <section className="rounded-[28px] border border-black/10 bg-white p-6 sm:p-7 shadow-xs">
        <div className="mb-6 flex items-center justify-between">
          <div>
            <h2 className="text-lg font-bold text-foreground">Recent Applications & Tailored Resumes</h2>
            <p className="text-xs text-muted">Browse past application runs, score improvements, and export artifacts.</p>
          </div>
          <div className="flex items-center gap-3">
            <span className="font-mono text-xs font-semibold rounded-full border border-black/10 bg-surface px-3 py-1 text-muted">
              {tailored === null ? '…' : tailoredTotal} applications
            </span>
            <Link
              href="/tailored"
              className="inline-flex items-center gap-1.5 rounded-lg border border-black/15 px-3 py-1.5 text-xs font-semibold text-foreground transition hover:bg-surface"
            >
              View all
              <ArrowRight className="h-3.5 w-3.5" />
            </Link>
          </div>
        </div>

        {tailored === null ? (
          <div className="py-8 text-center font-mono text-xs text-faint animate-pulse">
            Loading applications data…
          </div>
        ) : tailored.length === 0 ? (
          <div className="rounded-2xl border border-dashed border-black/20 p-10 text-center text-muted">
            <FileText className="mx-auto h-8 w-8 text-faint mb-3" />
            <p className="font-mono text-xs uppercase tracking-widest text-faint">No Tailored Resumes Yet</p>
            <p className="mt-1 text-xs">Run a match analysis or use &quot;Tailor a Job&quot; wizard to build your first tailored resume.</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead>
                <tr className="border-b border-black/10 font-mono text-[10px] uppercase text-faint">
                  <th className="pb-3 font-bold">Run ID</th>
                  <th className="pb-3 font-bold">Date Tailored</th>
                  <th className="pb-3 font-bold">ATS Score Delta</th>
                  <th className="pb-3 font-bold">Status</th>
                  <th className="pb-3 font-bold text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-black/5">
                {tailored.map((item) => (
                  <tr key={item.id} className="group hover:bg-surface/50 transition">
                    <td className="py-4 font-mono font-bold text-foreground">
                      Run #{item.id.slice(0, 8)}
                    </td>
                    <td className="py-4 text-muted">
                      {new Date(item.created_at).toLocaleDateString()}
                    </td>
                    <td className="py-4">
                      <span className="font-mono text-xs font-bold text-foreground">
                        {item.score_before ?? '—'} → <span className="text-brand">{item.score_after ?? '—'}</span>
                      </span>
                    </td>
                    <td className="py-4">
                      <span
                        className={`rounded-full px-2.5 py-0.5 font-mono text-[9px] uppercase tracking-wider font-bold ${
                          item.state === 'APPROVED'
                            ? 'bg-foreground text-white'
                            : item.state === 'READY'
                              ? 'bg-proof text-proof-ink'
                              : 'bg-brand-soft text-brand'
                        }`}
                      >
                        {item.state}
                      </span>
                    </td>
                    <td className="py-4 text-right">
                      <div className="inline-flex items-center gap-2">
                        <Link
                          href={`/tailored/${item.id}`}
                          className="rounded-lg border border-black/15 px-3 py-1 text-xs font-semibold text-foreground hover:bg-surface"
                        >
                          View / Edit
                        </Link>
                        <a
                          href={`${API_BASE_URL}/tailored/${item.id}/export/pdf`}
                          target="_blank"
                          rel="noreferrer"
                          className="inline-flex items-center gap-1 rounded-lg bg-foreground px-3 py-1 text-xs font-bold text-white hover:bg-brand transition"
                        >
                          <Download className="h-3 w-3 text-proof" />
                          <span>PDF</span>
                        </a>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}
