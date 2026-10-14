import { Application, ApplicationStatus } from '../../core/models/application.model';

export interface FunnelStage {
  /** Translation key of the stage name. */
  labelKey: string;
  value: number;
  color: string;
}

const FUNNEL_ORDER: { statuses: ApplicationStatus[]; labelKey: string; color: string }[] = [
  { statuses: ['SAVED', 'PREPARING'], labelKey: 'pipeline.stage.saved', color: 'var(--jb-text-dim)' },
  { statuses: ['APPLIED'], labelKey: 'pipeline.stage.applied', color: 'var(--jb-info)' },
  { statuses: ['RECRUITER_CONTACT'], labelKey: 'pipeline.stage.screen', color: 'var(--jb-violet)' },
  {
    statuses: ['INTERVIEW', 'TECHNICAL_TEST', 'FINAL_ROUND'], labelKey: 'pipeline.stage.interview',
    color: 'var(--jb-accent)',
  },
  { statuses: ['OFFER'], labelKey: 'pipeline.stage.offer', color: 'var(--jb-success)' },
];

/**
 * Cumulative funnel: each stage counts applications that reached it or any
 * later stage, so the funnel always narrows.
 */
export function buildFunnelStages(apps: Application[]): FunnelStage[] {
  const stageIndex = (status: ApplicationStatus): number =>
    FUNNEL_ORDER.findIndex(s => s.statuses.includes(status));
  return FUNNEL_ORDER.map((stage, i) => ({
    labelKey: stage.labelKey,
    value: apps.filter(a => {
      const idx = stageIndex(a.status);
      return idx >= i || (a.status === 'REJECTED' && i <= 1); // rejected apps still passed saved/applied
    }).length,
    color: stage.color,
  }));
}

/** Applications created per day over the last N weeks, oldest first. */
export function buildHeatmapData(apps: Application[], weeks: number): number[] {
  const days = weeks * 7;
  const counts = new Array<number>(days).fill(0);
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  for (const app of apps) {
    const created = new Date(app.appliedAt ?? app.createdAt);
    created.setHours(0, 0, 0, 0);
    const diff = Math.floor((today.getTime() - created.getTime()) / 86400000);
    if (diff >= 0 && diff < days) counts[days - 1 - diff]++;
  }
  return counts;
}
