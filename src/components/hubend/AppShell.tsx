import { Link, useRouterState } from "@tanstack/react-router";
import {
  ClipboardCheck,
  Cpu,
  GitBranch,
  LayoutDashboard,
  Package,
  Settings2,
  type LucideIcon,
} from "lucide-react";
import type { ReactNode } from "react";

import { BrandMark, Wordmark } from "./Brand";
import { ENGINES } from "@/lib/engines/catalog";
import { nativeStatusQuery } from "@/lib/bridge/queries";
import { cn } from "@/lib/utils";
import { useClientQuery } from "@/lib/bridge/useClientQuery";

type NavTo = "/" | "/motores" | "/compilacao" | "/integracao" | "/sistema" | "/auditoria";

const NAV: { to: NavTo; label: string; short: string; icon: LucideIcon; mobile: boolean }[] = [
  { to: "/", label: "Hub Central", short: "Hub", icon: LayoutDashboard, mobile: true },
  { to: "/motores", label: "Motores", short: "Motores", icon: Cpu, mobile: true },
  { to: "/compilacao", label: "Compilação e entrega", short: "Builds", icon: Package, mobile: true },
  { to: "/integracao", label: "Integração GitHub", short: "GitHub", icon: GitBranch, mobile: true },
  { to: "/auditoria", label: "Auditoria e fases", short: "Auditoria", icon: ClipboardCheck, mobile: false },
  { to: "/sistema", label: "Sistema", short: "Sistema", icon: Settings2, mobile: true },
];

function isActive(pathname: string, to: NavTo) {
  return to === "/" ? pathname === "/" : pathname === to || pathname.startsWith(`${to}/`);
}

export function RuntimeBadge() {
  const { data, isPending } = useClientQuery(nativeStatusQuery);
  const native = data?.available === true;
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 font-mono text-[10px] uppercase tracking-wider",
        native
          ? "border-status-tested/40 bg-status-tested/10 text-status-tested"
          : "border-border bg-muted text-muted-foreground",
      )}
    >
      <span className={cn("size-1.5 rounded-full", native ? "pulse-dot bg-status-tested" : "bg-status-pending")} />
      {isPending ? "A detetar" : native ? "Motores nativos" : "Pré-visualização web"}
    </span>
  );
}

export function AppShell({ children }: { children: ReactNode }) {
  const pathname = useRouterState({ select: (s) => s.location.pathname });

  return (
    <div className="min-h-screen bg-background">
      {/* Barra lateral (tablet/desktop) */}
      <aside className="fixed inset-y-0 left-0 z-30 hidden w-64 flex-col border-r border-sidebar-border bg-sidebar md:flex">
        <Link to="/" className="flex items-center gap-3 px-5 py-5">
          <BrandMark className="size-9" />
          <div className="leading-tight">
            <Wordmark />
            <p className="font-mono text-[10px] uppercase tracking-widest text-muted-foreground">HubOps · HybridApp</p>
          </div>
        </Link>
        <nav className="flex-1 overflow-y-auto px-3 pb-4">
          {NAV.map((item) => {
            const active = isActive(pathname, item.to);
            return (
              <div key={item.to}>
                <Link
                  to={item.to}
                  className={cn(
                    "flex items-center gap-3 rounded-lg px-3 py-2 text-sm transition-colors",
                    active
                      ? "bg-sidebar-accent text-sidebar-accent-foreground"
                      : "text-sidebar-foreground/80 hover:bg-sidebar-accent/60 hover:text-sidebar-foreground",
                  )}
                >
                  <item.icon className={cn("size-4", active && "text-primary")} />
                  {item.label}
                </Link>
                {item.to === "/motores" ? (
                  <div className="mb-1 ml-5 mt-1 border-l border-sidebar-border pl-3">
                    {ENGINES.map((engine) => {
                      const engineActive = pathname === `/motores/${engine.slug}`;
                      return (
                        <Link
                          key={engine.slug}
                          to="/motores/$engineId"
                          params={{ engineId: engine.slug }}
                          className={cn(
                            "flex items-center justify-between rounded-md px-2 py-1.5 text-xs transition-colors",
                            engineActive ? "text-primary" : "text-muted-foreground hover:text-foreground",
                          )}
                        >
                          {engine.name}
                          <span className="font-mono text-[9px] opacity-60">{engine.code}</span>
                        </Link>
                      );
                    })}
                  </div>
                ) : null}
              </div>
            );
          })}
        </nav>
        <div className="border-t border-sidebar-border px-5 py-4">
          <RuntimeBadge />
        </div>
      </aside>

      {/* Cabeçalho (telemóvel) */}
      <header className="sticky top-0 z-20 flex items-center justify-between border-b bg-background/85 px-4 py-3 backdrop-blur md:hidden">
        <Link to="/" className="flex items-center gap-2.5">
          <BrandMark className="size-8" />
          <Wordmark />
        </Link>
        <RuntimeBadge />
      </header>

      <main className="px-4 pb-28 pt-5 md:ml-64 md:px-8 md:pb-12 md:pt-8">
        <div className="mx-auto max-w-6xl">{children}</div>
      </main>

      {/* Navegação inferior (telemóvel) */}
      <nav className="fixed inset-x-0 bottom-0 z-30 border-t bg-sidebar/95 pb-[env(safe-area-inset-bottom)] backdrop-blur md:hidden">
        <div className="grid grid-cols-5">
          {NAV.filter((n) => n.mobile).map((item) => {
            const active = isActive(pathname, item.to);
            return (
              <Link
                key={item.to}
                to={item.to}
                className={cn(
                  "flex flex-col items-center gap-1 py-2.5 text-[10px] font-medium transition-colors",
                  active ? "text-primary" : "text-muted-foreground",
                )}
              >
                <item.icon className="size-5" />
                {item.short}
              </Link>
            );
          })}
        </div>
      </nav>
    </div>
  );
}
