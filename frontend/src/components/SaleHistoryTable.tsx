import type { ReactElement } from "react";
import type { Client } from "../api/clients";
import type { HistorySale } from "../hooks/useSaleHistoryFilters";

interface SaleHistoryTableProps<T extends HistorySale & { paymentMethod: string }> {
  sales: T[];
  clientsById: Map<string, Client>;
  selectedRowId: string;
  emptyMessage: string;
  styles: Record<string, string>;
  formatInvoiceNumber: (sale: T) => string | number;
  formatPaymentMethod: (method: T["paymentMethod"]) => string;
  formatStatus: (status: T["status"]) => string;
  onSelect: (saleId: string) => void;
}

export default function SaleHistoryTable<T extends HistorySale & { paymentMethod: string }>({
  sales,
  clientsById,
  selectedRowId,
  emptyMessage,
  styles,
  formatInvoiceNumber,
  formatPaymentMethod,
  formatStatus,
  onSelect,
}: SaleHistoryTableProps<T>): ReactElement {
  return (
    <div className={styles.tableContainer}>
      <table className={styles.table}>
        <thead>
          <tr>
            <th>Nro. Factura</th>
            <th>Fecha</th>
            <th>Cliente</th>
            <th>Método de pago</th>
            <th>Total</th>
            <th>Estado</th>
          </tr>
        </thead>
        <tbody>
          {sales.length === 0 ? (
            <tr>
              <td colSpan={6} className={styles.empty}>
                {emptyMessage}
              </td>
            </tr>
          ) : (
            sales.map((sale) => (
              <tr
                key={sale.id}
                className={selectedRowId === sale.id ? styles.selected : ""}
                onClick={() => onSelect(sale.id)}
              >
                <td>{formatInvoiceNumber(sale)}</td>
                <td>{new Date(sale.createdAt).toLocaleDateString("es-CR")}</td>
                <td>{clientsById.get(sale.clientId)?.name ?? sale.clientId}</td>
                <td>{formatPaymentMethod(sale.paymentMethod)}</td>
                <td>₡{Number(sale.total).toLocaleString("es-CR")}</td>{" "}
                <td>
                  <span
                    className={`${styles.status} ${styles[sale.status.toLowerCase()]}`}
                  >
                    {formatStatus(sale.status)}
                  </span>
                </td>
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  );
}
