import { useEffect } from "react";

type CellColumn = "name" | "quantity" | "price";

interface ShortcutLine {
  id: string;
}

interface ActiveCell {
  rowId: string;
  col: CellColumn;
}

interface FormShortcutOptions {
  enabled: boolean;
  blocked: boolean;
  isViewScreen: boolean;
  canCreateProduct: boolean;
  canReadProducts: boolean;
  canReadPrices: boolean;
  canSave: boolean;
  activeCell: ActiveCell | null;
  isInputFocused: () => boolean;
  lines: ShortcutLine[];
  selectedRowId: string;
  hasViewedProduct: boolean;
  onCreateProduct: () => void;
  onListProducts: () => void;
  onViewProduct: () => void;
  onAddRow: () => void;
  onRemoveRow: () => void;
  onSave: (printAfterSave: boolean) => void;
  onExit: () => void;
  onOpenModify?: () => void;
  onSelectRow: (rowId: string) => void;
  onFocusCell: (rowId: string, column: CellColumn) => void;
  onCloseViewedProduct: () => void;
  registration: {
    draft: unknown;
    productsById: unknown;
    addRow: unknown;
    removeRow: unknown;
    save: unknown;
    navigate: unknown;
  };
}

interface ProductModalShortcutOptions {
  showProductModal: boolean;
  showCreateProductModal: boolean;
  onCloseProductModal: () => void;
  onCloseCreateProductModal: () => void;
}

interface HistoryShortcutOptions<T extends ShortcutLine> {
  enabled: boolean;
  blocked: boolean;
  selectedRowId: string;
  rows: T[];
  canCreate: boolean;
  canOpenModify: boolean;
  canView: boolean;
  canPrint: boolean;
  canDelete: () => boolean;
  canPay: () => boolean;
  canOpenCashRegister: boolean;
  canCloseCashRegister: boolean;
  onCreate: () => void;
  onOpenModify: () => void;
  onView: () => void;
  onPrint: (row: T) => void;
  onDelete: () => void;
  onPay: () => void;
  onExit: () => void;
  onOpenCashRegister: () => void;
  onCloseCashRegister: () => void;
  onSelectRow: (rowId: string) => void;
  registration: {
    cashRegister: unknown;
    navigate: unknown;
  };
}

interface WhatsappShortcutOptions {
  enabled: boolean;
  modal: unknown;
  onConfirm: () => void;
  onClose: () => void;
}

interface SaleKeyboardShortcutOptions<T extends ShortcutLine> {
  whatsapp: WhatsappShortcutOptions;
  form: FormShortcutOptions;
  productModals: ProductModalShortcutOptions;
  history: HistoryShortcutOptions<T>;
}

export function useSaleKeyboardShortcuts<T extends ShortcutLine>({
  whatsapp,
  form,
  productModals,
  history,
}: SaleKeyboardShortcutOptions<T>): void {
  useEffect(() => {
    if (!whatsapp.enabled) return;

    function handleKeyDown(event: KeyboardEvent): void {
      if (event.key === "Enter") whatsapp.onConfirm();
      if (event.key === "Escape") whatsapp.onClose();
    }

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [whatsapp.modal]);

  useEffect(() => {
    if (!form.enabled) return;

    function handleKeyDown(event: KeyboardEvent): void {
      if (form.blocked) return;
      if (event.key === "F2") {
        if (form.isViewScreen || !form.canCreateProduct) return;
        event.preventDefault();
        form.onCreateProduct();
      }
      if (event.key === "F3") {
        if (form.isViewScreen || !form.canReadProducts) return;
        event.preventDefault();
        form.onListProducts();
      }
      if (event.key === "F4") {
        if (!form.canReadProducts || !form.canReadPrices) return;
        event.preventDefault();
        form.onViewProduct();
      }
      if (event.key === "F5") {
        if (form.isViewScreen) return;
        event.preventDefault();
        form.onAddRow();
      }
      if (event.key === "F6") {
        if (form.isViewScreen) return;
        event.preventDefault();
        form.onRemoveRow();
      }
      if (form.onOpenModify && event.altKey && event.key.toLowerCase() === "o") {
        event.preventDefault();
        form.onOpenModify();
      }
      if (event.altKey && event.key.toLowerCase() === "a") {
        if (form.isViewScreen || !form.canSave) return;
        event.preventDefault();
        form.onSave(false);
      }
      if (event.altKey && event.key.toLowerCase() === "m") {
        if (form.isViewScreen || !form.canSave) return;
        event.preventDefault();
        form.onSave(true);
      }
      if (event.altKey && event.code === "KeyS") {
        event.preventDefault();
        form.onExit();
      }
      if (event.altKey && event.key.toLowerCase() === "p") {
        event.preventDefault();
        document.querySelector<HTMLSelectElement>("select[value]")?.focus();
      }
      if (event.altKey && event.key.toLowerCase() === "b") {
        event.preventDefault();
        document
          .querySelector<HTMLInputElement>("input[placeholder='Buscar cliente...']")
          ?.focus();
      }

      if (form.activeCell) {
        const currentRowIndex = form.lines.findIndex(
          (line) => line.id === form.activeCell?.rowId,
        );
        const columns: CellColumn[] = ["name", "quantity", "price"];
        const currentColumnIndex = columns.indexOf(form.activeCell.col);

        if (event.key === "ArrowRight" && form.activeCell.col !== "price") {
          event.preventDefault();
          form.onFocusCell(form.activeCell.rowId, columns[currentColumnIndex + 1]);
        }
        if (event.key === "ArrowLeft" && form.activeCell.col !== "name") {
          event.preventDefault();
          form.onFocusCell(form.activeCell.rowId, columns[currentColumnIndex - 1]);
        }
        if (event.key === "Tab") {
          event.preventDefault();
          if (currentColumnIndex < columns.length - 1) {
            form.onFocusCell(form.activeCell.rowId, columns[currentColumnIndex + 1]);
          } else {
            const nextRow = form.lines[currentRowIndex + 1];
            if (nextRow) form.onFocusCell(nextRow.id, "name");
          }
        }
      } else if (!form.isInputFocused()) {
        if (event.key === "ArrowDown") {
          event.preventDefault();
          const currentIndex = form.lines.findIndex((line) => line.id === form.selectedRowId);
          const nextIndex = Math.min(currentIndex + 1, form.lines.length - 1);
          form.onSelectRow(form.lines[nextIndex]?.id ?? "");
        }
        if (event.key === "ArrowUp") {
          event.preventDefault();
          const currentIndex = form.lines.findIndex((line) => line.id === form.selectedRowId);
          const previousIndex = Math.max(currentIndex - 1, 0);
          form.onSelectRow(form.lines[previousIndex]?.id ?? "");
        }
      }

      if (event.key === "Escape" && form.hasViewedProduct) {
        event.preventDefault();
        form.onCloseViewedProduct();
      }
    }

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [
    form.enabled,
    form.registration.draft,
    form.selectedRowId,
    form.registration.productsById,
    form.registration.addRow,
    form.registration.removeRow,
    form.registration.save,
    form.registration.navigate,
  ]);

  useEffect(() => {
    if (!productModals.showProductModal) return;

    function handleKeyDown(event: KeyboardEvent): void {
      if (event.key === "Escape") {
        event.preventDefault();
        productModals.onCloseProductModal();
      }
      if (event.key === "Enter") {
        event.preventDefault();
        document.querySelector<HTMLButtonElement>("#saveProductBtn")?.click();
      }
    }

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [productModals.showProductModal]);

  useEffect(() => {
    if (!productModals.showCreateProductModal) return;

    function handleKeyDown(event: KeyboardEvent): void {
      if (event.key === "Escape") {
        event.preventDefault();
        event.stopPropagation();
        productModals.onCloseCreateProductModal();
      }
      if (event.altKey && event.code === "KeyG") {
        event.preventDefault();
        event.stopPropagation();
        document.querySelector<HTMLButtonElement>("#saveProductBtn")?.click();
      }
    }

    window.addEventListener("keydown", handleKeyDown, true);
    return () => window.removeEventListener("keydown", handleKeyDown, true);
  }, [productModals.showCreateProductModal]);

  useEffect(() => {
    if (!history.enabled) return;

    function handleKeyDown(event: KeyboardEvent): void {
      if (history.blocked) return;
      if (event.altKey && event.key.toLowerCase() === "c") {
        event.preventDefault();
        if (history.canCreate) history.onCreate();
      }
      if (event.altKey && event.key.toLowerCase() === "o") {
        event.preventDefault();
        if (history.canOpenModify) history.onOpenModify();
      }
      if (event.altKey && event.key.toLowerCase() === "v") {
        event.preventDefault();
        if (history.canView) history.onView();
      }
      if (event.altKey && event.key.toLowerCase() === "i") {
        event.preventDefault();
        if (history.canPrint) {
          const row = history.rows.find((item) => item.id === history.selectedRowId);
          if (row) history.onPrint(row);
        }
      }
      if (event.altKey && event.key.toLowerCase() === "e") {
        event.preventDefault();
        if (history.canDelete()) history.onDelete();
      }
      if (event.altKey && event.key.toLowerCase() === "z") {
        event.preventDefault();
        if (history.canPay()) history.onPay();
      }
      if (event.altKey && event.key.toLowerCase() === "r") {
        event.preventDefault();
        history.onExit();
      }
      if (event.altKey && event.key.toLowerCase() === "k") {
        event.preventDefault();
        if (history.canOpenCashRegister) history.onOpenCashRegister();
      }
      if (event.altKey && event.key.toLowerCase() === "x") {
        event.preventDefault();
        if (history.canCloseCashRegister) history.onCloseCashRegister();
      }
      if (event.key === "ArrowDown") {
        event.preventDefault();
        const currentIndex = history.rows.findIndex(
          (item) => item.id === history.selectedRowId,
        );
        const nextIndex = Math.min(currentIndex + 1, history.rows.length - 1);
        history.onSelectRow(history.rows[nextIndex]?.id ?? "");
      }
      if (event.key === "ArrowUp") {
        event.preventDefault();
        const currentIndex = history.rows.findIndex(
          (item) => item.id === history.selectedRowId,
        );
        const previousIndex = Math.max(currentIndex - 1, 0);
        history.onSelectRow(history.rows[previousIndex]?.id ?? "");
      }
    }

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [
    history.enabled,
    history.blocked,
    history.selectedRowId,
    history.registration.cashRegister,
    history.registration.navigate,
    history.rows,
  ]);
}

export function useEscapeShortcut(
  enabled: boolean,
  onEscape: () => void,
): void {
  useEffect(() => {
    if (!enabled) return;

    function handleKeyDown(event: KeyboardEvent): void {
      if (event.key === "Escape") onEscape();
    }

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [enabled]);
}
