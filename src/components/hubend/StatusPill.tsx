import { cva } from "class-variance-authority";

import { STATUS_LABEL, type FeatureStatus } from "@/lib/engines/catalog";
import { cn } from "@/lib/utils";

const pill = cva(
  "inline-flex w-fit shrink-0 items-center gap-1.5 rounded-full border px-2 py-0.5 font-mono text-[10px] uppercase tracking-wider whitespace-nowrap",
  {
    variants: {
      status: {
        TESTADA: "border-status-tested/40 bg-status-tested/10 text-status-tested",
        IMPLEMENTADA: "border-status-implemented/40 bg-status-implemented/10 text-status-implemented",
        PARCIAL: "border-status-partial/40 bg-status-partial/10 text-status-partial",
        PREPARADA: "border-status-prepared/40 bg-status-prepared/10 text-status-prepared",
        PENDENTE: "border-status-pending/40 bg-status-pending/10 text-status-pending",
        BLOQUEADA: "border-status-blocked/40 bg-status-blocked/10 text-status-blocked",
      },
    },
  },
);

const dot = cva("size-1.5 rounded-full", {
  variants: {
    status: {
      TESTADA: "bg-status-tested",
      IMPLEMENTADA: "bg-status-implemented",
      PARCIAL: "bg-status-partial",
      PREPARADA: "bg-status-prepared",
      PENDENTE: "bg-status-pending",
      BLOQUEADA: "bg-status-blocked",
    },
  },
});

export function StatusDot({ status, className }: { status: FeatureStatus; className?: string }) {
  return <span className={cn(dot({ status }), className)} aria-hidden />;
}

export function StatusPill({ status, short = false }: { status: FeatureStatus; short?: boolean }) {
  return (
    <span className={pill({ status })} title={STATUS_LABEL[status]}>
      <StatusDot status={status} />
      {short ? status : STATUS_LABEL[status]}
    </span>
  );
}
