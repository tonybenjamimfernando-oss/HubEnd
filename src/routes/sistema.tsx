import { createFileRoute, Link } from "@tanstack/react-router";
import { ClipboardCheck, RefreshCw, Trash2 } from "lucide-react";
import { toast } from "sonner";

import { Empty, Mono, Notice, PageHeader, Panel, Stat, formatBytes, timeAgo } from "@/components/hubend/bits";
import { Button } from "@/components/ui/button";
import { nativeLedgerQuery, nativeStatusQuery } from "@/lib/bridge/queries";
import { clearAll } from "@/lib/core/store";
import { cn } from "@/lib/utils";
import { useClientQuery } from "@/lib/bridge/useClientQuery";

export const Route = createFileRoute("/sistema")({
  head: () => ({
    meta: [
      { title: "Sistema — HubEnd" },
      { name: "description", content: "Estado da ponte nativa, dispositivo, suite Omega, ledger de integração e dados locais do HubEnd." },
      { property: "og:title", content: "Sistema — HubEnd" },
      { property: "og:description", content: "Diagnóstico, ledger de integração e armazenamento do HubEnd." },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary" },
    ],
  }),
  component: SystemPage,
});

function Fact({ label, ok, detail }: { label: string; ok: boolean; detail?: string }) {
  return (
    <li className="flex items-center justify-between gap-3 rounded-md bg-surface-raised px-3 py-2 text-sm">
      <span>{label}</span>
      <span className={cn("font-mono text-[11px]", ok ? "text-status-tested" : "text-muted-foreground")}>
        {detail ?? (ok ? "sim" : "não")}
      </span>
    </li>
  );
}

function SystemPage() {
  const status = useClientQuery(nativeStatusQuery);
  const ledger = useClientQuery(nativeLedgerQuery(30));
  const native = status.data?.available ? status.data.data : null;
  const led = ledger.data?.available ? ledger.data.data : null;

  return (
    <div>
      <PageHeader
        eyebrow="H · Administração e sistema"
        title="Sistema"
        description="Estado real lido do dispositivo através da ponte HubEndEngines. Na web, mostra apenas o que existe no navegador."
        actions={
          <>
            <Button
              variant="outline"
              size="sm"
              onClick={() => {
                void status.refetch();
                void ledger.refetch();
              }}
            >
              <RefreshCw /> Atualizar
            </Button>
            <Button asChild size="sm" variant="secondary">
              <Link to="/auditoria">
                <ClipboardCheck /> Auditoria
              </Link>
            </Button>
          </>
        }
      />

      {!native && status.data && !status.data.available ? (
        <div className="mb-5">
          <Notice>{status.data.reason}</Notice>
        </div>
      ) : null}

      <div className="grid gap-5 lg:grid-cols-2">
        <Panel eyebrow="Ponte HubEndEngines" title="Runtime">
          <div className="grid grid-cols-2 gap-2">
            <Stat label="Plataforma" value={<span className="text-lg">{native ? "Android" : "Web"}</span>} />
            <Stat label="HubOps" value={<span className="text-lg">{native?.hubopsVersion ?? "—"}</span>} />
            <Stat label="App" value={<span className="text-lg">{native?.appVersion ?? "—"}</span>} />
            <Stat label="Contrato" value={<span className="text-lg">v{native?.contractVersion ?? 1}</span>} />
          </div>
        </Panel>

        <Panel eyebrow="DeviceInfoService" title="Dispositivo">
          {native ? (
            <ul className="grid gap-1.5">
              <Fact label="Modelo" ok detail={native.device.model} />
              <Fact label="Android" ok detail={`${native.device.androidRelease} (API ${native.device.sdk})`} />
              <Fact label="Bateria" ok detail={`${native.device.batteryPercent}%${native.device.batteryCharging ? " · a carregar" : ""}`} />
              <Fact label="RAM livre" ok detail={`${formatBytes(native.device.availableRamBytes)} / ${formatBytes(native.device.totalRamBytes)}`} />
              <Fact label="Armazenamento livre" ok detail={`${formatBytes(native.device.freeBytes)} / ${formatBytes(native.device.totalBytes)}`} />
              <Fact label="Termux instalado" ok={native.device.termuxInstalled} />
              <Fact label="Shizuku instalado" ok={native.device.shizukuInstalled} />
            </ul>
          ) : (
            <Empty>Disponível no APK Android.</Empty>
          )}
        </Panel>

        <Panel eyebrow="Suite Omega" title="Runtimes externos">
          {native ? (
            <ul className="grid gap-1.5">
              <Fact label="Chave pública de release" ok={native.omega.releaseKeyConfigured} />
              <Fact label="Runtime llama.cpp" ok={native.omega.llamaRuntimeInstalled} />
              <Fact label="Shards SNSE (GGUF)" ok={native.omega.snseShardFiles > 0} detail={String(native.omega.snseShardFiles)} />
              <Fact label="SNSE neural operacional" ok={native.omega.snseNeuralOperational} />
              <Fact label="PRoot" ok={native.omega.prootInstalled} />
              <Fact label="Rootfs Alpine" ok={native.omega.alpineRootfsInstalled} />
              <Fact label="Isolamento NSC disponível" ok={native.omega.nscIsolationAvailable} />
            </ul>
          ) : (
            <Empty>Disponível no APK Android.</Empty>
          )}
        </Panel>

        <Panel eyebrow="HubOpsOrchestrator · só leitura" title="Ledger de integração">
          {led ? (
            <div className="grid gap-3">
              <div className="grid grid-cols-3 gap-2">
                <Stat label="Pendentes" value={led.pendingProposals.length} />
                <Stat label="Projetos" value={led.projectRecords.length} />
                <Stat label="Transações" value={led.recentTransactions} />
              </div>
              {led.auditLines.length ? (
                <ul className="max-h-64 overflow-y-auto rounded-md bg-background p-2">
                  {led.auditLines.map((l, i) => (
                    <li key={i} className="border-b border-border/50 py-1 last:border-0">
                      <Mono className="break-all">{l}</Mono>
                    </li>
                  ))}
                </ul>
              ) : (
                <Empty>Sem entradas no ledger.</Empty>
              )}
              {led.projectRecords.map((r) => (
                <p key={r.proposalId} className="text-xs text-muted-foreground">
                  {r.sourceEngine} → {r.destinationEngine} · {r.artifactName} · {timeAgo(r.acceptedAtEpochMs)}
                </p>
              ))}
            </div>
          ) : (
            <Empty>Disponível no APK Android.</Empty>
          )}
        </Panel>

        <Panel eyebrow="Armazenamento local" title="Dados da interface" className="lg:col-span-2">
          <p className="text-sm text-muted-foreground">
            A interface guarda apenas preferências e atividade local neste dispositivo. Os dados dos motores ficam na
            área privada da app Android e não são apagados aqui.
          </p>
          <Button
            variant="outline"
            size="sm"
            className="mt-3"
            onClick={() => {
              clearAll();
              toast.success("Dados locais da interface apagados.");
            }}
          >
            <Trash2 /> Apagar dados locais
          </Button>
        </Panel>
      </div>
    </div>
  );
}
