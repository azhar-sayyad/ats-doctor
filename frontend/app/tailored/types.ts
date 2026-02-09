export interface TailoredResume {
  id: string;
  analysis_id: string;
  resume_version_id: string;
  state: string;
  score_before: number | null;
  score_after: number | null;
  content: unknown;
  error: string | null;
  created_at: string;
  updated_at: string;
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