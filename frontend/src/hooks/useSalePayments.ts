import { useMemo, useState } from "react";

export type PaymentMethodCode = "CASH" | "SINPE" | "TRANSFER" | "CARD";
export type PaymentStatusCode = "PENDING" | "PARTIAL" | "PAID" | "CANCELLED";

export interface PaymentAmountDraft {
  id?: string;
  amount: string;
}

export interface PaymentDraft {
  enabled: boolean;
  amounts: PaymentAmountDraft[];
}

export type PaymentDraftState = Record<PaymentMethodCode, PaymentDraft>;

export interface ExistingPayment {
  id: string;
  method: PaymentMethodCode;
  amount: number;
}

export interface PaymentPayload {
  id?: string;
  method: PaymentMethodCode;
  amount: number;
}

const EMPTY_PAYMENTS: PaymentDraftState = {
  CASH: { enabled: false, amounts: [{ amount: "" }] },
  SINPE: { enabled: false, amounts: [{ amount: "" }] },
  TRANSFER: { enabled: false, amounts: [{ amount: "" }] },
  CARD: { enabled: false, amounts: [{ amount: "" }] },
};

function createDraftFromPayments(payments: ExistingPayment[]): PaymentDraftState {
  const nextPayments: PaymentDraftState = {
    CASH: { enabled: false, amounts: [{ amount: "" }] },
    SINPE: { enabled: false, amounts: [{ amount: "" }] },
    TRANSFER: { enabled: false, amounts: [{ amount: "" }] },
    CARD: { enabled: false, amounts: [{ amount: "" }] },
  };

  for (const payment of payments) {
    if (nextPayments[payment.method].enabled) {
      nextPayments[payment.method].amounts.push({
        id: payment.id,
        amount: String(payment.amount),
      });
    } else {
      nextPayments[payment.method] = {
        enabled: true,
        amounts: [{ id: payment.id, amount: String(payment.amount) }],
      };
    }
  }

  return nextPayments;
}

export function useSalePayments<TStatus extends PaymentStatusCode>(
  calculatedTotal: number,
  currentStatus?: TStatus,
) {
  const [paymentDraft, setPaymentDraft] =
    useState<PaymentDraftState>(EMPTY_PAYMENTS);

  function onPaymentToggle(method: PaymentMethodCode, enabled: boolean): void {
    setPaymentDraft((previous) => ({
      ...previous,
      [method]: {
        ...previous[method],
        enabled,
        amounts: enabled ? previous[method].amounts : [{ amount: "" }],
      },
    }));
  }

  function onPaymentAmountChange(
    method: PaymentMethodCode,
    index: number,
    amount: string,
  ): void {
    setPaymentDraft((previous) => {
      const amounts = [...previous[method].amounts];
      amounts[index] = { ...amounts[index], amount };
      return {
        ...previous,
        [method]: { ...previous[method], amounts },
      };
    });
  }

  function onPaymentAddAmount(method: PaymentMethodCode): void {
    setPaymentDraft((previous) => ({
      ...previous,
      [method]: {
        ...previous[method],
        amounts: [...previous[method].amounts, { amount: "" }],
      },
    }));
  }

  function onPaymentRemoveAmount(method: PaymentMethodCode, index: number): void {
    setPaymentDraft((previous) => {
      const amounts = previous[method].amounts.filter(
        (_, currentIndex) => currentIndex !== index,
      );
      return {
        ...previous,
        [method]: {
          ...previous[method],
          amounts: amounts.length > 0 ? amounts : [{ amount: "" }],
        },
      };
    });
  }

  const paymentTotal = useMemo(() => {
    return (Object.keys(paymentDraft) as PaymentMethodCode[]).reduce(
      (sum, method) => {
        if (!paymentDraft[method].enabled) return sum;
        return sum + paymentDraft[method].amounts.reduce((methodSum, entry) => {
          const amount = Number(entry.amount);
          return methodSum + (Number.isNaN(amount) ? 0 : amount);
        }, 0);
      },
      0,
    );
  }, [paymentDraft]);

  const calculatedStatus = useMemo((): TStatus => {
    if (currentStatus === "CANCELLED") return "CANCELLED" as TStatus;
    if (paymentTotal === 0) return "PENDING" as TStatus;
    if (paymentTotal < calculatedTotal) return "PARTIAL" as TStatus;
    return "PAID" as TStatus;
  }, [paymentTotal, calculatedTotal, currentStatus]);

  const paymentsPayload = useMemo((): PaymentPayload[] => {
    return (Object.keys(paymentDraft) as PaymentMethodCode[])
      .filter((method) => paymentDraft[method].enabled)
      .flatMap((method) =>
        paymentDraft[method].amounts
          .map((entry) => ({
            id: entry.id,
            method,
            amount: Number(entry.amount),
          }))
          .filter((payment) =>
            !Number.isNaN(payment.amount) && payment.amount > 0,
          ),
      );
  }, [paymentDraft]);

  function loadPayments(payments: ExistingPayment[]): void {
    setPaymentDraft(createDraftFromPayments(payments));
  }

  function resetPayments(): void {
    setPaymentDraft(EMPTY_PAYMENTS);
  }

  return {
    paymentDraft,
    paymentTotal,
    calculatedStatus,
    paymentsPayload,
    onPaymentToggle,
    onPaymentAmountChange,
    onPaymentAddAmount,
    onPaymentRemoveAmount,
    loadPayments,
    resetPayments,
  };
}
