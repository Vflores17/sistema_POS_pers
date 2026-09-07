import * as XLSX from "xlsx";
import type { Client } from "../api/clients";
import type { Product } from "../api/products";
import type { PaymentMethod, Sale, SalePaymentMovement } from "../api/sales";
import type { RouteSale } from "../api/route-sales";
import type { CashRegisterExpense } from "../hooks/useCashRegister";

interface WhatsappExportOptions<TSale> {
  sale: TSale;
  client: Client | undefined;
  productsById: Map<string, Product>;
  phone: string;
  message: string;
}

export function generateCashClosureExcel(
  movements: SalePaymentMovement[],
  _fromTime: number,
  toTime: number,
  clientsById: Map<string, Client>,
  expenses: CashRegisterExpense[],
): void {
  const emptyRow = (): Record<string, string | number> => ({
    Cliente: "", Efectivo: "", "": "", "SINPE/Transferencia": "", Tarjeta: "",
  });
  const rowsBySale = new Map<string, Record<string, string | number>>();
  movements.forEach((payment) => {
    const row = rowsBySale.get(payment.saleId) ?? {
      ...emptyRow(),
      Cliente: clientsById.get(payment.clientId)?.name ?? payment.clientId,
    };
    const amount = Number(payment.amount);
    if (payment.method === "CASH") {
      row.Efectivo = Number(row.Efectivo || 0) + amount;
    } else if (payment.method === "SINPE" || payment.method === "TRANSFER") {
      row["SINPE/Transferencia"] = Number(row["SINPE/Transferencia"] || 0) + amount;
    } else if (payment.method === "CARD") {
      row.Tarjeta = Number(row.Tarjeta || 0) + amount;
    }
    rowsBySale.set(payment.saleId, row);
  });
  const rows = Array.from(rowsBySale.values());
  const totalByMethod = (method: PaymentMethod): number => movements
    .filter((payment) => payment.method === method)
    .reduce((sum, payment) => sum + Number(payment.amount), 0);
  const totalEfectivo = totalByMethod("CASH");
  rows.push(emptyRow());
  const totalRow = rows.length + 1;
  rows.push({ ...emptyRow(), Cliente: "TOTAL EFECTIVO", Efectivo: totalEfectivo });
  rows.push(emptyRow());
  expenses.forEach((expense) => {
    rows.push({ ...emptyRow(), Cliente: expense.descripcion, Efectivo: Math.abs(expense.monto) });
  });
  const totalGastos = expenses.reduce((sum, expense) => sum + expense.monto, 0);
  rows.push(emptyRow());
  rows.push({ ...emptyRow(), Cliente: "EFECTIVO NETO", Efectivo: totalEfectivo - totalGastos });

  const worksheet = XLSX.utils.json_to_sheet(rows);
  if (rowsBySale.size > 0) {
    worksheet[`B${totalRow + 1}`] = {
      t: "n",
      f: `SUM(B2:B${rowsBySale.size + 1})`,
    };
  }
  const workbook = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(workbook, worksheet, "Cierre de Caja");
  XLSX.writeFile(
    workbook,
    `cierre_caja_${new Date(toTime).toLocaleDateString("es-CR").replace(/\//g, "-")}.xlsx`,
  );
}

export function generateSaleWhatsappPdf({
  sale,
  client,
  productsById,
  phone,
  message,
}: WhatsappExportOptions<Sale>): void {
  const fecha = new Date(sale.createdAt).toLocaleDateString("es-CR");
  const hora = new Date(sale.createdAt).toLocaleTimeString("es-CR");
  const totalCantidad = sale.details.reduce((sum, detail) => sum + detail.quantity, 0);
  const detalles = sale.details.map((detail) => `
  <tr style="page-break-inside: avoid;">
    <td style="text-align:center;padding:2mm 0">${detail.quantity}</td>
    <td style="padding:2mm 5mm">${productsById.get(detail.productId)?.name ?? detail.productName}</td>
    <td style="text-align:right;padding:2mm 0">₡${Number(detail.subtotal).toLocaleString("es-CR")}</td>
  </tr>
`).join("");
  const pagos = (sale.payments ?? []).map((payment) => `
    <tr>
      <td>${payment.method === "CASH" ? "Efectivo" : payment.method === "SINPE" ? "SINPE" : payment.method === "TRANSFER" ? "Transferencia" : "Tarjeta"}</td>
      <td style="text-align:right">₡${Number(payment.amount).toLocaleString("es-CR")}</td>
    </tr>
  `).join("");
  const html = `
    <div style="font-family:Arial,sans-serif;font-size:14px;color:black;padding:20mm;width:216mm;box-sizing:border-box">
      <p style="text-align:center;font-weight:bold;font-size:18px;margin-bottom:8mm">Gracias por su preferencia!</p>
      
      <div style="display:flex;justify-content:space-between;margin-bottom:4mm">
        <span style="font-weight:bold;font-size:20px">${sale.invoiceNumber}</span>
        <span style="text-align:right;font-size:13px">${fecha}<br/>${hora}</span>
      </div>

      <p style="margin:2mm 0 0 0;font-size:13px">Cliente:</p>
      <p style="margin:0 0 4mm 0;font-size:16px;font-weight:bold">${client?.name ?? "—"}</p>

      <hr style="border:none;border-top:1px dashed black;margin:3mm 0"/>

      <table style="width:100%;border-collapse:collapse">
        <thead>
          <tr style="border-bottom:1px dashed black">
            <th style="text-align:center;font-size:13px;padding-bottom:2mm;width:20mm">CANT.</th>
            <th style="text-align:left;font-size:13px;padding-bottom:2mm;padding-left:5mm">DESCRIPCION</th>
            <th style="text-align:right;font-size:13px;padding-bottom:2mm;width:35mm">TOTAL ₡</th>
          </tr>
        </thead>
        <tbody>${detalles}</tbody>
      </table>

      <hr style="border:none;border-top:1px dashed black;margin:3mm 0"/>
      <p style="font-weight:bold;margin:1mm 0">${totalCantidad}</p>
      <hr style="border:none;border-top:1px dashed black;margin:3mm 0"/>

      <table style="width:100%;border-collapse:collapse;margin:1mm 0">
        <tbody>${pagos}</tbody>
      </table>

      <hr style="border:none;border-top:1px dashed black;margin:3mm 0"/>

      <table style="width:100%;border-collapse:collapse;margin:1mm 0">
        <tbody>
          <tr>
            <td style="font-size:16px"><strong>T O T A L</strong></td>
            <td style="text-align:right;font-size:16px"><strong>₡${Number(sale.total).toLocaleString("es-CR")}</strong></td>
          </tr>
        </tbody>
      </table>

      <hr style="border:none;border-top:1px dashed black;margin:3mm 0"/>
      <p style="text-align:center;font-weight:bold;font-size:16px;margin-top:5mm">Estado: ${sale.status === "PAID" ? "PAGADA" : sale.status === "PARTIAL" ? "PARCIAL" : sale.status === "PENDING" ? "PENDIENTE" : "CANCELADA"}</p>
    </div>
  `;
  const clientName = (client?.name ?? "Cliente").replace(/[\\/:*?"<>|]/g, "").trim();
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const html2pdf = (window as any).html2pdf;

  html2pdf()
    .set({
      margin: 0,
      filename: `${clientName}_#${sale.invoiceNumber}.pdf`,
      image: { type: "jpeg", quality: 0.98 },
      html2canvas: { scale: 2 },
      jsPDF: { unit: "mm", format: "letter", orientation: "portrait" },
      pagebreak: { mode: ["avoid-all", "css", "legacy"] },
    })
    .from(html)
    .save()
    .then(() => {
      const phoneFormatted = phone.replace(/\D/g, "");
      window.open(
        `https://wa.me/506${phoneFormatted}?text=${encodeURIComponent(message)}`,
        "_blank",
      );
    });
}

export function generateRouteSaleWhatsappPdf({
  sale,
  client,
  productsById,
  phone,
  message,
}: WhatsappExportOptions<RouteSale>): void {
  const fecha = new Date(sale.createdAt).toLocaleDateString("es-CR");
  const hora = new Date(sale.createdAt).toLocaleTimeString("es-CR");
  const totalCantidad = sale.details.reduce((sum, detail) => sum + detail.quantity, 0);
  const detalles = sale.details.map((detail) => `
  <tr style="page-break-inside: avoid;">
    <td style="text-align:center;padding:2mm 0">${detail.quantity}</td>
    <td style="padding:2mm 5mm">${productsById.get(detail.productId)?.name ?? detail.productName}</td>
    <td style="text-align:right;padding:2mm 0">₡${Number(detail.subtotal).toLocaleString("es-CR")}</td>
  </tr>
`).join("");
  const pagos = (sale.payments ?? []).map((payment) => `
    <tr>
      <td>${payment.method === "CASH" ? "Efectivo" : payment.method === "SINPE" ? "SINPE" : payment.method === "TRANSFER" ? "Transferencia" : "Tarjeta"}</td>
      <td style="text-align:right">₡${Number(payment.amount).toLocaleString("es-CR")}</td>
    </tr>
  `).join("");
  const html = `
    <div style="font-family:Arial,sans-serif;font-size:14px;color:black;padding:20mm;width:216mm;box-sizing:border-box">
      <p style="text-align:center;font-weight:bold;font-size:18px;margin-bottom:8mm">Gracias por su preferencia!</p>
      <div style="display:flex;justify-content:space-between;margin-bottom:4mm">
        <span style="font-weight:bold;font-size:20px">R-${String(sale.invoiceNumber).padStart(3, "0")}</span>
        <span style="text-align:right;font-size:13px">${fecha}<br/>${hora}</span>
      </div>
      <p style="margin:2mm 0 0 0;font-size:13px">Cliente:</p>
      <p style="margin:0 0 4mm 0;font-size:16px;font-weight:bold">${client?.name ?? "—"}</p>
      <hr style="border:none;border-top:1px dashed black;margin:3mm 0"/>
      <table style="width:100%;border-collapse:collapse">
        <thead>
          <tr style="border-bottom:1px dashed black">
            <th style="text-align:center;font-size:13px;padding-bottom:2mm;width:20mm">CANT.</th>
            <th style="text-align:left;font-size:13px;padding-bottom:2mm;padding-left:5mm">DESCRIPCION</th>
            <th style="text-align:right;font-size:13px;padding-bottom:2mm;width:35mm">TOTAL ₡</th>
          </tr>
        </thead>
        <tbody>${detalles}</tbody>
      </table>
      <hr style="border:none;border-top:1px dashed black;margin:3mm 0"/>
      <p style="font-weight:bold;margin:1mm 0">${totalCantidad}</p>
      <hr style="border:none;border-top:1px dashed black;margin:3mm 0"/>
      <table style="width:100%;border-collapse:collapse;margin:1mm 0">
        <tbody>${pagos}</tbody>
      </table>
      <hr style="border:none;border-top:1px dashed black;margin:3mm 0"/>
      <table style="width:100%;border-collapse:collapse;margin:1mm 0">
        <tbody>
          <tr>
            <td style="font-size:16px"><strong>T O T A L</strong></td>
            <td style="text-align:right;font-size:16px"><strong>₡${Number(sale.total).toLocaleString("es-CR")}</strong></td>
          </tr>
        </tbody>
      </table>
      <hr style="border:none;border-top:1px dashed black;margin:3mm 0"/>
      <p style="text-align:center;font-weight:bold;font-size:16px;margin-top:5mm">Estado: ${sale.status === "PAID" ? "PAGADA" : sale.status === "PENDING" ? "PENDIENTE" : "CANCELADA"}</p>
    </div>
  `;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const html2pdf = (window as any).html2pdf;
  html2pdf()
    .set({
      margin: 0,
      filename: `${client?.name ?? "cliente"}_${sale.invoiceNumber}.pdf`,
      image: { type: "jpeg", quality: 0.98 },
      html2canvas: { scale: 2 },
      jsPDF: { unit: "mm", format: "letter", orientation: "portrait" },
      pagebreak: { mode: ["avoid-all", "css", "legacy"] },
    })
    .from(html)
    .save()
    .then(() => {
      const phoneFormatted = phone.replace(/\D/g, "");
      window.open(
        `https://wa.me/506${phoneFormatted}?text=${encodeURIComponent(message)}`,
        "_blank",
      );
    });
}
