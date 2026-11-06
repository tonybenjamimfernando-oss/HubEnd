import type { RunTone } from "@/lib/github/hubendRepo";
import { cn } from "@/lib/utils";

export function RunBadge({ tone }: { tone: RunTone }) {
  const label = { success: "Sucesso", failure: "Falhou", running: "A correr", neutral: "Cancelado" }[tone];
  return (
    <span
      className={cn("shrink-0 rounded-full border px-2 py-0.5 font-mono text-[10px] uppercase tracking-wider", {
        "border-status-tested/40 bg-status-tested/10 text-status-tested": tone === "success",
        "border-destructive/40 bg-destructive/10 text-destructive": tone === "failure",
        "border-status-partial/40 bg-status-partial/10 text-status-partial": tone === "running",
        "border-border bg-muted text-muted-foreground": tone === "neutral",
      })}
    >
      {label}
    </span>
  );
}
