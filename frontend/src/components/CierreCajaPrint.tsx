import type { ReactElement } from "react";
import { createPortal } from "react-dom";
import "./CierreCajaPrint.css";

interface CierreCajaData {
    horaInicio: string;
    horaCierre: string;
    montoInicial: number;
    cantidadFacturas: number;
    cantidadAbonosAnteriores: number;
    totalEfectivo: number;
    totalSinpe: number;
    totalTransferencia: number;
    totalTarjeta: number;
    totalGastos: number;      // 👈
    efectivoNeto: number;     // 👈
    total: number;
    gastos: { descripcion: string; monto: number }[]; // 👈
}

interface CierreCajaPrintProps {
    data: CierreCajaData;
}

export default function CierreCajaPrint({ data }: CierreCajaPrintProps): ReactElement {
    const ticket = (
        <div className="cierre-ticket">
            <p className="cierre-titulo">CIERRE DE CAJA</p>
            <div className="cierre-divider"></div>

            <table className="cierre-tabla">
                <tbody>
                    <tr>
                        <td>Inicio:</td>
                        <td className="cierre-derecha">{data.horaInicio}</td>
                    </tr>
                    <tr>
                        <td>Cierre:</td>
                        <td className="cierre-derecha">{data.horaCierre}</td>
                    </tr>
                    <tr>
                        <td>Monto inicial:</td>
                        <td className="cierre-derecha">₡{data.montoInicial.toLocaleString('es-CR')}</td>
                    </tr>
                </tbody>
            </table>

            <div className="cierre-divider"></div>

            <table className="cierre-tabla">
                <tbody>
                    <tr>
                        <td>Facturas:</td>
                        <td className="cierre-derecha">{data.cantidadFacturas}</td>
                    </tr>
                    <tr>
                        <td>Abonos fact. anteriores:</td>
                        <td className="cierre-derecha">{data.cantidadAbonosAnteriores}</td>
                    </tr>
                </tbody>
            </table>

            <div className="cierre-divider"></div>

            <table className="cierre-tabla">
                <tbody>
                    <tr>
                        <td>Efectivo:</td>
                        <td className="cierre-derecha">₡{data.totalEfectivo.toLocaleString('es-CR')}</td>
                    </tr>
                    <tr>
                        <td>SINPE:</td>
                        <td className="cierre-derecha">₡{data.totalSinpe.toLocaleString('es-CR')}</td>
                    </tr>
                    <tr>
                        <td>Transferencia:</td>
                        <td className="cierre-derecha">₡{data.totalTransferencia.toLocaleString('es-CR')}</td>
                    </tr>
                    <tr>
                        <td>Tarjeta:</td>
                        <td className="cierre-derecha">₡{data.totalTarjeta.toLocaleString('es-CR')}</td>
                    </tr>
                </tbody>
            </table>

            {data.gastos.length > 0 && (
                <>
                    <div className="cierre-divider"></div>
                    <table className="cierre-tabla">
                        <tbody>
                            {data.gastos.map((gasto, index) => (
                                <tr key={index}>
                                    <td>{gasto.descripcion}:</td>
                                    <td className="cierre-derecha">-₡{gasto.monto.toLocaleString('es-CR')}</td>
                                </tr>
                            ))}
                            <tr>
                                <td><strong>Efectivo neto:</strong></td>
                                <td className="cierre-derecha"><strong>₡{data.efectivoNeto.toLocaleString('es-CR')}</strong></td>
                            </tr>
                        </tbody>
                    </table>
                </>
            )}
        </div>
    );

    return createPortal(ticket, document.body) as ReactElement;
}
