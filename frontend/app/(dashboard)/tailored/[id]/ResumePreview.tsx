'use client';

import { useEffect, useRef, useState, type ReactNode } from 'react';
import type { DocModel, DocSection, DocBullet } from '../../../../lib/resumeModel';
import { EmptyState } from '../../../../components/ui';
import { ZoomIn, ZoomOut, RotateCcw } from 'lucide-react';

const BASE_WIDTH = 620; // Base reference width for standard A4 resume layout

// ---------------------------------------------------------------------------
// Auto-Fitting Scale Wrapper
// Observes container width and inner content height via ResizeObserver,
// scaling the fixed A4 resume sheet via CSS transform so it fits 100% of
// the available viewport width without horizontal scrollbars.
// ---------------------------------------------------------------------------

export interface AutoFitScaleProps {
  children: ReactNode;
  className?: string;
  variant?: 'dark' | 'light' | 'plain';
  showControls?: boolean;
  headerTitle?: string;
  headerActions?: ReactNode;
  baseWidth?: number;
  maxHeight?: string;
}

export function AutoFitScale({
  children,
  className = '',
  variant = 'plain',
  showControls = true,
  headerTitle,
  headerActions,
  baseWidth = BASE_WIDTH,
  maxHeight,
}: AutoFitScaleProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const contentRef = useRef<HTMLDivElement>(null);
  const [containerWidth, setContainerWidth] = useState<number>(baseWidth);
  const [contentHeight, setContentHeight] = useState<number>(877); // ~A4 default: 620 * (297 / 210)
  const [isAutoFit, setIsAutoFit] = useState(true);
  const [manualScale, setManualScale] = useState(1);

  // Monitor container width changes (e.g. window resize, split screen, column collapse)
  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    const observer = new ResizeObserver((entries) => {
      for (const entry of entries) {
        if (entry.contentRect.width > 0) {
          setContainerWidth(entry.contentRect.width);
        }
      }
    });

    observer.observe(container);
    return () => observer.disconnect();
  }, []);

  // Monitor content height changes (e.g. user editing fields, adding bullets)
  useEffect(() => {
    const content = contentRef.current;
    if (!content) return;

    const observer = new ResizeObserver((entries) => {
      for (const entry of entries) {
        if (entry.contentRect.height > 0) {
          setContentHeight(entry.contentRect.height);
        }
      }
    });

    observer.observe(content);
    return () => observer.disconnect();
  }, []);

  const horizontalPadding = variant === 'plain' ? 0 : 32;
  const availableWidth = Math.max(containerWidth - horizontalPadding, 80);
  const autoScale = Math.min(Math.max(availableWidth / baseWidth, 0.25), 1.05);
  const activeScale = isAutoFit ? autoScale : manualScale;

  const handleZoomIn = () => {
    setIsAutoFit(false);
    setManualScale((prev) => Math.min(Math.round((prev + 0.1) * 10) / 10, 1.8));
  };

  const handleZoomOut = () => {
    setIsAutoFit(false);
    setManualScale((prev) => Math.max(Math.round((prev - 0.1) * 10) / 10, 0.3));
  };

  const handleToggleFit = () => {
    if (isAutoFit) {
      setIsAutoFit(false);
      setManualScale(1.0);
    } else {
      setIsAutoFit(true);
    }
  };

  const scaledWidth = Math.ceil(baseWidth * activeScale);
  const scaledHeight = Math.ceil(contentHeight * activeScale);
  const zoomPercentage = Math.round(activeScale * 100);

  const containerClasses = {
    dark: 'rounded-[24px] border border-white/10 bg-[#18181b] text-white p-4 sm:p-6 shadow-2xl',
    light: 'rounded-[24px] border border-black/10 bg-surface p-4 sm:p-6 shadow-xs',
    plain: 'w-full',
  }[variant];

  const headerTextClasses = variant === 'dark' ? 'text-white' : 'text-foreground';
  const controlBtnClasses =
    variant === 'dark'
      ? 'border-white/15 bg-white/10 text-white hover:bg-white/20 hover:border-white/30'
      : 'border-black/10 bg-white text-muted hover:text-foreground hover:border-black/20';

  return (
    <div className={`${containerClasses} ${className}`} ref={containerRef}>
      {(showControls || headerTitle || headerActions) && (
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3 border-b border-black/10 dark:border-white/10 pb-3">
          <div className="flex items-center gap-2">
            {headerTitle ? (
              <div className="flex items-center gap-2">
                <span className="h-2 w-2 rounded-full bg-proof animate-pulse" />
                <h3 className={`text-xs font-bold uppercase tracking-wider ${headerTextClasses}`}>
                  {headerTitle}
                </h3>
              </div>
            ) : null}
          </div>

          <div className="flex items-center gap-1.5">
            {showControls && (
              <div className="flex items-center gap-1 font-mono text-[10px]">
                <button
                  type="button"
                  onClick={handleZoomOut}
                  title="Zoom out"
                  className={`rounded-lg border px-2 py-1 font-bold transition ${controlBtnClasses}`}
                >
                  <ZoomOut className="h-3 w-3" />
                </button>
                <button
                  type="button"
                  onClick={handleToggleFit}
                  title={isAutoFit ? 'Switch to 100% size' : 'Fit to width'}
                  className={`rounded-lg border px-2.5 py-1 font-bold transition ${controlBtnClasses}`}
                >
                  {isAutoFit ? 'Fit' : `${zoomPercentage}%`}
                </button>
                <button
                  type="button"
                  onClick={handleZoomIn}
                  title="Zoom in"
                  className={`rounded-lg border px-2 py-1 font-bold transition ${controlBtnClasses}`}
                >
                  <ZoomIn className="h-3 w-3" />
                </button>
                {!isAutoFit && (
                  <button
                    type="button"
                    onClick={() => setIsAutoFit(true)}
                    title="Reset to Fit"
                    className={`rounded-lg border p-1 font-bold transition ${controlBtnClasses}`}
                  >
                    <RotateCcw className="h-3 w-3" />
                  </button>
                )}
              </div>
            )}
            {headerActions}
          </div>
        </div>
      )}

      {/* Viewport canvas for scaled resume */}
      <div
        className={`relative w-full ${
          !isAutoFit && activeScale > autoScale ? 'overflow-x-auto' : 'overflow-x-hidden'
        } ${maxHeight ? 'overflow-y-auto' : ''} a4-scroll`}
        style={maxHeight ? { maxHeight } : undefined}
      >
        <div
          style={{
            width: `${scaledWidth}px`,
            height: `${scaledHeight}px`,
            position: 'relative',
            margin: '0 auto',
            transition: 'width 0.12s ease-out, height 0.12s ease-out',
          }}
        >
          <div
            ref={contentRef}
            style={{
              width: `${baseWidth}px`,
              transform: `scale(${activeScale})`,
              transformOrigin: 'top center',
              position: 'absolute',
              top: 0,
              left: '50%',
              marginLeft: `-${baseWidth / 2}px`,
              transition: 'transform 0.12s ease-out',
            }}
          >
            {children}
          </div>
        </div>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Shared A4 resume preview — used by the review Document tab, the Changes
// diff view (via renderBullet) and the full-screen Edit workspace.
// ---------------------------------------------------------------------------

export interface ResumePreviewProps {
  doc: DocModel | null;
  variant?: 'dark' | 'light' | 'plain';
  showControls?: boolean;
  headerTitle?: string;
  headerActions?: ReactNode;
  className?: string;
  maxHeight?: string;
}

export function ResumePreview({
  doc,
  variant = 'light',
  showControls = true,
  headerTitle,
  headerActions,
  className = '',
  maxHeight,
}: ResumePreviewProps) {
  if (!doc) {
    return <EmptyState label="No resume document available." />;
  }
  return (
    <AutoFitScale
      variant={variant}
      showControls={showControls}
      headerTitle={headerTitle}
      headerActions={headerActions}
      className={className}
      maxHeight={maxHeight}
    >
      <ResumeShell doc={doc} />
    </AutoFitScale>
  );
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
    <div
      className="mx-auto min-h-[877px] w-[620px] rounded-lg border border-black/10 bg-white p-8 sm:p-10 shadow-2xl"
      style={{ width: `${BASE_WIDTH}px` }}
    >
      <div className="flex min-h-full flex-col">
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
