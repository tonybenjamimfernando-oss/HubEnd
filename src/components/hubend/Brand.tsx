import { cn } from "@/lib/utils";

/** Logótipo oficial HubEnd, sempre sobre o "papel" do logótipo para preservar a identidade. */
export function BrandMark({ className }: { className?: string }) {
  return (
    <span className={cn("brand-seal inline-flex shrink-0 items-center justify-center p-1", className)}>
      <img src="/brand/hubend-mark.png" alt="HubEnd" className="size-full object-contain" />
    </span>
  );
}

export function BrandLogo({ className }: { className?: string }) {
  return (
    <span className={cn("brand-seal inline-flex items-center justify-center p-3", className)}>
      <img src="/brand/hubend-logo.png" alt="Logótipo HubEnd" className="size-full object-contain" />
    </span>
  );
}

export function Wordmark({ className }: { className?: string }) {
  return (
    <span className={cn("font-display text-lg font-bold tracking-tight", className)}>
      Hub<span className="text-primary">End</span>
    </span>
  );
}
