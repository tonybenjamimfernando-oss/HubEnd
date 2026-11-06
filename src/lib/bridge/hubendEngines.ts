/**
 * Cliente da ponte nativa "HubEndEngines" (android/app/.../bridge/HubEndEnginesPlugin.kt).
 *
 * No APK chama os motores HubOps reais. No navegador não existe motor nativo:
 * as funções devolvem { available: false } em vez de simular resultados.
 * O formato é fixado por BridgePayloads.kt (CONTRACT_VERSION).
 */

import type { RegistryEngineId, FlowMode } from "@/lib/engines/catalog";

export const BRIDGE_CONTRACT_VERSION = 1;

export interface NativeEngine {
  id: RegistryEngineId;
  displayName: string;
  capabilities: string[];
  outbound: { target: RegistryEngineId; mode: FlowMode }[];
}

export interface NativeDevice {
  androidRelease: string;
  sdk: number;
  model: string;
  batteryPercent: number;
  batteryCharging: boolean;
  availableRamBytes: number;
  totalRamBytes: number;
  freeBytes: number;
  totalBytes: number;
  uptimeMillis: number;
  termuxInstalled: boolean;
  shizukuInstalled: boolean;
}

export interface NativeOmega {
  releaseKeyConfigured: boolean;
  llamaRuntimeInstalled: boolean;
  snseShardFiles: number;
  prootInstalled: boolean;
  alpineRootfsInstalled: boolean;
  termuxInstalled: boolean;
  snseNeuralOperational: boolean;
  nscIsolationAvailable: boolean;
}

export interface NativeStatus {
  contractVersion: number;
  platform: "android";
  hubopsVersion: string;
  appVersion: string;
  engines: NativeEngine[];
  omega: NativeOmega;
  device: NativeDevice;
}

export interface NativeProposal {
  id: string;
  sourceEngine: RegistryEngineId;
  destinationEngine: RegistryEngineId;
  artifactName: string;
  artifactSha256: string;
  status: string;
  destinationPrefix: string;
  supportedFiles: number;
  collisions: number;
  riskNotes: string[];
  createdAtEpochMs: number;
}

export interface NativeRecord {
  proposalId: string;
  sourceEngine: RegistryEngineId;
  destinationEngine: RegistryEngineId;
  artifactName: string;
  artifactSha256: string;
  acceptedFiles: number;
  acceptedAtEpochMs: number;
}

export interface NativeLedger {
  auditLines: string[];
  projectRecords: NativeRecord[];
  pendingProposals: NativeProposal[];
  recentTransactions: number;
}

interface HubEndEnginesPlugin {
  status(): Promise<NativeStatus>;
  ledger(options: { limit?: number }): Promise<NativeLedger>;
  openNativeConsole(): Promise<{ opened: boolean }>;
}

export type BridgeResult<T> = { available: true; data: T } | { available: false; reason: string };

const WEB_REASON = "Os motores HubOps correm no APK Android. Nesta pré-visualização web não há motor nativo.";

let pluginPromise: Promise<HubEndEnginesPlugin | null> | null = null;

async function loadPlugin(): Promise<HubEndEnginesPlugin | null> {
  if (typeof window === "undefined") return null;
  pluginPromise ??= import("@capacitor/core").then(({ Capacitor, registerPlugin }) => {
    if (!Capacitor.isNativePlatform() || !Capacitor.isPluginAvailable("HubEndEngines")) return null;
    return registerPlugin<HubEndEnginesPlugin>("HubEndEngines");
  });
  return pluginPromise;
}

async function call<T>(fn: (p: HubEndEnginesPlugin) => Promise<T>): Promise<BridgeResult<T>> {
  const plugin = await loadPlugin();
  if (!plugin) return { available: false, reason: WEB_REASON };
  return { available: true, data: await fn(plugin) };
}

export const hubendEngines = {
  status: () => call((p) => p.status()),
  ledger: (limit = 30) => call((p) => p.ledger({ limit })),
  openNativeConsole: () => call((p) => p.openNativeConsole()),
};

export async function isNativeShell(): Promise<boolean> {
  return (await loadPlugin()) !== null;
}
