import Link from 'next/link';
import { notFound } from 'next/navigation';
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
    <div className="py-10">
      <Link href="/tailored" className="text-sm text-slate-600 hover:underline">
        ← Tailored resumes
      </Link>
      <TailoredView tailoredId={id} initialTailored={tailored} initialChanges={changes} />
    </div>
  );
}