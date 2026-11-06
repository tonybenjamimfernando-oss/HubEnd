import { createFileRoute, Link } from "@tanstack/react-router";
import { ArrowUpRight } from "lucide-react";

import { EngineFlowMap } from "@/components/hubend/EngineFlowMap";
import { StatusDot } from "@/components/hubend/StatusPill";
import { PageHeader, Panel } from "@/components/hubend/bits";
import { ENGINES, FLOW_LABEL, REGISTRY_FLOWS, STATUS_ORDER, statusBreakdown } from "@/lib/engines/catalog";

export const Route = createFileRoute("/motores/")({
  head: () => ({
    meta: [
      { title: "Motores — HubEnd" },
      { name: "description", content: "HubOps Core, Espelhar-X, Despertar, VislumbOps, SNSE, GoPA e NSC: capacidades, estado e fluxos autorizados." },
      { property: "og:title", content: "Motores — HubEnd" },
      { property: "og:description", content: "Sete motores autónomos com contratos claros e fluxos governados." },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary" },
    ],
  }),
  component: EnginesPage,
});

const GROUPS = [
  { title: "Núcleo e motores registados", slugs: ["hubops-core", "espelhar-x", "despertar", "vislumbops"] },
  { title: "Suite Omega (alojada no Core)", slugs: ["snse", "gopa", "nsc"] },
];

function EnginesPage() {
  return (
    <div>
      <PageHeader
        eyebrow="D · Motores"
        title="Motores autónomos"
        description="Dois motores de mutação (Despertar, Espelhar-X), um motor de evidência (VislumbOps) e a suite Omega, coordenados pelo HubOps Core. Nenhum motor aceita alterações de outro sem proveniência, impacto e validação."
      />
      <div className="grid gap-5 lg:grid-cols-3">
        <div className="grid gap-5 lg:col-span-2">
          {GROUPS.map((g) => (
            <Panel key={g.title} eyebrow={g.title}>
              <div className="grid gap-2">
                {g.slugs.map((slug) => {
                  const e = ENGINES.find((x) => x.slug === slug)!;
                  const b = statusBreakdown(e.capabilities);
                  return (
                    <Link
                      key={slug}
                      to="/motores/$engineId"
                      params={{ engineId: slug }}
                      className="panel-raised group flex items-center gap-4 p-3 transition-colors hover:border-primary/50"
                    >
                      <span className="grid size-11 shrink-0 place-items-center rounded-lg border bg-background font-mono text-[10px] tracking-wider text-primary">
                        {e.code}
                      </span>
                      <div className="min-w-0 flex-1">
                        <p className="font-display font-semibold">{e.name}</p>
                        <p className="truncate text-xs text-muted-foreground">{e.tagline}</p>
                        <div className="mt-1.5 flex flex-wrap gap-1">
                          {STATUS_ORDER.flatMap((s) =>
                            Array.from({ length: b[s] }, (_, i) => <StatusDot key={`${s}${i}`} status={s} className="size-2" />),
                          )}
                        </div>
                      </div>
                      <ArrowUpRight className="size-4 shrink-0 text-muted-foreground group-hover:text-primary" />
                    </Link>
                  );
                })}
              </div>
            </Panel>
          ))}
        </div>
        <div className="grid content-start gap-5">
          <Panel eyebrow="Registo nativo" title="Rede de fluxos">
            <EngineFlowMap />
          </Panel>
          <Panel eyebrow="HubOpsEngineRegistry.kt" title="Contratos">
            <ul className="grid gap-1.5 text-xs">
              {REGISTRY_FLOWS.map((f) => (
                <li key={`${f.source}${f.target}`} className="flex items-center justify-between gap-2 rounded-md bg-surface-raised px-2.5 py-1.5">
                  <span className="font-mono">
                    {f.source} → {f.target}
                  </span>
                  <span className="text-muted-foreground">{FLOW_LABEL[f.mode]}</span>
                </li>
              ))}
            </ul>
          </Panel>
        </div>
      </div>
    </div>
  );
}
