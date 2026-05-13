import type { ChangeEvent, ReactElement } from "react";
import { useEffect, useMemo, useState, useRef } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { listClients, type Client } from "../api/clients";
import {
  changeSaleStatus,
  createSale,
  deleteSale,
  getNextInvoiceNumber,
  getSaleById,
  listSales,
  updateSale,
  type PaymentMethod,
  type Sale,
  type SaleStatus,
  savePayments,
} from "../api/sales";
import styles from "./Sales.module.css";
import Modal from "../components/Modal";
import { listProducts, createProduct, createProductPrice, getProductPrices, type Product } from "../api/products";
import TicketPrint from "../components/TicketPrint";
import CierreCajaPrint from "../components/CierreCajaPrint";
import * as XLSX from 'xlsx';

type SortBy = "invoiceNumber" | "createdAt" | "client" | "total";
type StatusFilter = "ALL" | SaleStatus;

interface LineDraft {
  id: string;
  productId: string;
  quantity: number;
  unitPrice: string;
}

interface ModalState {
  show: boolean;
  type: "success" | "error" | "confirm" | "warning";
  title: string;
  message: string;
  onConfirm?: () => void;
  onCancel?: () => void;
  confirmLabel?: string;
  cancelLabel?: string;
  danger?: boolean;
}

interface SaleFormDraft {
  clientId: string;
  paymentMethod: PaymentMethod;
  lines: LineDraft[];
  comments: string;
  status?: SaleStatus; // 👈
}

const EMPTY_FORM: SaleFormDraft = {
  clientId: "",
  paymentMethod: "CASH",
  lines: [{ id: crypto.randomUUID(), productId: "", quantity: 1, unitPrice: "" }],
  comments: "",
};

interface PaymentDraft {
  enabled: boolean;
  amount: string;
}

type PaymentDraftState = Record<PaymentMethod, PaymentDraft>;

const EMPTY_PAYMENTS: PaymentDraftState = {
  CASH: { enabled: false, amount: "" },
  SINPE: { enabled: false, amount: "" },
  TRANSFER: { enabled: false, amount: "" },
  CARD: { enabled: false, amount: "" },
};

interface CajaState {
  abierta: boolean;
  montoInicial: number;
  horaInicio: string;
  facturaIds: string[];
  pagos: { facturaId: string; method: string; amount: number }[];
  gastos: { id: string; descripcion: string; monto: number }[]; // 👈

}

const CAJA_STORAGE_KEY = "caja_state";

function loadCaja(): CajaState {
  try {
    const stored = localStorage.getItem(CAJA_STORAGE_KEY);
    if (stored) {
      const parsed = JSON.parse(stored) as CajaState;
      return {
        ...parsed,
        pagos: parsed.pagos ?? [],
        gastos: parsed.gastos ?? [] // 👈
      };
    }
  } catch {
    // si falla retorna cerrada
  }
  return { abierta: false, montoInicial: 0, horaInicio: "", facturaIds: [], pagos: [], gastos: [] };
}

function saveCaja(caja: CajaState): void {
  localStorage.setItem(CAJA_STORAGE_KEY, JSON.stringify(caja));
}

export default function Sales(): ReactElement {
  const [caja, setCaja] = useState<CajaState>(loadCaja);
  const [showAbrirCajaModal, setShowAbrirCajaModal] = useState<boolean>(false);
  const [montoInicialDraft, setMontoInicialDraft] = useState<string>("30000");
  const [modal, setModal] = useState<ModalState>({ show: false, type: "success", title: "", message: "" });

  const [productModalIndex, setProductModalIndex] = useState<number>(-1);
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();
  const isNewScreen = window.location.pathname === "/sales/new";
  const isEditScreen = window.location.pathname.endsWith("/edit");
  const isViewScreen = window.location.pathname.endsWith("/view");
  const isFormScreen = isNewScreen || isEditScreen || isViewScreen;

  const selectedRowRef = useRef<HTMLTableRowElement | null>(null);
  const cellRefs = useRef<Record<string, HTMLInputElement | null>>({});

  const [sales, setSales] = useState<Sale[]>([]);
  const [clients, setClients] = useState<Client[]>([]);
  const [saleTotal, setSaleTotal] = useState<number>(0);
  const [products, setProducts] = useState<Product[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [saving, setSaving] = useState<boolean>(false);
  const [error, setError] = useState<string>("");
  const [saleDraft, setSaleDraft] = useState<SaleFormDraft>(EMPTY_FORM);
  const [invoiceNumber, setInvoiceNumber] = useState<number>(0);
  const [selectedRowId, setSelectedRowId] = useState<string>("");
  const [showProductModal, setShowProductModal] = useState<boolean>(false);
  const [clientSearch, setClientSearch] = useState<string>("");
  const [sortBy, setSortBy] = useState<SortBy>("createdAt");
  const [statusFilter, setStatusFilter] = useState<StatusFilter>("ALL");
  const [sortDir, setSortDir] = useState<"asc" | "desc">("desc");
  const [showClientDropdown, setShowClientDropdown] = useState<boolean>(false);
  const [showCreateProductModal, setShowCreateProductModal] = useState<boolean>(false);
  const [productModalSearch, setProductModalSearch] = useState<string>("");

  const [productDraft, setProductDraft] = useState<ProductDraft>({
    name: "", description: "", stock: "0",
    priceDetail: "0",
    priceWholesale: "0",
    priceNew: "0",
  });
  const [lineSearch, setLineSearch] = useState<Record<string, string>>({});
  const [activeLineId, setActiveLineId] = useState<string>("");
  const [activeCell, setActiveCell] = useState<{ rowId: string; col: "name" | "quantity" | "price" } | null>(null);

  const productsById = useMemo(() => {
    return new Map(products.map((product) => [product.id, product]));
  }, [products]);

  const [clientDropdownIndex, setClientDropdownIndex] = useState<number>(-1);

  const [viewProduct, setViewProduct] = useState<Product | null>(null);

  const [viewProductPrices, setViewProductPrices] = useState<{ type: string; price: number }[]>([]);
  const [dateFrom, setDateFrom] = useState<string>("");
  const [dateTo, setDateTo] = useState<string>("");
  const [saleToPrint, setSaleToPrint] = useState<Sale | null>(null);

  const [showGastosModal, setShowGastosModal] = useState<boolean>(false);
  const [gastoDraft, setGastoDraft] = useState<{ descripcion: string; monto: string }>({ descripcion: "", monto: "" });

  const [cierreToPrint, setCierreToPrint] = useState<{
    horaInicio: string;
    horaCierre: string;
    montoInicial: number;
    cantidadFacturas: number;
    totalEfectivo: number;
    totalSinpe: number;
    totalTransferencia: number;
    totalTarjeta: number;
    totalGastos: number;
    efectivoNeto: number;
    total: number;
    gastos: { descripcion: string; monto: number }[]; // 👈
  } | null>(null);

  function abrirCaja(): void {
    if (!montoInicialDraft) {
      setModal({
        show: true,
        type: "error",
        title: "Error",
        message: "Ingresa el monto inicial de efectivo.",
        confirmLabel: "Aceptar",
        onConfirm: closeModal,
      });
      return;
    }
    const nuevaCaja: CajaState = {
      abierta: true,
      montoInicial: Number(montoInicialDraft),
      horaInicio: new Date().toLocaleString('es-CR'),
      facturaIds: [],
      pagos: [],
      gastos: [], // 👈
    };
    setCaja(nuevaCaja);
    saveCaja(nuevaCaja);
    setMontoInicialDraft("");
    setShowAbrirCajaModal(false);
  }

  function cerrarCaja(): void {
    const facturasDeTurno = sales.filter((sale) => caja.facturaIds.includes(sale.id));

    const totalEfectivo = caja.pagos
      .filter(p => p.method === "CASH")
      .reduce((sum, p) => sum + p.amount, 0);

    const totalSinpe = caja.pagos
      .filter(p => p.method === "SINPE")
      .reduce((sum, p) => sum + p.amount, 0);

    const totalTransferencia = caja.pagos
      .filter(p => p.method === "TRANSFER")
      .reduce((sum, p) => sum + p.amount, 0);

    const totalTarjeta = caja.pagos
      .filter(p => p.method === "CARD")
      .reduce((sum, p) => sum + p.amount, 0);
    const horaInicio = caja.horaInicio;
    const totalGastos = caja.gastos.reduce((sum, g) => sum + g.monto, 0);
    const efectivoNeto = totalEfectivo - totalGastos;

    const mensaje = `
🕐 Inicio: ${horaInicio}
💵 Monto inicial: ₡${caja.montoInicial.toLocaleString('es-CR')}

📋 Facturas del turno: ${facturasDeTurno.length}

💰 Efectivo: ₡${totalEfectivo.toLocaleString('es-CR')}
📱 SINPE: ₡${totalSinpe.toLocaleString('es-CR')}
🏦 Transferencia: ₡${totalTransferencia.toLocaleString('es-CR')}
💳 Tarjeta: ₡${totalTarjeta.toLocaleString('es-CR')}

🧾 Gastos: ₡${totalGastos.toLocaleString('es-CR')}
💵 Efectivo neto: ₡${efectivoNeto.toLocaleString('es-CR')}

⚠️ Recuerde vaciar la memoria del datáfono.
`.trim();

    setModal({
      show: true,
      type: "success",
      title: "Cierre de Caja",
      message: mensaje,
      confirmLabel: "Imprimir y Cerrar",
      cancelLabel: "Cancelar",
      onConfirm: () => {
        closeModal();
        generarExcelCierre(); // 👈 descargar Excel


        // 👈 guardar datos para imprimir
        setCierreToPrint({
          horaInicio: caja.horaInicio,
          horaCierre: new Date().toLocaleString('es-CR'),
          montoInicial: caja.montoInicial,
          cantidadFacturas: facturasDeTurno.length,
          totalEfectivo,
          totalSinpe,
          totalTransferencia,
          totalTarjeta,
          totalGastos,        // 👈
          efectivoNeto,       // 👈
          total: totalEfectivo + totalSinpe + totalTransferencia + totalTarjeta,
          gastos: caja.gastos, // 👈
        });

        setTimeout(() => {
          window.print();
          const cajaCerrada: CajaState = {
            abierta: false,
            montoInicial: 0,
            horaInicio: "",
            facturaIds: [],
            pagos: [],
            gastos: []
          };
          setCaja(cajaCerrada);
          saveCaja(cajaCerrada);
        }, 300);
      },
      onCancel: closeModal,
    });
  }

  useEffect(() => {
    if (!isFormScreen) return;


    function handleKeyDown(e: KeyboardEvent): void {

      if (showCreateProductModal || showProductModal || modal.show) return;
      // F2 → Crear producto
      if (e.key === "F2") {
        e.preventDefault();
        setProductDraft({ name: "", description: "", stock: "0", priceDetail: "0", priceWholesale: "0", priceNew: "0" }); // 👈
        setShowCreateProductModal(true);
      }
      // F3 → Listar productos
      if (e.key === "F3") {
        e.preventDefault();
        setShowProductModal(true);
      }
      // F4 → Ver producto
      // F4 → Ver producto
      if (e.key === "F4") {
        e.preventDefault();
        const line = saleDraft.lines.find((item) => item.id === selectedRowId);
        const product = line ? productsById.get(line.productId) : undefined;
        if (product) {
          setViewProduct(product);
          // 👈 cargar precios
          getProductPrices(product.id).then(setViewProductPrices).catch(() => setViewProductPrices([]));
        } else {
          setError("Selecciona una fila con producto.");
        }
      }
      // F5 → Agregar fila
      if (e.key === "F5") {
        e.preventDefault();
        addEmptyRow();
      }
      // F6 → Eliminar fila
      if (e.key === "F6") {
        e.preventDefault();
        removeSelectedRow();
      }
      // Alt + A → Guardar
      if (e.altKey && e.key.toLowerCase() === "a") {
        e.preventDefault();
        void onSave(false);
      }
      // Alt + M → Guardar e Imprimir
      if (e.altKey && e.key.toLowerCase() === "m") {
        e.preventDefault();
        void onSave(true);
      }
      // Alt + S → Salir
      if (e.altKey && e.code === "KeyS") {
        e.preventDefault();
        if (hasUnsavedChanges()) {
          setModal({
            show: true,
            type: "warning",
            danger: true,
            title: "¿Salir sin guardar?",
            message: "La factura tiene cambios que no se han guardado. ¿Estás seguro que deseas salir?",
            confirmLabel: "Salir",
            cancelLabel: "Cancelar",
            onConfirm: () => {
              closeModal();
              navigate("/sales");
            },
            onCancel: closeModal,
          });
        } else {
          navigate("/sales");
        }
      }
      // Alt + P → Método de pago
      if (e.altKey && e.key.toLowerCase() === "p") {
        e.preventDefault();
        const select = document.querySelector<HTMLSelectElement>("select[value]");
        select?.focus();
      }
      // Alt + B → Buscar cliente
      if (e.altKey && e.key.toLowerCase() === "b") {
        e.preventDefault();
        const input = document.querySelector<HTMLInputElement>("input[placeholder='Buscar cliente...']");
        input?.focus();
      }
      if (activeCell) {

        const lines = saleDraft.lines;
        const currentRowIndex = lines.findIndex((line) => line.id === activeCell.rowId);
        const cols: Array<"name" | "quantity" | "price"> = ["name", "quantity", "price"];
        const currentColIndex = cols.indexOf(activeCell.col);


        if (e.key === "ArrowRight" && activeCell.col !== "price") {
          e.preventDefault();
          focusCell(activeCell.rowId, cols[currentColIndex + 1]);
        }
        if (e.key === "ArrowLeft" && activeCell.col !== "name") {
          e.preventDefault();
          focusCell(activeCell.rowId, cols[currentColIndex - 1]);
        }
        if (e.key === "Tab") {
          e.preventDefault();
          if (currentColIndex < cols.length - 1) {
            focusCell(activeCell.rowId, cols[currentColIndex + 1]);
          } else {
            const nextRow = lines[currentRowIndex + 1];
            if (nextRow) focusCell(nextRow.id, "name");
          }
        }
      } else if (!isInputFocused) {
        if (e.key === "ArrowDown") {
          e.preventDefault();
          const lines = saleDraft.lines;
          const currentIndex = lines.findIndex((line) => line.id === selectedRowId);
          const nextIndex = Math.min(currentIndex + 1, lines.length - 1);
          setSelectedRowId(lines[nextIndex]?.id ?? "");
        }
        if (e.key === "ArrowUp") {
          e.preventDefault();
          const lines = saleDraft.lines;
          const currentIndex = lines.findIndex((line) => line.id === selectedRowId);
          const prevIndex = Math.max(currentIndex - 1, 0);
          setSelectedRowId(lines[prevIndex]?.id ?? "");
        }
      }

      if (e.key === "Escape" && viewProduct) {
        e.preventDefault();
        setViewProduct(null);
        setViewProductPrices([]);
      }
    }


    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [isFormScreen, saleDraft, selectedRowId, productsById, addEmptyRow, removeSelectedRow, onSave, navigate]);

  useEffect(() => {
    if (!showProductModal) return;

    function handleKeyDown(e: KeyboardEvent): void {
      if (e.key === "Escape") {
        e.preventDefault();
        setShowProductModal(false);
        setProductModalSearch("");
      }

      if (e.key === "Enter") {
        e.preventDefault();
        document.querySelector<HTMLButtonElement>("#saveProductBtn")?.click();
      }
    }

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [showProductModal]);

  useEffect(() => {
    if (!showCreateProductModal) return;

    function handleKeyDown(e: KeyboardEvent): void {
      if (e.key === "Escape") {
        e.preventDefault();
        e.stopPropagation();
        setShowCreateProductModal(false);
      }
      if (e.altKey && e.code === "KeyG") {
        e.preventDefault();
        e.stopPropagation();
        document.querySelector<HTMLButtonElement>("#saveProductBtn")?.click();
      }
    }

    window.addEventListener("keydown", handleKeyDown, true); // 👈 true
    return () => window.removeEventListener("keydown", handleKeyDown, true); // 👈 true
  }, [showCreateProductModal]);

  useEffect(() => {
    void bootstrap();
  }, [isFormScreen, isEditScreen, id]);

  // 👈 aquí
  useEffect(() => {
    if (isFormScreen) return;

    function handleKeyDown(e: KeyboardEvent): void {
      if (modal.show) return;

      if (e.altKey && e.key.toLowerCase() === "c") {
        e.preventDefault();
        if (caja.abierta) navigate("/sales/new");
      }
      if (e.altKey && e.key.toLowerCase() === "o") {
        e.preventDefault();
        if (caja.abierta && selectedRowId) navigate(`/sales/${selectedRowId}/edit`);
      }
      if (e.altKey && e.key.toLowerCase() === "v") {
        e.preventDefault();
        if (caja.abierta && selectedRowId) navigate(`/sales/${selectedRowId}/edit`);
      }
      if (e.altKey && e.key.toLowerCase() === "i") {
        e.preventDefault();
        if (caja.abierta && selectedRowId) {
          const sale = sortedAndFilteredSales.find(s => s.id === selectedRowId);
          if (sale) {
            setSaleToPrint(sale);
            setTimeout(() => window.print(), 300);
          }
        }
      }
      if (e.altKey && e.key.toLowerCase() === "e") {
        e.preventDefault();
        if (caja.abierta && selectedRowId) void onDeleteSale(selectedRowId);
      }
      if (e.altKey && e.key.toLowerCase() === "z") {
        e.preventDefault();
        if (caja.abierta && selectedRowId &&
          (selectedSale?.status === "PENDING" || selectedSale?.status === "PARTIAL")) {
          navigate(`/sales/${selectedRowId}/edit`);
        }
      }
      if (e.altKey && e.key.toLowerCase() === "r") {
        e.preventDefault();
        navigate("/dashboard");
      }
      if (e.altKey && e.key.toLowerCase() === "k") {
        e.preventDefault();
        if (!caja.abierta) setShowAbrirCajaModal(true);
      }
      if (e.altKey && e.key.toLowerCase() === "x") {
        e.preventDefault();
        if (caja.abierta) cerrarCaja();
      }
    }

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [isFormScreen, modal.show, selectedRowId, caja, navigate]);



  useEffect(() => {
    if (!selectedRowRef.current) return;

    const container = document.querySelector(`.${styles.gridScrollArea}`) as HTMLElement;
    if (!container) return;

    const lines = saleDraft.lines;
    const index = lines.findIndex((line) => line.id === selectedRowId);

    if (index === 0) {
      container.scrollTop = 0;
      return;
    }

    const row = selectedRowRef.current;
    const thead = container.querySelector("thead") as HTMLElement;
    const theadHeight = thead ? thead.offsetHeight : 0;
    const rowTop = row.offsetTop - theadHeight;
    const rowBottom = row.offsetTop + row.offsetHeight;
    const containerTop = container.scrollTop;
    const containerBottom = container.scrollTop + container.offsetHeight;

    if (rowTop < containerTop) {
      container.scrollTop = rowTop;
    } else if (rowBottom > containerBottom) {
      container.scrollTop = rowBottom - container.offsetHeight;
    }
  }, [selectedRowId, saleDraft.lines]);

  const sessionUserLabel = useMemo(() => {
    const token = localStorage.getItem("token");
    if (!token) return "Usuario de sesión";
    try {
      const payload = JSON.parse(atob(token.split(".")[1]));
      return payload.sub ?? "Usuario de sesión";
    } catch {
      return "Usuario de sesión";
    }
  }, []);

  const [lineDropdownIndex, setLineDropdownIndex] = useState<Record<string, number>>({});

  const activeElement = document.activeElement?.tagName.toLowerCase();
  const isInputFocused = activeElement === "input" || activeElement === "select" || activeElement === "textarea";


  const clientsById = useMemo(() => {
    return new Map(clients.map((client) => [client.id, client]));
  }, [clients]);

  const filteredClientOptions = useMemo(() => {
    const term = clientSearch.trim().toLowerCase();
    if (!term) {
      return clients;
    }
    return clients.filter((client) => client.name.toLowerCase().includes(term));
  }, [clients, clientSearch]);

  const [paymentDraft, setPaymentDraft] = useState<PaymentDraftState>(EMPTY_PAYMENTS);



  const calculatedTotal = useMemo(() => {
    return saleDraft.lines.reduce((sum, line) => {
      const product = productsById.get(line.productId);
      const price = line.unitPrice !== ""
        ? Number(line.unitPrice)
        : product ? Number(product.price) : 0;
      return sum + price * line.quantity;
    }, 0);
  }, [saleDraft.lines, productsById]);

  function onPaymentToggle(method: PaymentMethod, enabled: boolean): void {
    setPaymentDraft((prev) => ({
      ...prev,
      [method]: { ...prev[method], enabled, amount: enabled ? prev[method].amount : "" },
    }));
  }

  function onPaymentAmountChange(method: PaymentMethod, amount: string): void {
    setPaymentDraft((prev) => ({
      ...prev,
      [method]: { ...prev[method], amount },
    }));
  }

  async function resolveUnitPrice(productId: string, clientId: string): Promise<string> {
    const client = clientsById.get(clientId);
    const priceType = client?.type === "WHOLESALE"
      ? "WHOLESALE"
      : client?.type === "NEW"
        ? "NEW"
        : "DETAIL";

    try {
      const prices = await getProductPrices(productId);
      const match = prices.find((p) => p.type === priceType);
      if (!match) throw new Error("No existe precio para el tipo de cliente.");
      return String(match.price);
    } catch (error) {
      console.error("error en getProductPrices:", error instanceof Error ? error.message : error);
      throw error;
    }
  }
  const paymentTotal = useMemo(() => {
    return (Object.keys(paymentDraft) as PaymentMethod[]).reduce((sum, method) => {
      if (!paymentDraft[method].enabled) return sum;
      const amount = Number(paymentDraft[method].amount);
      return sum + (Number.isNaN(amount) ? 0 : amount);
    }, 0);
  }, [paymentDraft]);
  const [activeDateFrom, setActiveDateFrom] = useState<string>("");
  const [activeDateTo, setActiveDateTo] = useState<string>("");
  const [activeSortBy, setActiveSortBy] = useState<SortBy>("createdAt");
  const [activeSortDir, setActiveSortDir] = useState<"asc" | "desc">("desc");
  const [activeStatusFilter, setActiveStatusFilter] = useState<StatusFilter>("ALL");
  const [amountOperator, setAmountOperator] = useState<">=" | "<=" | "=">(">=");
  const [amountValue, setAmountValue] = useState<string>("");
  const [activeAmountOperator, setActiveAmountOperator] = useState<">=" | "<=" | "=">(">=");
  const [activeAmountValue, setActiveAmountValue] = useState<string>("");
  const [clientFilterSearch, setClientFilterSearch] = useState<string>("");
  const [activeClientId, setActiveClientId] = useState<string>("");
  const [showClientFilterDropdown, setShowClientFilterDropdown] = useState<boolean>(false);
  const [clientFilterDropdownIndex, setClientFilterDropdownIndex] = useState<number>(-1);

  const sortedAndFilteredSales = useMemo(() => {
    let result = sales;
    if (activeStatusFilter !== "ALL") {
      result = result.filter((sale) => sale.status === activeStatusFilter);
    }
    if (activeDateFrom) {
      result = result.filter((sale) => {
        const saleDateCR = new Date(new Date(sale.createdAt).getTime() - 6 * 60 * 60 * 1000);
        return saleDateCR.toISOString().slice(0, 10) >= activeDateFrom;
      });
    }
    if (activeDateTo) {
      result = result.filter((sale) => {
        const saleDateCR = new Date(new Date(sale.createdAt).getTime() - 6 * 60 * 60 * 1000);
        return saleDateCR.toISOString().slice(0, 10) <= activeDateTo;
      });
    }
    if (activeAmountValue) {
      const amount = Number(activeAmountValue);
      result = result.filter((sale) => {
        const total = Number(sale.total);
        if (activeAmountOperator === ">=") return total >= amount;
        if (activeAmountOperator === "<=") return total <= amount;
        return total === amount;
      });
    }
    if (activeClientId) {
      result = result.filter((sale) => sale.clientId === activeClientId);
    }
    return [...result].sort((a, b) => {
      let comparison = 0;
      if (activeSortBy === "invoiceNumber") {
        comparison = a.invoiceNumber - b.invoiceNumber;
      } else if (activeSortBy === "createdAt") {
        comparison = new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime();
      } else if (activeSortBy === "total") {
        comparison = Number(a.total) - Number(b.total);
      } else {
        const aClient = clientsById.get(a.clientId)?.name ?? "";
        const bClient = clientsById.get(b.clientId)?.name ?? "";
        comparison = aClient.localeCompare(bClient);
      }
      return activeSortDir === "asc" ? comparison : -comparison;
    });
  }, [sales, activeSortBy, activeSortDir, activeStatusFilter, clientsById, activeDateFrom, activeDateTo, activeAmountOperator, activeAmountValue, activeClientId]);

  const [dropdownPosition, setDropdownPosition] = useState<{ top: number; left: number } | null>(null);





  interface ProductDraft {
    name: string;
    description: string;
    stock: string;
    priceDetail: string;
    priceWholesale: string;
    priceNew: string;
  }



  function closeModal(): void {
    setModal((prev) => ({ ...prev, show: false }));
  }

  function hasUnsavedChanges(): boolean {
    const hasClient = saleDraft.clientId !== "";
    const hasProducts = saleDraft.lines.some((line) => line.productId !== "");
    return hasClient || hasProducts;
  }

  async function bootstrap(): Promise<void> {
    setLoading(true);
    setError("");
    try {
      const baseData = await Promise.all([listClients(), listProducts()]);
      setClients(baseData[0]);
      setProducts(baseData[1]);
      if (isFormScreen) {
        if ((isEditScreen || isViewScreen) && id) {
          const sale = await getSaleById(id);
          setInvoiceNumber(sale.invoiceNumber);
          setSaleTotal(Number(sale.total));
          setSaleDraft({
            clientId: sale.clientId,
            paymentMethod: sale.paymentMethod,
            lines: sale.details.map((detail) => ({
              id: detail.id ?? crypto.randomUUID(),
              productId: detail.productId,
              quantity: detail.quantity,
              unitPrice: String(detail.price),
            })),
            comments: "",
            status: sale.status,
          });
          const clientName = baseData[0].find((c) => c.id === sale.clientId)?.name ?? "";
          setClientSearch(clientName);

          const nextPayments: PaymentDraftState = {
            CASH: { enabled: false, amount: "" },
            SINPE: { enabled: false, amount: "" },
            TRANSFER: { enabled: false, amount: "" },
            CARD: { enabled: false, amount: "" },
          };
          for (const payment of sale.payments ?? []) {
            nextPayments[payment.method] = {
              enabled: true,
              amount: String(payment.amount),
            };
          }
          setPaymentDraft(nextPayments);
        } else {
          const next = await getNextInvoiceNumber();
          setInvoiceNumber(next);
          setSaleDraft(EMPTY_FORM);
          setClientSearch("");
          setSelectedRowId("");
          setLineSearch({});
          setActiveLineId("");
          setClientDropdownIndex(-1);
          setPaymentDraft(EMPTY_PAYMENTS);

        }
      } else {
        const salesData = await listSales();
        setSales(salesData);
      }
    } catch (err) {
      setError(readError(err, "No se pudo cargar la información de ventas."));
    } finally {
      setLoading(false);
    }
  }


  function onCommentChange(event: ChangeEvent<HTMLTextAreaElement>): void {
    setSaleDraft((prev) => ({ ...prev, comments: event.target.value }));
  }

  async function onLineProductChange(lineId: string, productId: string): Promise<void> {
    console.log("onLineProductChange called", { productId, clientId: saleDraft.clientId });
    if (!productId) {
      setSaleDraft((prev) => ({
        ...prev,
        lines: prev.lines.map((line) =>
          line.id === lineId ? { ...line, productId, unitPrice: "" } : line
        ),
      }));
      return;
    }

    let unitPrice = "";
    if (saleDraft.clientId) {
      try {
        unitPrice = await resolveUnitPrice(productId, saleDraft.clientId);
        console.log("s resolvio el precio");
      } catch {
      }
    }

    setSaleDraft((prev) => ({
      ...prev,
      lines: prev.lines.map((line) =>
        line.id === lineId ? { ...line, productId, unitPrice } : line
      ),
    }));
  }

  function onLineQuantityChange(lineId: string, quantity: string): void {
    const parsed = Number(quantity);
    setSaleDraft((prev) => ({
      ...prev,
      lines: prev.lines.map((line) =>
        line.id === lineId
          ? { ...line, quantity: Number.isNaN(parsed) ? 0 : Math.max(0, parsed) }
          : line
      ),
    }));
  }

  function addEmptyRow(): void {
    const newLine = { id: crypto.randomUUID(), productId: "", quantity: 1, unitPrice: "" };

    if (!selectedRowId) {
      // Si no hay fila seleccionada, agrega al final
      setSaleDraft((prev) => ({
        ...prev,
        lines: [...prev.lines, newLine]
      }));
      return;
    }

    // Inserta después de la fila seleccionada
    setSaleDraft((prev) => {
      const index = prev.lines.findIndex((line) => line.id === selectedRowId);
      const updated = [...prev.lines];
      updated.splice(index + 1, 0, newLine);
      return { ...prev, lines: updated };
    });
  }

  function removeSelectedRow(): void {
    if (!selectedRowId) {
      setError("Selecciona una fila para eliminar.");
      return;
    }



    setSaleDraft((prev) => {
      const remaining = prev.lines.filter((line) => line.id !== selectedRowId);
      return {
        ...prev,
        lines: remaining.length > 0 ? remaining : [{ id: crypto.randomUUID(), productId: "", quantity: 1, unitPrice: "" }],
      };
    });
    setSelectedRowId("");
  }

  function focusCell(rowId: string, col: "name" | "quantity" | "price"): void {
    setActiveCell({ rowId, col });
    setTimeout(() => {
      const key = `${rowId}-${col}`;
      cellRefs.current[key]?.focus();
      cellRefs.current[key]?.select();
    }, 0);
  }

  function onLinePriceChange(lineId: string, price: string): void {
    setSaleDraft((prev) => ({
      ...prev,
      lines: prev.lines.map((line) =>
        line.id === lineId ? { ...line, unitPrice: price } : line
      ),
    }));
  }

  async function addProductFromModal(productId: string): Promise<void> {
    if (!saleDraft.clientId) {
      setError("Selecciona un cliente primero.");
      return;
    }

    let unitPrice = "";
    try {
      unitPrice = await resolveUnitPrice(productId, saleDraft.clientId);
    } catch {
      // si falla deja vacío
    }

    setSaleDraft((prev) => {
      const lines = prev.lines;
      if (lines.length === 1 && lines[0].productId === "") {
        return {
          ...prev,
          lines: [{ id: lines[0].id, productId, quantity: 1, unitPrice }]
        };
      }
      return {
        ...prev,
        lines: [...lines, { id: crypto.randomUUID(), productId, quantity: 1, unitPrice }],
      };
    });
    setShowProductModal(false);
    setProductModalIndex(-1);
  }

  async function onSave(printAfterSave: boolean): Promise<void> {
    setError("");
    if (!saleDraft.clientId) {
      setModal({
        show: true,
        type: "error",
        title: "Error",
        message: "Selecciona un cliente.",
        confirmLabel: "Aceptar",
        onConfirm: closeModal,
      }); return;
    }

    const cleanedLines = saleDraft.lines
      .filter((line) => line.productId.trim().length > 0)
      .map((line) => ({
        productId: line.productId,
        quantity: line.quantity,
        unitPrice: line.unitPrice,
      }));

    if (cleanedLines.length === 0) {
      setModal({
        show: true,
        type: "error",
        title: "Error",
        message: "Debes agregar al menos un producto.",
        confirmLabel: "Aceptar",
        onConfirm: closeModal,
      });
      return;
    }

    if (cleanedLines.some((line) => line.quantity <= 0)) {
      setModal({
        show: true,
        type: "error",
        title: "Error",
        message: "Todas las cantidades deben ser mayores a 0.",
        confirmLabel: "Aceptar",
        onConfirm: closeModal,
      });
      return;
    }

    const paymentsPayload = (Object.keys(paymentDraft) as PaymentMethod[])
      .filter((method) => paymentDraft[method].enabled)
      .map((method) => ({ method, amount: Number(paymentDraft[method].amount) }))
      .filter((payment) => !Number.isNaN(payment.amount) && payment.amount > 0);

    const paid = paymentsPayload.reduce((sum, p) => sum + p.amount, 0);
    const totalToCompare = isEditScreen ? saleTotal : calculatedTotal;


    if (paid > totalToCompare) {
      setModal({
        show: true,
        type: "error",
        title: "Error",
        message: "La suma de pagos no puede superar el total de la factura.",
        confirmLabel: "Aceptar",
        onConfirm: closeModal,
      });
      return;
    }
    setSaving(true);
    try {
      const payload = {
        clientId: saleDraft.clientId,
        paymentMethod: saleDraft.paymentMethod,
        items: cleanedLines.map((line) => ({
          productId: line.productId,
          quantity: line.quantity,
          price: line.unitPrice !== "" ? Number(line.unitPrice) : undefined,
        })),
      };
      const saved = isEditScreen && id
        ? await updateSale(id, payload)
        : await createSale(payload);
      if (caja.abierta) {
        const yaExiste = caja.facturaIds.includes(saved.id);
        const tienePagos = paymentsPayload.length > 0;
        if (!isEditScreen || tienePagos) {
          const nuevosPagos = paymentsPayload.map(p => ({
            facturaId: saved.id,
            method: p.method,
            amount: p.amount,
          }));
          const updatedCaja = {
            ...caja,
            facturaIds: yaExiste ? caja.facturaIds : [...caja.facturaIds, saved.id],
            // 👈 eliminar pagos anteriores de esta factura antes de agregar nuevos
            pagos: [...caja.pagos.filter(p => p.facturaId !== saved.id), ...nuevosPagos],
          };
          setCaja(updatedCaja);
          saveCaja(updatedCaja);
        }
      }
      if (paymentsPayload.length > 0) {
        await savePayments(saved.id, paymentsPayload);
      }
      if (!isEditScreen && saleDraft.status && saleDraft.status !== "PENDING") {
        await changeSaleStatus(saved.id, saleDraft.status);
      }

      if (printAfterSave) {
        setSaleToPrint(saved);
        setTimeout(() => window.print(), 300);
      }
      // Resetear formulario para nueva factura
      const next = await getNextInvoiceNumber();
      setInvoiceNumber(next);
      setSaleDraft(EMPTY_FORM);
      setClientSearch("");
      setSelectedRowId("");
      setLineSearch({});
      setPaymentDraft(EMPTY_PAYMENTS);
      setPaymentDraft(EMPTY_PAYMENTS);


      setModal({
        show: true,
        type: "success",
        title: isEditScreen ? "Factura modificada" : "Factura creada",
        message: isEditScreen
          ? "La factura fue modificada correctamente."
          : "La factura fue creada correctamente.",
        onConfirm: () => {
          closeModal();
          if (isEditScreen) navigate("/sales"); // 👈
        },
        confirmLabel: "Aceptar",
      });

    } catch (err) {
      setError(readError(err, "No se pudo guardar la venta."));
    } finally {
      setSaving(false);
    }
  }

  async function onDeleteSale(saleId: string): Promise<void> {
    setModal({
      show: true,
      type: "confirm",
      danger: true,
      title: "Eliminar factura",
      message: "¿Estás seguro que deseas eliminar esta factura? Esta acción no se puede deshacer.",
      confirmLabel: "Eliminar",
      cancelLabel: "Cancelar",
      onConfirm: async () => {
        closeModal();
        try {
          await deleteSale(saleId);
          setSales((prev) => prev.filter((sale) => sale.id !== saleId));
        } catch (err) {
          setError(readError(err, "No se pudo eliminar la venta."));
        }
      },
      onCancel: closeModal,
    });
  }

  if (loading) {
    return <section className={styles.page}>Cargando ventas...</section>;
  }

  if (isFormScreen) {
    return (
      <div style={{
        background: "#f0f4f0",
        minHeight: "90vh",
        display: "flex",
        justifyContent: "center",
        alignItems: "flex-start",
        padding: "1rem"
      }}>        <section className={styles.container}>
          <header className={styles.header}>
            <h2 className={styles.title}>
              {isEditScreen ? "Modificar Venta" : isViewScreen ? "Visualización de Factura" : "Nueva Venta"}
            </h2>
          </header>

          {error ? <p className={styles.error}>{error}</p> : null}

          <div className={styles.card}>          <div className={styles.topGrid}>
            <div className={styles.field}>
              <label>Número de factura</label>
              <div className={styles.invoiceNumber}>{invoiceNumber || "—"}</div>
            </div>
            <div className={styles.field}>
              <label><u>B</u>uscar cliente</label>
              <div style={{ position: "relative", zIndex: 50 }}>
                <input
                  value={clientSearch}
                  readOnly={isViewScreen}
                  onChange={(e) => {
                    if (isViewScreen) return;
                    setClientSearch(e.target.value);
                    setSaleDraft((prev) => ({ ...prev, clientId: "" }));
                    setShowClientDropdown(true);
                    setClientDropdownIndex(-1);
                  }}
                  onFocus={() => {
                    if (isViewScreen) return; // 👈
                    setShowClientDropdown(true);
                  }} onBlur={() => setTimeout(() => setShowClientDropdown(false), 200)}
                  onKeyDown={(e) => {
                    if (!showClientDropdown) return;
                    const options = filteredClientOptions.slice(0, 4);
                    if (e.key === "ArrowDown") {
                      e.preventDefault();
                      setClientDropdownIndex((prev) => Math.min(prev + 1, options.length - 1));
                    }
                    if (e.key === "ArrowUp") {
                      e.preventDefault();
                      setClientDropdownIndex((prev) => Math.max(prev - 1, 0));
                    }
                    if (e.key === "Enter" && clientDropdownIndex >= 0) {
                      e.preventDefault();
                      const selected = options[clientDropdownIndex];
                      setSaleDraft((prev) => ({ ...prev, clientId: selected.id }));
                      setClientSearch(selected.name);
                      setShowClientDropdown(false);
                      setClientDropdownIndex(-1);
                    }
                    if (e.key === "Escape") {
                      setShowClientDropdown(false);
                      setClientDropdownIndex(-1);
                    }
                  }}
                  placeholder="Buscar cliente..."
                  autoComplete="off"
                />
                {showClientDropdown && (
                  <div className={styles.clientDropdown}>
                    {filteredClientOptions.slice(0, 4).map((client, index) => (
                      <div
                        key={client.id}
                        className={styles.clientOption}
                        style={index === clientDropdownIndex ? { background: "#d1fae5", color: "#16a34a" } : {}}
                        onMouseDown={() => {
                          setSaleDraft((prev) => ({ ...prev, clientId: client.id }));
                          setClientSearch(client.name);
                          setShowClientDropdown(false);
                          setClientDropdownIndex(-1);
                        }}
                        onMouseEnter={() => setClientDropdownIndex(index)}
                      >
                        {client.name}
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>
            <div className={styles.field} style={{ gridColumn: "span 2" }}>
              <label>Método de <u>P</u>ago</label>
              <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: "0.5rem", marginTop: "0.25rem" }}>
                {([
                  { key: "CASH" as PaymentMethod, label: "Efectivo" },
                  { key: "SINPE" as PaymentMethod, label: "SINPE" },
                  { key: "TRANSFER" as PaymentMethod, label: "Transferencia" },
                  { key: "CARD" as PaymentMethod, label: "Tarjeta" },
                ]).map((item) => (
                  <div key={item.key} style={{ border: "1px solid #e5e7eb", borderRadius: 8, padding: "0.5rem" }}>
                    <label style={{ display: "flex", alignItems: "center", gap: "0.4rem", cursor: "pointer" }}>
                      <input
                        type="checkbox"
                        checked={paymentDraft[item.key].enabled}
                        onChange={(event) => onPaymentToggle(item.key, event.target.checked)}
                        disabled={isViewScreen} // 👈
                      />
                      {item.label}
                    </label>
                    {paymentDraft[item.key].enabled && (
                      <input
                        type="number"
                        min="0"
                        step="0.01"
                        placeholder="Monto"
                        value={paymentDraft[item.key].amount}
                        onChange={(e) => onPaymentAmountChange(item.key, e.target.value)}
                        style={{ width: "100%", marginTop: "0.25rem" }}
                        disabled={isViewScreen}
                      />
                    )}
                  </div>
                ))}
              </div>
              <div style={{ marginTop: "0.5rem", fontSize: "0.9rem", color: "#4b5563" }}>
                Suma pagos: ₡{paymentTotal.toLocaleString('es-CR')} / Total: ₡{calculatedTotal.toLocaleString('es-CR')}
                {paymentTotal > 0 && paymentTotal < calculatedTotal && (
                  <span style={{ color: "#b45309", marginLeft: "0.5rem" }}>
                    ⚠️ Faltante: ₡{(calculatedTotal - paymentTotal).toLocaleString('es-CR')}
                  </span>
                )}
                {paymentTotal > calculatedTotal && (
                  <span style={{ color: "#dc2626", marginLeft: "0.5rem" }}>
                    ❌ Excedente: ₡{(paymentTotal - calculatedTotal).toLocaleString('es-CR')}
                  </span>
                )}
                {paymentTotal > 0 && paymentTotal === calculatedTotal && (
                  <span style={{ color: "#16a34a", marginLeft: "0.5rem" }}>
                    ✅ Pago completo
                  </span>
                )}
              </div>
            </div>
            <div className={styles.field}>
              <label>Vendedor</label>
              <input value={sessionUserLabel} readOnly />
            </div>
            <div className={styles.field}>
              <label>Estado</label>
              <select
                value={saleDraft.status ?? "PENDING"}
                disabled={isViewScreen}
                onChange={async (e) => {

                  const newStatus = e.target.value as SaleStatus;
                  if (isEditScreen && id) {
                    // En edición → llama al backend de inmediato
                    try {
                      await changeSaleStatus(id, newStatus);
                    } catch {
                      setError("No se pudo cambiar el estado.");
                      return;
                    }
                  }
                  // En ambos casos actualiza el estado local
                  setSaleDraft((prev) => ({ ...prev, status: newStatus }));
                }}
              >
                <option value="PENDING">Pendiente</option>
                <option value="PARTIAL">Parciales</option>
                <option value="PAID">Pagada</option>
                <option value="CANCELLED">Cancelada</option>
              </select>
            </div>
          </div>

            {!isViewScreen && (
              <div className={styles.menuBar}>
                <button className={styles.primaryButton} type="button"
                  onClick={() => setShowCreateProductModal(true)}>
                  Crear producto <kbd>F2</kbd>
                </button>
                <button className={styles.button} type="button" onClick={() => setShowProductModal(true)}>
                  Listar productos <kbd>F3</kbd>
                </button>
                <button
                  className={styles.button}
                  type="button"
                  onClick={() => {
                    const line = saleDraft.lines.find((item) => item.id === selectedRowId);
                    const product = line ? productsById.get(line.productId) : undefined;
                    if (product) {
                      setViewProduct(product);
                    } else {
                      setError("Selecciona una fila con producto.");
                    }
                  }}
                >
                  Ver producto <kbd>F4</kbd>
                </button>
                <button className={styles.button} type="button" onClick={addEmptyRow}>
                  Agregar fila <kbd>F5</kbd>
                </button>
                <button className={styles.dangerButton} type="button" onClick={removeSelectedRow}>
                  Eliminar fila <kbd>F6</kbd>
                </button>
              </div>
            )}

            <div className={styles.gridScrollArea}>
              <table className={styles.table}>
                <thead>
                  <tr>
                    <th>Nombre</th>
                    <th>Cantidad</th>
                    <th>Precio unitario</th>
                    <th>Subtotal</th>
                  </tr>
                </thead>
                <tbody>
                  {saleDraft.lines.map((line) => {
                    const product = productsById.get(line.productId);
                    const unitPrice = line.unitPrice !== ""
                      ? Number(line.unitPrice)
                      : product ? Number(product.price) : 0;
                    return (
                      <tr
                        key={line.id}
                        ref={selectedRowId === line.id ? selectedRowRef : null}
                        onClick={() => setSelectedRowId(line.id)}
                        className={selectedRowId === line.id ? styles.selected : ""}
                        style={{ cursor: "pointer" }}
                      >
                        <td>
                          <input
                            readOnly={isViewScreen}
                            type="text"
                            placeholder="Buscar producto..."
                            value={
                              line.productId
                                ? (productsById.get(line.productId)?.name ?? "")
                                : (lineSearch[line.id] ?? "")
                            }
                            onChange={(e) => {
                              if (isViewScreen) return;
                              setLineSearch((prev) => ({ ...prev, [line.id]: e.target.value }));
                              void onLineProductChange(line.id, "");
                              setLineDropdownIndex((prev) => ({ ...prev, [line.id]: -1 }));
                            }}
                            onFocus={(e) => {
                              if (isViewScreen) return;
                              setActiveLineId(line.id);
                              setSelectedRowId(line.id);
                              setActiveCell({ rowId: line.id, col: "name" });
                              const rect = e.currentTarget.getBoundingClientRect();
                              setDropdownPosition({ top: rect.bottom, left: rect.left });
                            }}
                            onBlur={() => setTimeout(() => setActiveLineId(""), 500)}
                            onKeyDown={(e) => {
                              if (activeLineId !== line.id) return;
                              const options = products
                                .filter((p) =>
                                  p.name.toLowerCase().includes((lineSearch[line.id] ?? "").toLowerCase())
                                )
                                .slice(0, 5);
                              if (e.key === "ArrowDown") {
                                e.preventDefault();
                                setLineDropdownIndex((prev) => ({
                                  ...prev,
                                  [line.id]: Math.min((prev[line.id] ?? -1) + 1, options.length - 1)
                                }));
                              }
                              if (e.key === "ArrowUp") {
                                e.preventDefault();
                                setLineDropdownIndex((prev) => ({
                                  ...prev,
                                  [line.id]: Math.max((prev[line.id] ?? 0) - 1, 0)
                                }));
                              }
                              if (e.key === "Enter" && (lineDropdownIndex[line.id] ?? -1) >= 0) {
                                e.preventDefault();
                                const selected = options[lineDropdownIndex[line.id]];
                                void onLineProductChange(line.id, selected.id);
                                setLineSearch((prev) => ({ ...prev, [line.id]: "" }));
                                setActiveLineId("");
                                setLineDropdownIndex((prev) => ({ ...prev, [line.id]: -1 }));
                              }
                              if (e.key === "Escape") {
                                setActiveLineId("");
                                setLineDropdownIndex((prev) => ({ ...prev, [line.id]: -1 }));
                              }

                              if (e.key === "ArrowRight" && (lineDropdownIndex[line.id] ?? -1) < 0) {
                                e.preventDefault();
                                focusCell(line.id, "quantity");
                              }
                            }}
                            style={{ width: "180px" }}
                          />
                          {activeLineId === line.id && (
                            <div
                              className={styles.clientDropdown}
                              style={{
                                position: "fixed",
                                top: dropdownPosition?.top ?? 0,
                                left: dropdownPosition?.left ?? 0,
                                zIndex: 9999,
                                width: "200px",
                                pointerEvents: "all" // 👈
                              }}
                            >
                              {products
                                .filter((p) =>
                                  p.name.toLowerCase().includes((lineSearch[line.id] ?? "").toLowerCase())
                                )
                                .slice(0, 5)
                                .map((p, index) => (
                                  <div
                                    key={p.id}
                                    className={styles.clientOption}
                                    style={index === (lineDropdownIndex[line.id] ?? -1)
                                      ? { background: "#d1fae5", color: "#16a34a" }
                                      : {}}
                                    onMouseEnter={() => setLineDropdownIndex((prev) => ({ ...prev, [line.id]: index }))}
                                    onMouseDown={() => {
                                      console.log("mousedown fired", p.id);
                                      void onLineProductChange(line.id, p.id);
                                      setLineSearch((prev) => ({ ...prev, [line.id]: "" }));
                                      setActiveLineId("");
                                      setLineDropdownIndex((prev) => ({ ...prev, [line.id]: -1 }));
                                    }}
                                  >
                                    {p.name}
                                  </div>
                                ))}
                            </div>
                          )}
                        </td>
                        <td>
                          <input
                            readOnly={isViewScreen}
                            ref={(el) => { cellRefs.current[`${line.id}-quantity`] = el; }}
                            type="number"
                            min={1}
                            step={1}
                            value={line.quantity}
                            onFocus={() => {
                              setSelectedRowId(line.id);
                              setActiveCell({ rowId: line.id, col: "quantity" });
                            }}
                            onBlur={() => setActiveCell(null)}
                            onChange={(event) => onLineQuantityChange(line.id, event.target.value)}
                            onKeyDown={(e) => {
                              if (e.key === "ArrowRight") {
                                e.preventDefault();
                                focusCell(line.id, "price");
                              }
                              if (e.key === "ArrowLeft") {
                                e.preventDefault();
                                focusCell(line.id, "name");
                              }
                              if (e.key === "ArrowDown") {
                                e.preventDefault();
                                const lines = saleDraft.lines;
                                const idx = lines.findIndex((l) => l.id === line.id);
                                if (lines[idx + 1]) focusCell(lines[idx + 1].id, "quantity");
                              }
                              if (e.key === "ArrowUp") {
                                e.preventDefault();
                                const lines = saleDraft.lines;
                                const idx = lines.findIndex((l) => l.id === line.id);
                                if (lines[idx - 1]) focusCell(lines[idx - 1].id, "quantity");
                              }
                            }}
                          />
                        </td>
                        <td>
                          <input
                            readOnly={isViewScreen}
                            ref={(el) => { cellRefs.current[`${line.id}-price`] = el; }}
                            type="number"
                            min="0"
                            step="0.01"
                            value={line.unitPrice !== "" ? line.unitPrice : unitPrice}
                            onFocus={() => {
                              setSelectedRowId(line.id);
                              setActiveCell({ rowId: line.id, col: "price" });
                            }}
                            onBlur={() => setActiveCell(null)}
                            onChange={(e) => onLinePriceChange(line.id, e.target.value)}
                            onKeyDown={(e) => {
                              if (e.key === "ArrowLeft") {
                                e.preventDefault();
                                focusCell(line.id, "quantity");
                              }
                              if (e.key === "ArrowDown") {
                                e.preventDefault();
                                const lines = saleDraft.lines;
                                const idx = lines.findIndex((l) => l.id === line.id);
                                if (lines[idx + 1]) focusCell(lines[idx + 1].id, "price");
                              }
                              if (e.key === "ArrowUp") {
                                e.preventDefault();
                                const lines = saleDraft.lines;
                                const idx = lines.findIndex((l) => l.id === line.id);
                                if (lines[idx - 1]) focusCell(lines[idx - 1].id, "price");
                              }
                            }}
                          />
                        </td>
                        <td>{(unitPrice * line.quantity).toLocaleString('es-CR')}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>

            <div className={styles.commentsWrap}>
              <label>Comentarios (solo impresión)</label>
              <textarea value={saleDraft.comments} onChange={onCommentChange} rows={3} />
            </div>

          </div>

          <div className={styles.formBottomBar}>
            <p className={styles.totalBar}>Total: ₡{calculatedTotal.toLocaleString('es-CR')}</p>
            <div className={styles.bottomActions}>
              {!isViewScreen && (
                <>
                  <button className={styles.primaryButton} type="button" disabled={saving} onClick={() => void onSave(false)}>
                    Guard<u>a</u>r
                  </button>
                  <button className={styles.button} type="button" disabled={saving} onClick={() => void onSave(true)}>
                    Guardar e I<u>m</u>primir
                  </button>
                </>
              )}
              <button className={styles.button} type="button" onClick={() => navigate("/sales")}>
                <u>S</u>alir
              </button>
            </div>
          </div>

          {showCreateProductModal && (
            <div className={styles.modalBackdrop}>
              <div className={styles.modal}>
                <header className={styles.modalHeader}>
                  <h3>Crear Producto</h3>
                  <button className={styles.button} type="button"
                    onClick={() => setShowCreateProductModal(false)}>
                    Cerrar <kbd>Esc</kbd>
                  </button>
                </header>

                <div className={styles.topGrid} style={{ gridTemplateColumns: "1fr 1fr" }}>
                  <div className={styles.field}>
                    <label>Nombre</label>
                    <input
                      value={productDraft.name}
                      onChange={(e) => setProductDraft(prev => ({ ...prev, name: e.target.value }))}
                      placeholder="Nombre del producto"

                    />
                  </div>
                  <div className={styles.field}>
                    <label>Descripción</label>
                    <input
                      value={productDraft.description}
                      onChange={(e) => setProductDraft(prev => ({ ...prev, description: e.target.value }))}
                      placeholder="Opcional"
                    />
                  </div>
                  <div className={styles.field}>
                    <label>Stock</label>
                    <input
                      type="number" min="0"
                      value={productDraft.stock}
                      onChange={(e) => setProductDraft(prev => ({ ...prev, stock: e.target.value }))}
                    />
                  </div>
                  <div className={styles.field}>
                    <label>Precio Detalle</label>
                    <input
                      id="priceDetailInput"
                      type="number" min="0"
                      value={productDraft.priceDetail}
                      onChange={(e) => setProductDraft(prev => ({ ...prev, priceDetail: e.target.value }))}
                      onKeyDown={(e) => {
                        if (e.key === "Enter") {
                          e.preventDefault();
                          document.querySelector<HTMLInputElement>("#priceDetailInput")?.focus();
                        }
                      }}
                    />
                  </div>
                  <div className={styles.field}>
                    <label>Precio Mayorista</label>
                    <input
                      id="priceWholesaleInput"
                      type="number" min="0"
                      value={productDraft.priceWholesale}
                      onChange={(e) => setProductDraft(prev => ({ ...prev, priceWholesale: e.target.value }))}
                      onKeyDown={(e) => {
                        if (e.key === "Enter") {
                          e.preventDefault();
                          document.querySelector<HTMLButtonElement>("#saveProductBtn")?.click();
                        }
                      }}
                    />
                  </div>
                  <div className={styles.field}>
                    <label>Precio Nuevo</label>
                    <input
                      type="number" min="0"
                      value={productDraft.priceNew}
                      onChange={(e) => setProductDraft(prev => ({ ...prev, priceNew: e.target.value }))}
                    />
                  </div>
                </div>

                <div className={styles.formBottomBar} style={{ background: "transparent", padding: 0 }}>
                  <button
                    id="saveProductBtn"
                    className={styles.primaryButton}
                    type="button"
                    onClick={async () => {
                      try {
                        const created = await createProduct({
                          name: productDraft.name,
                          description: productDraft.description,
                          stock: Number(productDraft.stock),
                          price: Number(productDraft.priceDetail),
                          status: "ACTIVE"
                        });

                        // 👈 Crear precios
                        await createProductPrice(created.id, "DETAIL", Number(productDraft.priceDetail));
                        await createProductPrice(created.id, "WHOLESALE", Number(productDraft.priceWholesale));
                        await createProductPrice(created.id, "NEW", Number(productDraft.priceNew));


                        const updated = await listProducts();
                        setProducts(updated);
                        setSaleDraft((prev) => ({
                          ...prev,
                          lines: [
                            ...prev.lines,
                            { id: crypto.randomUUID(), productId: created.id, quantity: 1, unitPrice: "" }
                          ]
                        }));
                        setProductDraft({ name: "", description: "", stock: "0", priceDetail: "0", priceWholesale: "0", priceNew: "0" });
                        setShowCreateProductModal(false);
                        setModal({
                          show: true,
                          type: "success",
                          title: "Producto creado",
                          message: `El producto "${created.name}" fue creado y agregado a la factura.`,
                          onConfirm: closeModal,
                          confirmLabel: "Aceptar",
                        });
                      } catch {
                        setError("No se pudo crear el producto.");
                      }
                    }}
                  >
                    <u>G</u>uardar producto
                  </button>
                </div>
              </div>
            </div>
          )}

          {showProductModal && (
            <div className={styles.modalBackdrop}>
              <div className={styles.modal}>
                <header className={styles.modalHeader}>
                  <h3>Productos</h3>
                  <button className={styles.button} type="button"
                    onClick={() => { setShowProductModal(false); setProductModalSearch(""); }}>
                    Cerrar <kbd>Esc</kbd>
                  </button>
                </header>
                <div className={styles.field}>
                  <input
                    type="text"
                    placeholder="Buscar producto..."
                    value={productModalSearch}
                    onChange={(e) => {
                      setProductModalSearch(e.target.value);
                      setProductModalIndex(-1);
                    }} autoFocus
                    onKeyDown={(e) => {
                      const filtered = products.filter((p) =>
                        p.name.toLowerCase().includes(productModalSearch.toLowerCase())
                      );
                      if (e.key === "ArrowDown") {
                        e.preventDefault();
                        setProductModalIndex((prev) => Math.min(prev + 1, filtered.length - 1));
                      }
                      if (e.key === "ArrowUp") {
                        e.preventDefault();
                        setProductModalIndex((prev) => Math.max(prev - 1, 0));
                      }
                      if (e.key === "Enter" && productModalIndex >= 0) {
                        e.preventDefault();
                        const selected = filtered[productModalIndex];
                        if (selected) {
                          addProductFromModal(selected.id);
                          setProductModalIndex(-1);
                        }
                      }
                      if (e.key === "Escape") {
                        e.preventDefault();
                        setShowProductModal(false);
                        setProductModalSearch("");
                        setProductModalIndex(-1);
                      }
                    }}
                    readOnly={isViewScreen}
                  />
                </div>
                <div className={styles.tableWrap}>
                  <table className={styles.table}>
                    <thead>
                      <tr>
                        <th>Nombre</th>
                        <th>Stock</th>
                        <th>Precio</th>
                        <th>Acción</th>
                      </tr>
                    </thead>
                    <tbody>
                      {products
                        .filter((p) => p.name.toLowerCase().includes(productModalSearch.toLowerCase()))
                        .map((product, index) => (
                          <tr
                            key={product.id}
                            style={index === productModalIndex ? { background: "#dcfce7", cursor: "pointer" } : { cursor: "pointer" }}
                            onMouseEnter={() => setProductModalIndex(index)}
                            onClick={() => {
                              addProductFromModal(product.id);
                              setProductModalIndex(-1);
                            }}
                          >
                            <td>{product.name}</td>
                            <td>{product.stock}</td>
                            <td>₡{Number(product.price).toLocaleString('es-CR')}</td>
                            <td>
                              <button className={styles.button} type="button">
                                Agregar
                              </button>
                            </td>
                          </tr>
                        ))}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          )
          }

          {viewProduct && (
            <div className={styles.modalBackdrop}>
              <div className={styles.modal}>
                <header className={styles.modalHeader}>
                  <h3>Detalle del Producto</h3>
                  <button className={styles.button} type="button"
                    onClick={() => {
                      setViewProduct(null);
                      setViewProductPrices([]); // 👈
                    }}>
                    Cerrar <kbd>Esc</kbd>
                  </button>
                </header>

                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "1rem", padding: "0.5rem 0" }}>
                  <div className={styles.field}>
                    <label>Nombre</label>
                    <input value={viewProduct.name} readOnly />
                  </div>
                  <div className={styles.field}>
                    <label>Estado</label>
                    <input value={viewProduct.status === "ACTIVE" ? "Activo" : "Inactivo"} readOnly disabled={isViewScreen} />

                  </div>
                  <div className={styles.field}>
                    <label>Stock</label>
                    <input value={viewProduct.stock} readOnly />
                  </div>
                  {viewProduct.description && (
                    <div className={styles.field} style={{ gridColumn: "span 2" }}>
                      <label>Descripción</label>
                      <input value={viewProduct.description} readOnly />
                    </div>

                  )}
                  <div className={styles.field}>
                    <label>Precio Detalle</label>
                    <input
                      value={
                        viewProductPrices.find(p => p.type === "DETAIL")
                          ? `₡${Number(viewProductPrices.find(p => p.type === "DETAIL")?.price).toLocaleString('es-CR')}`
                          : "No definido"
                      }
                      readOnly
                    />
                  </div>
                  <div className={styles.field}>
                    <label>Precio Mayorista</label>
                    <input
                      value={
                        viewProductPrices.find(p => p.type === "WHOLESALE")
                          ? `₡${Number(viewProductPrices.find(p => p.type === "WHOLESALE")?.price).toLocaleString('es-CR')}`
                          : "No definido"
                      }
                      readOnly
                    />
                  </div>
                </div>
              </div>
            </div>
          )}
          {modal.show && (
            <Modal
              type={modal.type}
              title={modal.title}
              message={modal.message}
              onConfirm={modal.onConfirm}
              onCancel={modal.onCancel}
              confirmLabel={modal.confirmLabel}
              cancelLabel={modal.cancelLabel}
              danger={modal.danger}
            />
          )}
          {saleToPrint && (
            <TicketPrint
              sale={saleToPrint}
              client={clientsById.get(saleToPrint.clientId)}
              productsById={productsById}
            />
          )}
        </section>
      </div>
    );
  }

  const selectedSale = sortedAndFilteredSales.find(s => s.id === selectedRowId);

  return (

    <div style={{
      background: "#f0f4f0",
      minHeight: "90vh",
      display: "flex",
      justifyContent: "center",
      alignItems: "flex-start",
      padding: "1rem"
    }}>      <section className={styles.container}>
        <header className={styles.header}>
          <h2 className={styles.title}>FACTURACIÓN</h2>
          <div className={styles.headerActions}>
            <span style={{ color: '#9ca3af', fontSize: '0.9rem' }}>
              {new Date().toLocaleDateString('es-CR')}
            </span>
          </div>
        </header>

        {error ? <p className={styles.error}>{error}</p> : null}

        <div className={styles.filters}>
          <div className={styles.field}>
            <label>Ordenar por</label>
            <select value={sortBy} onChange={(e) => {
              setSortBy(e.target.value as SortBy);
              setDateFrom("");
              setDateTo("");
              setAmountValue("");        // 👈
              setClientFilterSearch(""); // 👈
              setActiveClientId("");     // 👈
            }}>
              <option value="invoiceNumber">Nro Factura</option>
              <option value="createdAt">Fecha</option>
              <option value="client">Cliente</option>
              <option value="total">Monto</option>
            </select>
          </div>
          <div className={styles.field}>
            <label>Dirección</label>
            <select value={sortDir} onChange={(e) => setSortDir(e.target.value as "asc" | "desc")}>
              <option value="desc">Descendente</option>
              <option value="asc">Ascendente</option>
            </select>
          </div>
          <div className={styles.field}>
            <label>Estado</label>
            <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value as StatusFilter)}>
              <option value="ALL">Todas</option>
              <option value="PENDING">Pendientes</option>
              <option value="PARTIAL">Parciales</option>
              <option value="PAID">Pagadas</option>
              <option value="CANCELLED">Canceladas</option>
            </select>
          </div>
          {sortBy === "createdAt" && (
            <>
              <div className={styles.field}>
                <label>Desde</label>
                <input
                  type="date"
                  value={dateFrom}
                  max={dateTo || undefined}
                  onChange={(e) => setDateFrom(e.target.value)}
                />
              </div>
              <div className={styles.field}>
                <label>Hasta</label>
                <input
                  type="date"
                  value={dateTo}
                  min={dateFrom || undefined}
                  onChange={(e) => setDateTo(e.target.value)}
                />
              </div>
            </>
          )}
          {sortBy === "total" && (
            <div className={styles.field}>
              <label>Monto</label>
              <div style={{ display: "flex", gap: "0.5rem" }}>
                <select
                  value={amountOperator}
                  onChange={(e) => setAmountOperator(e.target.value as ">=" | "<=" | "=")}
                  style={{ width: "135px" }}
                >
                  <option value=">=">Mayor o igual</option>
                  <option value="<=">Menor o igual</option>
                  <option value="=">Igual</option>
                </select>
                <input
                  type="number"
                  min="0"
                  value={amountValue}
                  onChange={(e) => setAmountValue(e.target.value)}
                  placeholder="Monto"
                  style={{ width: "100px" }}
                />
              </div>
            </div>
          )}
          {sortBy === "client" && (
            <div className={styles.field} style={{ position: "relative" }}>
              <label>Cliente</label>
              <input
                readOnly={isViewScreen}
                type="text"
                placeholder="Buscar cliente..."
                value={clientFilterSearch}
                autoComplete="off"
                onChange={(e) => {
                  setClientFilterSearch(e.target.value);
                  setActiveClientId("");
                  setShowClientFilterDropdown(true);
                  setClientFilterDropdownIndex(-1);
                }}
                onFocus={() => setShowClientFilterDropdown(true)}
                onBlur={() => setTimeout(() => setShowClientFilterDropdown(false), 200)}
                onKeyDown={(e) => {
                  const options = clients
                    .filter((c) => c.name.toLowerCase().includes(clientFilterSearch.toLowerCase()))
                    .slice(0, 6);
                  if (e.key === "ArrowDown") {
                    e.preventDefault();
                    setClientFilterDropdownIndex((prev) => Math.min(prev + 1, options.length - 1));
                  }
                  if (e.key === "ArrowUp") {
                    e.preventDefault();
                    setClientFilterDropdownIndex((prev) => Math.max(prev - 1, 0));
                  }
                  if (e.key === "Enter" && clientFilterDropdownIndex >= 0) {
                    e.preventDefault();
                    const selected = options[clientFilterDropdownIndex];
                    setClientFilterSearch(selected.name);
                    setActiveClientId(selected.id);
                    setShowClientFilterDropdown(false);
                    setClientFilterDropdownIndex(-1);
                  }
                  if (e.key === "Escape") {
                    setShowClientFilterDropdown(false);
                  }
                }}
              />
              {showClientFilterDropdown && clientFilterSearch && (
                <div className={styles.clientDropdown}>
                  {clients
                    .filter((c) => c.name.toLowerCase().includes(clientFilterSearch.toLowerCase()))
                    .slice(0, 6)
                    .map((client, index) => (
                      <div
                        key={client.id}
                        className={styles.clientOption}
                        style={index === clientFilterDropdownIndex ? { background: "#d1fae5", color: "#16a34a" } : {}}
                        onMouseDown={() => {
                          setClientFilterSearch(client.name);
                          setActiveClientId(client.id);
                          setShowClientFilterDropdown(false);
                          setClientFilterDropdownIndex(-1);
                        }}
                        onMouseEnter={() => setClientFilterDropdownIndex(index)}
                      >
                        {client.name}
                      </div>
                    ))}
                </div>
              )}
            </div>
          )}
          <button className={styles.primaryButton} type="button" onClick={() => {
            setActiveSortBy(sortBy);
            setActiveSortDir(sortDir);
            setActiveStatusFilter(statusFilter);
            setActiveDateFrom(dateFrom);
            setActiveDateTo(dateTo);
            setActiveAmountOperator(amountOperator);
            setActiveAmountValue(amountValue);
          }}>
            Buscar
          </button>
          <button className={styles.button} type="button" onClick={() => {
            setSortBy("createdAt");
            setSortDir("desc");
            setStatusFilter("ALL");
            setDateFrom("");
            setDateTo("");
            setActiveSortBy("createdAt");
            setActiveSortDir("desc");
            setActiveStatusFilter("ALL");
            setActiveDateFrom("");
            setActiveDateTo("");
            setActiveAmountOperator(">=");
            setActiveAmountValue("");
            setClientSearch("");
            setClientFilterSearch("");
            setActiveClientId("");


          }}>
            Limpiar
          </button>
        </div>
        <div className={styles.tableContainer}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th>Nro. Factura</th>
                <th>Fecha</th>
                <th>Cliente</th>
                <th>Método de pago</th>
                <th>Total</th>
                <th>Estado</th>
              </tr>
            </thead>
            <tbody>
              {sortedAndFilteredSales.length === 0 ? (
                <tr><td colSpan={6} className={styles.empty}>No hay ventas registradas.</td></tr>
              ) : (
                sortedAndFilteredSales.map((sale) => (
                  <tr
                    key={sale.id}
                    className={selectedRowId === sale.id ? styles.selected : ""}
                    onClick={() => setSelectedRowId(sale.id)}
                  >
                    <td>{sale.invoiceNumber}</td>
                    <td>{new Date(sale.createdAt).toLocaleDateString('es-CR')}</td>
                    <td>{clientsById.get(sale.clientId)?.name ?? sale.clientId}</td>
                    <td>{mapPaymentMethod(sale.paymentMethod)}</td>
                    <td>₡{Number(sale.total).toLocaleString('es-CR')}</td>                  <td>
                      <span className={`${styles.status} ${styles[sale.status.toLowerCase()]}`}>
                        {mapStatus(sale.status)}
                      </span>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        <div className={styles.bottomBar}>
          <div className={styles.bottomActions}>
            <button className={styles.primaryButton} type="button"
              disabled={!caja.abierta}
              onClick={() => navigate("/sales/new")}>
              <u>C</u>rear
            </button>

            <button className={styles.button} type="button"
              disabled={!caja.abierta || !selectedRowId}
              onClick={() => selectedRowId && navigate(`/sales/${selectedRowId}/edit`)}>
              M<u>o</u>dificar
            </button>

            <button className={styles.button} type="button"
              disabled={!caja.abierta || !selectedRowId}
              onClick={() => selectedRowId && navigate(`/sales/${selectedRowId}/view`)}>
              <u>V</u>er Factura
            </button>

            <button className={styles.button} type="button"
              disabled={!caja.abierta || !selectedRowId}
              onClick={() => {
                const sale = sortedAndFilteredSales.find(s => s.id === selectedRowId);
                if (sale) {
                  setSaleToPrint(sale);
                  setTimeout(() => window.print(), 300);
                }
              }}>
              <u>I</u>mprimir
            </button>

            <button className={styles.dangerButton} type="button"
              disabled={!caja.abierta || !selectedRowId}
              onClick={() => selectedRowId && void onDeleteSale(selectedRowId)}>
              <u>E</u>liminar
            </button>

            <button className={styles.primaryButton} type="button"
              disabled={!caja.abierta || !selectedRowId ||
                (selectedSale?.status !== "PENDING" && selectedSale?.status !== "PARTIAL")}
              onClick={() => selectedRowId && navigate(`/sales/${selectedRowId}/edit`)}>
              Pagar <kbd>Alt+Z</kbd>
            </button>
          </div>
          <button
            className={styles.button}
            type="button"
            disabled={!caja.abierta}
            onClick={() => setShowGastosModal(true)}
          >
            Gastos
          </button>
          <div className={styles.bottomGlobal}>
            <button className={styles.primaryButton} type="button"
              disabled={caja.abierta}
              onClick={() => setShowAbrirCajaModal(true)}>
              Iniciar Caja <kbd>Alt+K</kbd>
            </button>

            <button className={styles.button} type="button"
              disabled={!caja.abierta}
              onClick={cerrarCaja}>
              Cierre de Caja <kbd>Alt+X</kbd>
            </button>

            <button className={styles.button} type="button"
              onClick={() => navigate("/dashboard")}>
              Sali<u>r</u>
            </button>
          </div>
        </div>
        {modal.show && (
          <Modal
            type={modal.type}
            title={modal.title}
            message={modal.message}
            onConfirm={modal.onConfirm}
            onCancel={modal.onCancel}
            confirmLabel={modal.confirmLabel}
            cancelLabel={modal.cancelLabel}
            danger={modal.danger}
          />
        )}
        {showAbrirCajaModal && (
          <div className={styles.modalBackdrop}>
            <div className={styles.modal}>
              <header className={styles.modalHeader}>
                <h3>Iniciar Caja</h3>
                <button className={styles.button} type="button" onClick={() => setShowAbrirCajaModal(false)}>
                  Cerrar
                </button>
              </header>
              <div className={styles.field}>
                <label>Monto inicial de efectivo</label>
                <div style={{ display: "flex", gap: "0.5rem", alignItems: "center" }}>
                  <input
                    type="number"
                    min="0"
                    autoFocus
                    value={montoInicialDraft}
                    onChange={(e) => setMontoInicialDraft(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === "Enter") abrirCaja();
                      if (e.key === "Escape") setShowAbrirCajaModal(false);
                    }}
                    placeholder="₡0"
                    style={{ flex: 1 }}
                  />
                  <button
                    className={styles.button}
                    type="button"
                    onClick={() => setMontoInicialDraft("30000")}
                    style={{ fontSize: "0.75rem", padding: "0.3rem 0.6rem" }}
                  >
                    Restablecer
                  </button>
                </div>
                <span style={{ fontSize: "0.78rem", color: "#6b7280" }}>Por defecto: ₡30,000</span>
              </div>
              <p style={{ color: "#b45309", fontSize: "0.9rem", margin: "0.5rem 0" }}>
                ⚠️ Recuerde vaciar la memoria del datáfono antes de iniciar.
              </p>
              <div style={{ display: "flex", gap: "0.5rem", marginTop: "0.5rem" }}>
                <button className={styles.primaryButton} type="button" onClick={abrirCaja}>
                  Iniciar Caja
                </button>
                <button className={styles.button} type="button" onClick={() => setShowAbrirCajaModal(false)}>
                  Cancelar
                </button>
              </div>
            </div>
          </div>
        )}
        {saleToPrint && (
          <TicketPrint
            sale={saleToPrint}
            client={clientsById.get(saleToPrint.clientId)}
            productsById={productsById}
          />
        )}
        {cierreToPrint && (
          <CierreCajaPrint data={cierreToPrint} />
        )}
        {showGastosModal && (
          <div className={styles.modalBackdrop}>
            <div className={styles.modal}>
              <header className={styles.modalHeader}>
                <h3>Gastos del turno</h3>
                <button className={styles.button} type="button" onClick={() => setShowGastosModal(false)}>
                  Cerrar
                </button>
              </header>

              <div style={{ display: "flex", gap: "0.5rem", marginBottom: "1rem" }}>
                <div className={styles.field} style={{ flex: 2 }}>
                  <label>Descripción</label>
                  <input
                    type="text"
                    value={gastoDraft.descripcion}
                    onChange={(e) => setGastoDraft(prev => ({ ...prev, descripcion: e.target.value }))}
                    placeholder="Ej: Gasolina, almuerzo..."
                    onKeyDown={(e) => { if (e.key === "Enter") agregarGasto(); }}
                  />
                </div>
                <div className={styles.field} style={{ flex: 1 }}>
                  <label>Monto</label>
                  <input
                    type="number"
                    min="0"
                    value={gastoDraft.monto}
                    onChange={(e) => setGastoDraft(prev => ({ ...prev, monto: e.target.value }))}
                    onKeyDown={(e) => { if (e.key === "Enter") agregarGasto(); }}
                  />
                </div>
                <div style={{ display: "flex", alignItems: "flex-end" }}>
                  <button className={styles.primaryButton} type="button" onClick={agregarGasto}>
                    Agregar
                  </button>
                </div>
              </div>

              <div style={{ maxHeight: "300px", overflowY: "auto", border: "1px solid #e5e7eb", borderRadius: 8 }}>
                <table className={styles.table}>
                  <thead>
                    <tr>
                      <th>Descripción</th>
                      <th>Monto</th>
                      <th>Acción</th>
                    </tr>
                  </thead>
                  <tbody>
                    {caja.gastos.length === 0 ? (
                      <tr><td colSpan={3} className={styles.empty}>No hay gastos registrados.</td></tr>
                    ) : (
                      caja.gastos.map((gasto) => (
                        <tr key={gasto.id}>
                          <td>{gasto.descripcion}</td>
                          <td>₡{gasto.monto.toLocaleString('es-CR')}</td>
                          <td>
                            <button className={styles.dangerButton} type="button" onClick={() => eliminarGasto(gasto.id)}>
                              Eliminar
                            </button>
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>

              <p style={{ marginTop: "0.5rem", fontWeight: 600 }}>
                Total gastos: ₡{caja.gastos.reduce((sum, g) => sum + g.monto, 0).toLocaleString('es-CR')}
              </p>
            </div>
          </div>
        )}
      </section>
    </div>
  );

  function generarExcelCierre(): void {
    const facturasDeTurno = sales.filter((sale) => caja.facturaIds.includes(sale.id));

    const filas: Record<string, string | number>[] = facturasDeTurno.map((sale) => {
      const clientName = clientsById.get(sale.clientId)?.name ?? sale.clientId;
      const efectivo = caja.pagos
        .filter(p => p.facturaId === sale.id && p.method === "CASH")
        .reduce((sum, p) => sum + p.amount, 0);
      const sinpeTransfer = caja.pagos
        .filter(p => p.facturaId === sale.id && (p.method === "SINPE" || p.method === "TRANSFER"))
        .reduce((sum, p) => sum + p.amount, 0);
      const tarjeta = caja.pagos
        .filter(p => p.facturaId === sale.id && p.method === "CARD")
        .reduce((sum, p) => sum + p.amount, 0);

      return {
        "Cliente": clientName,
        "Efectivo": efectivo || "",
        "": "",
        "SINPE/Transferencia": sinpeTransfer || "",
        "Tarjeta": tarjeta || "",
      };
    });

    // Fila vacía
    filas.push({ "Cliente": "", "Efectivo": "", "": "", "SINPE/Transferencia": "", "Tarjeta": "" });

    // Total efectivo
    const totalEfectivo = caja.pagos
      .filter(p => p.method === "CASH")
      .reduce((sum, p) => sum + p.amount, 0);

    filas.push({
      "Cliente": "TOTAL EFECTIVO",
      "Efectivo": totalEfectivo,
      "": "",
      "SINPE/Transferencia": "",
      "Tarjeta": "",
    });

    // Fila vacía
    filas.push({ "Cliente": "", "Efectivo": "", "": "", "SINPE/Transferencia": "", "Tarjeta": "" });

    // Gastos
    caja.gastos.forEach((gasto) => {
      filas.push({
        "Cliente": gasto.descripcion,
        "Efectivo": -gasto.monto,
        "": "",
        "SINPE/Transferencia": "",
        "Tarjeta": "",
      });
    });

    // Fila vacía
    filas.push({ "Cliente": "", "Efectivo": "", "": "", "SINPE/Transferencia": "", "Tarjeta": "" });

    // Efectivo neto
    const totalGastos = caja.gastos.reduce((sum, g) => sum + g.monto, 0);
    const efectivoNeto = totalEfectivo - totalGastos;
    filas.push({
      "Cliente": "EFECTIVO NETO",
      "Efectivo": efectivoNeto,
      "": "",
      "SINPE/Transferencia": "",
      "Tarjeta": "",
    });

    const ws = XLSX.utils.json_to_sheet(filas);

    // Fórmula de suma para TOTAL EFECTIVO
    const dataRows = facturasDeTurno.length;
    const totalRow = dataRows + 2; // +1 header +1 fila vacía
    ws[`B${totalRow}`] = {
      t: 'n',
      f: `SUM(B2:B${dataRows + 1})`
    };

    const wb = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(wb, ws, "Cierre de Caja");
    XLSX.writeFile(wb, `cierre_caja_${new Date().toLocaleDateString('es-CR').replace(/\//g, '-')}.xlsx`);
  }

  function agregarGasto(): void {
    if (!gastoDraft.descripcion.trim()) {
      setError("La descripción del gasto es obligatoria.");
      return;
    }
    if (!gastoDraft.monto || Number(gastoDraft.monto) <= 0) {
      setError("El monto del gasto debe ser mayor a 0.");
      return;
    }
    const nuevoGasto = {
      id: crypto.randomUUID(),
      descripcion: gastoDraft.descripcion.trim(),
      monto: Number(gastoDraft.monto),
    };
    const updatedCaja = {
      ...caja,
      gastos: [...caja.gastos, nuevoGasto],
    };
    setCaja(updatedCaja);
    saveCaja(updatedCaja);
    setGastoDraft({ descripcion: "", monto: "" });
  }

  function eliminarGasto(id: string): void {
    const updatedCaja = {
      ...caja,
      gastos: caja.gastos.filter(g => g.id !== id),
    };
    setCaja(updatedCaja);
    saveCaja(updatedCaja);
  }

}

function mapPaymentMethod(paymentMethod: PaymentMethod): string {
  if (paymentMethod === "SINPE") {
    return "SINPE";
  }
  if (paymentMethod === "TRANSFER") {
    return "Transferencia";
  }
  return "Efectivo";
}

function mapStatus(status: SaleStatus): string {
  if (status === "PAID") {
    return "Pagada";
  }
  if (status === "CANCELLED") {
    return "Cancelada";
  }
  if (status === "PARTIAL") {
    return "Parcial";
  }
  return "Pendiente";
}

function readError(error: unknown, fallback: string): string {
  if (error instanceof Error && error.message.trim().length > 0) {
    return error.message;
  }
  return fallback;
}
