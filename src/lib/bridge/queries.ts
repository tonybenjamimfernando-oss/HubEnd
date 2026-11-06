import { queryOptions } from "@tanstack/react-query";

import { hubendEngines } from "./hubendEngines";
import { fetchHubEndRepoState } from "@/lib/github/hubendRepo";

/** Estado nativo: só é pedido no cliente (a ponte não existe no servidor). */
export const nativeStatusQuery = queryOptions({
  queryKey: ["native", "status"],
  queryFn: () => hubendEngines.status(),
  staleTime: 15_000,
});

export const nativeLedgerQuery = (limit = 30) =>
  queryOptions({
    queryKey: ["native", "ledger", limit],
    queryFn: () => hubendEngines.ledger(limit),
    staleTime: 10_000,
  });

export const hubendRepoQuery = queryOptions({
  queryKey: ["github", "hubend-repo"],
  queryFn: fetchHubEndRepoState,
  staleTime: 60_000,
  retry: false,
});
