import { createFileRoute } from "@tanstack/react-router";

import { StatusDot } from "@/components/hubend/StatusPill";
import { PageHeader, Panel } from "@/components/hubend/bits";
import { INTEGRATION_MATRIX, ISSUES, PHASES, type Decision } from "@/lib/audit/matrix";
import { ENGINES, STATUS_LABEL, STATUS_ORDER, statusBreakdown } from "@/lib/engines/catalog";
import { cn } from "@/lib/utils";

export const Route = createFileRoute("/auditoria")({
  head: () => ({
    meta: [
      { title: "Auditoria e fases — HubEnd" },
      { name: "description", content: "Entrega A: matriz de integração HubOps + HybridApp, problemas encontrados, estado por motor e fases de entrega." },
      { property: "og:title", content: "Auditoria e fases — HubEnd" },
      { property: "og:description", content: "Matriz de integração, problemas e fases do HubEnd." },
      { property: "og:type", content: "article" },
      { name: "twitter:card", content: "summary" },
    ],
  }),
  component: AuditPage,
});

const DECISION_CLASS: Record<Decision, string> = {
  Integrar: "text-status-tested border-status-tested/40",
  Adaptar: "text-status-prepared border-status-prepared/40",
  Unificar: "text-primary border-primary/40",
  Corrigir: "text-status-blocked border-status-blocked/40",
  Planear: "text-status-pending border-status-pending/40",
};

const PHASE_CLASS = {
  concluída: "bg-status-tested/15 text-status-tested",
  "em curso": "bg-status-partial/15 text-status-partial",
  pendente: "bg-muted text-muted-foreground",
  bloqueada: "bg-status-blocked/15 text-status-blocked",
} as const;

function AuditPage() {
  return (
    <div className="grid gap-5">
      <PageHeader
        eyebrow="Entrega A · Auditoria"
        title="Auditoria e fases"
        description="Inventário dos dois projetos, decisões de integração e problemas encontrados. Documento completo em docs/AUDITORIA.md no repositório."
      />

      <Panel eyebrow="Fases" title="Plano de entrega">
        <ol className="grid gap-2 md:grid-cols-3">
          {PHASES.map((p) => (
            <li key={p.id} className="panel-raised p-3">
              <div className="flex items-center justify-between">
                <span className="font-mono text-xs text-primary">Fase {p.id}</span>
                <span className={cn("rounded-full px-2 py-0.5 font-mono text-[10px] uppercase", PHASE_CLASS[p.status])}>{p.status}</span>
              </div>
              <p className="mt-1 font-display font-semibold">{p.title}</p>
              <ul className="mt-2 grid gap-1 text-xs text-muted-foreground">
                {p.criteria.map((c) => (
                  <li key={c}>• {c}</li>
                ))}
              </ul>
            </li>
          ))}
        </ol>
      </Panel>

      <Panel eyebrow="Matriz de integração" title="Componente · Origem · Função · Decisão">
        <div className="grid gap-2">
          {INTEGRATION_MATRIX.map((r) => (
            <div key={r.component} className="panel-raised grid gap-1 p-3 md:grid-cols-[2fr_0.7fr_1.3fr_0.8fr] md:items-center md:gap-3">
              <p className="text-sm font-medium">{r.component}</p>
              <p className="font-mono text-[11px] text-muted-foreground">{r.origin}</p>
              <p className="text-xs text-muted-foreground">
                {r.role}
                <span className="block text-foreground/70">{r.note}</span>
              </p>
              <span className={cn("w-fit rounded-full border px-2 py-0.5 font-mono text-[10px] uppercase", DECISION_CLASS[r.decision])}>
                {r.decision}
              </span>
            </div>
          ))}
        </div>
      </Panel>

      <div className="grid gap-5 lg:grid-cols-2">
        <Panel eyebrow="Problemas" title="Encontrados na inspeção">
          <ul className="grid gap-2">
            {ISSUES.map((i) => (
              <li key={i.title} className="panel-raised p-3">
                <div className="flex items-start justify-between gap-2">
                  <p className="text-sm font-medium">{i.title}</p>
                  <span
                    className={cn("shrink-0 font-mono text-[10px] uppercase", {
                      "text-status-blocked": i.severity === "Alta",
                      "text-status-partial": i.severity === "Média",
                      "text-muted-foreground": i.severity === "Baixa",
                    })}
                  >
                    {i.severity}
                  </span>
                </div>
                <p className="mt-1 text-xs text-muted-foreground">{i.detail}</p>
                <p className="mt-1.5 text-xs text-foreground/80">→ {i.resolution}</p>
              </li>
            ))}
          </ul>
        </Panel>

        <Panel eyebrow="Estado por motor" title="Capacidades documentadas">
          <div className="grid gap-2">
            {ENGINES.map((e) => {
              const b = statusBreakdown(e.capabilities);
              return (
                <div key={e.slug} className="flex items-center justify-between gap-3 rounded-md bg-surface-raised px-3 py-2">
                  <span className="text-sm">{e.name}</span>
                  <span className="flex flex-wrap justify-end gap-2">
                    {STATUS_ORDER.filter((s) => b[s]).map((s) => (
                      <span key={s} className="flex items-center gap-1 font-mono text-[11px] text-muted-foreground" title={STATUS_LABEL[s]}>
                        <StatusDot status={s} /> {b[s]}
                      </span>
                    ))}
                  </span>
                </div>
              );
            })}
          </div>
          <ul className="mt-4 grid grid-cols-2 gap-1.5 text-[11px] text-muted-foreground">
            {STATUS_ORDER.map((s) => (
              <li key={s} className="flex items-center gap-1.5">
                <StatusDot status={s} /> {STATUS_LABEL[s]}
              </li>
            ))}
          </ul>
        </Panel>
      </div>
    </div>
  );
}
