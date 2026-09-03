import { useMemo, useState } from "react";
import type { Dispatch, SetStateAction } from "react";
import type { Client } from "../api/clients";

export type HistorySortBy = "invoiceNumber" | "createdAt" | "client" | "total";
export type AmountOperator = ">=" | "<=" | "=";

export interface HistorySale {
  id: string;
  invoiceNumber: number;
  createdAt: string;
  clientId: string;
  total: number;
  status: string;
}

export interface SaleHistoryFilterState<T extends HistorySale> {
  sortedAndFilteredSales: T[];
  sortBy: HistorySortBy;
  setSortBy: (value: HistorySortBy) => void;
  sortDir: "asc" | "desc";
  setSortDir: (value: "asc" | "desc") => void;
  statusFilter: string;
  setStatusFilter: (value: string) => void;
  dateFrom: string;
  setDateFrom: (value: string) => void;
  dateTo: string;
  setDateTo: (value: string) => void;
  amountOperator: AmountOperator;
  setAmountOperator: (value: AmountOperator) => void;
  amountValue: string;
  setAmountValue: (value: string) => void;
  clientFilterSearch: string;
  setClientFilterSearch: (value: string) => void;
  activeClientId: string;
  setActiveClientId: (value: string) => void;
  showClientFilterDropdown: boolean;
  setShowClientFilterDropdown: (value: boolean) => void;
  clientFilterDropdownIndex: number;
  setClientFilterDropdownIndex: Dispatch<SetStateAction<number>>;
  apply: () => void;
  clear: () => void;
  resetConditionalFields: () => void;
}

export function useSaleHistoryFilters<T extends HistorySale>(
  sales: T[],
  clientsById: Map<string, Client>,
): SaleHistoryFilterState<T> {
  const [sortBy, setSortBy] = useState<HistorySortBy>("createdAt");
  const [sortDir, setSortDir] = useState<"asc" | "desc">("desc");
  const [statusFilter, setStatusFilter] = useState<string>("ALL");
  const [dateFrom, setDateFrom] = useState<string>("");
  const [dateTo, setDateTo] = useState<string>("");
  const [activeDateFrom, setActiveDateFrom] = useState<string>("");
  const [activeDateTo, setActiveDateTo] = useState<string>("");
  const [activeSortBy, setActiveSortBy] = useState<HistorySortBy>("createdAt");
  const [activeSortDir, setActiveSortDir] = useState<"asc" | "desc">("desc");
  const [activeStatusFilter, setActiveStatusFilter] = useState<string>("ALL");
  const [amountOperator, setAmountOperator] = useState<AmountOperator>(">=");
  const [amountValue, setAmountValue] = useState<string>("");
  const [activeAmountOperator, setActiveAmountOperator] = useState<AmountOperator>(">=");
  const [activeAmountValue, setActiveAmountValue] = useState<string>("");
  const [clientFilterSearch, setClientFilterSearch] = useState<string>("");
  const [activeClientId, setActiveClientId] = useState<string>("");
  const [showClientFilterDropdown, setShowClientFilterDropdown] = useState<boolean>(false);
  const [clientFilterDropdownIndex, setClientFilterDropdownIndex] = useState<number>(-1);

  const sortedAndFilteredSales = useMemo(() => {
    let result = sales;
    if (activeStatusFilter !== "ALL") {
      result = result.filter((sale) => sale.status === activeStatusFilter);
    }
    if (activeDateFrom) {
      result = result.filter((sale) => {
        const saleDateCR = new Date(new Date(sale.createdAt).getTime() - 6 * 60 * 60 * 1000);
        return saleDateCR.toISOString().slice(0, 10) >= activeDateFrom;
      });
    }
    if (activeDateTo) {
      result = result.filter((sale) => {
        const saleDateCR = new Date(new Date(sale.createdAt).getTime() - 6 * 60 * 60 * 1000);
        return saleDateCR.toISOString().slice(0, 10) <= activeDateTo;
      });
    }
    if (activeAmountValue) {
      const amount = Number(activeAmountValue);
      result = result.filter((sale) => {
        const total = Number(sale.total);
        if (activeAmountOperator === ">=") return total >= amount;
        if (activeAmountOperator === "<=") return total <= amount;
        return total === amount;
      });
    }
    if (activeClientId) {
      result = result.filter((sale) => sale.clientId === activeClientId);
    }
    return [...result].sort((a, b) => {
      let comparison: number;
      if (activeSortBy === "invoiceNumber") {
        comparison = a.invoiceNumber - b.invoiceNumber;
      } else if (activeSortBy === "createdAt") {
        comparison = new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime();
      } else if (activeSortBy === "total") {
        comparison = Number(a.total) - Number(b.total);
      } else {
        const aClient = clientsById.get(a.clientId)?.name ?? "";
        const bClient = clientsById.get(b.clientId)?.name ?? "";
        comparison = aClient.localeCompare(bClient);
      }
      return activeSortDir === "asc" ? comparison : -comparison;
    });
  }, [sales, activeSortBy, activeSortDir, activeStatusFilter, clientsById,
    activeDateFrom, activeDateTo, activeAmountOperator, activeAmountValue, activeClientId]);

  function apply(): void {
    setActiveSortBy(sortBy);
    setActiveSortDir(sortDir);
    setActiveStatusFilter(statusFilter);
    setActiveDateFrom(dateFrom);
    setActiveDateTo(dateTo);
    setActiveAmountOperator(amountOperator);
    setActiveAmountValue(amountValue);
  }

  function clear(): void {
    setSortBy("createdAt");
    setSortDir("desc");
    setStatusFilter("ALL");
    setDateFrom("");
    setDateTo("");
    setActiveSortBy("createdAt");
    setActiveSortDir("desc");
    setActiveStatusFilter("ALL");
    setActiveDateFrom("");
    setActiveDateTo("");
    setActiveAmountOperator(">=");
    setActiveAmountValue("");
    setClientFilterSearch("");
    setActiveClientId("");
  }

  function resetConditionalFields(): void {
    setDateFrom("");
    setDateTo("");
    setAmountValue("");
    setClientFilterSearch("");
    setActiveClientId("");
  }

  return {
    sortedAndFilteredSales, sortBy, setSortBy, sortDir, setSortDir,
    statusFilter, setStatusFilter, dateFrom, setDateFrom, dateTo, setDateTo,
    amountOperator, setAmountOperator, amountValue, setAmountValue,
    clientFilterSearch, setClientFilterSearch, activeClientId, setActiveClientId,
    showClientFilterDropdown, setShowClientFilterDropdown,
    clientFilterDropdownIndex, setClientFilterDropdownIndex,
    apply, clear, resetConditionalFields,
  };
}
