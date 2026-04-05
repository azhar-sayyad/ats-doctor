package com.atsdoctor.backend.infrastructure.tailoring;

import com.atsdoctor.backend.api.resume.ResumeDto;

import java.util.List;

/**
 * Stable identity for master-resume sections and bullets that lack explicit
 * ids (e.g. resumes parsed by the LLM parser, which never emits ids): a
 * deterministic {@code exp_N} / {@code exp_N_bullet_M} key derived from
 * position. Single source of truth so the tailor decider (which selects
 * bullets) and the pipeline writer (which stashes rewrites in the tailored
 * content JSON) can never disagree on a bullet key.
 */
public final class BulletKeys {

    private BulletKeys() {
    }

    public static String sectionId(List<ResumeDto.Experience> experience, ResumeDto.Experience exp) {
        if (exp.id() != null) {
            return exp.id();
        }
        return "exp_" + (experience.indexOf(exp) + 1);
    }

    public static String bulletId(List<ResumeDto.Experience> experience, ResumeDto.Experience exp,
                                  List<ResumeDto.Bullet> bullets, ResumeDto.Bullet bullet) {
        if (bullet.id() != null) {
            return bullet.id();
        }
        return sectionId(experience, exp) + "_bullet_" + (bullets.indexOf(bullet) + 1);
    }
}