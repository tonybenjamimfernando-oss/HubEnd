import { createFileRoute } from "@tanstack/react-router";
import { useQuery } from "@tanstack/react-query";
import { ExternalLink, GitFork, Search, Star } from "lucide-react";
import { useState, type FormEvent } from "react";

import { Empty, Notice, PageHeader, Panel, Stat, timeAgo } from "@/components/hubend/bits";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { hubendRepoQuery } from "@/lib/bridge/queries";
import { HUBEND_REPO, HUBEND_REPO_URL, searchRepositories } from "@/lib/github/hubendRepo";

import { useClientQuery } from "@/lib/bridge/useClientQuery";

export const Route = createFileRoute("/integracao")({
  head: () => ({
    meta: [
      { title: "Integração GitHub — HubEnd" },
      { name: "description", content: "Estado do repositório oficial HubEnd e pesquisa de repositórios públicos no GitHub." },
      { property: "og:title", content: "Integração GitHub — HubEnd" },
      { property: "og:description", content: "Repositório HubEnd, commits e pesquisa de repositórios GitHub." },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary" },
    ],
  }),
  component: IntegrationPage,
});

function IntegrationPage() {
  const repo = useClientQuery(hubendRepoQuery);
  const [draft, setDraft] = useState("");
  const [query, setQuery] = useState("");
  const results = useQuery({
    queryKey: ["github", "search", query],
    queryFn: () => searchRepositories(query),
    enabled: query.length > 0,
    staleTime: 60_000,
  });

  const submit = (e: FormEvent) => {
    e.preventDefault();
    setQuery(draft.trim());
  };

  return (
    <div>
      <PageHeader
        eyebrow="F · Integração"
        title="GitHub"
        description="Repositório oficial do HubEnd e pesquisa pública. Importar, comparar e estudar repositórios faz-se na consola nativa (Espelhar-X e Despertar), com o token guardado cifrado no dispositivo."
      />

      <div className="grid gap-5 lg:grid-cols-2">
        <Panel
          eyebrow="Repositório oficial"
          title={HUBEND_REPO}
          actions={
            <a href={HUBEND_REPO_URL} target="_blank" rel="noreferrer" className="text-primary" aria-label="Abrir no GitHub">
              <ExternalLink className="size-4" />
            </a>
          }
        >
          {repo.isPending ? (
            <p className="text-sm text-muted-foreground">A consultar…</p>
          ) : repo.isError ? (
            <Notice tone="warn">{(repo.error as Error).message}</Notice>
          ) : (
            <div className="grid gap-3">
              <div className="grid grid-cols-3 gap-2">
                <Stat label="Ramo" value={<span className="text-base">{repo.data.repo?.default_branch ?? "—"}</span>} />
                <Stat label="Commits" value={repo.data.empty ? 0 : `${repo.data.commits.length}+`} />
                <Stat label="Builds" value={repo.data.runs.length} />
              </div>
              {repo.data.empty ? (
                <Empty>O repositório está vazio. O código HubEnd será publicado aqui.</Empty>
              ) : (
                <ul className="grid gap-1">
                  {repo.data.commits.map((c) => (
                    <li key={c.sha} className="flex items-center gap-2 text-sm">
                      <span className="font-mono text-[11px] text-primary">{c.sha.slice(0, 7)}</span>
                      <span className="min-w-0 flex-1 truncate">{c.commit.message.split("\n")[0]}</span>
                      <span className="shrink-0 text-[11px] text-muted-foreground">
                        {c.commit.author ? timeAgo(c.commit.author.date) : ""}
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          )}
        </Panel>

        <Panel eyebrow="API pública" title="Pesquisar repositórios">
          <form onSubmit={submit} className="flex gap-2">
            <Input
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              placeholder="ex.: language:kotlin stars:>100"
              aria-label="Pesquisa GitHub"
            />
            <Button type="submit" disabled={!draft.trim()}>
              <Search /> Pesquisar
            </Button>
          </form>
          <div className="mt-4">
            {!query ? (
              <Empty>Escreva uma pesquisa. Usa a API pública (60 pedidos/hora sem sessão).</Empty>
            ) : results.isPending ? (
              <p className="text-sm text-muted-foreground">A pesquisar…</p>
            ) : results.isError ? (
              <Notice tone="warn">{(results.error as Error).message}</Notice>
            ) : results.data.length === 0 ? (
              <Empty>Sem resultados.</Empty>
            ) : (
              <ul className="grid gap-1.5">
                {results.data.map((r) => (
                  <li key={r.full_name}>
                    <a href={r.html_url} target="_blank" rel="noreferrer" className="panel-raised block p-3 hover:border-primary/40">
                      <p className="truncate text-sm font-medium">{r.full_name}</p>
                      {r.description ? <p className="mt-0.5 line-clamp-2 text-xs text-muted-foreground">{r.description}</p> : null}
                      <p className="mt-1.5 flex gap-3 font-mono text-[11px] text-muted-foreground">
                        <span className="flex items-center gap-1">
                          <Star className="size-3" /> {r.stargazers_count}
                        </span>
                        <span className="flex items-center gap-1">
                          <GitFork className="size-3" /> {r.forks_count}
                        </span>
                        {r.language ? <span>{r.language}</span> : null}
                      </p>
                    </a>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </Panel>
      </div>
    </div>
  );
}
