package com.atsdoctor.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared pipeline seeding + polling for the real-PostgreSQL integration tests
 * (TASK-088 fixtures). The upload/create/await helpers were copy-pasted across
 * the analysis, tailoring and omniroute suites; this base keeps the fixtures
 * identical (Jane Doe resume vs canned Google JD) and the poll semantics
 * uniform — READY/FAILED with a 20s cap. Subclasses keep their own
 * {@code @Container} Postgres + {@code @DynamicPropertySource} storage dirs.
 */
abstract class PipelineIntegrationTestBase {

    protected static final String RESUME_TEXT = "Jane Doe\n"
            + "Senior Software Engineer\njane.doe@example.com | San Francisco, CA\n"
            + "Senior Software Engineer with 5+ years building distributed systems.\n"
            + "SKILLS\nLanguages: Python, FastAPI, PostgreSQL, Redis\n"
            + "EXPERIENCE\nTech Corp — Senior Backend Engineer (2020-01 to present)\n"
            + "- Built FastAPI services processing 2M events/day.\n"
            + "- Led a team of 4 engineers delivering the fraud detection platform.\n"
            + "PROJECTS\nDistributed Queue System — MSc in Computer Science capstone.\n";

    protected static final String JD_TEXT = """
            Senior Backend Engineer (Google, Mountain View, CA)

            At least 5 years of backend development experience.
            Experience with Python and FastAPI required.
            Strong knowledge of PostgreSQL and Redis.
            BSc in Computer Science or related field preferred.

            Responsibilities: design and implement scalable REST APIs,
            optimize database performance, collaborate with frontend teams.
            """;

    @Autowired
    protected MockMvc mvc;

    protected final ObjectMapper mapper = new ObjectMapper();

    /** Upload the Jane Doe fixture and wait for the async parse to finish. */
    protected String uploadResumeAndAwaitReady() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "master-resume.txt",
                "text/plain", RESUME_TEXT.getBytes());
        MvcResult upload = mvc.perform(multipart("/api/v1/resumes/upload").file(file))
                .andExpect(status().isOk())
                .andReturn();
        String versionId = mapper.readTree(upload.getResponse().getContentAsString()).get("id").asText();
        awaitReady("/api/v1/resumes/" + versionId);
        return versionId;
    }

    /** Paste the Google JD fixture and wait for parsing + requirement extraction. */
    protected String createJobAndAwaitReady() throws Exception {
        MvcResult create = mvc.perform(post("/api/v1/jobs").param("text", JD_TEXT))
                .andExpect(status().isOk())
                .andReturn();
        String jobId = mapper.readTree(create.getResponse().getContentAsString()).get("id").asText();
        awaitReady("/api/v1/jobs/" + jobId);
        return jobId;
    }

    protected JsonNode awaitAnalysis(String analysisId) throws Exception {
        return awaitReady("/api/v1/analyses/" + analysisId);
    }

    protected JsonNode awaitTailored(String tailoredId) throws Exception {
        return awaitReady("/api/v1/tailored/" + tailoredId);
    }

    /** GET a resource and expect 200. */
    protected JsonNode getJson(String path) throws Exception {
        MvcResult result = mvc.perform(get(path)).andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    interface Poll {
        JsonNode poll() throws Exception;
    }

    /** Poll until the resource reaches READY; FAILED fails fast, 20s cap. */
    static JsonNode await(long deadline, Poll poll) throws Exception {
        while (System.currentTimeMillis() < deadline) {
            JsonNode node = poll.poll();
            String state = node.get("state").asText();
            if ("READY".equals(state)) {
                return node;
            }
            if ("FAILED".equals(state)) {
                throw new AssertionError("Pipeline FAILED: " + node.get("error").asText());
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Pipeline did not reach READY within 20s");
    }

    static long deadline() {
        return System.currentTimeMillis() + 20_000;
    }

    private JsonNode awaitReady(String path) throws Exception {
        return await(deadline(), () -> getJson(path));
    }
}
