import type { ReactElement, ReactNode } from "react";

interface SaleFormHeaderProps {
  title: string;
  error: string;
  styles: Record<string, string>;
}

export function SaleFormHeader({
  title,
  error,
  styles,
}: SaleFormHeaderProps): ReactElement {
  return (
    <>
      <header className={styles.header}>
        <h2 className={styles.title}>{title}</h2>
      </header>
      {error ? <p className={styles.error}>{error}</p> : null}
    </>
  );
}

interface SaleInvoiceFieldProps {
  invoiceNumber: ReactNode;
  styles: Record<string, string>;
}

export function SaleInvoiceField({
  invoiceNumber,
  styles,
}: SaleInvoiceFieldProps): ReactElement {
  return (
    <div className={styles.field}>
      <label>Número de factura</label>
      <div className={styles.invoiceNumber}>{invoiceNumber}</div>
    </div>
  );
}
