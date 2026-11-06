import { createFileRoute, Link } from "@tanstack/react-router";
import { ArrowUpRight, ClipboardCheck, Cpu, ExternalLink, GitCommitHorizontal, Package } from "lucide-react";

import { BrandLogo } from "@/components/hubend/Brand";
import { EngineFlowMap } from "@/components/hubend/EngineFlowMap";
import { StatusDot, StatusPill } from "@/components/hubend/StatusPill";
import { Empty, Notice, Panel, Stat, timeAgo } from "@/components/hubend/bits";
import { Button } from "@/components/ui/button";
import { ENGINES, STATUS_LABEL, STATUS_ORDER, allCapabilities, statusBreakdown } from "@/lib/engines/catalog";
import { PHASES } from "@/lib/audit/matrix";
import { hubendRepoQuery, nativeStatusQuery } from "@/lib/bridge/queries";
import { HUBEND_REPO_URL, runTone } from "@/lib/github/hubendRepo";
import { RunBadge } from "@/components/hubend/RunBadge";
import { cn } from "@/lib/utils";
import { useClientQuery } from "@/lib/bridge/useClientQuery";

export const Route = createFileRoute("/")({
  head: () => ({
    meta: [
      { title: "HubEnd — Hub Central" },
      { name: "description", content: "Plataforma Android de desenvolvimento e operação: motores HubOps, auditoria, compilação e integração GitHub." },
      { property: "og:title", content: "HubEnd — Hub Central" },
      { property: "og:description", content: "Motores autónomos HubOps com infraestrutura híbrida HybridApp, num único ecossistema Android." },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
  }),
  component: HubCentral,
});

function HubCentral() {
  const caps = allCapabilities();
  const breakdown = statusBreakdown(caps);
  const native = useClientQuery(nativeStatusQuery);
  const repo = useClientQuery(hubendRepoQuery);
  const lastRun = repo.data?.runs[0];
  const phase0 = PHASES.find((p) => p.id === "0");

  return (
    <div className="grid gap-5">
      <section className="hero-surface grid-backdrop overflow-hidden p-5 md:p-8">
        <div className="flex flex-col gap-6 md:flex-row md:items-center">
          <BrandLogo className="size-28 shrink-0 md:size-36" />
          <div className="min-w-0">
            <p className="eyebrow">Ecossistema integrado · HubOps 1.29.0 + HybridApp</p>
            <h1 className="mt-2 text-3xl font-bold md:text-4xl">Hub Central</h1>
            <p className="mt-3 max-w-xl text-sm leading-relaxed text-muted-foreground">
              Sete motores autónomos, um orquestrador comum e uma rede de fluxos governados. Cada capacidade
              mostra o seu estado real e a evidência no código.
            </p>
            <div className="mt-5 flex flex-wrap gap-2">
              <Button asChild>
                <Link to="/motores">
                  <Cpu /> Abrir motores
                </Link>
              </Button>
              <Button asChild variant="outline">
                <Link to="/auditoria">
                  <ClipboardCheck /> Ver auditoria
                </Link>
              </Button>
            </div>
          </div>
        </div>
      </section>

      <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
        <Stat label="Motores" value={ENGINES.length} hint="4 no registo + 3 Omega" />
        <Stat label="Capacidades" value={caps.length} hint="com evidência" />
        <Stat label="Testadas" value={breakdown.TESTADA} hint="cobertas por testes" />
        <Stat
          label="Runtime"
          value={native.data?.available ? "Nativo" : "Web"}
          hint={native.data?.available ? `HubOps ${native.data.data.hubopsVersion}` : "motores no APK"}
        />
      </div>

      <div className="grid gap-5 lg:grid-cols-5">
        <Panel eyebrow="Estado do ecossistema" title="Capacidades por estado" className="lg:col-span-3">
          <div className="flex h-2.5 overflow-hidden rounded-full bg-muted">
            {STATUS_ORDER.map((s) =>
              breakdown[s] ? (
                <div
                  key={s}
                  className={cn("h-full", {
                    "bg-status-tested": s === "TESTADA",
                    "bg-status-implemented": s === "IMPLEMENTADA",
                    "bg-status-partial": s === "PARCIAL",
                    "bg-status-prepared": s === "PREPARADA",
                    "bg-status-pending": s === "PENDENTE",
                    "bg-status-blocked": s === "BLOQUEADA",
                  })}
                  style={{ width: `${(breakdown[s] / caps.length) * 100}%` }}
                />
              ) : null,
            )}
          </div>
          <ul className="mt-4 grid grid-cols-1 gap-2 sm:grid-cols-2">
            {STATUS_ORDER.map((s) => (
              <li key={s} className="flex items-center justify-between rounded-md bg-surface-raised px-3 py-2 text-sm">
                <span className="flex items-center gap-2">
                  <StatusDot status={s} /> {STATUS_LABEL[s]}
                </span>
                <span className="font-mono text-xs text-muted-foreground">{breakdown[s]}</span>
              </li>
            ))}
          </ul>
        </Panel>

        <Panel eyebrow="Rede de motores" title="Fluxos autorizados" className="lg:col-span-2">
          <EngineFlowMap />
        </Panel>
      </div>

      <Panel eyebrow="Acesso aos motores" title="Motores">
        <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
          {ENGINES.map((engine) => {
            const b = statusBreakdown(engine.capabilities);
            return (
              <Link
                key={engine.slug}
                to="/motores/$engineId"
                params={{ engineId: engine.slug }}
                className="panel-raised group flex flex-col gap-2 p-3 transition-colors hover:border-primary/50"
              >
                <div className="flex items-center justify-between">
                  <span className="font-mono text-[10px] tracking-widest text-primary">{engine.code}</span>
                  <ArrowUpRight className="size-4 text-muted-foreground transition-colors group-hover:text-primary" />
                </div>
                <p className="font-display font-semibold">{engine.name}</p>
                <p className="line-clamp-2 text-xs text-muted-foreground">{engine.tagline}</p>
                <div className="mt-auto flex gap-1 pt-1">
                  {STATUS_ORDER.flatMap((s) => Array.from({ length: b[s] }, (_, i) => <StatusDot key={`${s}${i}`} status={s} className="size-2" />))}
                </div>
              </Link>
            );
          })}
        </div>
      </Panel>

      <div className="grid gap-5 lg:grid-cols-2">
        <Panel
          eyebrow="Compilação"
          title="Último build no GitHub"
          actions={
            <Button asChild size="sm" variant="ghost">
              <Link to="/compilacao">
                <Package /> Builds
              </Link>
            </Button>
          }
        >
          {repo.isPending ? (
            <p className="text-sm text-muted-foreground">A consultar o repositório…</p>
          ) : repo.isError ? (
            <Notice tone="warn">Não foi possível consultar o GitHub: {(repo.error as Error).message}</Notice>
          ) : repo.data?.empty ? (
            <Empty>O repositório HubEnd ainda não tem commits.</Empty>
          ) : lastRun ? (
            <a href={lastRun.html_url} target="_blank" rel="noreferrer" className="panel-raised flex items-center justify-between gap-3 p-3">
              <div className="min-w-0">
                <p className="truncate text-sm font-medium">{lastRun.display_title}</p>
                <p className="mt-0.5 font-mono text-[11px] text-muted-foreground">
                  #{lastRun.run_number} · {lastRun.head_sha.slice(0, 7)} · {timeAgo(lastRun.created_at)}
                </p>
              </div>
              <RunBadge tone={runTone(lastRun)} />
            </a>
          ) : (
            <Empty>Ainda não há execuções de CI.</Empty>
          )}
        </Panel>

        <Panel eyebrow="Fase atual" title={phase0 ? `Fase ${phase0.id} — ${phase0.title}` : "Fases"}>
          <ul className="grid gap-2 text-sm">
            {phase0?.criteria.map((c) => (
              <li key={c} className="flex items-start gap-2">
                <StatusPill status="PENDENTE" short />
                <span className="text-muted-foreground">{c}</span>
              </li>
            ))}
          </ul>
          <p className="mt-3 text-xs text-muted-foreground">
            Os critérios só passam a aprovados com evidência real do CI ou do dispositivo.
          </p>
        </Panel>
      </div>

      <Panel eyebrow="Atividade" title="Commits recentes">
        {repo.data?.commits.length ? (
          <ul className="grid gap-1.5">
            {repo.data.commits.map((c) => (
              <li key={c.sha}>
                <a href={c.html_url} target="_blank" rel="noreferrer" className="flex items-center gap-3 rounded-md px-2 py-1.5 text-sm hover:bg-surface-raised">
                  <GitCommitHorizontal className="size-4 shrink-0 text-primary" />
                  <span className="min-w-0 flex-1 truncate">{c.commit.message.split("\n")[0]}</span>
                  <span className="shrink-0 font-mono text-[11px] text-muted-foreground">{c.sha.slice(0, 7)}</span>
                </a>
              </li>
            ))}
          </ul>
        ) : (
          <Empty>
            Sem commits para mostrar.{" "}
            <a href={HUBEND_REPO_URL} target="_blank" rel="noreferrer" className="inline-flex items-center gap-1 text-primary">
              Abrir repositório <ExternalLink className="size-3" />
            </a>
          </Empty>
        )}
      </Panel>
    </div>
  );
}
