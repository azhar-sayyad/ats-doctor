'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import {
  getCurrentResume,
  getJobs,
  createJobFromText,
  createAnalysis,
  tailorAnalysis,
  ApiError,
  type ResumeVersion,
  type Job,
  type Analysis,
  type TailoredResume,
} from '../../../lib/api';
import StepGuide from '../../../components/StepGuide';
import { Wand2, Sparkles, FileText, Briefcase, Download, CheckCircle2, AlertCircle, Loader2, ArrowRight } from 'lucide-react';

export default function TailorWizardPage() {
  const router = useRouter();
  const [resume, setResume] = useState<ResumeVersion | null>(null);
  const [jobs, setJobs] = useState<Job[] | null>(null);
  const [selectedResumeId, setSelectedResumeId] = useState('');
  const [title, setTitle] = useState('');
  const [company, setCompany] = useState('');
  const [jdText, setJdText] = useState('');
  const [step, setStep] = useState<1 | 2 | 3>(1);
  const [tailoring, setTailoring] = useState(false);
  const [tailoredResult, setTailoredResult] = useState<TailoredResume | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getCurrentResume()
      .then((r) => {
        setResume(r);
        setSelectedResumeId(r.id);
      })
      .catch(() => setResume(null));

    getJobs()
      .then(setJobs)
      .catch(() => setJobs(null));
  }, []);

  const runTailorWizard = async () => {
    if (!selectedResumeId) {
      setError('Select a base resume version first.');
      return;
    }
    if (!jdText.trim()) {
      setError('Paste a target job description first.');
      return;
    }
    setError(null);
    setTailoring(true);
    try {
      // 1. Create job
      const job = await createJobFromText(jdText.trim());
      // 2. Queue analysis
      const analysis = await createAnalysis(job.id, selectedResumeId);
      // 3. Queue tailoring run
      const tailored = await tailorAnalysis(analysis.id);
      setTailoredResult(tailored);
      setStep(3);
      router.push(`/tailored/${tailored.id}`);
    } catch (err) {
      setError(err instanceof ApiError ? err.detail ?? err.message : String(err));
    } finally {
      setTailoring(false);
    }
  };

  const inputClass =
    'w-full rounded-xl border border-black/15 bg-white px-3.5 py-2.5 text-sm text-foreground placeholder:text-faint focus:border-brand focus:outline-none focus:ring-1 focus:ring-brand transition';

  return (
    <div className="mx-auto max-w-5xl px-4 py-10 sm:px-6">
      <StepGuide
        step={3}
        title="Tailor a Job Wizard"
        description="Select a base resume, input target job requirements, and generate evidence-grounded ATS rewrites."
        nextHref="/tailored"
        nextLabel="View Tailored Resumes →"
      />

      {/* Header Bar */}
      <div className="mb-8 border-b border-black/10 pb-6">
        <p className="font-mono text-xs font-semibold uppercase tracking-[0.2em] text-brand">
          TAILORING WIZARD
        </p>
        <h1 className="mt-1 text-3xl font-bold tracking-[-0.04em]">Tailor a Job</h1>
        <p className="mt-1 text-sm text-muted">
          Align your experience with target job criteria while staying 100% truthful to your master resume.
        </p>
      </div>

      {error && (
        <div className="mb-6 flex items-center gap-2 rounded-xl border border-coral/30 bg-coral-soft p-4 text-sm text-coral font-medium">
          <AlertCircle className="h-4 w-4 shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {/* Wizard Stepper Header */}
      <div className="mb-8 flex items-center justify-between rounded-2xl bg-surface p-4 border border-black/10">
        {[
          { num: 1, label: '1. Base Resume' },
          { num: 2, label: '2. Job Target' },
          { num: 3, label: '3. Result & Download' },
        ].map((s) => (
          <div
            key={s.num}
            className={`flex items-center gap-2 font-mono text-xs font-bold ${
              step === s.num
                ? 'text-brand'
                : step > s.num
                  ? 'text-proof-ink'
                  : 'text-faint'
            }`}
          >
            <span
              className={`flex h-6 w-6 items-center justify-center rounded-full text-[10px] ${
                step === s.num
                  ? 'bg-brand text-white'
                  : step > s.num
                    ? 'bg-proof text-proof-ink'
                    : 'bg-black/10 text-faint'
              }`}
            >
              {s.num}
            </span>
            <span>{s.label}</span>
          </div>
        ))}
      </div>

      {/* Step 1: Select Base Resume */}
      {step === 1 && (
        <section className="rounded-[28px] border border-black/10 bg-white p-7 shadow-xs space-y-6">
          <h2 className="text-lg font-bold tracking-tight text-foreground flex items-center gap-2">
            <FileText className="h-5 w-5 text-brand" />
            Step 1: Select Base Master Resume
          </h2>

          {resume ? (
            <div className="rounded-2xl border-2 border-brand bg-brand-soft/20 p-5">
              <label className="flex items-start gap-3 cursor-pointer">
                <input
                  type="radio"
                  name="baseResume"
                  checked={selectedResumeId === resume.id}
                  onChange={() => setSelectedResumeId(resume.id)}
                  className="mt-1 accent-brand"
                />
                <div>
                  <p className="font-bold text-foreground text-sm">
                    Master Resume v{resume.version} ({resume.source_filename ?? 'Pasted text'})
                  </p>
                  <p className="mt-1 text-xs text-muted">
                    State: {resume.state} · {resume.evidence_summary.total} evidence claims available for tailoring.
                  </p>
                </div>
              </label>
            </div>
          ) : (
            <p className="text-xs text-coral font-semibold italic">
              No master resume loaded — upload one on the Master Resume page first.
            </p>
          )}

          <div className="flex justify-end">
            <button
              onClick={() => setStep(2)}
              disabled={!selectedResumeId}
              className="inline-flex items-center gap-2 rounded-xl bg-foreground px-6 py-2.5 text-xs font-bold text-white transition hover:bg-brand disabled:opacity-40"
            >
              <span>Next: Input Job Target</span>
              <ArrowRight className="h-3.5 w-3.5" />
            </button>
          </div>
        </section>
      )}

      {/* Step 2: Input Job Target */}
      {step === 2 && (
        <section className="rounded-[28px] border border-black/10 bg-white p-7 shadow-xs space-y-6">
          <h2 className="text-lg font-bold tracking-tight text-foreground flex items-center gap-2">
            <Briefcase className="h-5 w-5 text-brand" />
            Step 2: Input Target Job Posting
          </h2>

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <div>
              <label className="mb-1 block text-xs font-semibold text-muted">Job Title (Optional)</label>
              <input
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                placeholder="Senior Engineer"
                className={inputClass}
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-semibold text-muted">Company Name (Optional)</label>
              <input
                value={company}
                onChange={(e) => setCompany(e.target.value)}
                placeholder="Acme Corp"
                className={inputClass}
              />
            </div>
          </div>

          <div>
            <label className="mb-1 block text-xs font-semibold text-muted">Full Job Description Text</label>
            <textarea
              value={jdText}
              onChange={(e) => setJdText(e.target.value)}
              rows={6}
              placeholder="Paste full job description requirements here..."
              className={inputClass}
            />
          </div>

          <div className="flex items-center justify-between">
            <button
              onClick={() => setStep(1)}
              className="rounded-xl border border-black/15 px-4 py-2 text-xs font-semibold text-muted hover:text-foreground"
            >
              ← Back
            </button>
            <button
              onClick={runTailorWizard}
              disabled={tailoring || !jdText.trim()}
              className="inline-flex items-center gap-2 rounded-xl bg-brand px-6 py-2.5 text-xs font-bold text-white transition hover:bg-brand-hover disabled:opacity-40 shadow-sm"
            >
              {tailoring ? <Loader2 className="h-4 w-4 animate-spin" /> : <Wand2 className="h-4 w-4 text-proof" />}
              <span>{tailoring ? 'Tailoring resume…' : 'Generate Role-Tailored Resume'}</span>
            </button>
          </div>
        </section>
      )}
    </div>
  );
}
