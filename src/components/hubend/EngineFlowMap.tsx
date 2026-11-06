import { Link } from "@tanstack/react-router";

import { FLOW_LABEL, REGISTRY_FLOWS, type FlowMode, type RegistryEngineId } from "@/lib/engines/catalog";
import { cn } from "@/lib/utils";

/**
 * Mapa dos fluxos autorizados entre motores, desenhado a partir de REGISTRY_FLOWS
 * (o mesmo conteúdo de HubOpsEngineRegistry.kt). Omega (SNSE, NSC, GoPA) vive dentro do Core.
 */
const NODES: Record<RegistryEngineId, { x: number; y: number; label: string; slug: string }> = {
  HUBOPS: { x: 160, y: 40, label: "HubOps Core", slug: "hubops-core" },
  DESPERTAR: { x: 50, y: 190, label: "Despertar", slug: "despertar" },
  ESPELHAR_X: { x: 270, y: 190, label: "Espelhar-X", slug: "espelhar-x" },
  VISLUMBOPS: { x: 160, y: 300, label: "VislumbOps", slug: "vislumbops" },
};

const MODE_CLASS: Record<FlowMode, string> = {
  GOVERNED_MUTATION: "stroke-primary",
  READ_ONLY_ANALYSIS: "stroke-status-prepared",
  EVIDENCE_ONLY: "stroke-status-partial",
};
const MODE_SWATCH: Record<FlowMode, string> = {
  GOVERNED_MUTATION: "bg-primary",
  READ_ONLY_ANALYSIS: "bg-status-prepared",
  EVIDENCE_ONLY: "bg-status-partial",
};

function path(a: RegistryEngineId, b: RegistryEngineId) {
  const p = NODES[a];
  const q = NODES[b];
  // Curva ligeira para distinguir os dois sentidos entre o mesmo par.
  const mx = (p.x + q.x) / 2;
  const my = (p.y + q.y) / 2;
  const dx = q.x - p.x;
  const dy = q.y - p.y;
  const len = Math.hypot(dx, dy) || 1;
  const off = 18;
  const cx = mx + (-dy / len) * off;
  const cy = my + (dx / len) * off;
  return `M ${p.x} ${p.y} Q ${cx} ${cy} ${q.x} ${q.y}`;
}

export function EngineFlowMap({ className }: { className?: string }) {
  return (
    <div className={cn("grid gap-4", className)}>
      <svg viewBox="0 0 320 340" className="mx-auto w-full max-w-sm" role="img" aria-label="Fluxos autorizados entre motores">
        <defs>
          {(Object.keys(MODE_CLASS) as FlowMode[]).map((m) => (
            <marker key={m} id={`arrow-${m}`} viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
              <path d="M 0 0 L 10 5 L 0 10 z" className={cn(MODE_CLASS[m].replace("stroke-", "fill-"))} />
            </marker>
          ))}
        </defs>
        {REGISTRY_FLOWS.map((f) => (
          <path
            key={`${f.source}-${f.target}`}
            d={path(f.source, f.target)}
            fill="none"
            strokeWidth={1.6}
            strokeDasharray={f.mode === "GOVERNED_MUTATION" ? undefined : "4 4"}
            className={MODE_CLASS[f.mode]}
            markerEnd={`url(#arrow-${f.mode})`}
            opacity={0.85}
          />
        ))}
        {(Object.keys(NODES) as RegistryEngineId[]).map((id) => {
          const n = NODES[id];
          return (
            <Link key={id} to="/motores/$engineId" params={{ engineId: n.slug }}>
              <g className="cursor-pointer">
                <rect x={n.x - 52} y={n.y - 17} width={104} height={34} rx={9} className="fill-surface-raised stroke-border" strokeWidth={1} />
                <text x={n.x} y={n.y + 4} textAnchor="middle" className="fill-foreground font-display text-[12px] font-semibold">
                  {n.label}
                </text>
              </g>
            </Link>
          );
        })}
        <text x={160} y={72} textAnchor="middle" className="fill-muted-foreground font-mono text-[8px] uppercase tracking-widest">
          SNSE · NSC · GoPA
        </text>
      </svg>
      <ul className="flex flex-wrap justify-center gap-x-4 gap-y-1.5 text-xs text-muted-foreground">
        {(Object.keys(FLOW_LABEL) as FlowMode[]).map((m) => (
          <li key={m} className="flex items-center gap-1.5">
            <span className={cn("h-0.5 w-4 rounded", MODE_SWATCH[m])} />
            {FLOW_LABEL[m]}
          </li>
        ))}
      </ul>
    </div>
  );
}
