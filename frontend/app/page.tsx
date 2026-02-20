'use client';

import Link from 'next/link';
import { useState } from 'react';
import {
  ArrowRight,
  Check,
  CheckCircle2,
  FileText,
  Sparkles,
  Shield,
  Zap,
  ChevronDown,
  Layers,
  Target,
  Wand2,
  Lock,
} from 'lucide-react';

export default function Home() {
  const [openFaq, setOpenFaq] = useState<number | null>(null);

  const faqs = [
    {
      q: 'Does ATSDoctor invent experience or qualifications?',
      a: 'No. Tailoring should improve relevance and clarity while staying strictly grounded in the master resume experience you provide. Every rewrite is evidence-traced, allowing you to review and approve before export.',
    },
    {
      q: 'What does the ATS match score represent?',
      a: 'It is a directional alignment comparison based on extracted role requirements, missing keyword coverage, and section weightings. It gives you clear signal on where your application needs work.',
    },
    {
      q: 'Can I edit the generated resume?',
      a: 'Yes. ATSDoctor keeps all content completely editable in real-time, providing live document previews and customizable section arrangements before you download your final PDF or DOCX.',
    },
    {
      q: 'How does local-first privacy work?',
      a: 'ATSDoctor is engineered local-first. Your master resume and job documents stay stored on your machine. You can connect local Ollama models or cloud providers via OmniRoute with full visibility.',
    },
  ];

  return (
    <div className="marketing-page bg-background text-foreground overflow-hidden">
      {/* 1. HERO SECTION */}
      <section className="relative py-12 sm:py-20 lg:py-24">
        <div className="mx-auto grid max-w-[1200px] items-center gap-12 px-4 sm:gap-14 sm:px-6 lg:grid-cols-[0.88fr_1.12fr] lg:gap-16">
          {/* Left Column Text & CTAs */}
          <div className="relative z-10">
            <div className="inline-flex items-center gap-2 rounded-full border border-black/10 bg-surface px-3.5 py-1.5 font-mono text-[10px] font-semibold uppercase tracking-[0.16em] text-muted shadow-2xs">
              <span className="h-2 w-2 rounded-full bg-brand animate-pulse" />
              Precision resume architecture
            </div>

            <h1 className="mt-7 max-w-[620px] text-[clamp(2.2rem,6vw,3.8rem)] font-bold leading-[0.96] tracking-[-0.055em]">
              <span className="block whitespace-nowrap">Stop guessing.</span>
              <span className="block whitespace-nowrap font-editorial font-normal italic tracking-[-0.04em]">
                Start getting
              </span>
              <span className="block whitespace-nowrap text-brand">interviews.</span>
            </h1>

            <p className="mt-7 max-w-xl text-base leading-7 text-muted sm:text-xl sm:leading-8">
              ATSDoctor transforms your master resume into a strategic asset. Higher scores,
              measurable keywords, and evidence-backed PDF exports in seconds.
            </p>

            <div className="mt-8 flex flex-col gap-3 sm:flex-row">
              <Link
                href="/dashboard"
                className="inline-flex items-center justify-center gap-2 rounded-[10px] bg-foreground px-6 py-3.5 text-sm font-semibold text-white shadow-[0_14px_34px_rgba(17,19,24,.16)] transition hover:-translate-y-0.5 hover:bg-brand"
              >
                <span>Build your profile</span>
                <ArrowRight className="h-4 w-4" />
              </Link>
              <a
                href="#transformation"
                className="inline-flex items-center justify-center rounded-[10px] border border-black/20 bg-surface px-6 py-3.5 text-sm font-semibold transition hover:bg-white"
              >
                View workflow
              </a>
            </div>

            <div className="mt-9 grid max-w-lg grid-cols-2 gap-3 border-t border-black/10 pt-6 text-xs sm:gap-5 sm:text-sm">
              <div>
                <p className="font-mono text-[10px] uppercase tracking-widest text-faint">
                  Security
                </p>
                <p className="mt-1 font-semibold flex items-center gap-1.5">
                  <Lock className="h-3.5 w-3.5 text-brand" />
                  Private-by-design
                </p>
              </div>
              <div>
                <p className="font-mono text-[10px] uppercase tracking-widest text-faint">
                  Formatting
                </p>
                <p className="mt-1 font-semibold flex items-center gap-1.5">
                  <FileText className="h-3.5 w-3.5 text-brand" />
                  ATS-readable exports
                </p>
              </div>
            </div>
          </div>

          {/* Right Column: Hero Visual Stack with Animations */}
          <div className="relative min-h-[470px] min-[360px]:min-h-[500px] sm:min-h-[570px]">
            {/* Background Canvas Box */}
            <div className="absolute inset-0 rounded-[32px] border border-black/[0.06] bg-[#f7f7f8]" />

            {/* Back Card (Rotated, Floating) */}
            <div className="float-slow absolute left-[5%] top-[7%] w-[53%] -rotate-6 rounded-xl border border-black/[0.06] bg-white/80 p-5 opacity-70 shadow-[0_14px_45px_rgba(17,19,24,.08)] backdrop-blur-sm sm:p-6">
              <div className="flex items-center justify-between">
                <div className="h-2.5 w-20 rounded-full bg-black/[0.05]" />
                <span className="font-editorial text-2xl text-faint">46</span>
              </div>
              <div className="mt-5 space-y-3">
                <div className="h-2 w-[86%] rounded-full bg-black/[0.04]" />
                <div className="h-2 w-full rounded-full bg-black/[0.035]" />
                <div className="h-2 w-[72%] rounded-full bg-black/[0.04]" />
              </div>
            </div>

            {/* Main Foreground Card (Rotated opposite, with Scan Beam) */}
            <div className="float-reverse absolute left-[6%] top-[24%] z-20 w-[82%] rounded-2xl border border-black/10 bg-white p-4 shadow-[0_28px_75px_rgba(17,19,24,.16)] sm:left-[15%] sm:w-[72%] sm:p-7">
              <div className="flex items-start justify-between gap-4">
                <div>
                  <p className="font-mono text-[9px] uppercase tracking-[0.16em] text-faint">
                    Analysis_log_v2
                  </p>
                  <p className="mt-1 text-sm font-bold sm:text-base text-foreground">
                    Senior Product Designer
                  </p>
                </div>
                <div className="text-right">
                  <p className="font-mono text-[9px] uppercase tracking-[0.16em] text-faint">
                    ATS score
                  </p>
                  <p className="font-editorial mt-0.5 text-4xl font-normal leading-none text-brand">
                    89
                  </p>
                </div>
              </div>

              {/* Scanning Beam Highlight Box */}
              <div className="relative mt-6 overflow-hidden rounded-xl border-l-4 border-brand bg-brand-soft p-4">
                {/* Vertical Laser Scan Beam Line */}
                <div className="scan-beam absolute bottom-0 top-0 left-0 right-0 z-10 pointer-events-none">
                  <div className="h-[2px] w-full bg-gradient-to-r from-transparent via-brand to-transparent shadow-[0_0_12px_#3157f6]" />
                </div>

                <p className="font-mono text-[9px] font-bold uppercase tracking-widest text-brand">
                  ATSDoctor rewrite
                </p>
                <p className="mt-2 text-xs leading-5 sm:text-sm sm:leading-6 text-foreground">
                  Orchestrated the{' '}
                  <mark className="bg-proof px-1.5 py-0.5 rounded-xs font-semibold text-proof-ink">
                    full-cycle design
                  </mark>{' '}
                  of a scalable{' '}
                  <mark className="bg-proof px-1.5 py-0.5 rounded-xs font-semibold text-proof-ink">
                    SaaS architecture
                  </mark>
                  , improving user retention by 42%.
                </p>
              </div>

              {/* Keywords Delta */}
              <div className="mt-4 flex flex-wrap gap-2">
                <span className="rounded-sm bg-proof px-2 py-1 font-mono text-[8px] font-bold text-proof-ink">
                  + PROTOTYPING
                </span>
                <span className="rounded-sm bg-proof px-2 py-1 font-mono text-[8px] font-bold text-proof-ink">
                  + SYSTEM DESIGN
                </span>
                <span className="rounded-sm bg-black/[0.04] px-2 py-1 font-mono text-[8px] text-muted">
                  #accessibility
                </span>
              </div>
            </div>

            {/* Overlaid Recruiter Shortlist Badge */}
            <div className="absolute bottom-[8%] right-[-2%] z-30 w-[68%] rotate-2 rounded-xl border border-black/10 bg-white p-3 shadow-[0_18px_45px_rgba(17,19,24,.14)] sm:right-[-5%] sm:w-[48%] sm:p-5">
              <div className="flex items-center gap-3">
                <span className="inline-flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-brand-soft text-brand">
                  <CheckCircle2 className="h-5 w-5" />
                </span>
                <div className="min-w-0">
                  <p className="whitespace-nowrap text-xs font-bold text-foreground">
                    Recruiter Signal
                  </p>
                  <p className="whitespace-nowrap font-mono text-[9px] uppercase tracking-wide text-faint">
                    Candidate shortlisted
                  </p>
                </div>
              </div>
              <div className="mt-4 h-1.5 overflow-hidden rounded-full bg-brand-soft">
                <div className="h-full w-[89%] bg-gradient-to-r from-brand to-proof" />
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* 2. VALUE PROPOSITION BAR */}
      <section className="border-y border-black/10 bg-surface">
        <div className="mx-auto grid max-w-[1200px] grid-cols-2 divide-x divide-y divide-black/10 px-4 sm:px-6 md:grid-cols-4 md:divide-y-0">
          {[
            'Truthful rewrites',
            'Visible match delta',
            'Editable output',
            'ATS-readable format',
          ].map((item, idx) => (
            <div
              key={idx}
              className="flex items-center gap-2.5 px-3 py-5 text-xs font-semibold sm:px-6 sm:text-sm text-foreground"
            >
              <span className="inline-flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-proof text-proof-ink">
                <Check className="h-3 w-3" />
              </span>
              <span>{item}</span>
            </div>
          ))}
        </div>
      </section>

      {/* 3. THE METHOD (#workflow) */}
      <section id="workflow" className="bg-surface py-20 sm:py-28">
        <div className="mx-auto max-w-[1200px] px-4 sm:px-6">
          <div className="grid gap-7 lg:grid-cols-[0.9fr_1.1fr] lg:items-end">
            <div>
              <p className="font-mono text-xs font-semibold uppercase tracking-[0.2em] text-brand">
                The method
              </p>
              <h2 className="mt-4 max-w-2xl text-4xl font-bold leading-tight tracking-[-0.045em] sm:text-5xl">
                Designed for{' '}
                <span className="font-editorial font-normal italic">impact</span>. Built with
                precision.
              </h2>
            </div>
            <p className="max-w-xl text-lg leading-8 text-muted lg:justify-self-end">
              ATSDoctor bridges the gap between the experience you already have and the exact
              language a specific role is asking for.
            </p>
          </div>

          <div className="mt-14 grid gap-5 md:grid-cols-3">
            {[
              {
                step: '01',
                badge: 'Source',
                title: 'Build the career source',
                desc: 'Add your master resume once. ATSDoctor parses, structures, and categorizes your skills, metrics, and outcomes.',
                link: '/resume',
              },
              {
                step: '02',
                badge: 'Signal',
                title: 'Read the role precisely',
                desc: 'Paste any job description. The AI matching layer surfaces missing keywords, required skills, and key criteria.',
                link: '/jobs',
              },
              {
                step: '03',
                badge: 'Synthesis',
                title: 'Tailor without inventing',
                desc: 'ATSDoctor rewrites bullets around the target role with full evidence tracing, then generates ATS-ready exports.',
                link: '/tailored',
              },
            ].map((card) => (
              <Link
                key={card.step}
                href={card.link}
                className="group rounded-[24px] border border-black/[0.08] bg-background p-7 transition duration-300 hover:-translate-y-1 hover:bg-foreground hover:text-white sm:p-8"
              >
                <div className="flex items-start justify-between">
                  <span className="font-editorial text-5xl text-brand group-hover:text-proof transition-colors">
                    {card.step}
                  </span>
                  <span className="rounded-full border border-current/15 px-2.5 py-1 font-mono text-[9px] uppercase tracking-widest opacity-60">
                    {card.badge}
                  </span>
                </div>
                <h3 className="mt-10 text-xl font-bold tracking-[-0.025em]">{card.title}</h3>
                <p className="mt-3 text-sm leading-6 text-muted group-hover:text-white/70">
                  {card.desc}
                </p>
              </Link>
            ))}
          </div>
        </div>
      </section>

      {/* 4. BEFORE / AFTER COMPARISON (#transformation) */}
      <section id="transformation" className="py-20 sm:py-28">
        <div className="mx-auto max-w-[1200px] px-4 sm:px-6">
          <div className="text-center">
            <p className="font-mono text-xs font-semibold uppercase tracking-[0.2em] text-brand">
              Before → ATSDoctor → After
            </p>
            <h2 className="mx-auto mt-4 max-w-3xl text-4xl font-bold tracking-[-0.045em] sm:text-5xl">
              See exactly what gets{' '}
              <span className="font-editorial font-normal italic">stronger</span>.
            </h2>
            <p className="mx-auto mt-5 max-w-2xl text-lg leading-8 text-muted">
              The visual comparison makes the transformation legible: generic bullets on the left,
              role requirements in the middle, and tailored impact on the right.
            </p>
          </div>

          {/* Interactive Quote Comparison Card */}
          <div className="mt-12 grid overflow-hidden rounded-[24px] border border-black/10 bg-white lg:grid-cols-2 shadow-[0_24px_70px_rgba(17,19,24,.10)]">
            {/* Before Column */}
            <div className="border-b border-black/10 bg-[#faf9f6] p-7 lg:border-b-0 lg:border-r sm:p-9">
              <div className="flex items-center justify-between gap-3">
                <p className="font-mono text-[10px] font-bold uppercase tracking-widest text-faint">
                  Before ATSDoctor
                </p>
                <span className="rounded-full bg-coral-soft px-2.5 py-1 text-[10px] font-bold text-coral">
                  Weak signal
                </span>
              </div>
              <blockquote className="font-editorial mt-8 text-xl leading-8 text-muted sm:text-2xl sm:leading-9">
                “Responsible for managing a team and helping deliver features on time.”
              </blockquote>
              <div className="mt-8 rounded-xl bg-coral-soft p-4 text-xs font-medium leading-6 text-coral">
                Passive wording · No scale metrics · No explicit outcome · Missing role language
              </div>
            </div>

            {/* After Column */}
            <div className="p-7 sm:p-9 bg-white">
              <div className="flex items-center justify-between gap-3">
                <p className="font-mono text-[10px] font-bold uppercase tracking-widest text-brand">
                  After ATSDoctor
                </p>
                <span className="rounded-full bg-proof px-2.5 py-1 text-[10px] font-bold text-proof-ink">
                  Role-aligned
                </span>
              </div>
              <blockquote className="font-editorial mt-8 text-xl leading-8 sm:text-2xl sm:leading-9 text-foreground">
                “Led a cross-functional product team to ship priority features on schedule,
                coordinating scope and stakeholder reviews.”
              </blockquote>
              <div className="mt-8 flex flex-wrap gap-2">
                <span className="rounded-full bg-brand-soft px-3 py-1.5 font-mono text-[10px] font-bold text-brand">
                  + cross-functional
                </span>
                <span className="rounded-full bg-brand-soft px-3 py-1.5 font-mono text-[10px] font-bold text-brand">
                  + scope
                </span>
                <span className="rounded-full bg-brand-soft px-3 py-1.5 font-mono text-[10px] font-bold text-brand">
                  + stakeholders
                </span>
                <span className="rounded-full bg-brand-soft px-3 py-1.5 font-mono text-[10px] font-bold text-brand">
                  + delivery
                </span>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* 5. PLATFORM WORKSPACE CARDS (#features) */}
      <section id="features" className="bg-foreground py-20 text-white sm:py-28">
        <div className="mx-auto max-w-[1200px] px-4 sm:px-6">
          <div className="grid gap-8 lg:grid-cols-2 lg:items-end">
            <div>
              <p className="font-mono text-xs font-semibold uppercase tracking-[0.2em] text-proof">
                One application workspace
              </p>
              <h2 className="mt-4 max-w-2xl text-4xl font-bold tracking-[-0.045em] sm:text-5xl">
                More than a resume{' '}
                <span className="font-editorial font-normal italic text-proof">rewriter</span>.
              </h2>
            </div>
            <p className="max-w-xl text-lg leading-8 text-white/60 lg:justify-self-end">
              Build a consistent application story across your master resume, role tailored versions,
              and match analyses.
            </p>
          </div>

          <div className="mt-14 grid gap-5 sm:grid-cols-2">
            {[
              {
                badge: 'CORE',
                badgeStyle: 'bg-brand-soft text-brand',
                title: 'Resume Tailor',
                desc: 'Role-specific resumes with visible match feedback, evidence tracing, and editable bullet points.',
                href: '/tailored',
              },
              {
                badge: 'SOURCE',
                badgeStyle: 'bg-proof text-proof-ink',
                title: 'Master Resume',
                desc: 'One evidence-tracked source of truth — parsed, structured, and fully editable before any tailoring.',
                href: '/resume',
              },
              {
                badge: 'SIGNAL',
                badgeStyle: 'bg-[#e7f2ff] text-[#0a66c2]',
                title: 'Role Intelligence',
                desc: 'Paste any job description to extract requirements, skill groups, and role keywords.',
                href: '/jobs',
              },
              {
                badge: 'ANALYSIS',
                badgeStyle: 'bg-coral-soft text-coral',
                title: 'Match Comparison',
                desc: 'See missing keywords, signal deltas, and breakdown scores across experience categories.',
                href: '/analyses',
              },
            ].map((item, idx) => (
              <Link
                key={idx}
                href={item.href}
                className="group rounded-[24px] border border-white/10 bg-white/[0.045] p-7 transition duration-300 hover:-translate-y-1 hover:bg-white/[0.08] sm:p-8"
              >
                <div className="flex items-center justify-between gap-4">
                  <span
                    className={`rounded-full px-2.5 py-1 font-mono text-[9px] font-bold tracking-wider ${item.badgeStyle}`}
                  >
                    {item.badge}
                  </span>
                  <ArrowRight className="h-5 w-5 text-white/35 transition group-hover:text-white" />
                </div>
                <h3 className="mt-12 text-2xl font-bold tracking-[-0.03em] text-white">
                  {item.title}
                </h3>
                <p className="mt-3 max-w-md text-sm leading-6 text-white/60">{item.desc}</p>
              </Link>
            ))}
          </div>
        </div>
      </section>

      {/* 6. FAQ SECTION */}
      <section className="py-20 sm:py-28">
        <div className="mx-auto max-w-3xl px-4 sm:px-6">
          <div className="text-center">
            <p className="font-mono text-xs font-semibold uppercase tracking-[0.2em] text-brand">
              FAQ
            </p>
            <h2 className="mt-4 text-4xl font-bold tracking-[-0.045em]">
              Questions &{' '}
              <span className="font-editorial font-normal italic">context</span>.
            </h2>
          </div>

          <div className="mt-10 space-y-3">
            {faqs.map((faq, idx) => (
              <div
                key={idx}
                className="rounded-2xl border border-black/10 bg-white p-5 sm:p-6 transition"
              >
                <button
                  onClick={() => setOpenFaq(openFaq === idx ? null : idx)}
                  className="flex w-full cursor-pointer items-center justify-between gap-5 text-left font-semibold text-foreground"
                >
                  <span>{faq.q}</span>
                  <span
                    className={`text-xl text-muted transition-transform duration-200 ${
                      openFaq === idx ? 'rotate-45' : ''
                    }`}
                  >
                    +
                  </span>
                </button>
                {openFaq === idx && (
                  <p className="mt-4 max-w-2xl text-sm leading-7 text-muted">{faq.a}</p>
                )}
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* 7. CTA BANNER */}
      <section className="px-4 pb-20 sm:px-6 sm:pb-28">
        <div className="relative mx-auto max-w-[1200px] overflow-hidden rounded-[32px] bg-foreground px-6 py-16 text-center text-white sm:px-12 sm:py-20">
          <div className="absolute -left-16 -top-16 h-56 w-56 rounded-full bg-brand/30 blur-3xl pointer-events-none" />
          <div className="absolute -bottom-20 -right-12 h-64 w-64 rounded-full bg-proof/15 blur-3xl pointer-events-none" />

          <div className="relative z-10">
            <p className="font-mono text-xs font-semibold uppercase tracking-[0.2em] text-proof">
              Your next application
            </p>
            <h2 className="mx-auto mt-5 max-w-4xl text-4xl font-bold tracking-[-0.05em] sm:text-6xl">
              Apply with{' '}
              <span className="font-editorial font-normal italic text-proof">more signal</span>,
              less guesswork.
            </h2>
            <p className="mx-auto mt-6 max-w-2xl text-lg leading-8 text-white/60">
              Upload your master resume, paste the target role, and experience proof-backed ATS optimization.
            </p>
            <Link
              href="/analyses"
              className="mt-9 inline-flex items-center gap-2 rounded-[10px] bg-proof px-6 py-3.5 text-sm font-bold text-proof-ink transition hover:-translate-y-0.5 hover:bg-white"
            >
              <span>Build your profile</span>
              <ArrowRight className="h-4 w-4" />
            </Link>
          </div>
        </div>
      </section>

      {/* 8. FOOTER */}
      <footer className="border-t border-black/10 bg-surface">
        <div className="mx-auto flex max-w-[1200px] flex-col gap-8 px-5 py-10 sm:flex-row sm:items-end sm:justify-between sm:px-6">
          <div>
            <Link href="/" className="inline-flex items-center gap-2.5">
              <span className="relative inline-flex h-8 w-8 items-center justify-center rounded-md bg-foreground text-sm font-bold text-white">
                A<span className="absolute inset-y-0 right-1 w-0.5 bg-proof" />
              </span>
              <span className="font-bold tracking-[-0.04em] text-foreground">ATSDoctor</span>
            </Link>
            <p className="mt-3 max-w-sm text-sm leading-6 text-muted">
              One master resume. Every application, focused with precision.
            </p>
          </div>
          <nav className="flex flex-wrap items-center gap-x-6 gap-y-3 text-sm font-medium text-muted">
            <Link href="/resume" className="transition-colors hover:text-foreground">
              Master Resume
            </Link>
            <Link href="/jobs" className="transition-colors hover:text-foreground">
              Jobs
            </Link>
            <Link href="/analyses" className="transition-colors hover:text-foreground">
              Analyses
            </Link>
            <Link href="/tailored" className="transition-colors hover:text-foreground">
              Tailored
            </Link>
          </nav>
        </div>
        <div className="border-t border-black/[0.06]">
          <p className="mx-auto max-w-[1200px] px-5 py-4 font-mono text-[10px] uppercase tracking-wider text-faint sm:px-6">
            © 2026 ATSDoctor. Built for evidence-backed, higher-impact applications.
          </p>
        </div>
      </footer>
    </div>
  );
}