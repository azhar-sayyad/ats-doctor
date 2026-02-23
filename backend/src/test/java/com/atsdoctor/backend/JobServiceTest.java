package com.atsdoctor.backend;

import com.atsdoctor.backend.api.jobs.JobDto;
import com.atsdoctor.backend.api.jobs.JobResponse;
import com.atsdoctor.backend.application.job.JobNotFoundException;
import com.atsdoctor.backend.application.job.JobService;
import com.atsdoctor.backend.domain.states.JobState;
import com.atsdoctor.backend.infrastructure.files.LocalFileStorage;
import com.atsdoctor.backend.infrastructure.parsing.RequirementExtractor;
import com.atsdoctor.backend.infrastructure.persistence.Job;
import com.atsdoctor.backend.infrastructure.persistence.JobRepository;
import com.atsdoctor.backend.infrastructure.persistence.JobRequirement;
import com.atsdoctor.backend.infrastructure.persistence.JobRequirementRepository;
import jakarta.validation.Validation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** TASK-039/043/045 — JD orchestration, state machine enforcement, endpoints backing. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JobServiceTest {

    @Mock
    private JobRepository jobRepository;
    @Mock
    private JobRequirementRepository requirementRepository;
    @Mock
    private LocalFileStorage storage;
    @Mock
    private ApplicationEventPublisher publisher;

    private final RequirementExtractor requirementExtractor = new RequirementExtractor();

    private JobService service;
    private final java.util.ArrayList<JobRequirement> savedRequirements = new java.util.ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new JobService(jobRepository, requirementRepository, storage,
                requirementExtractor, publisher);
        savedRequirements.clear();
        when(requirementRepository.save(any(JobRequirement.class))).thenAnswer(inv -> {
            JobRequirement row = inv.getArgument(0);
            savedRequirements.add(row);
            return row;
        });
        when(requirementRepository.findByJobId(any())).thenAnswer(inv -> savedRequirements);
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void create_from_text_returns_created_job_and_schedules_pipeline() {
        when(jobRepository.saveAndFlush(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));

        JobResponse response = service.createFromText("Senior Backend Engineer\nPython");

        assertThat(response.state()).isEqualTo("CREATED");
        assertThat(response.rawText()).isEqualTo("Senior Backend Engineer\nPython");
        assertThat(response.sourceFilename()).isNull();
        verify(requirementRepository, never()).save(any());
    }

    @Test
    void create_from_file_stores_the_file() throws Exception {
        when(jobRepository.saveAndFlush(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));

        JobResponse response = service.createFromFile("jd.pdf", new byte[]{1, 2, 3});

        assertThat(response.state()).isEqualTo("CREATED");
        assertThat(response.sourceFilename()).isEqualTo("jd.pdf");
        verify(storage).storeJob(any(), anyString(), any(byte[].class));
    }

    @Test
    void mark_enforces_the_state_machine() {
        Job job = job(JobState.CREATED);

        service.mark(job.getId(), JobState.EXTRACTING);

        verify(jobRepository).save(argState(JobState.EXTRACTING));
    }

    @Test
    void mark_rejects_illegal_transitions() {
        Job job = job(JobState.CREATED);

        assertThatThrownBy(() -> service.mark(job.getId(), JobState.PARSING))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CREATED");
    }

    @Test
    void complete_success_persists_structured_data_and_requirements() {
        Job job = job(JobState.PARSING);
        JobDto dto = JobDto.parse("""
                {"job":{"title":"Senior Backend Engineer","company":"Google"},"metadata":{"temperature":0.1}}
                """, Validation.buildDefaultValidatorFactory().getValidator());

        JobResponse response = service.completeSuccess(job.getId(), "raw jd", dto,
                "{\"job\":{\"title\":\"Senior Backend Engineer\"}}",
                """
                {"requirements":[
                  {"text":"Experience with Python and FastAPI","type":"skill","importance":"high","keywords":["Python"]},
                  {"text":"5+ years of backend experience","type":"experience","importance":"high"}
                ]}
                """,
                "stub", "stub", "jd-parser-v1");

        assertThat(response.state()).isEqualTo("READY");
        assertThat(response.title()).isEqualTo("Senior Backend Engineer");
        assertThat(response.rawText()).isEqualTo("raw jd");
        assertThat(response.requirementSummary().get("total")).isEqualTo(2);
        assertThat(response.requirementSummary().get("high")).isEqualTo(2L);
        verify(jobRepository).save(argState(JobState.READY));
    }

    @Test
    void complete_success_caps_overlong_parsed_values_to_column_widths() {
        Job job = job(JobState.PARSING);
        String longCompany = "Company".repeat(60);
        JobDto dto = JobDto.parse("""
                {"job":{"title":"Senior Backend Engineer","company":"%s","location":"Remote"}}
                """.formatted(longCompany), Validation.buildDefaultValidatorFactory().getValidator());

        JobResponse response = service.completeSuccess(job.getId(), "raw jd", dto,
                "{\"job\":{\"title\":\"Senior Backend Engineer\"}}",
                """
                {"requirements":[{"text":"Experience with Python","type":"skill","importance":"high"}]}
                """,
                "stub", "stub", "jd-parser-v1");

        assertThat(response.state()).isEqualTo("READY");
        assertThat(response.company()).hasSize(255);
        assertThat(response.company()).isEqualTo(longCompany.substring(0, 255));
        assertThat(response.location()).isEqualTo("Remote");
    }

    @Test
    void complete_failure_marks_failed_with_error() {
        Job job = job(JobState.PARSING);

        JobResponse response = service.completeFailure(job.getId(), new IllegalStateException("boom"));

        assertThat(response.state()).isEqualTo("FAILED");
        assertThat(response.error()).isEqualTo("boom");
    }

    @Test
    void lookup_surfaces_raw_text_and_filename() {
        Job job = job(JobState.CREATED);

        JobService.JobLookup lookup = service.lookup(job.getId());

        assertThat(lookup.rawText()).isEqualTo("pasted text");
        assertThat(lookup.sourceFilename()).isNull();
    }

    @Test
    void delete_removes_the_job() {
        Job job = job(JobState.READY);

        service.delete(job.getId());

        verify(jobRepository).delete(job);
    }

    @Test
    void missing_job_yields_404_exception() {
        assertThatThrownBy(() -> service.byId(UUID.randomUUID()))
                .isInstanceOf(JobNotFoundException.class)
                .hasMessageContaining("No job found");
    }

    private Job job(JobState state) {
        Job job = new Job();
        job.setState(state.name());
        if (JobState.CREATED.equals(state)) {
            job.setRawText("pasted text");
        }
        when(jobRepository.findById(job.getId())).thenReturn(Optional.of(job));
        return job;
    }

    private static Job argState(JobState state) {
        return ArgumentMatchers.argThat((Job j) -> state.name().equals(j.getState()));
    }
}