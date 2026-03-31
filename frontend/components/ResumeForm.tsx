'use client';

import { Plus, Trash2 } from 'lucide-react';
import type { StructuredResume } from '../app/(dashboard)/tailored/types';

interface Props {
  draft: StructuredResume;
  patch: (updater: (d: StructuredResume) => void) => void;
}

const inputClass =
  'w-full rounded-xl border border-black/15 bg-white px-3.5 py-2 text-sm text-foreground placeholder:text-faint focus:border-brand focus:outline-none focus:ring-1 focus:ring-brand transition';

/** Structured resume editor shared by the master resume page and the tailored edit workspace. */
export default function ResumeForm({ draft, patch }: Props) {
  return (
    <div className="space-y-6">
      {/* Basics Section */}
      <section className="rounded-[24px] border border-black/10 bg-white p-6 sm:p-7 shadow-xs">
        <h2 className="mb-4 text-lg font-bold tracking-tight text-foreground flex items-center gap-2">
          Basics & Profile
        </h2>
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <input
            className={inputClass}
            value={draft.basics.name ?? ''}
            placeholder="Full Name"
            onChange={(e) => patch((d) => void (d.basics.name = e.target.value))}
          />
          <input
            className={inputClass}
            value={draft.basics.email ?? ''}
            placeholder="Email Address"
            onChange={(e) => patch((d) => void (d.basics.email = e.target.value))}
          />
          <input
            className={inputClass}
            value={draft.basics.phone ?? ''}
            placeholder="Phone"
            onChange={(e) => patch((d) => void (d.basics.phone = e.target.value))}
          />
          <input
            className={inputClass}
            value={draft.basics.location ?? ''}
            placeholder="Location (e.g. San Francisco, CA)"
            onChange={(e) => patch((d) => void (d.basics.location = e.target.value))}
          />
          <input
            className={inputClass}
            value={draft.basics.linkedin ?? ''}
            placeholder="LinkedIn Profile URL"
            onChange={(e) => patch((d) => void (d.basics.linkedin = e.target.value))}
          />
          <input
            className={inputClass}
            value={draft.basics.github ?? ''}
            placeholder="GitHub Profile URL"
            onChange={(e) => patch((d) => void (d.basics.github = e.target.value))}
          />
        </div>
        <textarea
          className={`${inputClass} mt-3`}
          rows={3}
          value={draft.summary ?? ''}
          placeholder="Professional Summary"
          onChange={(e) => patch((d) => void (d.summary = e.target.value))}
        />
      </section>

      {/* Skills Section */}
      <section className="rounded-[24px] border border-black/10 bg-white p-6 sm:p-7 shadow-xs">
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-lg font-bold tracking-tight text-foreground">Skills Architecture</h2>
          <button
            onClick={() =>
              patch((d) => {
                if (!d.skills) d.skills = [];
                d.skills.push({ name: '', category: 'Technical' });
              })
            }
            className="inline-flex items-center gap-1 rounded-lg bg-surface border border-black/15 px-3 py-1 text-xs font-semibold text-foreground hover:bg-white transition"
          >
            <Plus className="h-3.5 w-3.5 text-brand" />
            <span>Add Skill</span>
          </button>
        </div>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          {(draft.skills ?? []).map((skill, i) => (
            <div key={i} className="flex items-center gap-2">
              <input
                className={inputClass}
                value={skill.name}
                placeholder="Skill name"
                onChange={(e) =>
                  patch((d) => {
                    if (d.skills?.[i]) d.skills[i].name = e.target.value;
                  })
                }
              />
              <input
                className={`${inputClass} max-w-[9rem] font-mono text-xs`}
                value={skill.category ?? ''}
                placeholder="Category"
                onChange={(e) =>
                  patch((d) => {
                    if (d.skills?.[i]) d.skills[i].category = e.target.value;
                  })
                }
              />
              <button
                onClick={() =>
                  patch((d) => {
                    if (d.skills) d.skills.splice(i, 1);
                  })
                }
                className="rounded-lg p-2 text-coral hover:bg-coral-soft transition"
                title="Remove skill"
              >
                <Trash2 className="h-4 w-4" />
              </button>
            </div>
          ))}
        </div>
        {(draft.skills ?? []).length === 0 && (
          <p className="text-sm text-faint italic">No skills extracted yet. Click &quot;Add Skill&quot; to add skills manually.</p>
        )}
      </section>

      {/* Experience Section */}
      <section className="rounded-[24px] border border-black/10 bg-white p-6 sm:p-7 shadow-xs">
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-lg font-bold tracking-tight text-foreground">Work Experience</h2>
          <button
            onClick={() =>
              patch((d) => {
                if (!d.experience) d.experience = [];
                d.experience.push({
                  company: 'New Company',
                  title: 'Title',
                  bullets: [{ text: '' }],
                });
              })
            }
            className="inline-flex items-center gap-1 rounded-lg bg-surface border border-black/15 px-3 py-1 text-xs font-semibold text-foreground hover:bg-white transition"
          >
            <Plus className="h-3.5 w-3.5 text-brand" />
            <span>Add Experience</span>
          </button>
        </div>
        {(draft.experience ?? []).map((exp, i) => (
          <div key={i} className="mb-6 border-b border-black/10 pb-6 last:border-0 last:pb-0">
            <div className="mb-3 flex items-center justify-between gap-3">
              <span className="font-mono text-xs font-bold text-brand">Role #{i + 1}</span>
              <button
                onClick={() =>
                  patch((d) => {
                    if (d.experience) d.experience.splice(i, 1);
                  })
                }
                className="inline-flex items-center gap-1 rounded-md px-2 py-1 text-xs font-medium text-coral hover:bg-coral-soft transition"
              >
                <Trash2 className="h-3.5 w-3.5" />
                <span>Remove Role</span>
              </button>
            </div>
            <div className="mb-3 grid grid-cols-1 gap-3 sm:grid-cols-2">
              <input
                className={inputClass}
                value={exp.company}
                placeholder="Company Name"
                onChange={(e) =>
                  patch((d) => {
                    if (d.experience?.[i]) d.experience[i].company = e.target.value;
                  })
                }
              />
              <input
                className={inputClass}
                value={exp.title ?? ''}
                placeholder="Role / Title"
                onChange={(e) =>
                  patch((d) => {
                    if (d.experience?.[i]) d.experience[i].title = e.target.value;
                  })
                }
              />
            </div>
            <div className="mb-3 grid grid-cols-1 gap-3 sm:grid-cols-2">
              <input
                className={inputClass}
                value={exp.start ?? ''}
                placeholder="Start Date (YYYY-MM)"
                onChange={(e) =>
                  patch((d) => {
                    if (d.experience?.[i]) d.experience[i].start = e.target.value;
                  })
                }
              />
              <input
                className={inputClass}
                value={exp.end ?? ''}
                placeholder="End Date (YYYY-MM or Present)"
                onChange={(e) =>
                  patch((d) => {
                    if (d.experience?.[i]) d.experience[i].end = e.target.value;
                  })
                }
              />
            </div>
            <div className="mb-3 grid grid-cols-1 gap-3 sm:grid-cols-2">
              <input
                className={inputClass}
                value={exp.location ?? ''}
                placeholder="Location"
                onChange={(e) =>
                  patch((d) => {
                    if (d.experience?.[i]) d.experience[i].location = e.target.value;
                  })
                }
              />
            </div>
            <div className="space-y-2 mt-3">
              <div className="flex items-center justify-between">
                <p className="font-mono text-[10px] font-bold uppercase tracking-widest text-faint">Bullets</p>
                <button
                  onClick={() =>
                    patch((d) => {
                      if (d.experience?.[i]) {
                        if (!d.experience[i].bullets) d.experience[i].bullets = [];
                        d.experience[i].bullets.push({ text: '' });
                      }
                    })
                  }
                  className="inline-flex items-center gap-1 text-[11px] font-semibold text-brand hover:underline"
                >
                  <Plus className="h-3 w-3" />
                  <span>Add Bullet</span>
                </button>
              </div>
              {(exp.bullets ?? []).map((bullet, j) => (
                <div key={j} className="flex items-start gap-2">
                  <textarea
                    className={inputClass}
                    rows={2}
                    value={bullet.text}
                    placeholder="Achievement bullet..."
                    onChange={(e) =>
                      patch((d) => {
                        const target = d.experience?.[i]?.bullets?.[j];
                        if (target) target.text = e.target.value;
                      })
                    }
                  />
                  <button
                    onClick={() =>
                      patch((d) => {
                        if (d.experience?.[i]?.bullets) {
                          d.experience[i].bullets.splice(j, 1);
                        }
                      })
                    }
                    className="mt-2 text-coral hover:bg-coral-soft p-1.5 rounded-lg transition"
                    title="Remove bullet"
                  >
                    <Trash2 className="h-4 w-4" />
                  </button>
                </div>
              ))}
            </div>
          </div>
        ))}
        {(draft.experience ?? []).length === 0 && (
          <p className="text-sm text-faint italic">No work experience entries parsed. Click &quot;Add Experience&quot; to add entries.</p>
        )}
      </section>

      {/* Projects Section */}
      <section className="rounded-[24px] border border-black/10 bg-white p-6 sm:p-7 shadow-xs">
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-lg font-bold tracking-tight text-foreground">Projects</h2>
          <button
            onClick={() =>
              patch((d) => {
                if (!d.projects) d.projects = [];
                d.projects.push({ name: 'New Project', description: '', technologies: [] });
              })
            }
            className="inline-flex items-center gap-1 rounded-lg bg-surface border border-black/15 px-3 py-1 text-xs font-semibold text-foreground hover:bg-white transition"
          >
            <Plus className="h-3.5 w-3.5 text-brand" />
            <span>Add Project</span>
          </button>
        </div>
        {(draft.projects ?? []).map((project, i) => (
          <div key={i} className="mb-5 border-b border-black/10 pb-5 last:border-0 last:pb-0">
            <div className="mb-2 flex items-center justify-between">
              <input
                className={inputClass}
                value={project.name}
                placeholder="Project Name"
                onChange={(e) =>
                  patch((d) => {
                    if (d.projects?.[i]) d.projects[i].name = e.target.value;
                  })
                }
              />
              <button
                onClick={() =>
                  patch((d) => {
                    if (d.projects) d.projects.splice(i, 1);
                  })
                }
                className="ml-2 text-coral hover:bg-coral-soft p-2 rounded-lg transition shrink-0"
                title="Remove project"
              >
                <Trash2 className="h-4 w-4" />
              </button>
            </div>
            <textarea
              className={`${inputClass} mt-2`}
              rows={2}
              value={project.description ?? ''}
              placeholder="Project summary..."
              onChange={(e) =>
                patch((d) => {
                  if (d.projects?.[i]) d.projects[i].description = e.target.value;
                })
              }
            />
            <div className="mt-2 grid grid-cols-1 sm:grid-cols-2 gap-2">
              <input
                className={`${inputClass} font-mono text-xs`}
                value={(project.technologies ?? []).join(', ')}
                placeholder="Tech stack (comma separated)"
                onChange={(e) =>
                  patch((d) => {
                    if (d.projects?.[i]) {
                      d.projects[i].technologies = e.target.value
                        .split(',')
                        .map((t) => t.trim())
                        .filter(Boolean);
                    }
                  })
                }
              />
              <input
                className={`${inputClass} font-mono text-xs`}
                value={(project.outcomes ?? []).join(', ')}
                placeholder="Outcomes (comma separated)"
                onChange={(e) =>
                  patch((d) => {
                    if (d.projects?.[i]) {
                      d.projects[i].outcomes = e.target.value
                        .split(',')
                        .map((t) => t.trim())
                        .filter(Boolean);
                    }
                  })
                }
              />
            </div>
          </div>
        ))}
        {(draft.projects ?? []).length === 0 && (
          <p className="text-sm text-faint italic">No projects entries parsed. Click &quot;Add Project&quot; to add project entries.</p>
        )}
      </section>
    </div>
  );
}
