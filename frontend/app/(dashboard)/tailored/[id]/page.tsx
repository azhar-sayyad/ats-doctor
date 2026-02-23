import Link from 'next/link';
import { notFound } from 'next/navigation';
import { ArrowLeft } from 'lucide-react';
import { apiGet, ApiError } from '../../../../lib/api';
import type { TailoredChange, TailoredResume } from '../types';
import TailoredView from './TailoredView';

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
  let changes: TailoredChange[] = [];
  try {
    changes = await apiGet<TailoredChange[]>(`/tailored/${id}/changes`);
  } catch {
    // changes list unavailable — the drawer will surface the error
  }

  return (
    <div className="mx-auto max-w-6xl px-4 py-10 sm:px-6">
      <Link
        href="/tailored"
        className="inline-flex items-center gap-1.5 font-mono text-xs font-semibold uppercase tracking-[0.2em] text-muted transition hover:text-brand"
      >
        <ArrowLeft className="h-3.5 w-3.5" />
        Tailored resumes
      </Link>
      <TailoredView tailoredId={id} initialTailored={tailored} initialChanges={changes} />
    </div>
  );
}