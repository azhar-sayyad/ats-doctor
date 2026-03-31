'use client';

import type { ReactNode } from 'react';
import type { DocModel, DocSection, DocBullet } from '../../../../lib/resumeModel';
import { EmptyState } from '../../../../components/ui';

// ---------------------------------------------------------------------------
// Shared A4 resume preview — used by the review Document tab, the Changes
// diff view (via renderBullet) and the full-screen Edit workspace.
// ---------------------------------------------------------------------------

export function ResumePreview({ doc }: { doc: DocModel | null }) {
  if (!doc) {
    return <EmptyState label="No resume document available." />;
  }
  return <ResumeShell doc={doc} />;
}

export function ResumeShell({
  doc,
  renderBullet,
}: {
  doc: DocModel;
  renderBullet?: (section: DocSection, bullet: DocBullet, index: number) => ReactNode;
}) {
  const contact = [doc.basics.email, doc.basics.phone, doc.basics.location, doc.basics.linkedin, doc.basics.github]
    .filter(Boolean)
    .join('  ·  ');
  const bulletNode = renderBullet ?? ((_section: DocSection, bullet: DocBullet) => <DefaultBullet bullet={bullet} />);

  return (
    <div className="w-full overflow-x-auto rounded-lg">
      <div
        className="a4-scroll mx-auto aspect-[210/297] w-[620px] overflow-y-auto rounded-lg border border-black/10 bg-white shadow-lg"
        style={{ minWidth: '620px' }}
      >
        <div className="flex min-h-full flex-col px-10 py-9">
        {doc.basics.name && (
          <h1 className="font-editorial text-2xl font-semibold tracking-tight text-foreground">
            {doc.basics.name}
          </h1>
        )}
        {contact && <p className="mt-1 font-mono text-[9px] uppercase tracking-widest text-muted">{contact}</p>}

        <div className="mt-5 space-y-4">
          {doc.summary && (
            <div>
              <SectionRule />
              <p className="text-[11px] leading-relaxed text-foreground">{doc.summary.effective}</p>
            </div>
          )}

          {doc.skills.length > 0 && (
            <div>
              <SectionRule label="Skills" />
              <div className="flex flex-wrap gap-1.5">
                {doc.skills.map((skill, i) => (
                  <span
                    key={i}
                    className="rounded-md border border-black/10 bg-surface px-2 py-0.5 text-[9px] font-mono text-foreground"
                  >
                    {skill.name}
                  </span>
                ))}
              </div>
            </div>
          )}

          {doc.sections.length > 0 && (
            <div>
              <SectionRule label="Experience" />
              <div className="space-y-4">
                {doc.sections.map((section, i) => (
                  <ExperienceBlock key={i} section={section} bulletRenderer={bulletNode} />
                ))}
              </div>
            </div>
          )}

          {doc.projects.length > 0 && (
            <div>
              <SectionRule label="Projects" />
              <div className="space-y-2">
                {doc.projects.map((project, i) => (
                  <div key={i}>
                    <p className="text-[11px] font-bold text-foreground">{project.name}</p>
                    {project.description && (
                      <p className="text-[11px] leading-relaxed text-muted">{project.description}</p>
                    )}
                    {(project.technologies ?? []).length > 0 && (
                      <p className="mt-0.5 font-mono text-[8px] uppercase tracking-wider text-faint">
                        {project.technologies!.join(', ')}
                      </p>
                    )}
                  </div>
                ))}
              </div>
            </div>
          )}

          {doc.education.length > 0 && (
            <div>
              <SectionRule label="Education" />
              <div className="space-y-1.5">
                {doc.education.map((edu, i) => (
                  <p key={i} className="text-[11px] text-foreground">
                    <span className="font-semibold">{[edu.degree, edu.field].filter(Boolean).join(' — ')}</span>
                    {edu.institution ? `, ${edu.institution}` : ''}
                    {[edu.start, edu.end].filter(Boolean).length > 0 && (
                      <span className="text-muted"> · {[edu.start, edu.end].filter(Boolean).join(' – ')}</span>
                    )}
                  </p>
                ))}
              </div>
            </div>
          )}
        </div>
        </div>
      </div>
    </div>
  );
}

function SectionRule({ label }: { label?: string }) {
  return (
    <div className="mb-1.5 flex items-center gap-2">
      {label && (
        <span className="font-mono text-[8px] font-bold uppercase tracking-[0.2em] text-brand">{label}</span>
      )}
      <div className="h-px flex-1 bg-black/15" />
    </div>
  );
}

function ExperienceBlock({
  section,
  bulletRenderer,
}: {
  section: DocSection;
  bulletRenderer: (section: DocSection, bullet: DocBullet, index: number) => ReactNode;
}) {
  const dates = [section.start, section.end].filter(Boolean).join(' – ');
  return (
    <div>
      <div className="flex items-baseline justify-between gap-3">
        <p className="text-[11px] font-bold text-foreground">
          {[section.title, section.company].filter(Boolean).join(' · ')}
          {section.location ? <span className="font-normal text-muted"> — {section.location}</span> : null}
        </p>
        {dates && <p className="shrink-0 font-mono text-[8px] text-faint">{dates}</p>}
      </div>
      <ul className="mt-1 space-y-1">
        {section.bullets.map((bullet, j) => (
          <li key={j} className="text-[10.5px] leading-relaxed">
            {bulletRenderer(section, bullet, j)}
          </li>
        ))}
      </ul>
    </div>
  );
}

function DefaultBullet({ bullet }: { bullet: DocBullet }) {
  return (
    <span className="flex items-start gap-1.5">
      <span className="mt-[0.35em] h-[3px] w-[3px] shrink-0 rounded-full bg-foreground/70" />
      <span className="text-foreground">{bullet.effectiveText}</span>
    </span>
  );
}
