-- CL-020: export template selection. The chosen template slug (resume-templates.yml)
-- is persisted per tailored resume; the definitions themselves stay in the app.
ALTER TABLE tailored_resumes ADD COLUMN template TEXT NOT NULL DEFAULT 'ats_clean';
