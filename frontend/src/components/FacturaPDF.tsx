import type { ReactElement } from "react";
import { createPortal } from "react-dom";
import "./FacturaPDF.css";
import type { Sale } from "../api/sales";
import type { Client } from "../api/clients";
import type { Product } from "../api/products";

interface FacturaPDFProps {
    sale: Sale;
    client: Client | undefined;
    productsById: Map<string, Product>;
}

function mapPaymentMethod(method: string): string {
    if (method === "CASH") return "Efectivo";
    if (method === "SINPE") return "SINPE";
    if (method === "TRANSFER") return "Transferencia";
    if (method === "CARD") return "Tarjeta";
    return method;
}

function mapStatus(status: string): string {
    if (status === "PAID") return "PAGADA";
    if (status === "PARTIAL") return "PARCIAL";
    if (status === "PENDING") return "PENDIENTE";
    if (status === "CANCELLED") return "CANCELADA";
    return status;
}

export default function FacturaPDF({ sale, client, productsById }: FacturaPDFProps): ReactElement {
    const timestamp = new Date(sale.createdAt).getTime();
const fechaObj = new Date(timestamp < 1e12 ? timestamp * 1000 : timestamp);
const fecha = fechaObj.toLocaleDateString('es-CR');
const hora = fechaObj.toLocaleTimeString('es-CR');const totalCantidad = sale.details.reduce((sum, d) => sum + d.quantity, 0);

    const factura = (
        <div className="factura-pdf" id="factura-pdf-content">
            <p className="factura-gracias">¡Gracias por su preferencia!</p>

            <div className="factura-header">
                <div>
                    <span className="factura-numero">{sale.invoiceNumber}</span>
                </div>
                <div className="factura-fecha">
                    <span>{fecha}</span>
                    <span>{hora}</span>
                </div>
            </div>

            <p className="factura-label">Cliente:</p>
            <p className="factura-cliente">{client?.name ?? "—"}</p>

            <div className="factura-divider"></div>

            <table className="factura-tabla-productos">
                <thead>
                    <tr>
                        <th className="factura-col-cant">CANT.</th>
                        <th className="factura-col-desc">DESCRIPCION</th>
                        <th className="factura-col-total">TOTAL ₡</th>
                    </tr>
                </thead>
                <tbody>
                    {sale.details.map((detail) => (
                        <tr key={detail.productId}>
                            <td className="factura-col-cant">{detail.quantity}</td>
                            <td className="factura-col-desc">{productsById.get(detail.productId)?.name ?? detail.productName}</td>
                            <td className="factura-col-total">{Number(detail.subtotal).toLocaleString('es-CR')}</td>
                        </tr>
                    ))}
                </tbody>
            </table>

            <div className="factura-divider"></div>
            <p className="factura-total-cant">{totalCantidad}</p>
            <div className="factura-divider"></div>

            <table className="factura-tabla-pagos">
                <tbody>
                    {(sale.payments ?? []).map((payment) => (
                        <tr key={payment.id}>
                            <td>{mapPaymentMethod(payment.method)}</td>
                            <td className="factura-derecha">₡{Number(payment.amount).toLocaleString('es-CR')}</td>
                        </tr>
                    ))}
                </tbody>
            </table>

            <div className="factura-divider"></div>

            <table className="factura-tabla-total">
                <tbody>
                    <tr>
                        <td><strong>T O T A L</strong></td>
                        <td className="factura-derecha"><strong>₡{Number(sale.total).toLocaleString('es-CR')}</strong></td>
                    </tr>
                </tbody>
            </table>

            <div className="factura-divider"></div>
            <p className="factura-estado">Estado: {mapStatus(sale.status)}</p>
        </div>
    );

    return createPortal(factura, document.body) as ReactElement;
}