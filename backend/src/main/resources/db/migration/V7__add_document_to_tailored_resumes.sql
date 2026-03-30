-- Full editable tailored document storage (tailored edit workspace).
-- `document` jsonb holds the merged tailored resume (basics, summary,
-- skills, experience with effective bullets, projects, education) exactly as
-- the preview/editors render it. NULL until the user saves an edit via
-- PUT /tailored/{id}/edit; while NULL the preview falls back to the derived
-- model (master structured_data + tailored content + change statuses).
ALTER TABLE tailored_resumes ADD COLUMN document JSONB;
