import Link from 'next/link';
import TailoredList from './TailoredList';
import StepGuide from '../../../components/StepGuide';
import { ArrowLeft } from 'lucide-react';

export default function TailoredIndex() {
  return (
    <div className="mx-auto max-w-5xl px-4 py-10 sm:px-6">
      <StepGuide
        step={4}
        title="Step 4: Tailored Rewrites, Review & Export"
        description="Inspect evidence-backed bullet rewrites, verify claims with TraceDrawer, approve and download final PDF/DOCX files."
        nextHref="/dashboard"
        nextLabel="Dashboard Overview →"
      />

      <div className="border-b border-black/10 pb-6">
        <p className="font-mono text-xs font-semibold uppercase tracking-[0.2em] text-brand">
          04 / SYNTHESIS & REWRITES
        </p>
        <h1 className="mt-1 text-3xl font-bold tracking-[-0.04em]">Tailored Resumes</h1>
        <p className="mt-1 text-sm text-muted">
          Review role-optimized rewrites, inspect evidence-based claims, and approve final exports.
        </p>
      </div>

      <TailoredList />
    </div>
  );
}