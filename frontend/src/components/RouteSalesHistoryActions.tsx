import type { ReactElement } from "react";

interface RouteSalesHistoryActionsProps {
  canCreate: boolean;
  canDelete: boolean;
  canCancel: boolean;
  canUpdate: boolean;
  canReadDrivers: boolean;
  hasSelection: boolean;
  selectedStatus?: string;
  styles: Record<string, string>;
  onCreate: () => void;
  onModify: () => void;
  onView: () => void;
  onPrint: () => void;
  onWhatsapp: () => void;
  onDelete: () => void;
  onCancel: () => void;
  onPay: () => void;
  onDrivers: () => void;
  onExit: () => void;
}

export default function RouteSalesHistoryActions({
  canCreate,
  canDelete,
  canCancel,
  canUpdate,
  canReadDrivers,
  hasSelection,
  selectedStatus,
  styles,
  onCreate,
  onModify,
  onView,
  onPrint,
  onWhatsapp,
  onDelete,
  onCancel,
  onPay,
  onDrivers,
  onExit,
}: RouteSalesHistoryActionsProps): ReactElement {
  return (
    <div className={styles.bottomBar}>
      <div className={styles.bottomActions}>
        <button className={styles.primaryButton} type="button" disabled={!canCreate} onClick={onCreate}>
          <u>C</u>rear
        </button>
        <button className={styles.button} type="button" disabled={!hasSelection} onClick={onModify}>
          M<u>o</u>dificar
        </button>
        <button className={styles.button} type="button" disabled={!hasSelection} onClick={onView}>
          <u>V</u>er Factura
        </button>
        <button className={styles.button} type="button" disabled={!hasSelection} onClick={onPrint}>
          <u>I</u>mprimir
        </button>
        <button className={styles.button} type="button" disabled={!hasSelection} onClick={onWhatsapp}>
          WhatsApp
        </button>
        <button className={styles.dangerButton} type="button" disabled={!canDelete || !hasSelection || selectedStatus === "PAID"} onClick={onDelete}>
          <u>E</u>liminar
        </button>
        {canCancel && (
          <button className={styles.dangerButton} type="button" disabled={!hasSelection || selectedStatus === "CANCELLED"} onClick={onCancel}>
            Cancelar
          </button>
        )}
        <button className={styles.primaryButton} type="button" disabled={!canUpdate || !hasSelection || selectedStatus !== "PENDING"} onClick={onPay}>
          Pagar <kbd>Alt+Z</kbd>
        </button>
      </div>
      <div className={styles.bottomGlobal}>
        {canReadDrivers && (
          <button className={styles.button} type="button" onClick={onDrivers}>
            Choferes
          </button>
        )}
        <button className={styles.button} type="button" onClick={onExit}>
          Sali<u>r</u>
        </button>
      </div>
    </div>
  );
}
