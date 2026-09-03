import type { ReactElement } from "react";
import type { Client } from "../api/clients";
import type { AmountOperator, HistorySale, HistorySortBy, SaleHistoryFilterState } from "../hooks/useSaleHistoryFilters";

interface SaleHistoryFiltersProps {
  filters: SaleHistoryFilterState<HistorySale>;
  clients: Client[];
  styles: Record<string, string>;
  isViewScreen: boolean;
  clientLimit: number;
  statusLabels: {
    pending: string;
    partial: string;
    paid: string;
    cancelled: string;
  };
  onClear: () => void;
  constrainDropdownHeight?: boolean;
}

export default function SaleHistoryFilters({
  filters,
  clients,
  styles,
  isViewScreen,
  clientLimit,
  statusLabels,
  onClear,
  constrainDropdownHeight = false,
}: SaleHistoryFiltersProps): ReactElement {
  const clientOptions = clients
    .filter((client) => client.name.toLowerCase().includes(filters.clientFilterSearch.toLowerCase()))
    .slice(0, clientLimit);

  return (
    <div className={styles.filters}>
      <div className={styles.field}>
        <label>Ordenar por</label>
        <select value={filters.sortBy} onChange={(event) => {
          filters.setSortBy(event.target.value as HistorySortBy);
          filters.resetConditionalFields();
        }}>
          <option value="invoiceNumber">Nro Factura</option>
          <option value="createdAt">Fecha</option>
          <option value="client">Cliente</option>
          <option value="total">Monto</option>
        </select>
      </div>
      <div className={styles.field}>
        <label>Dirección</label>
        <select value={filters.sortDir} onChange={(event) => filters.setSortDir(event.target.value as "asc" | "desc")}>
          <option value="desc">Descendente</option>
          <option value="asc">Ascendente</option>
        </select>
      </div>
      <div className={styles.field}>
        <label>Estado</label>
        <select value={filters.statusFilter} onChange={(event) => filters.setStatusFilter(event.target.value)}>
          <option value="ALL">Todas</option>
          <option value="PENDING">{statusLabels.pending}</option>
          <option value="PARTIAL">{statusLabels.partial}</option>
          <option value="PAID">{statusLabels.paid}</option>
          <option value="CANCELLED">{statusLabels.cancelled}</option>
        </select>
      </div>
      {filters.sortBy === "createdAt" && <>
        <div className={styles.field}>
          <label>Desde</label>
          <input type="date" value={filters.dateFrom} max={filters.dateTo || undefined}
            onChange={(event) => filters.setDateFrom(event.target.value)} />
        </div>
        <div className={styles.field}>
          <label>Hasta</label>
          <input type="date" value={filters.dateTo} min={filters.dateFrom || undefined}
            onChange={(event) => filters.setDateTo(event.target.value)} />
        </div>
      </>}
      {filters.sortBy === "total" && <div className={styles.field}>
        <label>Monto</label>
        <div style={{ display: "flex", gap: "0.5rem" }}>
          <select value={filters.amountOperator}
            onChange={(event) => filters.setAmountOperator(event.target.value as AmountOperator)}
            style={{ width: "135px" }}>
            <option value=">=">Mayor o igual</option>
            <option value="<=">Menor o igual</option>
            <option value="=">Igual</option>
          </select>
          <input type="number" min="0" value={filters.amountValue}
            onChange={(event) => filters.setAmountValue(event.target.value)}
            placeholder="Monto" style={{ width: "100px" }} />
        </div>
      </div>}
      {filters.sortBy === "client" && <div className={styles.field} style={{ position: "relative" }}>
        <label>Cliente</label>
        <input readOnly={isViewScreen} type="text" placeholder="Buscar cliente..."
          value={filters.clientFilterSearch} autoComplete="off"
          onChange={(event) => {
            filters.setClientFilterSearch(event.target.value);
            filters.setActiveClientId("");
            filters.setShowClientFilterDropdown(true);
            filters.setClientFilterDropdownIndex(-1);
          }}
          onFocus={() => filters.setShowClientFilterDropdown(true)}
          onBlur={() => setTimeout(() => filters.setShowClientFilterDropdown(false), 200)}
          onKeyDown={(event) => {
            if (event.key === "ArrowDown") {
              event.preventDefault();
              filters.setClientFilterDropdownIndex((previous) => Math.min(previous + 1, clientOptions.length - 1));
            }
            if (event.key === "ArrowUp") {
              event.preventDefault();
              filters.setClientFilterDropdownIndex((previous) => Math.max(previous - 1, 0));
            }
            if (event.key === "Enter" && filters.clientFilterDropdownIndex >= 0) {
              event.preventDefault();
              const selected = clientOptions[filters.clientFilterDropdownIndex];
              filters.setClientFilterSearch(selected.name);
              filters.setActiveClientId(selected.id);
              filters.setShowClientFilterDropdown(false);
              filters.setClientFilterDropdownIndex(-1);
            }
            if (event.key === "Escape") filters.setShowClientFilterDropdown(false);
          }} />
        {filters.showClientFilterDropdown && filters.clientFilterSearch && <div
          className={styles.clientDropdown}
          style={constrainDropdownHeight ? { maxHeight: "200px", overflowY: "auto" } : undefined}>
          {clientOptions.map((client, index) => <div key={client.id}
            data-index={constrainDropdownHeight ? index : undefined}
            className={styles.clientOption}
            style={index === filters.clientFilterDropdownIndex ? { background: "#d1fae5", color: "#16a34a" } : {}}
            onMouseDown={() => {
              filters.setClientFilterSearch(client.name);
              filters.setActiveClientId(client.id);
              filters.setShowClientFilterDropdown(false);
              filters.setClientFilterDropdownIndex(-1);
            }}
            onMouseEnter={() => filters.setClientFilterDropdownIndex(index)}>{client.name}</div>)}
        </div>}
      </div>}
      <button className={styles.primaryButton} type="button" onClick={filters.apply}>Buscar</button>
      <button className={styles.button} type="button" onClick={() => {
        filters.clear();
        onClear();
      }}>Limpiar</button>
    </div>
  );
}
