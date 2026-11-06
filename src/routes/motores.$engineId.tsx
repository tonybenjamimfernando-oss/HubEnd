import { createFileRoute, Link, notFound } from "@tanstack/react-router";
import { useMutation } from "@tanstack/react-query";
import { ArrowLeft, FileCode2, MonitorSmartphone, TriangleAlert } from "lucide-react";
import { toast } from "sonner";

import { StatusPill } from "@/components/hubend/StatusPill";
import { Mono, Notice, PageHeader, Panel } from "@/components/hubend/bits";
import { Button } from "@/components/ui/button";
import {
  ENGINES,
  FLOW_LABEL,
  REGISTRY_FLOWS,
  STATUS_ORDER,
  getEngine,
  type EngineSpec,
} from "@/lib/engines/catalog";
import { hubendEngines } from "@/lib/bridge/hubendEngines";
import { nativeStatusQuery } from "@/lib/bridge/queries";

import { useClientQuery } from "@/lib/bridge/useClientQuery";

export const Route = createFileRoute("/motores/$engineId")({
  loader: ({ params }) => {
    const engine = getEngine(params.engineId);
    if (!engine) throw notFound();
    return { slug: engine.slug };
  },
  head: ({ loaderData }) => {
    const engine = loaderData ? getEngine(loaderData.slug) : undefined;
    const title = engine ? `${engine.name} — Motores HubEnd` : "Motor — HubEnd";
    const description = engine ? `${engine.tagline}. ${engine.role}` : "Motor HubEnd";
    return {
      meta: [
        { title },
        { name: "description", content: description.slice(0, 160) },
        { property: "og:title", content: title },
        { property: "og:description", content: description.slice(0, 200) },
        { property: "og:type", content: "article" },
        { name: "twitter:card", content: "summary" },
      ],
    };
  },
  component: EngineDetail,
});

function nameOf(id: string) {
  return ENGINES.find((e) => e.registryId === id && !e.hostedBy)?.name ?? id;
}

function EngineDetail() {
  const { slug } = Route.useLoaderData();
  const engine = getEngine(slug) as EngineSpec;
  const native = useClientQuery(nativeStatusQuery);
  const nativeData = native.data?.available ? native.data.data : null;
  const isNative = nativeData !== null;
  const nativeEngine = nativeData?.engines.find((e) => e.id === engine.registryId);

  const open = useMutation({
    mutationFn: () => hubendEngines.openNativeConsole(),
    onSuccess: (r) => {
      if (!r.available) toast.info(r.reason);
    },
    onError: (e: Error) => toast.error(e.message),
  });

  const caps = [...engine.capabilities].sort(
    (a, b) => STATUS_ORDER.indexOf(a.status) - STATUS_ORDER.indexOf(b.status),
  );
  const outbound = engine.hostedBy ? [] : REGISTRY_FLOWS.filter((f) => f.source === engine.registryId);
  const inbound = engine.hostedBy ? [] : REGISTRY_FLOWS.filter((f) => f.target === engine.registryId);

  return (
    <div>
      <Link to="/motores" className="mb-4 inline-flex items-center gap-1.5 text-xs text-muted-foreground hover:text-foreground">
        <ArrowLeft className="size-3.5" /> Motores
      </Link>
      <PageHeader
        eyebrow={`Motor · ${engine.code}${engine.hostedBy ? " · Omega" : ""}`}
        title={engine.name}
        description={engine.role}
        actions={
          <Button onClick={() => open.mutate()} disabled={open.isPending || !isNative} title={isNative ? undefined : "Disponível no APK Android"}>
            <MonitorSmartphone /> Abrir consola nativa
          </Button>
        }
      />

      {!isNative ? (
        <div className="mb-5">
          <Notice>
            Este motor corre em Kotlin dentro do APK. Aqui vê o inventário real de capacidades; as operações
            ficam disponíveis no telemóvel, na consola nativa HubOps.
          </Notice>
        </div>
      ) : null}

      <div className="grid gap-5 lg:grid-cols-3">
        <Panel eyebrow={`${caps.length} capacidades`} title="Capacidades e estado" className="lg:col-span-2">
          <ul className="grid gap-2">
            {caps.map((c) => (
              <li key={c.name} className="panel-raised p-3">
                <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                  <p className="font-medium">{c.name}</p>
                  <StatusPill status={c.status} />
                </div>
                <p className="mt-1 text-sm text-muted-foreground">{c.detail}</p>
                <p className="mt-2 flex items-center gap-1.5">
                  <FileCode2 className="size-3.5 text-muted-foreground" />
                  <Mono>{c.evidence}</Mono>
                </p>
              </li>
            ))}
          </ul>
        </Panel>

        <div className="grid content-start gap-5">
          <Panel eyebrow="Código-fonte" title="Localização">
            <Mono className="break-all">{engine.sourcePath}</Mono>
            <p className="mt-2 text-xs text-muted-foreground">≈ {engine.approxLines.toLocaleString("pt-PT")} linhas Kotlin/Go</p>
          </Panel>

          {engine.hostedBy ? (
            <Panel eyebrow="Registo" title="Alojado no HubOps Core">
              <p className="text-sm text-muted-foreground">
                Não tem ID próprio no registo; é exposto pelas capacidades do Core e coordenado pelo orquestrador.
              </p>
            </Panel>
          ) : (
            <Panel eyebrow="Registo nativo" title="Fluxos">
              <p className="eyebrow mb-1.5">Saída</p>
              <ul className="mb-3 grid gap-1 text-sm">
                {outbound.map((f) => (
                  <li key={f.target} className="flex justify-between gap-2">
                    <span>→ {nameOf(f.target)}</span>
                    <span className="text-xs text-muted-foreground">{FLOW_LABEL[f.mode]}</span>
                  </li>
                ))}
              </ul>
              <p className="eyebrow mb-1.5">Entrada</p>
              <ul className="grid gap-1 text-sm">
                {inbound.map((f) => (
                  <li key={f.source} className="flex justify-between gap-2">
                    <span>← {nameOf(f.source)}</span>
                    <span className="text-xs text-muted-foreground">{FLOW_LABEL[f.mode]}</span>
                  </li>
                ))}
              </ul>
              {nativeEngine ? (
                <p className="mt-3 text-xs text-status-tested">
                  Confirmado no dispositivo: {nativeEngine.capabilities.length} capacidades declaradas.
                </p>
              ) : null}
            </Panel>
          )}

          <Panel eyebrow="Dependências" title="Depende de">
            <ul className="grid gap-1 text-sm text-muted-foreground">
              {engine.dependencies.map((d) => (
                <li key={d}>• {d}</li>
              ))}
            </ul>
          </Panel>

          <Panel eyebrow="Limitações declaradas" title="Riscos">
            <ul className="grid gap-2 text-sm">
              {engine.limitations.map((l) => (
                <li key={l} className="flex gap-2 text-muted-foreground">
                  <TriangleAlert className="mt-0.5 size-4 shrink-0 text-status-partial" />
                  {l}
                </li>
              ))}
            </ul>
          </Panel>
        </div>
      </div>
    </div>
  );
}
