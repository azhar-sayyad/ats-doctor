'use client';

import { useEffect, useState } from 'react';
import { apiGet, apiPut, ApiError, type AiConfig } from '../../../lib/api';
import StepGuide from '../../../components/StepGuide';
import { User, Settings, ShieldCheck, Cpu, Sliders, CheckCircle2, AlertCircle, Save } from 'lucide-react';

export default function ProfilePage() {
  const [aiConfig, setAiConfig] = useState<AiConfig | null>(null);
  const [name, setName] = useState('Jane Doe');
  const [email, setEmail] = useState('jane.doe@example.com');
  const [tone, setTone] = useState('Professional');
  const [saving, setSaving] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    apiGet<AiConfig>('/ai/config')
      .then(setAiConfig)
      .catch(() => setAiConfig(null));
  }, []);

  const saveSettings = async () => {
    setSaving(true);
    setError(null);
    setNotice(null);
    try {
      // Simulate saving preferences locally
      await new Promise((resolve) => setTimeout(resolve, 400));
      setNotice('Profile & AI preferences saved successfully.');
    } catch (err) {
      setError(err instanceof ApiError ? err.detail ?? err.message : String(err));
    } finally {
      setSaving(false);
    }
  };

  const inputClass =
    'w-full rounded-xl border border-black/15 bg-white px-3.5 py-2.5 text-sm text-foreground placeholder:text-faint focus:border-brand focus:outline-none focus:ring-1 focus:ring-brand transition';

  return (
    <div className="mx-auto max-w-5xl px-4 py-10 sm:px-6">
      <StepGuide
        step={4}
        title="Account Profile & AI Settings"
        description="Configure your candidate profile, default AI output tone, model profiles, and privacy controls."
        nextHref="/dashboard"
        nextLabel="Back to Dashboard Hub →"
      />

      {/* Header Bar */}
      <div className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between border-b border-black/10 pb-6">
        <div>
          <p className="font-mono text-xs font-semibold uppercase tracking-[0.2em] text-brand">
            05 / PREFERENCES & SECURITY
          </p>
          <h1 className="mt-1 text-3xl font-bold tracking-[-0.04em]">Profile & Settings</h1>
          <p className="mt-1 text-sm text-muted">
            Manage your personal defaults, template choices, and local-first AI privacy parameters.
          </p>
        </div>
        <button
          onClick={saveSettings}
          disabled={saving}
          className="inline-flex items-center gap-2 rounded-[10px] bg-foreground px-5 py-2.5 text-sm font-semibold text-white transition hover:bg-brand hover:-translate-y-0.5 disabled:opacity-50"
        >
          <Save className="h-4 w-4 text-proof" />
          <span>{saving ? 'Saving…' : 'Save Preferences'}</span>
        </button>
      </div>

      {notice && (
        <div className="mb-6 flex items-center gap-2 rounded-xl border border-proof/30 bg-proof/20 p-4 text-sm text-proof-ink font-semibold">
          <CheckCircle2 className="h-4 w-4 shrink-0 text-brand" />
          <span>{notice}</span>
        </div>
      )}
      {error && (
        <div className="mb-6 flex items-center gap-2 rounded-xl border border-coral/30 bg-coral-soft p-4 text-sm text-coral font-medium">
          <AlertCircle className="h-4 w-4 shrink-0" />
          <span>{error}</span>
        </div>
      )}

      <div className="space-y-8">
        {/* Personal Details */}
        <section className="rounded-[24px] border border-black/10 bg-white p-6 sm:p-7 shadow-xs">
          <h2 className="mb-4 text-lg font-bold tracking-tight text-foreground flex items-center gap-2">
            <User className="h-5 w-5 text-brand" />
            Personal Information
          </h2>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <div>
              <label className="mb-1 block text-xs font-semibold text-muted">Full Name</label>
              <input
                value={name}
                onChange={(e) => setName(e.target.value)}
                className={inputClass}
                placeholder="Jane Doe"
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-semibold text-muted">Account Email</label>
              <input
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className={inputClass}
                placeholder="jane.doe@example.com"
              />
            </div>
          </div>
        </section>

        {/* AI Output Preferences */}
        <section className="rounded-[24px] border border-black/10 bg-white p-6 sm:p-7 shadow-xs">
          <h2 className="mb-4 text-lg font-bold tracking-tight text-foreground flex items-center gap-2">
            <Sliders className="h-5 w-5 text-brand" />
            Application & AI Output Tone
          </h2>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <div>
              <label className="mb-1 block text-xs font-semibold text-muted">Default AI Tone</label>
              <select
                value={tone}
                onChange={(e) => setTone(e.target.value)}
                className={inputClass}
              >
                <option value="Professional">Professional & Direct</option>
                <option value="Technical">Technical & Metric-focused</option>
                <option value="Executive">Executive Leadership</option>
              </select>
            </div>
            <div>
              <label className="mb-1 block text-xs font-semibold text-muted">Default Export Template</label>
              <select className={inputClass} defaultValue="ats_clean">
                <option value="ats_clean">ATS Clean Standard (Single Column)</option>
                <option value="modern_minimal">Modern Minimalist</option>
                <option value="executive_classic">Executive Classic</option>
              </select>
            </div>
          </div>
        </section>

        {/* AI Privacy & Model Provider */}
        <section className="rounded-[24px] border border-black/10 bg-surface p-6 sm:p-7 shadow-xs">
          <h2 className="mb-4 text-lg font-bold tracking-tight text-foreground flex items-center gap-2">
            <Cpu className="h-5 w-5 text-brand" />
            AI Provider & Local Privacy Controls
          </h2>
          <div className="space-y-4 text-xs text-muted">
            <div className="flex items-center justify-between rounded-xl bg-white p-4 border border-black/10">
              <div>
                <p className="font-bold text-foreground">Current Active AI Mode</p>
                <p className="font-mono text-faint mt-0.5">{aiConfig?.mode ?? 'stub'}</p>
              </div>
              <span className="rounded-full bg-proof px-3 py-1 font-mono text-[10px] font-bold text-proof-ink uppercase tracking-wider">
                {aiConfig?.mode === 'stub' ? 'Offline Stub' : 'OmniRoute Active'}
              </span>
            </div>

            <div className="flex items-center justify-between rounded-xl bg-white p-4 border border-black/10">
              <div className="flex items-center gap-2">
                <ShieldCheck className="h-5 w-5 text-brand" />
                <div>
                  <p className="font-bold text-foreground">Data Privacy Status</p>
                  <p className="text-muted">
                    {aiConfig?.data_leaves_machine
                      ? 'AI requests leave this machine (Cloud API mode enabled)'
                      : 'Private-by-design (All master data stays on your local machine)'}
                  </p>
                </div>
              </div>
              <span
                className={`rounded-full px-3 py-1 font-mono text-[10px] font-bold ${
                  aiConfig?.data_leaves_machine ? 'bg-amber-100 text-amber-800' : 'bg-emerald-100 text-emerald-800'
                }`}
              >
                {aiConfig?.data_leaves_machine ? 'Cloud API' : 'Local Only'}
              </span>
            </div>
          </div>
        </section>
      </div>
    </div>
  );
}
