import { createFileRoute } from "@tanstack/react-router";
import { useQuery } from "@tanstack/react-query";
import { ExternalLink, FileArchive, RefreshCw } from "lucide-react";
import { useState } from "react";

import { RunBadge } from "@/components/hubend/RunBadge";
import { Empty, Notice, PageHeader, Panel, formatBytes, timeAgo } from "@/components/hubend/bits";
import { Button } from "@/components/ui/button";
import { hubendRepoQuery } from "@/lib/bridge/queries";
import { HUBEND_REPO_URL, fetchRunArtifacts, runTone, type WorkflowRun } from "@/lib/github/hubendRepo";
import { cn } from "@/lib/utils";
import { useClientQuery } from "@/lib/bridge/useClientQuery";

export const Route = createFileRoute("/compilacao")({
  head: () => ({
    meta: [
      { title: "Compilação e entrega — HubEnd" },
      { name: "description", content: "Builds Android do HubEnd no GitHub Actions: testes unitários, APK debug, artefactos e histórico." },
      { property: "og:title", content: "Compilação e entrega — HubEnd" },
      { property: "og:description", content: "Histórico de compilações, APK debug e relatórios de testes do HubEnd." },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary" },
    ],
  }),
  component: BuildsPage,
});

const PIPELINE = [
  { step: "Ferramentas", detail: "Testes Go do servidor GoPA e verificação Python SNSE/NSC" },
  { step: "Qualidade estática", detail: "check_kotlin_static.py e validação da base CVE" },
  { step: "Interface", detail: "bun install · vitest · build web" },
  { step: "Capacitor", detail: "cap sync android (shell + plugins)" },
  { step: "Testes", detail: ":hubops:testDebugUnitTest · :app:testDebugUnitTest" },
  { step: "APK Debug", detail: ":app:assembleDebug + SHA-256 + atestação de proveniência" },
  { step: "APK Release", detail: "Só com chave de assinatura configurada nos segredos do repositório" },
];

function BuildsPage() {
  const repo = useClientQuery(hubendRepoQuery);
  const [selected, setSelected] = useState<WorkflowRun | null>(null);
  const run = selected ?? repo.data?.runs[0] ?? null;

  return (
    <div>
      <PageHeader
        eyebrow="G · Compilação e entrega"
        title="Builds Android"
        description="O APK é compilado pelo GitHub Actions a cada envio para o repositório HubEnd. Os resultados abaixo vêm diretamente do GitHub."
        actions={
          <>
            <Button variant="outline" size="sm" onClick={() => repo.refetch()} disabled={repo.isFetching}>
              <RefreshCw className={cn(repo.isFetching && "animate-spin")} /> Atualizar
            </Button>
            <Button asChild size="sm">
              <a href={`${HUBEND_REPO_URL}/actions`} target="_blank" rel="noreferrer">
                <ExternalLink /> GitHub Actions
              </a>
            </Button>
          </>
        }
      />

      <div className="grid gap-5 lg:grid-cols-5">
        <Panel eyebrow="Histórico" title="Execuções de CI" className="lg:col-span-3">
          {repo.isPending ? (
            <p className="text-sm text-muted-foreground">A consultar o GitHub…</p>
          ) : repo.isError ? (
            <Notice tone="warn">Não foi possível consultar o GitHub: {(repo.error as Error).message}</Notice>
          ) : repo.data.empty ? (
            <Empty>O repositório ainda não tem commits, por isso não há builds.</Empty>
          ) : repo.data.runs.length === 0 ? (
            <Empty>Ainda não há execuções de CI neste repositório.</Empty>
          ) : (
            <ul className="grid gap-1.5">
              {repo.data.runs.map((r) => (
                <li key={r.id}>
                  <button
                    type="button"
                    onClick={() => setSelected(r)}
                    className={cn(
                      "panel-raised flex w-full items-center justify-between gap-3 p-3 text-left transition-colors",
                      run?.id === r.id ? "border-primary/60" : "hover:border-primary/30",
                    )}
                  >
                    <div className="min-w-0">
                      <p className="truncate text-sm font-medium">{r.display_title}</p>
                      <p className="mt-0.5 font-mono text-[11px] text-muted-foreground">
                        {r.name} · #{r.run_number} · {r.head_branch} · {timeAgo(r.created_at)}
                      </p>
                    </div>
                    <RunBadge tone={runTone(r)} />
                  </button>
                </li>
              ))}
            </ul>
          )}
        </Panel>

        <div className="grid content-start gap-5 lg:col-span-2">
          {run ? <RunArtifacts run={run} /> : null}
          <Panel eyebrow="android-ci.yml" title="Pipeline">
            <ol className="grid gap-2">
              {PIPELINE.map((p, i) => (
                <li key={p.step} className="flex gap-3">
                  <span className="grid size-6 shrink-0 place-items-center rounded-full border font-mono text-[10px] text-primary">{i + 1}</span>
                  <div>
                    <p className="text-sm font-medium">{p.step}</p>
                    <p className="text-xs text-muted-foreground">{p.detail}</p>
                  </div>
                </li>
              ))}
            </ol>
          </Panel>
        </div>
      </div>
    </div>
  );
}

function RunArtifacts({ run }: { run: WorkflowRun }) {
  const artifacts = useQuery({
    queryKey: ["github", "artifacts", run.id],
    queryFn: () => fetchRunArtifacts(run.id),
    staleTime: 60_000,
  });
  return (
    <Panel
      eyebrow={`Execução #${run.run_number}`}
      title="Artefactos"
      actions={
        <a href={run.html_url} target="_blank" rel="noreferrer" className="text-xs text-primary">
          Abrir
        </a>
      }
    >
      {artifacts.isPending ? (
        <p className="text-sm text-muted-foreground">A carregar…</p>
      ) : artifacts.isError ? (
        <Notice tone="warn">{(artifacts.error as Error).message}</Notice>
      ) : artifacts.data.length === 0 ? (
        <Empty>Esta execução não publicou artefactos.</Empty>
      ) : (
        <ul className="grid gap-1.5">
          {artifacts.data.map((a) => (
            <li key={a.id} className="flex items-center gap-3 rounded-md bg-surface-raised px-3 py-2">
              <FileArchive className="size-4 shrink-0 text-primary" />
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm">{a.name}</p>
                <p className="font-mono text-[11px] text-muted-foreground">
                  {formatBytes(a.size_in_bytes)} {a.expired ? "· expirado" : ""}
                </p>
              </div>
            </li>
          ))}
        </ul>
      )}
      <p className="mt-3 text-xs text-muted-foreground">
        Para descarregar o APK, abra a execução no GitHub com sessão iniciada.
      </p>
    </Panel>
  );
}
