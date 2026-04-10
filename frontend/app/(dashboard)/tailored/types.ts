export interface TailoredResume {
  id: string;
  analysis_id: string;
  resume_version_id: string;
  state: string;
  score_before: number | null;
  score_after: number | null;
  content: TailoredContent | null;
  /** Full editable tailored document JSON (merged resume); null until the user saves an edit. */
  document: string | null;
  /** Export template slug (resume-templates.yml catalog); defaults to ats_clean. */
  template: string;
  error: string | null;
  created_at: string;
  updated_at: string;
}

/** §7.7 tailored content JSONB — one rewrite entry per selected bullet. */
export interface TailoredContent {
  summary: { original: string; tailored: string } | null;
  experience: TailoredExperience[];
  order: string[] | null;
  generation?: { mode?: string; ai_enabled?: boolean; prompt_version?: string } | null;
}

export interface TailoredExperience {
  id: string;
  company: string;
  title: string;
  bullets: TailoredBullet[];
}

export interface TailoredBullet {
  original_id: string;
  original_text: string;
  tailored_text: string;
}

// ---------------------------------------------------------------------------
// Master resume structured_data (§4.1) — mirrors the resume editor shapes.
// ---------------------------------------------------------------------------

export interface ResumeBasics {
  name?: string;
  email?: string;
  phone?: string;
  location?: string;
  linkedin?: string;
  github?: string;
}

export interface ResumeBullet {
  id?: string;
  text: string;
  technologies?: string[];
  metrics?: string[];
  domains?: string[];
  evidence_level?: string;
}

export interface ResumeExperience {
  id?: string;
  company: string;
  title?: string;
  start?: string;
  end?: string;
  location?: string;
  description?: string;
  bullets?: ResumeBullet[];
}

export interface ResumeProject {
  id?: string;
  name: string;
  description?: string;
  technologies?: string[];
  outcomes?: string[];
}

export interface ResumeEducation {
  institution?: string;
  degree?: string;
  field?: string;
  start?: string;
  end?: string;
}

export interface ResumeSkill {
  name: string;
  category?: string;
  years?: number;
}

export interface StructuredResume {
  basics: ResumeBasics;
  summary?: string;
  skills?: ResumeSkill[];
  experience?: ResumeExperience[];
  projects?: ResumeProject[];
  education?: ResumeEducation[];
}

export interface TailoredChange {
  id: string;
  original_text: string;
  tailored_text: string;
  reason: string | null;
  claim_category: string;
  status: string;
  prompt_version: string | null;
  created_at: string;
}

/** UI-only: which change is currently being processed by a review action. */
export interface ReviewBusy {
  changeId: string;
  action: string;
}

export interface TraceEvidence {
  id: string;
  text: string;
  section: string;
  section_id: string | null;
  claim_category: string;
  source_refs: string[];
}

export interface TraceSection {
  id: string;
  company: string;
  title: string;
}

export interface TraceBullet {
  original_id: string;
  original_text: string;
  tailored_text: string;
}

export interface ValidationIssue {
  type: string;
  code: string;
  text: string;
  suggestion: string;
  context: string;
}

export interface ChangeTrace {
  change_id: string;
  original_text: string;
  tailored_text: string;
  reason: string | null;
  claim_category: string;
  status: string;
  evidence: TraceEvidence[];
  section: TraceSection | null;
  bullet: TraceBullet | null;
  validation_issues: ValidationIssue[];
}