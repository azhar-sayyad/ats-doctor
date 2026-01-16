-- Fact-validation output storage (PRD §5.5/§5.8, FEAT-035, TASK-071).
-- `validation` jsonb holds the rule-engine report written by the VALIDATING
-- stage (pipeline) and by POST /tailored/{id}/validate (revalidation after
-- READY). Document shape (snake_case):
--   {"valid": bool, "checked_changes": int, "validated_at": iso,
--    "issues": [{"type","code","text","suggestion","context","change_id","claim_category"}]}
ALTER TABLE tailored_resumes ADD COLUMN validation JSONB;