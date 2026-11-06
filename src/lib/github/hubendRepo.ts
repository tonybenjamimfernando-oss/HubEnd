import { apiClient, HttpError } from "@/lib/core/apiClient";

/** Repositório oficial do HubEnd (destino do código e do CI Android). */
export const HUBEND_REPO = "tonybenjamimfernando-oss/HubEnd";
export const HUBEND_REPO_URL = `https://github.com/${HUBEND_REPO}`;

export interface RepoSummary {
  full_name: string;
  html_url: string;
  description: string | null;
  default_branch: string;
  stargazers_count: number;
  forks_count: number;
  open_issues_count: number;
  language: string | null;
  pushed_at: string | null;
  visibility?: string;
  size: number;
}

export interface CommitSummary {
  sha: string;
  html_url: string;
  commit: { message: string; author: { name: string; date: string } | null };
}

export interface WorkflowRun {
  id: number;
  name: string | null;
  display_title: string;
  status: "queued" | "in_progress" | "completed" | string;
  conclusion: "success" | "failure" | "cancelled" | "skipped" | null | string;
  html_url: string;
  head_branch: string | null;
  head_sha: string;
  run_number: number;
  event: string;
  created_at: string;
  updated_at: string;
}

export interface RunArtifact {
  id: number;
  name: string;
  size_in_bytes: number;
  expired: boolean;
  created_at: string;
}

export interface RepoState {
  repo: RepoSummary | null;
  empty: boolean;
  commits: CommitSummary[];
  runs: WorkflowRun[];
}

/** Estado público do repositório HubEnd: metadados, últimos commits e execuções de CI. */
export async function fetchHubEndRepoState(): Promise<RepoState> {
  const repo = await apiClient.request<RepoSummary>(`/repos/${HUBEND_REPO}`, { useCache: true });
  let commits: CommitSummary[] = [];
  let empty = false;
  try {
    commits = await apiClient.request<CommitSummary[]>(`/repos/${HUBEND_REPO}/commits?per_page=6`, {
      useCache: true,
    });
  } catch (error) {
    // A API devolve 409 para repositórios sem commits.
    if (error instanceof HttpError && error.status === 409) empty = true;
    else throw error;
  }
  let runs: WorkflowRun[] = [];
  if (!empty) {
    const data = await apiClient.request<{ workflow_runs: WorkflowRun[] }>(
      `/repos/${HUBEND_REPO}/actions/runs?per_page=10`,
      { useCache: true },
    );
    runs = data.workflow_runs;
  }
  return { repo, empty, commits, runs };
}

export async function fetchRunArtifacts(runId: number): Promise<RunArtifact[]> {
  const data = await apiClient.request<{ artifacts: RunArtifact[] }>(
    `/repos/${HUBEND_REPO}/actions/runs/${runId}/artifacts`,
    { useCache: true },
  );
  return data.artifacts;
}

export async function searchRepositories(query: string): Promise<RepoSummary[]> {
  const q = query.trim();
  if (!q) return [];
  const data = await apiClient.request<{ items: RepoSummary[] }>(
    `/search/repositories?q=${encodeURIComponent(q)}&per_page=12`,
    { useCache: true },
  );
  return data.items;
}

export type RunTone = "success" | "failure" | "running" | "neutral";

/** Converte o estado de uma execução de CI num tom visual. */
export function runTone(run: Pick<WorkflowRun, "status" | "conclusion">): RunTone {
  if (run.status !== "completed") return "running";
  if (run.conclusion === "success") return "success";
  if (run.conclusion === "failure" || run.conclusion === "timed_out") return "failure";
  return "neutral";
}
