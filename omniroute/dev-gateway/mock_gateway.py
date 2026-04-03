#!/usr/bin/env python3
"""
ATS Doctor — development mock AI gateway (OpenAI-compatible).

OmniRoute is external/fictional (docs/13-project-status.md §4), so there is no
real gateway to flip `ATS_DOCTOR_AI_MODE=omniroute` against during development.
This server stands in for it: it implements the OpenAI `/v1/chat/completions`
shape that Spring AI's OpenAiChatClient calls, routes on the prompt text, and
returns the SAME schema-valid deterministic payloads the app's stub provider
produces — so the whole omniroute plumbing (Spring AI -> HTTP -> gateway ->
structured output -> pipeline -> ai_runs provider=omniroute) can be exercised
end to end with zero API keys and full determinism.

When a real OmniRoute instance is available, stop this server, point
SPRING_AI_OPENAI_BASE_URL at it and the app needs no code changes.

Run:
    python3 omniroute/dev-gateway/mock_gateway.py [port]   # default 8080

Then (separate terminal):
    ATS_DOCTOR_AI_MODE=omniroute \
    SPRING_AI_OPENAI_BASE_URL=http://localhost:8080 \
    SPRING_AI_OPENAI_API_KEY=dev-mock \
    mvn -f backend/pom.xml spring-boot:run

Request/response bodies are logged to stdout so you can watch the calls.
"""

import json
import os
import re
import sys
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

MODEL = "deepseek-v4-flash"

# The payloads below mirror StubAiProvider's canned outputs (same §4.2/§4.3
# schemas the pipeline validates), relabelled as dev-mock so ai_runs output is
# unambiguous when a run came through this gateway.

RESUME_JSON = {
    "_mock_gateway": True,
    "metadata": {
        "id": "resume_001",
        "version": 1,
        "source_filename": "master-resume.txt",
        "parsed_at": "2026-08-14T00:00:00Z",
        "model_used": "dev-mock-gateway",
        "model_version": "dev",
        "prompt_version": "resume-parser-v1",
        "temperature": 0.1,
        "is_source_of_truth": True,
        "note": "Dev mock gateway response - connect a real gateway to enable real generation.",
    },
    "basics": {
        "name": "Jane Doe",
        "email": "jane.doe@example.com",
        "phone": "+1234567890",
        "location": "San Francisco, CA",
        "linkedin": "linkedin.com/in/janedoe",
        "github": "github.com/janedoe",
    },
    "summary": "Senior Software Engineer with 5+ years building distributed systems.",
    "skills": [
        {"name": "Python", "category": "Language", "years": 5},
        {"name": "FastAPI", "category": "Framework", "years": 3},
        {"name": "PostgreSQL", "category": "Database", "years": 4},
    ],
    "experience": [
        {
            "id": "exp_001",
            "company": "Tech Corp",
            "title": "Senior Backend Engineer",
            "start": "2020-01",
            "end": "present",
            "location": "Remote",
            "description": "Backend team lead for the payments platform.",
            "bullets": [
                {
                    "id": "exp_001_bullet_001",
                    "text": "Built FastAPI services processing 2M events/day.",
                    "technologies": ["FastAPI", "Python", "PostgreSQL"],
                    "metrics": ["2M events/day"],
                    "domains": ["distributed systems", "backend"],
                    "evidenceLevel": "explicit",
                },
                {
                    "id": "exp_001_bullet_002",
                    "text": "Led a team of 4 engineers delivering the fraud detection platform.",
                    "technologies": ["Python", "Redis"],
                    "metrics": ["4 engineers"],
                    "domains": ["fraud detection", "leadership"],
                    "evidenceLevel": "explicit",
                },
            ],
        }
    ],
    "projects": [
        {
            "id": "proj_001",
            "name": "Distributed Queue System",
            "description": "Scalable task queue for event ingestion.",
            "technologies": ["Python", "Redis"],
            "outcomes": ["Reduced latency by 40%"],
            "bullets": [],
        }
    ],
    "education": [
        {
            "institution": "Stanford University",
            "degree": "MSc Computer Science",
            "field": "Computer Science",
            "start": "2018-01",
            "end": "2020-01",
        }
    ],
    "evidence_model": {
        "source_of_truth": "resume_001_v1",
        "claims": [
            {
                "id": "exp_001_bullet_001",
                "type": "bullet",
                "text": "Built FastAPI services processing 2M events/day.",
                "category": "A",
                "source_refs": ["exp_001"],
                "facts": [
                    {"kind": "technology", "value": "FastAPI"},
                    {"kind": "metric", "value": "2M events/day"},
                ],
            },
            {"id": "skill_python", "type": "skill", "name": "Python", "category": "A"},
        ],
    },
    "task": "resume_parser",
}

JOB_JSON = {
    "_mock_gateway": True,
    "metadata": {
        "id": "job_001",
        "source": "upload",
        "filename": "resume_parser-air.txt",
        "parsed_at": "2026-08-14T00:00:00Z",
        "model_used": "dev-mock-gateway",
        "model_version": "dev",
        "prompt_version": "jd-parser-v1",
        "temperature": 0.1,
        "note": "Dev mock gateway response - connect a real gateway to enable real generation.",
    },
    "job": {
        "title": "Senior Backend Engineer",
        "company": "Google",
        "location": "Mountain View, CA",
        "seniority": "Senior",
        "post_date": "2026-08-10",
    },
    "requirements": [
        {"id": "req_001", "text": "5+ years of backend development experience",
         "type": "experience", "importance": "high", "keywords": ["backend", "5+ years", "experience"]},
        {"id": "req_002", "text": "Experience with Python and FastAPI",
         "type": "skill", "importance": "high", "keywords": ["Python", "FastAPI"]},
        {"id": "req_003", "text": "Strong knowledge of PostgreSQL and Redis",
         "type": "skill", "importance": "high", "keywords": ["PostgreSQL", "Redis"]},
        {"id": "req_004", "text": "BSc in Computer Science or related field",
         "type": "education", "importance": "medium", "keywords": ["Computer Science", "BSc"]},
    ],
    "skills": {"required": ["Python", "FastAPI", "PostgreSQL"],
               "preferred": ["Docker", "AWS", "Kubernetes"],
               "nice_to_have": ["Kafka", "gRPC"]},
    "responsibilities": [
        "Design and implement scalable REST APIs",
        "Optimize database performance",
        "Collaborate with frontend teams",
    ],
    "keywords": ["Python", "FastAPI", "PostgreSQL", "REST", "scalable", "distributed"],
    "task": "jd_parser",
}

REQUIREMENTS_JSON = {
    "_mock_gateway": True,
    "task": "requirement_extraction",
    "requirements": [
        {"id": "req_001", "text": "5+ years of backend development experience",
         "type": "experience", "importance": "high", "keywords": ["backend", "5+ years", "experience"]},
        {"id": "req_002", "text": "Experience with Python and FastAPI",
         "type": "skill", "importance": "high", "keywords": ["Python", "FastAPI"]},
        {"id": "req_003", "text": "Strong knowledge of PostgreSQL and Redis",
         "type": "skill", "importance": "high", "keywords": ["PostgreSQL", "Redis"]},
        {"id": "req_004", "text": "BSc in Computer Science or related field",
         "type": "education", "importance": "medium", "keywords": ["Computer Science", "BSc"]},
        {"id": "req_005", "text": "Experience with REST API design",
         "type": "skill", "importance": "medium", "keywords": ["REST", "API design"]},
    ],
    "note": "Dev mock gateway response - connect a real gateway to enable real generation.",
}

STOP_WORDS = {
    "experience", "strong", "knowledge", "with", "of", "and", "in", "or",
    "related", "field", "years", "year", "required", "preferred", "the",
    "a", "an", "to", "for", "design", "implement", "collaborate", "optimize",
    "development", "technologies", "using", "based", "other",
}

STOP_WORDS_VALIDATOR = STOP_WORDS | {
    "focus", "python", "fastapi", "postgresql", "redis", "rest", "api", "scalable",
}

TOKEN_RE = re.compile(r"[^a-z0-9+#.\-]+")
TRAILING = ".?!;:,"


def tokens(text: str):
    out = []
    for raw in TOKEN_RE.split((text or "").lower()):
        tok = raw.rstrip(TRAILING)
        if tok:
            out.append(tok)
    return out


def label(prompt: str, key: str):
    """Return the text that follows '<key>:' until the next blank-line rule."""
    start = prompt.find(key)
    if start < 0:
        return ""
    rest = prompt[start + len(key):].strip()
    return rest.split("\n\nRules:")[0].split("\n\n")[0].strip()


def rewrite_bullet(prompt: str) -> str:
    """Mirror StubAiProvider.cannedTailoring: append a grounded focus clause."""
    original = label(prompt, "Original bullet:\n") or ""
    reqs = label(prompt, "Target requirements:\n") or ""
    keywords = label(prompt, "Keywords to align with (only where they are already supported by evidence):\n") or ""
    evidence = label(prompt, "Evidence (facts allowed to reference):\n") or ""

    evidence_set = set(tokens(evidence))
    bullet_set = set(tokens(original))
    keyword = "python"
    for tok in tokens(keywords + "\n" + reqs):
        if len(tok) < 3 or tok in STOP_WORDS:
            continue
        if tok not in bullet_set:
            keyword = tok
            break

    body = original[:-1] if original.endswith(".") else original
    tailored = f"{body}, with a focus on {keyword}." if keyword else original
    return {"_mock_gateway": True, "task": "resume_tailoring",
            "tailored_text": tailored,
            "note": "Dev mock gateway response - connect a real gateway to enable real generation."}


def validate(prompt: str):
    """Mirror StubAiProvider.cannedFactValidation (grounded-token rule)."""
    original = label(prompt, "Original resume:\n") or ""
    tailored = label(prompt, "Tailored resume:\n") or ""
    evidence = label(prompt, "Evidence (facts the tailored text may reference):\n") or ""
    category = label(prompt, "Claim category under validation:\n") or ""

    grounded = set(tokens(original + "\n" + evidence))
    issues = []
    if category.upper() != "C":
        for tok in tokens(tailored):
            if len(tok) < 3 or tok in STOP_WORDS_VALIDATOR or tok in grounded:
                continue
            if re.search(r"\d", tok):
                itype = "inflated_metric"
            elif re.search(r"[+#.]", tok):
                itype = "unsupported_technology"
            else:
                itype = "unsupported_descriptor"
            issues.append({
                "type": itype, "text": tok, "original_evidence": "",
                "suggestion": f"Remove or ground '{tok}' in the original resume.",
            })
    return {"_mock_gateway": True, "task": "fact_validation",
            "is_valid": not issues, "issues": issues}


def route(prompt: str):
    if "You are a resume parser" in prompt:
        return RESUME_JSON
    if "You are a job-description parser" in prompt:
        return JOB_JSON
    if "You extract discrete requirements" in prompt:
        return REQUIREMENTS_JSON
    if "Rewrite a single resume bullet" in prompt:
        return rewrite_bullet(prompt)
    if "Validate that a tailored resume" in prompt:
        return validate(prompt)
    return {"_mock_gateway": True, "task": "unknown",
            "note": "Dev mock gateway could not route this prompt."}


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):  # quieter logs
        sys.stderr.write("mock-gateway %s\n" % (fmt % args))

    def _send(self, status, payload):
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path == "/health":
            self._send(200, {"status": "ok", "service": "ats-doctor-mock-gateway"})
            return
        if self.path == "/v1/models":
            self._send(200, {"object": "list", "data": [{"id": MODEL, "object": "model"}]})
            return
        self._send(404, {"error": {"message": "not found", "type": "invalid_request_error"}})

    def _read_body(self):
        """Read the request body, handling Content-Length or chunked encoding.

        Reactor Netty (Spring AI's WebClient) sends /v1/chat/completions bodies
        with Transfer-Encoding: chunked, which BaseHTTPRequestHandler does not
        decode — without this, the payload is seen as empty and the leftover
        chunk frames corrupt the next keep-alive request.
        """
        length = int(self.headers.get("Content-Length", "0") or 0)
        if length:
            return self.rfile.read(length)
        body = b""
        while True:
            size_line = self.rfile.readline().strip()
            if not size_line:
                break
            try:
                size = int(size_line.split(b";", 1)[0], 16)
            except ValueError:
                break
            if size == 0:
                self.rfile.readline()
                break
            body += self.rfile.read(size)
            self.rfile.readline()
        return body

    def do_POST(self):
        if self.path != "/v1/chat/completions":
            self._send(404, {"error": {"message": "not found", "type": "invalid_request_error"}})
            return
        try:
            request = json.loads(self._read_body() or b"{}")
        except json.JSONDecodeError:
            self._send(400, {"error": {"message": "invalid JSON", "type": "invalid_request_error"}})
            return

        messages = request.get("messages", [])
        prompt = "\n".join(str(m.get("content", "")) for m in messages if m.get("content"))
        model = request.get("model") or MODEL

        content = route(prompt)
        print(f"mock-gateway: routed prompt to task={content.get('task', 'unknown')} model={model}")

        self._send(200, {
            "id": f"chatcmpl-mock-{int(time.time() * 1000)}",
            "object": "chat.completion",
            "created": int(time.time()),
            "model": model,
            "choices": [
                {"index": 0, "message": {"role": "assistant", "content": json.dumps(content)},
                 "finish_reason": "stop"}
            ],
            "usage": {"prompt_tokens": 0, "completion_tokens": 0, "total_tokens": 0},
        })


def main():
    port = int(sys.argv[1]) if len(sys.argv) > 1 else int(os.environ.get("MOCK_GATEWAY_PORT", "8080"))
    server = ThreadingHTTPServer(("0.0.0.0", port), Handler)
    print(f"ATS Doctor mock gateway listening on http://localhost:{port}"
          f" (set ATS_DOCTOR_AI_MODE=omniroute + SPRING_AI_OPENAI_BASE_URL=http://localhost:{port})")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nmock-gateway shutting down")


if __name__ == "__main__":
    main()
