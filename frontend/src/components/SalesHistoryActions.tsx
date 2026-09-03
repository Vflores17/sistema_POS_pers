import type { ReactElement } from "react";

interface SalesHistoryActionsProps {
  canCreate: boolean;
  canDelete: boolean;
  canOperateCashRegister: boolean;
  cashRegisterOpen: boolean;
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
  onExpenses: () => void;
  onOpenCashRegister: () => void;
  onCloseCashRegister: () => void;
  onExit: () => void;
}

export default function SalesHistoryActions({
  canCreate,
  canDelete,
  canOperateCashRegister,
  cashRegisterOpen,
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
  onExpenses,
  onOpenCashRegister,
  onCloseCashRegister,
  onExit,
}: SalesHistoryActionsProps): ReactElement {
  return (
    <div className={styles.bottomBar}>
      <div className={styles.bottomActions}>
        <button className={styles.primaryButton} type="button" disabled={!canCreate || !cashRegisterOpen} onClick={onCreate}>
          <u>C</u>rear
        </button>
        <button className={styles.button} type="button" disabled={!cashRegisterOpen || !hasSelection} onClick={onModify}>
          M<u>o</u>dificar
        </button>
        <button className={styles.button} type="button" disabled={!cashRegisterOpen || !hasSelection} onClick={onView}>
          <u>V</u>er Factura
        </button>
        <button className={styles.button} type="button" disabled={!cashRegisterOpen || !hasSelection} onClick={onPrint}>
          <u>I</u>mprimir
        </button>
        <button className={styles.button} type="button" disabled={!cashRegisterOpen || !hasSelection} onClick={onWhatsapp}>
          WhatsApp
        </button>
        <button
          className={styles.dangerButton}
          type="button"
          disabled={!canDelete || !cashRegisterOpen || !hasSelection || selectedStatus === "PAID" || selectedStatus === "PARTIAL"}
          onClick={onDelete}
        >
          <u>E</u>liminar
        </button>
        <button className={styles.dangerButton} type="button" disabled={!hasSelection || selectedStatus === "CANCELLED"} onClick={onCancel}>
          Cancelar
        </button>
        <button
          className={styles.primaryButton}
          type="button"
          disabled={!cashRegisterOpen || !hasSelection || (selectedStatus !== "PENDING" && selectedStatus !== "PARTIAL")}
          onClick={onPay}
        >
          Pagar <kbd>Alt+Z</kbd>
        </button>
        <button className={styles.button} type="button" disabled={!canOperateCashRegister || !cashRegisterOpen} onClick={onExpenses}>
          Gastos
        </button>
      </div>
      <div className={styles.bottomGlobal}>
        <button className={styles.primaryButton} type="button" disabled={!canOperateCashRegister || cashRegisterOpen} onClick={onOpenCashRegister}>
          Iniciar Caja <kbd>Alt+K</kbd>
        </button>
        <button className={styles.button} type="button" disabled={!canOperateCashRegister || !cashRegisterOpen} onClick={onCloseCashRegister}>
          Cierre de Caja <kbd>Alt+X</kbd>
        </button>
        <button className={styles.button} type="button" onClick={onExit}>
          Sali<u>r</u>
        </button>
      </div>
    </div>
  );
}
