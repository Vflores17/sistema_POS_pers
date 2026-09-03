import type { Client } from "../api/clients";
import { getProductPrices, type Product } from "../api/products";
import { isGloballyReportedError, notifyGlobalError } from "../api/errors";
import type { PaymentMethodCode } from "../hooks/useSalePayments";

export interface SaleLineDraft {
  id: string;
  productId: string;
  quantity: number;
  unitPrice: string;
}

export function getSessionUserLabel(): string {
  const token = localStorage.getItem("token");
  if (!token) return "Usuario de sesión";
  try {
    const payload = JSON.parse(atob(token.split(".")[1]));
    return payload.sub ?? "Usuario de sesión";
  } catch {
    return "Usuario de sesión";
  }
}

export function filterClientsByName(clients: Client[], search: string): Client[] {
  const term = search.trim().toLowerCase();
  if (!term) return clients;
  return clients.filter((client) => client.name.toLowerCase().includes(term));
}

export function calculateLinesTotal(
  lines: SaleLineDraft[],
  productsById: Map<string, Product>,
): number {
  return lines.reduce((sum, line) => {
    const product = productsById.get(line.productId);
    const price = line.unitPrice !== ""
      ? Number(line.unitPrice)
      : product
        ? Number(product.price)
        : 0;
    return sum + price * line.quantity;
  }, 0);
}

export async function resolveConfiguredUnitPrice(
  productId: string,
  clientId: string,
  clientsById: Map<string, Client>,
): Promise<string> {
  const client = clientsById.get(clientId);
  const priceType = client?.type === "WHOLESALE"
    ? "WHOLESALE"
    : client?.type === "NEW"
      ? "NEW"
      : "DETAIL";

  try {
    const prices = await getProductPrices(productId);
    const match = prices.find((price) => price.type === priceType);
    if (!match) throw new Error("No existe precio para el tipo de cliente.");
    return String(match.price);
  } catch (error) {
    if (!isGloballyReportedError(error)) {
      notifyGlobalError(readSalePageError(error, "No se pudo obtener el precio del producto."));
    }
    throw error;
  }
}

export function replaceLineProduct(
  lines: SaleLineDraft[],
  lineId: string,
  productId: string,
  unitPrice: string,
): SaleLineDraft[] {
  return lines.map((line) =>
    line.id === lineId ? { ...line, productId, unitPrice } : line,
  );
}

export function replaceLineQuantity(
  lines: SaleLineDraft[],
  lineId: string,
  quantity: string,
): SaleLineDraft[] {
  const parsed = Number(quantity);
  return lines.map((line) =>
    line.id === lineId
      ? {
          ...line,
          quantity: Number.isNaN(parsed) ? 0 : Math.max(0, parsed),
        }
      : line,
  );
}

export function insertEmptyLine(
  lines: SaleLineDraft[],
  selectedRowId: string,
  newLine: SaleLineDraft,
): SaleLineDraft[] {
  if (!selectedRowId) return [...lines, newLine];
  const index = lines.findIndex((line) => line.id === selectedRowId);
  const updated = [...lines];
  updated.splice(index + 1, 0, newLine);
  return updated;
}

export function removeLine(
  lines: SaleLineDraft[],
  selectedRowId: string,
): SaleLineDraft[] {
  const remaining = lines.filter((line) => line.id !== selectedRowId);
  return remaining.length > 0
    ? remaining
    : [{
        id: crypto.randomUUID(),
        productId: "",
        quantity: 1,
        unitPrice: "",
      }];
}

export function replaceLinePrice(
  lines: SaleLineDraft[],
  lineId: string,
  price: string,
): SaleLineDraft[] {
  return lines.map((line) =>
    line.id === lineId ? { ...line, unitPrice: price } : line,
  );
}

export function mapSalePaymentMethod(paymentMethod: PaymentMethodCode): string {
  if (paymentMethod === "SINPE") return "SINPE";
  if (paymentMethod === "TRANSFER") return "Transferencia";
  return "Efectivo";
}

export function readSalePageError(error: unknown, fallback: string): string {
  if (isGloballyReportedError(error)) return "";
  if (error instanceof Error && error.message.trim().length > 0) {
    return error.message;
  }
  return fallback;
}
