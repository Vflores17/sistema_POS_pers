import type { ReactElement } from "react";
import { createPortal } from "react-dom";
import type { Sale } from "../api/sales";
import type { Client } from "../api/clients";
import type { Product } from "../api/products";
import "./TicketPrint.css";

interface TicketPrintProps {
    sale: Sale;
    client: Client | undefined;
    productsById: Map<string, Product>;
    routeTicket?: boolean;
}

function mapStatus(status: string): string {
    if (status === "PAID") return "PAGADA";
    if (status === "PARTIAL") return "PARCIAL";
    if (status === "PENDING") return "PENDIENTE";
    if (status === "CANCELLED") return "CANCELADA";
    return status;
}

function mapPaymentMethod(method: string): string {
    if (method === "CASH") return "Efectivo";
    if (method === "SINPE") return "SINPE";
    if (method === "TRANSFER") return "Transferencia";
    if (method === "CARD") return "Tarjeta";
    return method;
}

export default function TicketPrint({
    sale,
    client,
    productsById,
    routeTicket = false,
}: TicketPrintProps): ReactElement {
    const timestamp = new Date(sale.createdAt).getTime();
    const fecha = new Date(timestamp < 1e12 ? timestamp * 1000 : timestamp);    const fechaStr = fecha.toLocaleDateString('es-CR');
    const horaStr = fecha.toLocaleTimeString('es-CR');
    const totalCantidad = sale.details.reduce((sum, d) => sum + d.quantity, 0);

    const ticket = (
        <div className="ticket">
            <p className="gracias">¡Gracias por su preferencia!</p>

            <div className="header-row">
                <span className="factura-num">{sale.invoiceNumber}</span>
                <span className="fecha-hora">
                    {fechaStr}<br />{horaStr}
                </span>
            </div>

            <p className="cliente">Cliente:</p>
            <p className="cliente-nombre">{client?.name ?? "—"}</p>

            <div className="divider"></div>

            <table className="productos">
                <thead>
                    <tr>
                        <th className="col-cant">CANT.</th>
                        <th className="col-desc">&nbsp;&nbsp;&nbsp;DESCRIPCION</th>
                        <th className="col-total">TOTAL ₡</th>
                    </tr>
                </thead>
                <tbody>
                    {sale.details.map((detail) => (
                        <tr key={detail.productId}>
                            <td className="col-cant">{detail.quantity}</td>
                            <td className="col-desc">{productsById.get(detail.productId)?.name ?? detail.productName}</td>
                            <td className="col-total">{Number(detail.subtotal).toLocaleString('es-CR')}</td>
                        </tr>
                    ))}
                </tbody>
            </table>

            <div className="divider"></div>

            <p className="total-cant">&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;{totalCantidad}</p>

            <div className="divider"></div>

            <table className="pagos">
                <tbody>
                    {(sale.payments ?? []).map((payment) => (
                        <tr key={payment.id}>
                            <td>{mapPaymentMethod(payment.method)}</td>
                            <td className="monto">₡{Number(payment.amount).toLocaleString('es-CR')}</td>
                        </tr>
                    ))}
                </tbody>
            </table>

            <div className="divider"></div>

            <table className="totales">
                <tbody>
                    <tr>
                        <td><strong>T O T A L</strong></td>
                        <td className="monto"><strong>₡{Number(sale.total).toLocaleString('es-CR')}</strong></td>
                    </tr>
                </tbody>
            </table>

            <div className="divider"></div>
{sale.comments && (
  <>
    <div className="divider"></div>
    <p style={{ fontSize: "9pt", fontStyle: "italic" }}>{sale.comments}</p>
  </>
)}
            <p className="estado">Estado: {mapStatus(sale.status)}</p>
            {routeTicket && <div className="espacio-anotaciones" />}
        </div>
    );
    return createPortal(ticket, document.body) as ReactElement;
}