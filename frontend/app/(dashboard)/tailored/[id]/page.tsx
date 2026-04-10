import Link from 'next/link';
import { notFound } from 'next/navigation';
import { ArrowLeft } from 'lucide-react';
import { apiGet, ApiError, getAnalysis, getJob, getResumeVersion, type Analysis, type Job, type ResumeVersion } from '../../../../lib/api';
import type { TailoredChange, TailoredResume } from '../types';
import Workspace from './Workspace';

export const dynamic = 'force-dynamic';

export default async function TailoredDetail({ params }: { params: { id: string } }) {
  const { id } = params;

  let tailored: TailoredResume;
  try {
    tailored = await apiGet<TailoredResume>(`/tailored/${id}`);
  } catch (err) {
    if (err instanceof ApiError && err.status === 404) {
      notFound();
    }
    throw err;
  }

  // Independent, degradable fetches — a missing analysis/job/resume never
  // takes down the workspace; the affected panels render partial data.
  const [changes, analysis, job, resumeVersion] = await Promise.all([
    apiGet<TailoredChange[]>(`/tailored/${id}/changes`).catch(() => [] as TailoredChange[]),
    tailored.analysis_id
      ? getAnalysis(tailored.analysis_id).catch(() => null as Analysis | null)
      : Promise.resolve(null as Analysis | null),
    tailored.analysis_id
      ? getJobOf(tailored.analysis_id).catch(() => null as Job | null)
      : Promise.resolve(null as Job | null),
    tailored.resume_version_id
      ? getResumeVersion(tailored.resume_version_id).catch(() => null as ResumeVersion | null)
      : Promise.resolve(null as ResumeVersion | null),
  ]);

  return (
    <div className="mx-auto max-w-7xl px-4 py-10 sm:px-6 lg:px-8">
      <Link
        href="/tailored"
        className="inline-flex items-center gap-1.5 font-mono text-xs font-semibold uppercase tracking-[0.2em] text-muted transition hover:text-brand"
      >
        <ArrowLeft className="h-3.5 w-3.5" />
        Tailored resumes
      </Link>
      <Workspace
        tailoredId={id}
        initialTailored={tailored}
        initialChanges={changes}
        initialAnalysis={analysis}
        initialJob={job}
        initialResumeVersion={resumeVersion}
      />
    </div>
  );
}

async function getJobOf(analysisId: string): Promise<Job> {
  const analysis = await getAnalysis(analysisId);
  return getJob(analysis.job_id);
}
