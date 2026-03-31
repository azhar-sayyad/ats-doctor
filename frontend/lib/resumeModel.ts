import type {
  StructuredResume,
  ResumeBasics,
  ResumeBullet,
  ResumeSkill,
  ResumeExperience,
  ResumeProject,
  ResumeEducation,
  TailoredContent,
  TailoredChange,
} from '../app/(dashboard)/tailored/types';
import type { Analysis, Job, JobDto, TailoredResume } from './api';

// ---------------------------------------------------------------------------
// Structured-data parsing
// ---------------------------------------------------------------------------

export function cloneResume(resume: StructuredResume): StructuredResume {
  return structuredClone(resume);
}

export function parseStructured<T>(raw: string | null): T | null {
  if (!raw) return null;
  try {
    return JSON.parse(raw) as T;
  } catch {
    return null;
  }
}

// ---------------------------------------------------------------------------
// Effective document model — reconciles review status into final text.
// ---------------------------------------------------------------------------

export interface DocBullet {
  originalId?: string;
  originalText: string;
  effectiveText: string;
  status: string | null;
  claimCategory: string | null;
  reason: string | null;
  change: TailoredChange | null;
}

export interface DocSection {
  id?: string;
  company: string;
  title?: string;
  start?: string;
  end?: string;
  location?: string;
  bullets: DocBullet[];
}

export interface DocModel {
  basics: ResumeBasics;
  summary: { original: string; effective: string; rewritten: boolean } | null;
  skills: ResumeSkill[];
  sections: DocSection[];
  projects: ResumeProject[];
  education: ResumeEducation[];
}

const changeByOriginal = (changes: TailoredChange[]): Map<string, TailoredChange> =>
  new Map(changes.map((c) => [c.original_text, c]));

/** A change whose tailored/original text equals the given document text (document path — content is null). */
const findDocumentChange = (
  bullet: { id?: string; text: string },
  changes: TailoredChange[],
): TailoredChange | null => {
  for (const c of changes) {
    if (c.tailored_text === bullet.text || (c.original_text !== '' && c.original_text === bullet.text)) {
      return c;
    }
  }
  return null;
};

const findSummaryChange = (summaryText: string, changes: TailoredChange[]): TailoredChange | null => {
  for (const c of changes) {
    if (c.tailored_text === summaryText || (c.original_text !== '' && c.original_text === summaryText)) {
      return c;
    }
  }
  return null;
};

const effectiveTailoredText = (
  bullet: { original_id: string; original_text: string; tailored_text: string },
  changesByOriginal: Map<string, TailoredChange>,
): { text: string; status: string | null; change: TailoredChange | null } => {
  const change = changesByOriginal.get(bullet.original_text) ?? null;
  const status = change?.status ?? null;
  if (!change) {
    return { text: bullet.tailored_text, status: null, change: null };
  }
  if (status === 'REJECTED') {
    return { text: bullet.original_text, status, change };
  }
  if (status === 'EDITED' || status === 'REGENERATED') {
    return { text: change.tailored_text, status, change };
  }
  return { text: bullet.tailored_text, status, change };
};

/**
 * Builds the review-aware document model.
 *
 * Two sources of truth:
 * - {@code content} non-null (default): master structured data + tailored
 *   content + change statuses reconcile into the effective document.
 * - {@code content} null: {@code resume} is already the full editable tailored
 *   document (saved via PUT /tailored/{id}/edit) — its bullet text is
 *   authoritative, and {@code changes} are only overlaid so the Changes
 *   highlight view can annotate AI-touched bullets.
 */
export function buildDocumentModel(
  resume: StructuredResume | null,
  content: TailoredContent | null,
  changes: TailoredChange[],
): DocModel | null {
  if (!resume) return null;
  const changesByOriginal = changeByOriginal(changes);

  const summary =
    content?.summary && content.summary.tailored
      ? (() => {
          const change = changesByOriginal.get(content.summary.original) ?? null;
          if (change?.status === 'REJECTED') {
            return { original: content.summary.original, effective: content.summary.original, rewritten: false };
          }
          const effective =
            change && (change.status === 'EDITED' || change.status === 'REGENERATED')
              ? change.tailored_text
              : content.summary.tailored;
          return { original: content.summary.original, effective, rewritten: true };
        })()
      : resume.summary
        ? (() => {
            const change = findSummaryChange(resume.summary!, changes);
            return {
              original: change?.original_text ?? resume.summary!,
              effective: resume.summary!,
              rewritten: change !== null,
            };
          })()
        : null;

  const ordered = orderSections(resume.experience ?? [], content);
  const sections: DocSection[] = ordered.map((exp) => {
    const bullets: DocBullet[] = (exp.bullets ?? []).map((b) => {
      const originalText = b.text ?? '';
      const matched = content ? findTailoredBullet(content, b) : null;
      if (matched) {
        const { text, status, change } = effectiveTailoredText(matched, changesByOriginal);
        return {
          originalId: b.id,
          originalText,
          effectiveText: text,
          status,
          claimCategory: change?.claim_category ?? null,
          reason: change?.reason ?? null,
          change,
        };
      }
      const change = findDocumentChange(b, changes);
      if (change) {
        return {
          originalId: b.id,
          originalText: change.original_text || originalText,
          effectiveText: originalText,
          status: change.status,
          claimCategory: change.claim_category ?? null,
          reason: change.reason ?? null,
          change,
        };
      }
      return {
        originalId: b.id,
        originalText,
        effectiveText: originalText,
        status: null,
        claimCategory: null,
        reason: null,
        change: null,
      };
    });
    return {
      id: exp.id,
      company: exp.company ?? '',
      title: exp.title,
      start: exp.start,
      end: exp.end,
      location: exp.location,
      bullets,
    };
  });

  return {
    basics: resume.basics,
    summary,
    skills: resume.skills ?? [],
    sections,
    projects: resume.projects ?? [],
    education: resume.education ?? [],
  };
}

/**
 * The editable tailored document — the effective {@link DocModel} flattened
 * back to the StructuredResume shape persisted via PUT /tailored/{id}/edit.
 * Bullets keep the effective text so the saved document is authoritative.
 */
export function toDocument(doc: DocModel): StructuredResume {
  return {
    basics: { ...doc.basics },
    summary: doc.summary?.effective ?? undefined,
    skills: doc.skills ?? [],
    experience: doc.sections.map((s) => ({
      id: s.id,
      company: s.company,
      title: s.title,
      start: s.start,
      end: s.end,
      location: s.location,
      bullets: s.bullets.map((b) => ({ id: b.originalId, text: b.effectiveText })),
    })),
    projects: doc.projects ?? [],
    education: doc.education ?? [],
  };
}

/**
 * LaTeX source for the effective document — mirrors the backend export template
 * (templates/export/latex.tex) with the same escaping rules so the generated
 * .tex compiles cleanly in Overleaf / pdflatex. Returns '' when no document.
 */
export function toLatex(doc: DocModel | null): string {
  if (!doc) return '';

  const contact = [
    doc.basics.email,
    doc.basics.phone,
    doc.basics.location,
    doc.basics.linkedin,
    doc.basics.github,
  ]
    .filter((v): v is string => !!v && v.trim() !== '')
    .map(latexEscape)
    .join(' | ');

  const lines: string[] = [
    '% ATS Doctor — tailored resume',
    '% ATS-readable LaTeX export. Edit in Overleaf or any pdflatex/xelatex setup.',
    '\\documentclass[a4paper,10pt]{article}',
    '\\usepackage[margin=0.7in]{geometry}',
    '\\usepackage[T1]{fontenc}',
    '\\usepackage[utf8]{inputenc}',
    '\\usepackage{enumitem}',
    '\\usepackage{titlesec}',
    '\\pagestyle{empty}',
    '',
    '\\titleformat{\\section}{\\large\\bfseries\\MakeUppercase}{}{0em}{}[\\titlerule]',
    '\\setlist[itemize]{nosep,leftmargin=1.2em,topsep=2pt}',
    '',
    '\\begin{document}',
    '',
    '\\begin{center}',
    `{\\LARGE\\bfseries ${latexEscape(doc.basics.name ?? 'Candidate Resume')}}\\\\[6pt]`,
  ];
  if (contact) {
    lines.push('\\small', contact);
  }
  lines.push('\\end{center}', '');

  if (doc.summary?.effective?.trim()) {
    lines.push('\\section*{Professional Summary}', doc.summary.effective, '');
  }

  if (doc.skills.length > 0) {
    lines.push(
      '\\section*{Technical Skills}',
      doc.skills.map((s) => latexEscape(s.name)).join(' · '),
      '',
    );
  }

  if (doc.sections.length > 0) {
    lines.push('\\section*{Work Experience}');
    for (const section of doc.sections) {
      const heading = [section.company, section.title].filter(Boolean).join(', ');
      lines.push(`\\subsection*{${latexEscape(heading)}}`, '\\begin{itemize}');
      for (const bullet of section.bullets) {
        lines.push(`  \\item ${latexEscape(bullet.effectiveText)}`);
      }
      lines.push('\\end{itemize}');
    }
    lines.push('');
  }

  if (doc.projects.length > 0) {
    lines.push('\\section*{Projects}');
    for (const project of doc.projects) {
      lines.push(`\\subsection*{${latexEscape(project.name)}}`);
      if (project.description?.trim()) {
        lines.push(latexEscape(project.description));
      }
    }
    lines.push('');
  }

  if (doc.education.length > 0) {
    lines.push('\\section*{Education}');
    for (const edu of doc.education) {
      lines.push(`\\subsection*{${latexEscape(edu.institution ?? '')}}`);
      const degreeLine = [latexEscape(edu.degree ?? ''), latexEscape(edu.field ?? '')]
        .filter(Boolean)
        .join(' — ');
      if (degreeLine) {
        lines.push(degreeLine);
      }
    }
    lines.push('');
  }

  lines.push('\\end{document}');
  return lines.join('\n');
}

/** Escapes LaTeX specials (same rules as the backend ExportService). */
function latexEscape(text: string | null | undefined): string {
  if (!text) return '';
  // Escape the backslash last (via a placeholder) so the braces inserted by
  // \textbackslash{} / \textasciitilde{} / \textasciicircum{} are not
  // re-escaped, keeping the round-trip with latexUnescape exact.
  return text
    .replace(/\\/g, '\u0000')
    .replace(/&/g, '\\&')
    .replace(/%/g, '\\%')
    .replace(/\$/g, '\\$')
    .replace(/#/g, '\\#')
    .replace(/_/g, '\\_')
    .replace(/\{/g, '\\{')
    .replace(/\}/g, '\\}')
    .replace(/~/g, '\\textasciitilde{}')
    .replace(/\^/g, '\\textasciicircum{}')
    .replace(/\u0000/g, '\\textbackslash{}');
}

/** Inverse of {@link latexEscape} — restores user text from escaped LaTeX. */
function latexUnescape(text: string): string {
  return text
    .replace(/\\textbackslash\{\}/g, '\\')
    .replace(/\\textasciitilde\{\}/g, '~')
    .replace(/\\textasciicircum\{\}/g, '^')
    .replace(/\\&/g, '&')
    .replace(/\\%/g, '%')
    .replace(/\\\$/g, '$')
    .replace(/\\#/g, '#')
    .replace(/\\_/g, '_')
    .replace(/\\\{/g, '{')
    .replace(/\\\}/g, '}');
}

/**
 * Best-effort parse of the LaTeX emitted by {@link toLatex} back into the
 * StructuredResume shape, so the LaTeX editor can drive the live preview.
 * Returns null when the document is blank or structurally broken.
 */
export function parseLatex(tex: string): StructuredResume | null {
  const clean = tex.replace(/^\s*%.*$/gm, '');
  if (clean.trim() === '') return null;
  if (!/\\end\{document\}/.test(clean)) return null;

  const basics: ResumeBasics = {};
  const center = clean.match(/\\begin\{center\}([\s\S]*?)\\end\{center\}/);
  if (center) {
    const nameMatch = center[1].match(/\\LARGE\\bfseries\s*([^}]*)\}/);
    if (nameMatch) basics.name = latexUnescape(nameMatch[1].trim());
    const contactMatch = center[1].match(/\\small\s*([\s\S]*)$/);
    if (contactMatch) {
      const parts = contactMatch[1].split(' | ').map((p) => latexUnescape(p.trim()));
      basics.email = parts[0] ?? '';
      basics.phone = parts[1] ?? '';
      basics.location = parts[2] ?? '';
      basics.linkedin = parts[3] ?? '';
      basics.github = parts[4] ?? '';
    }
  }

  const skills: ResumeSkill[] = [];
  const experience: ResumeExperience[] = [];
  const projects: ResumeProject[] = [];
  const education: ResumeEducation[] = [];
  let summary: string | undefined;

  const sectionRe = /\\section\*\{([^}]*)\}([\s\S]*?)(?=\\section\*\{|\\end\{document\})/g;
  let section: RegExpExecArray | null;
  while ((section = sectionRe.exec(clean)) !== null) {
    const title = latexUnescape(section[1]).trim();
    const body = section[2];
    switch (title) {
      case 'Professional Summary':
        summary = latexUnescape(body.trim());
        break;
      case 'Technical Skills':
        for (const name of body.trim().split(' · ')) {
          const n = latexUnescape(name.trim());
          if (n) skills.push({ name: n });
        }
        break;
      case 'Work Experience':
        eachSubsection(body, (heading, rest) => {
          const itemize = rest.match(/\\begin\{itemize\}\s*([\s\S]*?\\end\{itemize\})/);
          const bullets: ResumeBullet[] = [];
          if (itemize) {
            const itemRe = /\\item\s+([\s\S]*?)(?=\\item\s+|\\end\{itemize\})/g;
            let item: RegExpExecArray | null;
            while ((item = itemRe.exec(itemize[1])) !== null) {
              const text = latexUnescape(item[1].trim());
              if (text) bullets.push({ text });
            }
          }
          if (heading || bullets.length > 0) {
            experience.push({ company: heading, bullets });
          }
        });
        break;
      case 'Projects':
        eachSubsection(body, (name, rest) => {
          const description = latexUnescape(rest.trim());
          projects.push({ name, description: description || undefined });
        });
        break;
      case 'Education':
        eachSubsection(body, (institution, rest) => {
          const line = latexUnescape(rest.trim());
          const sep = line.indexOf(' — ');
          const degree = sep >= 0 ? line.slice(0, sep).trim() : line;
          const field = sep >= 0 ? line.slice(sep + 3).trim() : '';
          education.push({
            institution: institution || undefined,
            degree: degree || undefined,
            field: field || undefined,
          });
        });
        break;
    }
  }

  const result: StructuredResume = { basics, skills, experience, projects, education };
  if (summary) result.summary = summary;
  return result;
}

/** Runs `visit` for each `\subsection*{heading}` block in a LaTeX section body. */
function eachSubsection(
  body: string,
  visit: (heading: string, rest: string) => void,
): void {
  const subRe = /\\subsection\*\{([^}]*)\}([\s\S]*?)(?=\\subsection\*\{|$)/g;
  let sub: RegExpExecArray | null;
  while ((sub = subRe.exec(body)) !== null) {
    visit(latexUnescape(sub[1]).trim(), sub[2]);
  }
}

function findTailoredBullet(
  content: TailoredContent | null,
  bullet: { id?: string; text: string },
): { original_id: string; original_text: string; tailored_text: string } | null {
  if (!content?.experience) return null;
  for (const entry of content.experience) {
    for (const tb of entry.bullets) {
      if (bullet.id && tb.original_id && bullet.id === tb.original_id) return tb;
      if (!bullet.id && tb.original_text && tb.original_text === bullet.text) return tb;
    }
  }
  return null;
}

/** Section ordering honoring content.order (reordered to most-relevant-first). */
export function orderSections(
  experience: ResumeExperience[],
  content: TailoredContent | null,
): ResumeExperience[] {
  if (!content?.order || content.order.length === 0) return experience;
  const byId = new Map(experience.map((e) => [e.id ?? '', e]));
  const ordered: ResumeExperience[] = [];
  const seen = new Set<string>();
  for (const id of content.order) {
    const section = byId.get(id);
    if (section && !seen.has(id)) {
      ordered.push(section);
      seen.add(id);
    }
  }
  for (const section of experience) {
    if (!seen.has(section.id ?? '')) ordered.push(section);
  }
  return ordered;
}

// ---------------------------------------------------------------------------
// Analytics — everything derived client-side from existing APIs.
// ---------------------------------------------------------------------------

export interface RequirementBucket {
  label: string;
  total: number;
  covered: number;
  items: { text: string; importance: string; status: string }[];
}

export interface Analytics {
  scoreBefore: number | null;
  scoreAfter: number | null;
  scoreDelta: number | null;
  roleCoverage: number | null;
  resumeStrength: number | null;
  mustHaves: RequirementBucket;
  preferred: RequirementBucket;
  niceToHave: RequirementBucket;
  surfacedKeywords: string[];
  missingKeywords: string[];
  changedCount: number;
  reordered: boolean;
  summaryRewritten: boolean;
  topStrengths: string[];
  topGaps: string[];
  seniority: string | null;
  roleTitle: string | null;
  company: string | null;
  jobLocation: string | null;
  jdRawText: string | null;
  jdRequirements: { text: string; importance: string }[];
  analysisId: string | null;
  resumeVersionId: string | null;
}

const COVERED = new Set(['matched', 'partial']);

export function computeAnalytics(
  tailored: TailoredResume | null,
  analysis: Analysis | null,
  job: Job | null,
  changes: TailoredChange[],
  content: TailoredContent | null,
  resume: StructuredResume | null,
): Analytics {
  const jobDto = job ? parseStructured<JobDto>(job.structured_data) : null;
  const jobInfo = jobDto?.job ?? null;
  const requirementRows =
    jobDto?.requirements?.map((r) => ({
      text: r.text,
      importance: r.importance ?? 'medium',
      keywords: r.keywords ?? [],
    })) ?? [];

  const matches = analysis?.matches ?? [];
  const statusByText = new Map<string, string>();
  for (const m of matches) statusByText.set(m.requirement_text, m.status);

  const coveredCount = matches.filter((m) => COVERED.has(m.status)).length;
  const roleCoverage =
    matches.length > 0 ? Math.round((coveredCount / matches.length) * 100) : null;

  const breakdown = analysis?.score_breakdown ?? null;
  const surfacedKeywords = breakdown?.keywords.matched ?? [];
  const missingKeywords = breakdown?.keywords.missing ?? [];

  const bucket = (label: string, importance: string): RequirementBucket => {
    const items = requirementRows
      .filter((r) => r.importance === importance)
      .map((r) => ({ text: r.text, importance, status: statusByText.get(r.text) ?? 'unmatched' }));
    const total = items.length;
    const covered = items.filter((i) => COVERED.has(i.status)).length;
    return { label, total, covered, items };
  };

  const mustHaves = bucket('Must-haves', 'high');
  const preferred = bucket('Preferred', 'medium');
  const niceToHave = bucket('Nice-to-have', 'low');

  const gaps = analysis?.gaps ?? [];
  const topGaps = gaps
    .filter((g) => g.type === 'requirement' || g.status === 'unmatched')
    .map((g) => g.text)
    .slice(0, 3);

  const skillMatches = breakdown?.skills.matched ?? [];
  const topStrengths = Array.from(new Set([...skillMatches, ...surfacedKeywords])).slice(0, 4);

  const resumeOrder = (resume?.experience ?? []).map((e) => e.id);
  const reordered =
    !!content?.order &&
    content.order.length > 0 &&
    JSON.stringify(content.order) !== JSON.stringify(resumeOrder);

  const scoreBefore = tailored?.score_before ?? analysis?.score ?? null;
  const scoreAfter = tailored?.score_after ?? null;
  const scoreDelta =
    scoreBefore !== null && scoreAfter !== null ? scoreAfter - scoreBefore : null;

  return {
    scoreBefore,
    scoreAfter,
    scoreDelta,
    roleCoverage,
    resumeStrength: analysis?.score ?? null,
    mustHaves,
    preferred,
    niceToHave,
    surfacedKeywords,
    missingKeywords,
    changedCount: changes.length,
    reordered,
    summaryRewritten: !!content?.summary,
    topStrengths,
    topGaps,
    seniority: jobInfo?.seniority ?? job?.seniority ?? null,
    roleTitle: jobInfo?.title ?? job?.title ?? null,
    company: jobInfo?.company ?? job?.company ?? null,
    jobLocation: jobInfo?.location ?? job?.location ?? null,
    jdRawText: job?.raw_text ?? null,
    jdRequirements: requirementRows.map((r) => ({ text: r.text, importance: r.importance })),
    analysisId: tailored?.analysis_id ?? analysis?.id ?? null,
    resumeVersionId: tailored?.resume_version_id ?? null,
  };
}

/** Rewritten bullets across the document — used to render the Changes tab. */
export function rewrittenBullets(model: DocModel | null): { section: DocSection; bullet: DocBullet }[] {
  if (!model) return [];
  const out: { section: DocSection; bullet: DocBullet }[] = [];
  for (const section of model.sections) {
    for (const bullet of section.bullets) {
      if (bullet.status) {
        out.push({ section, bullet });
      }
    }
  }
  return out;
}
