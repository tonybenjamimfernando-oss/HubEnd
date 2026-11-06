import { useQuery, type UseQueryOptions, type UseQueryResult, type QueryKey } from "@tanstack/react-query";
import { useEffect, useState } from "react";

/** Verdadeiro só depois da hidratação (o servidor e o primeiro render do cliente veem false). */
export function useHydrated(): boolean {
  const [hydrated, setHydrated] = useState(false);
  useEffect(() => setHydrated(true), []);
  return hydrated;
}

/**
 * Consulta só do cliente (ponte nativa, API GitHub com dados do dispositivo).
 * Até à hidratação devolve sempre o estado "pendente", mesmo que outra parte da página
 * já tenha os dados em cache — assim o HTML do servidor e o do cliente coincidem.
 */
export function useClientQuery<TData, TKey extends QueryKey = QueryKey>(
  options: UseQueryOptions<TData, Error, TData, TKey>,
): UseQueryResult<TData, Error> {
  const hydrated = useHydrated();
  const query = useQuery({ ...options, enabled: hydrated && (options.enabled ?? true) });
  if (hydrated) return query;
  return {
    ...query,
    data: undefined,
    error: null,
    isPending: true,
    isLoading: true,
    isError: false,
    isSuccess: false,
    status: "pending",
  } as UseQueryResult<TData, Error>;
}
