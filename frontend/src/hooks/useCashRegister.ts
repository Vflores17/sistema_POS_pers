import { useState } from "react";

export interface CashRegisterPayment {
  facturaId: string;
  method: string;
  amount: number;
}

export interface CashRegisterExpense {
  id: string;
  descripcion: string;
  monto: number;
}

interface BaseCashRegisterState {
  abierta: boolean;
  montoInicial: number;
  horaInicio: string;
  openedAt: string;
  facturaIds: string[];
  pagos: CashRegisterPayment[];
}

export interface SalesCashRegisterState extends BaseCashRegisterState {
  gastos: CashRegisterExpense[];
}

const SALES_STORAGE_KEY = "caja_state";

function closedSalesCashRegister(): SalesCashRegisterState {
  return {
    abierta: false,
    montoInicial: 0,
    horaInicio: "",
    openedAt: "",
    facturaIds: [],
    pagos: [],
    gastos: [],
  };
}

function loadSalesCashRegister(): SalesCashRegisterState {
  try {
    const stored = localStorage.getItem(SALES_STORAGE_KEY);
    if (stored) {
      const parsed = JSON.parse(stored) as SalesCashRegisterState;
      return {
        ...parsed,
        pagos: parsed.pagos ?? [],
        gastos: parsed.gastos ?? [],
        openedAt: parsed.openedAt ?? "",
      };
    }
  } catch {
    // Mantiene el comportamiento legacy: una lectura inválida inicia la caja cerrada.
  }
  return closedSalesCashRegister();
}

function useStoredCashRegister<T>(
  storageKey: string,
  load: () => T,
) {
  const [cashRegister, setCashRegister] = useState<T>(load);

  function persistCashRegister(next: T): void {
    setCashRegister(next);
    localStorage.setItem(storageKey, JSON.stringify(next));
  }

  return { cashRegister, persistCashRegister };
}

export function useSalesCashRegister() {
  const { cashRegister, persistCashRegister } = useStoredCashRegister(
    SALES_STORAGE_KEY,
    loadSalesCashRegister,
  );

  function openCashRegister(initialAmount: number): void {
    persistCashRegister({
      abierta: true,
      montoInicial: initialAmount,
      horaInicio: new Date().toLocaleString("es-CR"),
      openedAt: new Date().toISOString(),
      facturaIds: [],
      pagos: [],
      gastos: [],
    });
  }

  function closeCashRegister(): void {
    persistCashRegister(closedSalesCashRegister());
  }

  function addExpense(expense: CashRegisterExpense): void {
    persistCashRegister({
      ...cashRegister,
      gastos: [...cashRegister.gastos, expense],
    });
  }

  function removeExpense(id: string): void {
    persistCashRegister({
      ...cashRegister,
      gastos: cashRegister.gastos.filter((expense) => expense.id !== id),
    });
  }

  return {
    cashRegister,
    persistCashRegister,
    openCashRegister,
    closeCashRegister,
    addExpense,
    removeExpense,
  };
}