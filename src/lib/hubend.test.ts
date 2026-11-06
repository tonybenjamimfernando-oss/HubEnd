import { afterEach, describe, expect, it, vi } from "vitest";

import { ApiClient, HttpError } from "@/lib/core/apiClient";
import { EventBus } from "@/lib/core/eventBus";
import { hubendEngines } from "@/lib/bridge/hubendEngines";
import {
  ENGINES,
  REGISTRY_FLOWS,
  allCapabilities,
  isFlowAllowed,
  statusBreakdown,
} from "@/lib/engines/catalog";
import { runTone } from "@/lib/github/hubendRepo";

describe("Registo de motores (espelho de HubOpsEngineRegistry.kt)", () => {
  it("declara exatamente os seis fluxos do registo nativo", () => {
    expect(REGISTRY_FLOWS).toHaveLength(6);
  });

  it("Espelhar-X só pode ler o Despertar, nunca alterá-lo", () => {
    expect(isFlowAllowed("ESPELHAR_X", "DESPERTAR", "READ_ONLY_ANALYSIS")).toBe(true);
    expect(isFlowAllowed("ESPELHAR_X", "DESPERTAR", "GOVERNED_MUTATION")).toBe(false);
  });

  it("VislumbOps só envia evidência ao Core e não altera Despertar nem Espelhar-X", () => {
    expect(isFlowAllowed("VISLUMBOPS", "HUBOPS", "EVIDENCE_ONLY")).toBe(true);
    expect(isFlowAllowed("VISLUMBOPS", "DESPERTAR", "GOVERNED_MUTATION")).toBe(false);
    expect(isFlowAllowed("VISLUMBOPS", "ESPELHAR_X", "GOVERNED_MUTATION")).toBe(false);
  });

  it("o Core altera Despertar e Espelhar-X apenas por mutação governada", () => {
    expect(isFlowAllowed("HUBOPS", "DESPERTAR", "GOVERNED_MUTATION")).toBe(true);
    expect(isFlowAllowed("HUBOPS", "ESPELHAR_X", "GOVERNED_MUTATION")).toBe(true);
  });
});

describe("Catálogo de capacidades", () => {
  it("inclui os sete motores pedidos", () => {
    expect(ENGINES.map((e) => e.slug).sort()).toEqual(
      ["despertar", "espelhar-x", "gopa", "hubops-core", "nsc", "snse", "vislumbops"].sort(),
    );
  });

  it("a inferência neural SNSE não é declarada operacional", () => {
    const snse = ENGINES.find((e) => e.slug === "snse")!;
    const neural = snse.capabilities.find((c) => c.name.includes("Inferência neural"))!;
    expect(neural.status).toBe("PREPARADA");
  });

  it("o desbloqueio pago do Despertar continua pendente (sem Play Billing)", () => {
    const despertar = ENGINES.find((e) => e.slug === "despertar")!;
    const paid = despertar.capabilities.find((c) => c.name.includes("pago"))!;
    expect(paid.status).toBe("PENDENTE");
  });

  it("toda a capacidade tem evidência", () => {
    for (const c of allCapabilities()) expect(c.evidence.trim().length).toBeGreaterThan(0);
  });

  it("conta capacidades por estado", () => {
    const b = statusBreakdown([
      { name: "a", detail: "", status: "TESTADA", evidence: "x" },
      { name: "b", detail: "", status: "TESTADA", evidence: "x" },
      { name: "c", detail: "", status: "BLOQUEADA", evidence: "x" },
    ]);
    expect(b.TESTADA).toBe(2);
    expect(b.BLOQUEADA).toBe(1);
    expect(b.PARCIAL).toBe(0);
  });
});

describe("Ponte nativa no navegador", () => {
  it("não simula motores: devolve indisponível", async () => {
    const r = await hubendEngines.status();
    expect(r.available).toBe(false);
  });
});

describe("Estado do CI", () => {
  it("só é sucesso quando concluído com sucesso", () => {
    expect(runTone({ status: "completed", conclusion: "success" })).toBe("success");
    expect(runTone({ status: "in_progress", conclusion: null })).toBe("running");
    expect(runTone({ status: "completed", conclusion: "failure" })).toBe("failure");
    expect(runTone({ status: "completed", conclusion: "cancelled" })).toBe("neutral");
  });
});

describe("Serviços partilhados herdados do HybridApp", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("EventBus: once dispara uma vez", () => {
    const bus = new EventBus();
    const fn = vi.fn();
    bus.once("x", fn);
    bus.emit("x");
    bus.emit("x");
    expect(fn).toHaveBeenCalledTimes(1);
  });

  it("ApiClient não repete erros 404", async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response("{}", { status: 404 }));
    vi.stubGlobal("fetch", fetchMock);
    const client = new ApiClient("https://api.test", 1);
    await expect(client.request("/x", { retries: 2 })).rejects.toBeInstanceOf(HttpError);
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("ApiClient repete erros 5xx até ao limite", async () => {
    const fetchMock = vi.fn().mockImplementation(async () => new Response("{}", { status: 503 }));
    vi.stubGlobal("fetch", fetchMock);
    const client = new ApiClient("https://api.test", 1);
    await expect(client.request("/x", { retries: 2 })).rejects.toThrow();
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });
});
