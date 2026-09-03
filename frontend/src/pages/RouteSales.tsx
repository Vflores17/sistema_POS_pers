import type { ChangeEvent, ReactElement } from "react";
import { useEffect, useMemo, useState, useRef } from "react";
import { useNavigate, useParams, useLocation } from "react-router-dom";
import { listClients, type Client } from "../api/clients";
import {
  createRouteSale,
  changeRouteSaleStatus,
  deleteRouteSale,
  getNextRouteSaleInvoiceNumber,
  getRouteSaleById,
  listRouteSales,
  listRouteSalePaymentMovements,
  updateRouteSale,
  type RoutePaymentMethod as PaymentMethod,
  type RouteSale as Sale,
  type RouteSaleStatus as SaleStatus,
  type RouteSalePaymentMovement,
  saveRouteSalePayments as savePayments,
} from "../api/route-sales";
import styles from "./RouteSales.module.css";
import Modal from "../components/Modal";
import {
  listProducts,
  createProduct,
  createProductPrice,
  getProductPrices,
  type Product,
} from "../api/products";
import TicketPrint from "../components/TicketPrint";
import CierreCajaPrint from "../components/CierreCajaPrint";
import {
  createDriver,
  deleteDriver,
  listDrivers,
  updateDriver,
  type Driver,
  type DriverStatus,
} from "../api/drivers";
import { usePermissions } from "../auth/PermissionContext";
import { isAdminAuthorizationCancelled } from "../api/admin-authorizations";
import { isGloballyReportedError, notifyGlobalError } from "../api/errors";
import ModuleLoadingSkeleton from "../components/ModuleLoadingSkeleton";
import SaleHistoryFilters from "../components/SaleHistoryFilters";
import SaleHistoryTable from "../components/SaleHistoryTable";
import RouteSalesHistoryActions from "../components/RouteSalesHistoryActions";
import {
  SaleFormHeader,
  SaleInvoiceField,
} from "../components/SaleFormPresentation";
import { useSaleHistoryFilters } from "../hooks/useSaleHistoryFilters";
import {
  useEscapeShortcut,
  useSaleKeyboardShortcuts,
} from "../hooks/useSaleKeyboardShortcuts";
import { useSalePayments } from "../hooks/useSalePayments";
import { useRouteCashRegister } from "../hooks/useCashRegister";
import { generateRouteSaleWhatsappPdf } from "../utils/saleExportUtils";
import {
  calculateLinesTotal,
  filterClientsByName,
  getSessionUserLabel,
  insertEmptyLine,
  mapSalePaymentMethod as mapPaymentMethod,
  readSalePageError as readError,
  removeLine,
  replaceLinePrice,
  replaceLineProduct,
  replaceLineQuantity,
  resolveConfiguredUnitPrice,
} from "../utils/salePageUtils";

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
  driverId: string;
  paymentMethod: PaymentMethod;
  lines: LineDraft[];
  comments: string;
  status?: SaleStatus;
}

const EMPTY_FORM: SaleFormDraft = {
  clientId: "",
  driverId: "",
  paymentMethod: "CASH",
  lines: [
    { id: crypto.randomUUID(), productId: "", quantity: 1, unitPrice: "" },
  ],
  comments: "",
};

export default function RouteSales(): ReactElement {
  const { hasPermission, hasAllPermissions } = usePermissions();
  const canCreate = hasPermission("ROUTE_CREATE");
  const canUpdate = hasPermission("ROUTE_UPDATE");
  const canDelete = hasPermission("ROUTE_DELETE");
  const canCancel = hasPermission("ROUTE_CANCEL");
  const canReadProducts = hasPermission("PRODUCT_READ");
  const canReadPrices = hasPermission("PRICE_READ");
  const canCreateProduct = hasAllPermissions("PRODUCT_CREATE", "PRICE_CREATE");
  const canReadDrivers = hasPermission("DRIVER_READ");
  const canCreateDriver = hasPermission("DRIVER_CREATE");
  const canDeleteDriver = hasPermission("DRIVER_DELETE");
  const canOperateCaja = canCreate || canUpdate;
  const [showModificarModal, setShowModificarModal] = useState<boolean>(false);
  const [modificarInvoiceInput, setModificarInvoiceInput] =
    useState<string>("");
  const {
    cashRegister: caja,
    openCashRegister,
    closeCashRegister,
  } = useRouteCashRegister();
  const [showAbrirCajaModal, setShowAbrirCajaModal] = useState<boolean>(false);
  const [montoInicialDraft, setMontoInicialDraft] = useState<string>("30000");
  const [modal, setModal] = useState<ModalState>({
    show: false,
    type: "success",
    title: "",
    message: "",
  });

  const [productModalIndex, setProductModalIndex] = useState<number>(-1);
  const navigate = useNavigate();
  const location = useLocation();
  const { id } = useParams<{ id: string }>();
  const isNewScreen = window.location.pathname === "/route-sales/new";
  const isEditScreen = window.location.pathname.endsWith("/edit");

  const isViewScreen = window.location.pathname.endsWith("/view");
  const isFormScreen = isNewScreen || isEditScreen || isViewScreen;

  const selectedRowRef = useRef<HTMLTableRowElement | null>(null);
  const cellRefs = useRef<Record<string, HTMLInputElement | null>>({});

  const [sales, setSales] = useState<Sale[]>([]);
  const [clients, setClients] = useState<Client[]>([]);
  const [drivers, setDrivers] = useState<Driver[]>([]);
  const [showDriversModal, setShowDriversModal] = useState<boolean>(false);
  const [driverNameDraft, setDriverNameDraft] = useState<string>("");
  const [driverStatusDraft, setDriverStatusDraft] =
    useState<DriverStatus>("ACTIVE");
  const [products, setProducts] = useState<Product[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [saving, setSaving] = useState<boolean>(false);
  const [error, setError] = useState<string>("");
  const [saleDraft, setSaleDraft] = useState<SaleFormDraft>(EMPTY_FORM);
  const [invoiceNumber, setInvoiceNumber] = useState<number>(0);
  const [selectedRowId, setSelectedRowId] = useState<string>("");
  const [showProductModal, setShowProductModal] = useState<boolean>(false);
  const [clientSearch, setClientSearch] = useState<string>("");
  const [showClientDropdown, setShowClientDropdown] = useState<boolean>(false);
  const [showCreateProductModal, setShowCreateProductModal] =
    useState<boolean>(false);
  const [productModalSearch, setProductModalSearch] = useState<string>("");

  const [productDraft, setProductDraft] = useState<ProductDraft>({
    name: "",
    description: "",
    stock: "0",
    priceDetail: "0",
    priceWholesale: "0",
    priceNew: "0",
  });
  const [lineSearch, setLineSearch] = useState<Record<string, string>>({});
  const [activeLineId, setActiveLineId] = useState<string>("");
  const [activeCell, setActiveCell] = useState<{
    rowId: string;
    col: "name" | "quantity" | "price";
  } | null>(null);

  const productsById = useMemo(() => {
    return new Map(products.map((product) => [product.id, product]));
  }, [products]);

  const [clientDropdownIndex, setClientDropdownIndex] = useState<number>(-1);

  const [viewProduct, setViewProduct] = useState<Product | null>(null);

  const [viewProductPrices, setViewProductPrices] = useState<
    { type: string; price: number }[]
  >([]);
  const [saleToPrint, setSaleToPrint] = useState<Sale | null>(null);
  const [cierreToPrint, setCierreToPrint] = useState<{
    horaInicio: string;
    horaCierre: string;
    montoInicial: number;
    cantidadFacturas: number;
    cantidadAbonosAnteriores: number;
    totalEfectivo: number;
    totalSinpe: number;
    totalTransferencia: number;
    totalTarjeta: number;
    totalGastos: number;
    efectivoNeto: number;
    total: number;
    gastos: { descripcion: string; monto: number }[];
  } | null>(null);

  const [dropdownPosition, setDropdownPosition] = useState<{
    top: number;
    left: number;
  } | null>(null);

  const clientsById = useMemo(() => {
    return new Map(clients.map((client) => [client.id, client]));
  }, [clients]);

  const historyFilters = useSaleHistoryFilters(sales, clientsById);
  const { sortedAndFilteredSales } = historyFilters;

  function printSale(sale: Sale): void {
    setCierreToPrint(null);
    setSaleToPrint(sale);
    window.setTimeout(() => {
      window.print();
      setSaleToPrint(null);
    }, 300);
  }

  function onAbrirModificar(): void {
    const selected = sortedAndFilteredSales.find((s) => s.id === selectedRowId);
    setModificarInvoiceInput(selected ? String(selected.invoiceNumber) : "");
    setShowModificarModal(true);
  }

  async function onConfirmarModificar(): Promise<void> {
    const numero = Number(modificarInvoiceInput);
    if (!numero) {
      setError("Ingresá un número de factura válido.");
      return;
    }
    const sale =
      sortedAndFilteredSales.find((s) => s.invoiceNumber === numero) ??
      sales.find((s) => s.invoiceNumber === numero);
    if (!sale) {
      setError(`No se encontró la ruta número ${numero}.`);
      return;
    }
    setShowModificarModal(false);
    navigate(`/route-sales/${sale.id}/edit`, {
      state: { selectedId: sale.id },
    });
  }
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
    openCashRegister(Number(montoInicialDraft));
    setMontoInicialDraft("");
    setShowAbrirCajaModal(false);
  }

  const [whatsappModal, setWhatsappModal] = useState<{
    show: boolean;
    sale: Sale | null;
    mensaje: string;
    telefono: string;
  } | null>(null);

  const [showComments, setShowComments] = useState<boolean>(false);

  async function cerrarCaja(): Promise<void> {
    const openedAt =
      caja.openedAt ||
      (() => {
        const legacyDate = new Date(caja.horaInicio);
        return Number.isNaN(legacyDate.getTime())
          ? ""
          : legacyDate.toISOString();
      })();
    if (!openedAt) {
      setModal({
        show: true,
        type: "error",
        title: "No se puede cerrar la caja",
        message:
          "La apertura actual no tiene una fecha válida. Cierra manualmente esta caja local y vuelve a abrirla.",
        confirmLabel: "Aceptar",
        onConfirm: closeModal,
      });
      return;
    }
    const closedAt = new Date();
    let movements: RouteSalePaymentMovement[];
    try {
      movements = await listRouteSalePaymentMovements(
        openedAt,
        closedAt.toISOString(),
      );
    } catch (cause) {
      if (!isGloballyReportedError(cause))
        notifyGlobalError(
          readError(
            cause,
            "No se pudieron consultar los pagos del turno de rutas.",
          ),
        );
      return;
    }
    const fromTime = new Date(openedAt).getTime();
    const toTime = closedAt.getTime();
    const facturasDeTurno = sales.filter((sale) => {
      const createdAt = new Date(sale.createdAt).getTime();
      return createdAt >= fromTime && createdAt <= toTime;
    });
    const totalByMethod = (method: PaymentMethod): number =>
      movements
        .filter((payment) => payment.method === method)
        .reduce((sum, payment) => sum + Number(payment.amount), 0);
    const totalEfectivo = totalByMethod("CASH");
    const totalSinpe = totalByMethod("SINPE");
    const totalTransferencia = totalByMethod("TRANSFER");
    const totalTarjeta = totalByMethod("CARD");
    const abonosAnteriores = movements.filter(
      (payment) => new Date(payment.routeSaleCreatedAt).getTime() < fromTime,
    ).length;
    const mensaje = `
🕐 Inicio: ${caja.horaInicio}
💵 Monto inicial: ₡${caja.montoInicial.toLocaleString("es-CR")}

📋 Facturas del turno: ${facturasDeTurno.length}
📥 Abonos a facturas anteriores: ${abonosAnteriores}

💰 Efectivo: ₡${totalEfectivo.toLocaleString("es-CR")}
📱 SINPE: ₡${totalSinpe.toLocaleString("es-CR")}
🏦 Transferencia: ₡${totalTransferencia.toLocaleString("es-CR")}
💳 Tarjeta: ₡${totalTarjeta.toLocaleString("es-CR")}

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
        setSaleToPrint(null);
        setCierreToPrint({
          horaInicio: caja.horaInicio,
          horaCierre: closedAt.toLocaleString("es-CR"),
          montoInicial: caja.montoInicial,
          cantidadFacturas: facturasDeTurno.length,
          cantidadAbonosAnteriores: abonosAnteriores,
          totalEfectivo,
          totalSinpe,
          totalTransferencia,
          totalTarjeta,
          totalGastos: 0,
          efectivoNeto: totalEfectivo,
          total: totalEfectivo + totalSinpe + totalTransferencia + totalTarjeta,
          gastos: [],
        });
        window.setTimeout(() => {
          window.print();
          setCierreToPrint(null);
          closeCashRegister();
        }, 300);
      },
      onCancel: closeModal,
    });
  }

  useSaleKeyboardShortcuts({
    whatsapp: {
      enabled: whatsappModal?.show === true,
      modal: whatsappModal,
      onConfirm: confirmarEnvioWhatsApp,
      onClose: () => setWhatsappModal(null),
    },
    form: {
      enabled: isFormScreen,
      blocked: showCreateProductModal || showProductModal || modal.show,
      isViewScreen,
      canCreateProduct,
      canReadProducts,
      canReadPrices,
      canSave: isEditScreen || canCreate,
      activeCell,
      isInputFocused: () => isInputFocused,
      lines: saleDraft.lines,
      selectedRowId,
      hasViewedProduct: viewProduct !== null,
      onCreateProduct: () => {
        setProductDraft({
          name: "",
          description: "",
          stock: "0",
          priceDetail: "0",
          priceWholesale: "0",
          priceNew: "0",
        });
        setShowCreateProductModal(true);
      },
      onListProducts: () => setShowProductModal(true),
      onViewProduct: () => {
        const line = saleDraft.lines.find((item) => item.id === selectedRowId);
        const product = line ? productsById.get(line.productId) : undefined;
        if (product) {
          setViewProduct(product);
          getProductPrices(product.id)
            .then(setViewProductPrices)
            .catch(() => setViewProductPrices([]));
        } else {
          setError("Selecciona una fila con producto.");
        }
      },
      onAddRow: addEmptyRow,
      onRemoveRow: removeSelectedRow,
      onSave: (printAfterSave) => void onSave(printAfterSave),
      onExit: () => {
        if (hasUnsavedChanges()) {
          setModal({
            show: true,
            type: "warning",
            danger: true,
            title: "¿Salir sin guardar?",
            message:
              "La factura tiene cambios que no se han guardado. ¿Estás seguro que deseas salir?",
            confirmLabel: "Salir",
            cancelLabel: "Cancelar",
            onConfirm: () => {
              closeModal();
              navigate("/route-sales", { state: { selectedId: id } });
            },
            onCancel: closeModal,
          });
        } else {
          navigate("/route-sales", { state: { selectedId: id } });
        }
      },
      onOpenModify: () => {
        if (caja.abierta) onAbrirModificar();
      },
      onSelectRow: setSelectedRowId,
      onFocusCell: focusCell,
      onCloseViewedProduct: () => {
        setViewProduct(null);
        setViewProductPrices([]);
      },
      registration: {
        draft: saleDraft,
        productsById,
        addRow: addEmptyRow,
        removeRow: removeSelectedRow,
        save: onSave,
        navigate,
      },
    },
    productModals: {
      showProductModal,
      showCreateProductModal,
      onCloseProductModal: () => {
        setShowProductModal(false);
        setProductModalSearch("");
      },
      onCloseCreateProductModal: () => setShowCreateProductModal(false),
    },
    history: {
      enabled: !isFormScreen,
      blocked: modal.show,
      selectedRowId,
      rows: sortedAndFilteredSales,
      canCreate: canCreate && caja.abierta,
      canOpenModify: caja.abierta,
      canView: caja.abierta && Boolean(selectedRowId),
      canPrint: caja.abierta && Boolean(selectedRowId),
      canDelete: () => canDelete && caja.abierta && Boolean(selectedRowId),
      canPay: () =>
        canUpdate &&
        caja.abierta &&
        Boolean(selectedRowId) &&
        selectedSale?.status === "PENDING",
      canOpenCashRegister: canOperateCaja && !caja.abierta,
      canCloseCashRegister: canOperateCaja && caja.abierta,
      onCreate: () => navigate("/route-sales/new"),
      onOpenModify: onAbrirModificar,
      onView: () => navigate(`/route-sales/${selectedRowId}/edit`),
      onPrint: printSale,
      onDelete: () => void onDeleteSale(selectedRowId),
      onPay: () => navigate(`/route-sales/${selectedRowId}/edit`),
      onExit: () => navigate("/dashboard"),
      onOpenCashRegister: () => setShowAbrirCajaModal(true),
      onCloseCashRegister: cerrarCaja,
      onSelectRow: setSelectedRowId,
      registration: { cashRegister: caja, navigate },
    },
  });

  useEffect(() => {
    void bootstrap();
  }, [isFormScreen, isEditScreen, id]);

  useEffect(() => {
    if (!selectedRowRef.current) return;

    const container = document.querySelector(
      `.${styles.gridScrollArea}`,
    ) as HTMLElement;
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

  useEffect(() => {
    if (isFormScreen) return;
    if (!selectedRowId) return;

    const container = document.querySelector(
      `.${styles.tableContainer}`,
    ) as HTMLElement;
    if (!container) return;

    const selectedRow = container.querySelector(
      `tr.${styles.selected}`,
    ) as HTMLElement;
    if (!selectedRow) return;

    const thead = container.querySelector("thead") as HTMLElement;
    const theadHeight = thead ? thead.offsetHeight : 0;

    const rowTop = selectedRow.offsetTop;
    const rowBottom = rowTop + selectedRow.offsetHeight;

    const visibleTop = container.scrollTop + theadHeight;
    const visibleBottom = container.scrollTop + container.clientHeight;

    if (rowTop < visibleTop) {
      container.scrollTop = Math.max(0, rowTop - theadHeight);
    } else if (rowBottom > visibleBottom) {
      container.scrollTop = rowBottom - container.clientHeight;
    }
  }, [selectedRowId, isFormScreen]);

  const sessionUserLabel = useMemo(() => getSessionUserLabel(), []);

  const [lineDropdownIndex, setLineDropdownIndex] = useState<
    Record<string, number>
  >({});

  const activeElement = document.activeElement?.tagName.toLowerCase();
  const isInputFocused =
    activeElement === "input" ||
    activeElement === "select" ||
    activeElement === "textarea";

  const filteredClientOptions = useMemo(
    () => filterClientsByName(clients, clientSearch),
    [clients, clientSearch],
  );

  const calculatedTotal = useMemo(
    () => calculateLinesTotal(saleDraft.lines, productsById),
    [saleDraft.lines, productsById],
  );

  const {
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
  } = useSalePayments<SaleStatus>(calculatedTotal, saleDraft.status);

  async function resolveUnitPrice(
    productId: string,
    clientId: string,
  ): Promise<string> {
    return resolveConfiguredUnitPrice(productId, clientId, clientsById);
  }

  function enviarWhatsApp(sale: Sale): void {
    const client = clientsById.get(sale.clientId);
    const phone = client?.phone ?? "";
    const mensajePredeterminado = `Hola buenas tardes.!! Adjuntamos la factura que se te entregará el dia de mañana. =)`;

    setWhatsappModal({
      show: true,
      sale,
      mensaje: mensajePredeterminado,
      telefono: phone,
    });
  }

  function confirmarEnvioWhatsApp(): void {
    if (!whatsappModal?.sale) return;
    const sale = whatsappModal.sale;
    const client = clientsById.get(sale.clientId);

    if (!whatsappModal.telefono.trim()) {
      setModal({
        show: true,
        type: "error",
        title: "Error",
        message: "El número de teléfono es obligatorio.",
        confirmLabel: "Aceptar",
        onConfirm: closeModal,
      });
      return;
    }

    setWhatsappModal(null);
    generateRouteSaleWhatsappPdf({
      sale,
      client,
      productsById,
      phone: whatsappModal.telefono,
      message: whatsappModal.mensaje,
    });
  }

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

  useEscapeShortcut(showDriversModal, () => setShowDriversModal(false));

  async function refreshDrivers(): Promise<void> {
    const data = await listDrivers();
    setDrivers(data);
  }

  async function onCreateDriver(): Promise<void> {
    if (!canCreateDriver) return;
    if (!driverNameDraft.trim()) {
      setError("El nombre del chofer es requerido.");
      return;
    }
    try {
      await createDriver({
        name: driverNameDraft.trim(),
        status: driverStatusDraft,
      });
      setDriverNameDraft("");
      setDriverStatusDraft("ACTIVE");
      await refreshDrivers();
    } catch (err) {
      setError(readError(err, "No se pudo crear el chofer."));
    }
  }

  async function onToggleDriverStatus(driver: Driver): Promise<void> {
    const nextStatus: DriverStatus =
      driver.status === "ACTIVE" ? "INACTIVE" : "ACTIVE";
    try {
      await updateDriver(driver.id, { name: driver.name, status: nextStatus });
      await refreshDrivers();
    } catch (err) {
      if (isAdminAuthorizationCancelled(err)) return;
      setError(readError(err, "No se pudo actualizar el chofer."));
    }
  }

  async function onDeleteDriver(driver: Driver): Promise<void> {
    if (!canDeleteDriver) return;
    try {
      await deleteDriver(driver.id);
      await refreshDrivers();
      if (saleDraft.driverId === driver.id) {
        setSaleDraft((prev) => ({ ...prev, driverId: "" }));
      }
    } catch (err) {
      setError(readError(err, "No se pudo eliminar el chofer."));
    }
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
      const baseData = await Promise.all([
        hasPermission("CLIENT_READ") ? listClients() : Promise.resolve([]),
        canReadProducts ? listProducts() : Promise.resolve([]),
        canReadDrivers ? listDrivers() : Promise.resolve([]),
      ]);
      setClients(baseData[0]);
      setProducts(baseData[1]);
      setDrivers(baseData[2]);
      if (isFormScreen) {
        if ((isEditScreen || isViewScreen) && id) {
          const sale = await getRouteSaleById(id);
          setInvoiceNumber(sale.invoiceNumber);
          setSaleDraft({
            clientId: sale.clientId,
            driverId: sale.driverId,
            paymentMethod: sale.paymentMethod,
            lines: sale.details.map((detail) => ({
              id: detail.id ?? crypto.randomUUID(),
              productId: detail.productId,
              quantity: detail.quantity,
              unitPrice: String(detail.price),
            })),
            comments: sale.comments ?? "",
            status: sale.status,
          });
          const clientName =
            baseData[0].find((c) => c.id === sale.clientId)?.name ?? "";
          setClientSearch(clientName);

          loadPayments(sale.payments ?? []);
        } else {
          const next = await getNextRouteSaleInvoiceNumber();
          setInvoiceNumber(next);
          setSaleDraft(EMPTY_FORM);
          setClientSearch("");
          setSelectedRowId("");
          setLineSearch({});
          setActiveLineId("");
          setClientDropdownIndex(-1);
          resetPayments();
        }
      } else {
        const salesData = await listRouteSales();
        setSales(salesData);

        const state = location.state as { selectedId?: string } | null;
        if (state?.selectedId) {
          setSelectedRowId(state.selectedId);
          setTimeout(() => {
            const container = document.querySelector(
              `.${styles.tableContainer}`,
            ) as HTMLElement;
            const selectedRow = container?.querySelector(
              `tr.${styles.selected}`,
            ) as HTMLElement;
            if (container && selectedRow) {
              container.scrollTop =
                selectedRow.offsetTop - container.clientHeight / 2;
            }
          }, 100);
        }
      }
    } catch (err) {
      setError(readError(err, "No se pudo cargar la información de rutas."));
    } finally {
      setLoading(false);
    }
  }

  function onCommentChange(event: ChangeEvent<HTMLTextAreaElement>): void {
    setSaleDraft((prev) => ({ ...prev, comments: event.target.value }));
  }

  async function onLineProductChange(
    lineId: string,
    productId: string,
  ): Promise<void> {
    if (!productId) {
      setSaleDraft((prev) => ({
        ...prev,
        lines: replaceLineProduct(prev.lines, lineId, productId, ""),
      }));
      return;
    }

    let unitPrice = "";
    if (saleDraft.clientId) {
      try {
        unitPrice = await resolveUnitPrice(productId, saleDraft.clientId);
      } catch {
        // Keep the row unpriced when its configured price is unavailable.
      }
    }

    setSaleDraft((prev) => ({
      ...prev,
      lines: replaceLineProduct(prev.lines, lineId, productId, unitPrice),
    }));
  }

  function onLineQuantityChange(lineId: string, quantity: string): void {
    setSaleDraft((prev) => ({
      ...prev,
      lines: replaceLineQuantity(prev.lines, lineId, quantity),
    }));
  }

  function addEmptyRow(): void {
    const newLine = {
      id: crypto.randomUUID(),
      productId: "",
      quantity: 1,
      unitPrice: "",
    };

    setSaleDraft((prev) => ({
      ...prev,
      lines: insertEmptyLine(prev.lines, selectedRowId, newLine),
    }));
  }

  function removeSelectedRow(): void {
    if (!selectedRowId) {
      setError("Selecciona una fila para eliminar.");
      return;
    }

    setSaleDraft((prev) => {
      return {
        ...prev,
        lines: removeLine(prev.lines, selectedRowId),
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
      lines: replaceLinePrice(prev.lines, lineId, price),
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
          lines: [{ id: lines[0].id, productId, quantity: 1, unitPrice }],
        };
      }
      return {
        ...prev,
        lines: [
          ...lines,
          { id: crypto.randomUUID(), productId, quantity: 1, unitPrice },
        ],
      };
    });
    setShowProductModal(false);
    setProductModalIndex(-1);
    setProductModalSearch("");
  }

  async function onSave(printAfterSave: boolean): Promise<void> {
    setError("");
    if (isViewScreen) return;
    if (!isEditScreen && !canCreate) return;
    if (!saleDraft.clientId) {
      setModal({
        show: true,
        type: "error",
        title: "Error",
        message: "Selecciona un cliente.",
        confirmLabel: "Aceptar",
        onConfirm: closeModal,
      });
      return;
    }
    if (!saleDraft.driverId) {
      setModal({
        show: true,
        type: "error",
        title: "Error",
        message: "Selecciona un chofer.",
        confirmLabel: "Aceptar",
        onConfirm: closeModal,
      });
      return;
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

    const paid = paymentsPayload.reduce((sum, p) => sum + p.amount, 0);
    if (saleDraft.status === "CANCELLED" && !canCancel) {
      setError("No tienes permiso para cancelar rutas.");
      return;
    }

    if (paid > calculatedTotal) {
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
        driverId: saleDraft.driverId,
        paymentMethod: saleDraft.paymentMethod,
        items: cleanedLines.map((line) => ({
          productId: line.productId,
          quantity: line.quantity,
          price: line.unitPrice !== "" ? Number(line.unitPrice) : undefined,
        })),
        comments: saleDraft.comments,
        status: calculatedStatus,
      };

      const saved =
        isEditScreen && id
          ? await updateRouteSale(id, payload)
          : await createRouteSale(payload);

      if (canUpdate && paymentsPayload.length > 0) {
        await savePayments(saved.id, paymentsPayload);
      }

      if (printAfterSave) {
        printSale(saved);
      }

      const next = await getNextRouteSaleInvoiceNumber();
      setInvoiceNumber(next);
      setSaleDraft(EMPTY_FORM);
      setClientSearch("");
      setSelectedRowId("");
      setLineSearch({});
      resetPayments();

      setModal({
        show: true,
        type: "success",
        title: `Factura número: ${String(saved.invoiceNumber)}`,
        message: `La factura fue creada correctamente.`,
        onConfirm: () => {
          closeModal();
          if (isEditScreen)
            navigate("/route-sales", { state: { selectedId: id } });
        },
        confirmLabel: "Aceptar",
      });
    } catch (err) {
      if (isAdminAuthorizationCancelled(err)) return;
      if (isGloballyReportedError(err)) return;
      setModal({
        show: true,
        type: "error",
        title: "Error",
        message: readError(err, "No se pudo guardar la ruta."),
        confirmLabel: "Aceptar",
        onConfirm: closeModal,
      });
    } finally {
      setSaving(false);
    }
  }

  async function onDeleteSale(saleId: string): Promise<void> {
    if (!canDelete) return;
    setModal({
      show: true,
      type: "confirm",
      danger: true,
      title: "Eliminar ruta",
      message:
        "¿Estás seguro que deseas eliminar esta ruta? Esta acción no se puede deshacer.",
      confirmLabel: "Eliminar",
      cancelLabel: "Cancelar",
      onConfirm: async () => {
        closeModal();
        try {
          await deleteRouteSale(saleId);
          setSales((prev) => prev.filter((sale) => sale.id !== saleId));
        } catch (err) {
          setError(readError(err, "No se pudo eliminar la venta."));
        }
      },
      onCancel: closeModal,
    });
  }

  function onCancelSale(saleId: string): void {
    if (!canCancel) return;
    setModal({
      show: true,
      type: "confirm",
      danger: true,
      title: "Cancelar ruta",
      message: "La factura de ruta quedará cancelada.",
      confirmLabel: "Cancelar ruta",
      cancelLabel: "Volver",
      onConfirm: () =>
        void (async () => {
          closeModal();
          try {
            const updated = await changeRouteSaleStatus(saleId, "CANCELLED");
            setSales((current) =>
              current.map((sale) => (sale.id === saleId ? updated : sale)),
            );
          } catch (caught) {
            setError(readError(caught, "No se pudo cancelar la ruta."));
          }
        })(),
      onCancel: closeModal,
    });
  }

  if (loading) {
    return <ModuleLoadingSkeleton columns={7} variant="history" rows={8} />;
  }

  if (isFormScreen) {
    return (
      <div
        style={{
          background: "#f0f4f0",
          height: "100vh",
          display: "flex",
          justifyContent: "center",
          alignItems: "flex-start",
          padding: "0.5rem",
          overflow: "hidden",
          boxSizing: "border-box",
        }}
      >
        {" "}
        <section className={styles.container}>
          <SaleFormHeader
            title={
              isEditScreen
                ? "Modificar Ruta"
                : isViewScreen
                  ? "Visualización de Factura de Ruta"
                  : "Nueva Factura Ruta"
            }
            error={error}
            styles={styles}
          />

          <div className={styles.card}>
            {" "}
            <div className={styles.topGrid}>
              <SaleInvoiceField
                invoiceNumber={
                  invoiceNumber
                    ? `R-${String(invoiceNumber).padStart(3, "0")}`
                    : "—"
                }
                styles={styles}
              />
              <div className={styles.field}>
                <label>
                  <u>B</u>uscar cliente
                </label>
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
                      if (isViewScreen) return;
                      setShowClientDropdown(true);
                    }}
                    onBlur={() =>
                      setTimeout(() => setShowClientDropdown(false), 200)
                    }
                    onKeyDown={(e) => {
                      if (!showClientDropdown) return;
                      const options = filteredClientOptions;
                      if (e.key === "ArrowDown") {
                        e.preventDefault();
                        setClientDropdownIndex((prev) =>
                          Math.min(prev + 1, options.length - 1),
                        );
                      }
                      if (e.key === "ArrowUp") {
                        e.preventDefault();
                        setClientDropdownIndex((prev) => Math.max(prev - 1, 0));
                      }
                      if (e.key === "Enter" && clientDropdownIndex >= 0) {
                        e.preventDefault();
                        const selected = options[clientDropdownIndex];
                        setSaleDraft((prev) => ({
                          ...prev,
                          clientId: selected.id,
                        }));
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
                    <div
                      className={styles.clientDropdown}
                      style={{ maxHeight: "200px", overflowY: "auto" }}
                    >
                      {filteredClientOptions.map((client, index) => (
                        <div
                          key={client.id}
                          className={styles.clientOption}
                          style={
                            index === clientDropdownIndex
                              ? { background: "#d1fae5", color: "#16a34a" }
                              : {}
                          }
                          onMouseDown={() => {
                            setSaleDraft((prev) => ({
                              ...prev,
                              clientId: client.id,
                            }));
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
                <label>
                  Método de <u>P</u>ago
                </label>
                <div className={styles.paymentsRow}>
                  {[
                    { key: "CASH" as PaymentMethod, label: "Efectivo" },
                    { key: "SINPE" as PaymentMethod, label: "SINPE" },
                    {
                      key: "TRANSFER" as PaymentMethod,
                      label: "Transferencia",
                    },
                    { key: "CARD" as PaymentMethod, label: "Tarjeta" },
                  ].map((item) => (
                    <div key={item.key} className={styles.paymentItem}>
                      <input
                        type="checkbox"
                        checked={paymentDraft[item.key].enabled}
                        onChange={(event) =>
                          onPaymentToggle(item.key, event.target.checked)
                        }
                        disabled={
                          paymentDraft[item.key].amounts.some((entry) =>
                            Boolean(entry.id),
                          ) ||
                          !isEditScreen ||
                          !canUpdate
                        }
                      />
                      <span
                        style={{ fontSize: "0.85rem", whiteSpace: "nowrap" }}
                      >
                        {item.label}
                      </span>
                      {paymentDraft[item.key].enabled && (
                        <div className={styles.paymentAmounts}>
                          {paymentDraft[item.key].amounts.map(
                            (entry, index) => (
                              <div
                                key={entry.id ?? index}
                                className={styles.paymentAmountRow}
                              >
                                <input
                                  type="number"
                                  min="0"
                                  step="0.01"
                                  placeholder="Monto"
                                  value={entry.amount}
                                  onChange={(e) =>
                                    onPaymentAmountChange(
                                      item.key,
                                      index,
                                      e.target.value,
                                    )
                                  }
                                  disabled={
                                    Boolean(entry.id) ||
                                    !isEditScreen ||
                                    !canUpdate
                                  }
                                />

                                {!entry.id &&
                                  isEditScreen &&
                                  canUpdate &&
                                  paymentDraft[item.key].amounts.length > 1 && (
                                    <button
                                      type="button"
                                      className={`${styles.paymentActionButton} ${styles.paymentRemoveButton}`}
                                      onClick={() =>
                                        onPaymentRemoveAmount(item.key, index)
                                      }
                                      title="Eliminar monto"
                                    >
                                      −
                                    </button>
                                  )}
                              </div>
                            ),
                          )}

                          {isEditScreen && canUpdate && (
                            <button
                              type="button"
                              className={`${styles.paymentActionButton} ${styles.paymentAddButton}`}
                              onClick={() => onPaymentAddAmount(item.key)}
                              title="Agregar monto"
                            >
                              +
                            </button>
                          )}
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              </div>
              <div className={styles.field}>
                <label>Chofer</label>
                <select
                  value={saleDraft.driverId}
                  disabled={isViewScreen}
                  onChange={(e) =>
                    setSaleDraft((prev) => ({
                      ...prev,
                      driverId: e.target.value,
                    }))
                  }
                >
                  <option value="">Seleccionar chofer</option>
                  {drivers.map((driver) => (
                    <option key={driver.id} value={driver.id}>
                      {driver.name}
                    </option>
                  ))}
                </select>
              </div>
              <div className={styles.field}>
                <label>Vendedor</label>
                <input value={sessionUserLabel} readOnly />
              </div>
              <div className={styles.field}>
                <label>Estado</label>
                <div
                  style={{
                    display: "flex",
                    alignItems: "flex-start",
                    gap: "0.75rem",
                  }}
                >
                  <select
                    value={calculatedStatus}
                    disabled={!isEditScreen || calculatedStatus === "PAID"}
                    style={{
                      opacity:
                        !isEditScreen || calculatedStatus === "PAID" ? 0.6 : 1,
                    }}
                    onChange={(e) => {
                      const newStatus = e.target.value as SaleStatus;
                      setSaleDraft((prev) => ({ ...prev, status: newStatus }));
                    }}
                  >
                    <option value="PENDING">Pendiente</option>
                    <option value="PARTIAL">Parcial</option>
                    <option value="PAID">Pagada</option>
                    {canCancel && <option value="CANCELLED">Cancelada</option>}
                  </select>
                  <div
                    style={{
                      fontSize: "0.78rem",
                      color: "#4b5563",
                      lineHeight: 1.4,
                    }}
                  >
                    <div>Suma: ₡{paymentTotal.toLocaleString("es-CR")}</div>
                    <div>Total: ₡{calculatedTotal.toLocaleString("es-CR")}</div>
                    {paymentTotal > 0 && paymentTotal < calculatedTotal && (
                      <div style={{ color: "#b45309" }}>
                        ⚠️ ₡
                        {(calculatedTotal - paymentTotal).toLocaleString(
                          "es-CR",
                        )}
                      </div>
                    )}
                    {paymentTotal > calculatedTotal && (
                      <div style={{ color: "#dc2626" }}>
                        ❌ ₡
                        {(paymentTotal - calculatedTotal).toLocaleString(
                          "es-CR",
                        )}
                      </div>
                    )}
                    {paymentTotal > 0 && paymentTotal === calculatedTotal && (
                      <div style={{ color: "#16a34a" }}>✅ Completo</div>
                    )}
                  </div>
                </div>
              </div>
            </div>
            {!isViewScreen && (
              <div className={styles.menuBar}>
                <button
                  className={styles.primaryButton}
                  type="button"
                  disabled={!canCreateProduct}
                  onClick={() =>
                    canCreateProduct && setShowCreateProductModal(true)
                  }
                >
                  Crear producto <kbd>F2</kbd>
                </button>
                <button
                  className={styles.button}
                  type="button"
                  disabled={!canReadProducts}
                  onClick={() => canReadProducts && setShowProductModal(true)}
                >
                  Listar productos <kbd>F3</kbd>
                </button>
                <button
                  className={styles.button}
                  type="button"
                  disabled={!canReadProducts || !canReadPrices}
                  onClick={() => {
                    if (!canReadProducts || !canReadPrices) return;
                    const line = saleDraft.lines.find(
                      (item) => item.id === selectedRowId,
                    );
                    const product = line
                      ? productsById.get(line.productId)
                      : undefined;
                    if (product) {
                      setViewProduct(product);
                    } else {
                      setError("Selecciona una fila con producto.");
                    }
                  }}
                >
                  Ver producto <kbd>F4</kbd>
                </button>
                <button
                  className={styles.button}
                  type="button"
                  onClick={addEmptyRow}
                >
                  Agregar fila <kbd>F5</kbd>
                </button>
                <button
                  className={styles.dangerButton}
                  type="button"
                  onClick={removeSelectedRow}
                >
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
                    const unitPrice =
                      line.unitPrice !== ""
                        ? Number(line.unitPrice)
                        : product
                          ? Number(product.price)
                          : 0;
                    return (
                      <tr
                        key={line.id}
                        ref={selectedRowId === line.id ? selectedRowRef : null}
                        onClick={() => setSelectedRowId(line.id)}
                        className={
                          selectedRowId === line.id ? styles.selected : ""
                        }
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
                              setLineSearch((prev) => ({
                                ...prev,
                                [line.id]: e.target.value,
                              }));
                              void onLineProductChange(line.id, "");
                              setLineDropdownIndex((prev) => ({
                                ...prev,
                                [line.id]: -1,
                              }));
                            }}
                            onFocus={(e) => {
                              if (isViewScreen) return;
                              setActiveLineId(line.id);
                              setSelectedRowId(line.id);
                              setActiveCell({ rowId: line.id, col: "name" });
                              const rect =
                                e.currentTarget.getBoundingClientRect();
                              setDropdownPosition({
                                top: rect.bottom,
                                left: rect.left,
                              });
                            }}
                            onBlur={() =>
                              setTimeout(() => setActiveLineId(""), 500)
                            }
                            onKeyDown={(e) => {
                              if (activeLineId !== line.id) return;
                              const options = products
                                .filter((p) =>
                                  p.name
                                    .toLowerCase()
                                    .includes(
                                      (lineSearch[line.id] ?? "").toLowerCase(),
                                    ),
                                )
                                .slice(0, 5);
                              if (e.key === "ArrowDown") {
                                e.preventDefault();
                                setLineDropdownIndex((prev) => ({
                                  ...prev,
                                  [line.id]: Math.min(
                                    (prev[line.id] ?? -1) + 1,
                                    options.length - 1,
                                  ),
                                }));
                              }
                              if (e.key === "ArrowUp") {
                                e.preventDefault();
                                setLineDropdownIndex((prev) => ({
                                  ...prev,
                                  [line.id]: Math.max(
                                    (prev[line.id] ?? 0) - 1,
                                    0,
                                  ),
                                }));
                              }
                              if (
                                e.key === "Enter" &&
                                (lineDropdownIndex[line.id] ?? -1) >= 0
                              ) {
                                e.preventDefault();
                                const selected =
                                  options[lineDropdownIndex[line.id]];
                                void onLineProductChange(line.id, selected.id);
                                setLineSearch((prev) => ({
                                  ...prev,
                                  [line.id]: "",
                                }));
                                setActiveLineId("");
                                setLineDropdownIndex((prev) => ({
                                  ...prev,
                                  [line.id]: -1,
                                }));
                              }
                              if (e.key === "Escape") {
                                setActiveLineId("");
                                setLineDropdownIndex((prev) => ({
                                  ...prev,
                                  [line.id]: -1,
                                }));
                              }

                              if (
                                e.key === "ArrowRight" &&
                                (lineDropdownIndex[line.id] ?? -1) < 0
                              ) {
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
                                pointerEvents: "all",
                              }}
                            >
                              {products
                                .filter((p) =>
                                  p.name
                                    .toLowerCase()
                                    .includes(
                                      (lineSearch[line.id] ?? "").toLowerCase(),
                                    ),
                                )
                                .slice(0, 5)
                                .map((p, index) => (
                                  <div
                                    key={p.id}
                                    className={styles.clientOption}
                                    style={
                                      index ===
                                      (lineDropdownIndex[line.id] ?? -1)
                                        ? {
                                            background: "#d1fae5",
                                            color: "#16a34a",
                                          }
                                        : {}
                                    }
                                    onMouseEnter={() =>
                                      setLineDropdownIndex((prev) => ({
                                        ...prev,
                                        [line.id]: index,
                                      }))
                                    }
                                    onMouseDown={() => {
                                      void onLineProductChange(line.id, p.id);
                                      setLineSearch((prev) => ({
                                        ...prev,
                                        [line.id]: "",
                                      }));
                                      setActiveLineId("");
                                      setLineDropdownIndex((prev) => ({
                                        ...prev,
                                        [line.id]: -1,
                                      }));
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
                            ref={(el) => {
                              cellRefs.current[`${line.id}-quantity`] = el;
                            }}
                            type="number"
                            min={1}
                            step={1}
                            value={line.quantity}
                            onFocus={() => {
                              setSelectedRowId(line.id);
                              setActiveCell({
                                rowId: line.id,
                                col: "quantity",
                              });
                            }}
                            onBlur={() => setActiveCell(null)}
                            onChange={(event) =>
                              onLineQuantityChange(line.id, event.target.value)
                            }
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
                                const idx = lines.findIndex(
                                  (l) => l.id === line.id,
                                );
                                if (lines[idx + 1])
                                  focusCell(lines[idx + 1].id, "quantity");
                              }
                              if (e.key === "ArrowUp") {
                                e.preventDefault();
                                const lines = saleDraft.lines;
                                const idx = lines.findIndex(
                                  (l) => l.id === line.id,
                                );
                                if (lines[idx - 1])
                                  focusCell(lines[idx - 1].id, "quantity");
                              }
                            }}
                          />
                        </td>
                        <td>
                          <input
                            readOnly={isViewScreen}
                            ref={(el) => {
                              cellRefs.current[`${line.id}-price`] = el;
                            }}
                            type="number"
                            min="0"
                            step="0.01"
                            value={
                              line.unitPrice !== "" ? line.unitPrice : unitPrice
                            }
                            onFocus={() => {
                              setSelectedRowId(line.id);
                              setActiveCell({ rowId: line.id, col: "price" });
                            }}
                            onBlur={() => setActiveCell(null)}
                            onChange={(e) =>
                              onLinePriceChange(line.id, e.target.value)
                            }
                            onKeyDown={(e) => {
                              if (e.key === "ArrowLeft") {
                                e.preventDefault();
                                focusCell(line.id, "quantity");
                              }
                              if (e.key === "ArrowDown") {
                                e.preventDefault();
                                const lines = saleDraft.lines;
                                const idx = lines.findIndex(
                                  (l) => l.id === line.id,
                                );
                                if (lines[idx + 1])
                                  focusCell(lines[idx + 1].id, "price");
                              }
                              if (e.key === "ArrowUp") {
                                e.preventDefault();
                                const lines = saleDraft.lines;
                                const idx = lines.findIndex(
                                  (l) => l.id === line.id,
                                );
                                if (lines[idx - 1])
                                  focusCell(lines[idx - 1].id, "price");
                              }
                            }}
                          />
                        </td>
                        <td>
                          {(unitPrice * line.quantity).toLocaleString("es-CR")}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
            <div className={styles.commentsWrap}>
              <button
                className={styles.commentsToggle}
                type="button"
                onClick={() => setShowComments((prev) => !prev)}
              >
                {showComments
                  ? "▲ Ocultar comentarios"
                  : "▼ Comentarios (solo impresión)"}
              </button>
              {showComments && (
                <textarea
                  value={saleDraft.comments}
                  onChange={onCommentChange}
                  rows={2}
                  style={{ marginTop: "0.4rem" }}
                />
              )}
            </div>
          </div>

          <div className={styles.formBottomBar}>
            <p
              className={styles.totalBar}
              style={{ fontSize: "2rem", fontWeight: "bold" }}
            >
              Total: ₡{calculatedTotal.toLocaleString("es-CR")}
            </p>
            <div className={styles.bottomActions}>
              {!isViewScreen && (
                <>
                  <button
                    className={styles.primaryButton}
                    type="button"
                    disabled={saving || (!isEditScreen && !canCreate)}
                    onClick={() => void onSave(false)}
                  >
                    Guard<u>a</u>r
                  </button>
                  <button
                    className={styles.button}
                    type="button"
                    disabled={saving || (!isEditScreen && !canCreate)}
                    onClick={() => void onSave(true)}
                  >
                    Guardar e I<u>m</u>primir
                  </button>
                </>
              )}
              <button
                className={styles.button}
                type="button"
                onClick={() =>
                  navigate("/route-sales", { state: { selectedId: id } })
                }
              >
                <u>S</u>alir
              </button>
            </div>
          </div>

          {showCreateProductModal && (
            <div className={styles.modalBackdrop}>
              <div className={styles.modal}>
                <header className={styles.modalHeader}>
                  <h3>Crear Producto</h3>
                  <button
                    className={styles.button}
                    type="button"
                    onClick={() => setShowCreateProductModal(false)}
                  >
                    Cerrar <kbd>Esc</kbd>
                  </button>
                </header>

                <div
                  className={styles.topGrid}
                  style={{ gridTemplateColumns: "1fr 1fr" }}
                >
                  <div className={styles.field}>
                    <label>Nombre</label>
                    <input
                      value={productDraft.name}
                      onChange={(e) =>
                        setProductDraft((prev) => ({
                          ...prev,
                          name: e.target.value,
                        }))
                      }
                      placeholder="Nombre del producto"
                    />
                  </div>
                  <div className={styles.field}>
                    <label>Descripción</label>
                    <input
                      value={productDraft.description}
                      onChange={(e) =>
                        setProductDraft((prev) => ({
                          ...prev,
                          description: e.target.value,
                        }))
                      }
                      placeholder="Opcional"
                    />
                  </div>
                  <div className={styles.field}>
                    <label>Stock</label>
                    <input
                      type="number"
                      min="0"
                      value={productDraft.stock}
                      onChange={(e) =>
                        setProductDraft((prev) => ({
                          ...prev,
                          stock: e.target.value,
                        }))
                      }
                    />
                  </div>
                  <div className={styles.field}>
                    <label>Precio Detalle</label>
                    <input
                      id="priceDetailInput"
                      type="number"
                      min="0"
                      value={productDraft.priceDetail}
                      onChange={(e) =>
                        setProductDraft((prev) => ({
                          ...prev,
                          priceDetail: e.target.value,
                        }))
                      }
                      onKeyDown={(e) => {
                        if (e.key === "Enter") {
                          e.preventDefault();
                          document
                            .querySelector<HTMLInputElement>(
                              "#priceDetailInput",
                            )
                            ?.focus();
                        }
                      }}
                    />
                  </div>
                  <div className={styles.field}>
                    <label>Precio Mayorista</label>
                    <input
                      id="priceWholesaleInput"
                      type="number"
                      min="0"
                      value={productDraft.priceWholesale}
                      onChange={(e) =>
                        setProductDraft((prev) => ({
                          ...prev,
                          priceWholesale: e.target.value,
                        }))
                      }
                      onKeyDown={(e) => {
                        if (e.key === "Enter") {
                          e.preventDefault();
                          document
                            .querySelector<HTMLButtonElement>("#saveProductBtn")
                            ?.click();
                        }
                      }}
                    />
                  </div>
                  <div className={styles.field}>
                    <label>Precio Nuevo</label>
                    <input
                      type="number"
                      min="0"
                      value={productDraft.priceNew}
                      onChange={(e) =>
                        setProductDraft((prev) => ({
                          ...prev,
                          priceNew: e.target.value,
                        }))
                      }
                    />
                  </div>
                </div>

                <div
                  className={styles.formBottomBar}
                  style={{ background: "transparent", padding: 0 }}
                >
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
                          status: "ACTIVE",
                        });

                        await createProductPrice(
                          created.id,
                          "DETAIL",
                          Number(productDraft.priceDetail),
                        );
                        await createProductPrice(
                          created.id,
                          "WHOLESALE",
                          Number(productDraft.priceWholesale),
                        );
                        await createProductPrice(
                          created.id,
                          "NEW",
                          Number(productDraft.priceNew),
                        );

                        const updated = await listProducts();
                        setProducts(
                          updated.filter((p) => p.status === "ACTIVE"),
                        );
                        setSaleDraft((prev) => ({
                          ...prev,
                          lines: [
                            ...prev.lines,
                            {
                              id: crypto.randomUUID(),
                              productId: created.id,
                              quantity: 1,
                              unitPrice: "",
                            },
                          ],
                        }));
                        setProductDraft({
                          name: "",
                          description: "",
                          stock: "0",
                          priceDetail: "0",
                          priceWholesale: "0",
                          priceNew: "0",
                        });
                        setShowCreateProductModal(false);
                        setModal({
                          show: true,
                          type: "success",
                          title: "Producto creado",
                          message: `El producto "${created.name}" fue creado y agregado a la factura.`,
                          onConfirm: closeModal,
                          confirmLabel: "Aceptar",
                        });
                      } catch (error) {
                        if (!isGloballyReportedError(error)) {
                          setError("No se pudo crear el producto.");
                        }
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
                  <button
                    className={styles.button}
                    type="button"
                    onClick={() => {
                      setShowProductModal(false);
                      setProductModalSearch("");
                    }}
                  >
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
                    }}
                    autoFocus
                    onKeyDown={(e) => {
                      const filtered = products.filter((p) =>
                        p.name
                          .toLowerCase()
                          .includes(productModalSearch.toLowerCase()),
                      );
                      if (e.key === "ArrowDown") {
                        e.preventDefault();
                        setProductModalIndex((prev) =>
                          Math.min(prev + 1, filtered.length - 1),
                        );
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
                        .filter((p) =>
                          p.name
                            .toLowerCase()
                            .includes(productModalSearch.toLowerCase()),
                        )
                        .map((product, index) => (
                          <tr
                            key={product.id}
                            style={
                              index === productModalIndex
                                ? { background: "#dcfce7", cursor: "pointer" }
                                : { cursor: "pointer" }
                            }
                            onMouseEnter={() => setProductModalIndex(index)}
                            onClick={() => {
                              addProductFromModal(product.id);
                              setProductModalIndex(-1);
                            }}
                          >
                            <td>{product.name}</td>
                            <td>{product.stock}</td>
                            <td>
                              ₡{Number(product.price).toLocaleString("es-CR")}
                            </td>
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
          )}

          {viewProduct && (
            <div className={styles.modalBackdrop}>
              <div className={styles.modal}>
                <header className={styles.modalHeader}>
                  <h3>Detalle del Producto</h3>
                  <button
                    className={styles.button}
                    type="button"
                    onClick={() => {
                      setViewProduct(null);
                      setViewProductPrices([]);
                    }}
                  >
                    Cerrar <kbd>Esc</kbd>
                  </button>
                </header>

                <div
                  style={{
                    display: "grid",
                    gridTemplateColumns: "1fr 1fr",
                    gap: "1rem",
                    padding: "0.5rem 0",
                  }}
                >
                  <div className={styles.field}>
                    <label>Nombre</label>
                    <input value={viewProduct.name} readOnly />
                  </div>
                  <div className={styles.field}>
                    <label>Estado</label>
                    <input
                      value={
                        viewProduct.status === "ACTIVE" ? "Activo" : "Inactivo"
                      }
                      readOnly
                      disabled={isViewScreen}
                    />
                  </div>
                  <div className={styles.field}>
                    <label>Stock</label>
                    <input value={viewProduct.stock} readOnly />
                  </div>
                  {viewProduct.description && (
                    <div
                      className={styles.field}
                      style={{ gridColumn: "span 2" }}
                    >
                      <label>Descripción</label>
                      <input value={viewProduct.description} readOnly />
                    </div>
                  )}
                  <div className={styles.field}>
                    <label>Precio Detalle</label>
                    <input
                      value={
                        viewProductPrices.find((p) => p.type === "DETAIL")
                          ? `₡${Number(viewProductPrices.find((p) => p.type === "DETAIL")?.price).toLocaleString("es-CR")}`
                          : "No definido"
                      }
                      readOnly
                    />
                  </div>
                  <div className={styles.field}>
                    <label>Precio Mayorista</label>
                    <input
                      value={
                        viewProductPrices.find((p) => p.type === "WHOLESALE")
                          ? `₡${Number(viewProductPrices.find((p) => p.type === "WHOLESALE")?.price).toLocaleString("es-CR")}`
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
              routeTicket
            />
          )}
        </section>
      </div>
    );
  }

  const selectedSale = sortedAndFilteredSales.find(
    (s) => s.id === selectedRowId,
  );

  return (
    <div
      style={{
        background: "#f0f4f0",
        height: "100vh",
        display: "flex",
        justifyContent: "center",
        alignItems: "flex-start",
        padding: "1rem",
        overflow: "hidden",
        boxSizing: "border-box",
      }}
    >
      {" "}
      <section className={styles.container}>
        <header className={styles.header}>
          <h2 className={styles.title}>RUTAS</h2>
          <div className={styles.headerActions}>
            <span style={{ color: "#9ca3af", fontSize: "0.9rem" }}>
              {new Date().toLocaleDateString("es-CR")}
            </span>
          </div>
        </header>

        {error ? <p className={styles.error}>{error}</p> : null}

        <SaleHistoryFilters
          filters={historyFilters}
          clients={clients}
          styles={styles}
          isViewScreen={isViewScreen}
          clientLimit={6}
          statusLabels={{
            pending: "Pendiente",
            partial: "Parcial",
            paid: "Pagada",
            cancelled: "Cancelada",
          }}
          onClear={() => setClientSearch("")}
        />
        <SaleHistoryTable
          sales={sortedAndFilteredSales}
          clientsById={clientsById}
          selectedRowId={selectedRowId}
          emptyMessage="No hay rutas registradas."
          styles={styles}
          formatInvoiceNumber={(sale) =>
            `R-${String(sale.invoiceNumber).padStart(3, "0")}`
          }
          formatPaymentMethod={mapPaymentMethod}
          formatStatus={mapStatus}
          onSelect={setSelectedRowId}
        />

        <RouteSalesHistoryActions
          canCreate={canCreate}
          canDelete={canDelete}
          canCancel={canCancel}
          canUpdate={canUpdate}
          canReadDrivers={canReadDrivers}
          hasSelection={Boolean(selectedRowId)}
          selectedStatus={selectedSale?.status}
          styles={styles}
          onCreate={() => navigate("/route-sales/new")}
          onModify={onAbrirModificar}
          onView={() =>
            selectedRowId && navigate(`/route-sales/${selectedRowId}/view`)
          }
          onPrint={() => {
            const sale = sortedAndFilteredSales.find(
              (s) => s.id === selectedRowId,
            );
            if (sale) printSale(sale);
          }}
          onWhatsapp={() => {
            const sale = sortedAndFilteredSales.find(
              (s) => s.id === selectedRowId,
            );
            if (sale) enviarWhatsApp(sale);
          }}
          onDelete={() => selectedRowId && void onDeleteSale(selectedRowId)}
          onCancel={() => selectedRowId && onCancelSale(selectedRowId)}
          onPay={() =>
            selectedRowId && navigate(`/route-sales/${selectedRowId}/edit`)
          }
          onDrivers={() => setShowDriversModal(true)}
          onExit={() => navigate("/dashboard")}
        />
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
                <button
                  className={styles.button}
                  type="button"
                  onClick={() => setShowAbrirCajaModal(false)}
                >
                  Cerrar
                </button>
              </header>
              <div className={styles.field}>
                <label>Monto inicial de efectivo</label>
                <div
                  style={{
                    display: "flex",
                    gap: "0.5rem",
                    alignItems: "center",
                  }}
                >
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
                <span style={{ fontSize: "0.78rem", color: "#6b7280" }}>
                  Por defecto: ₡30,000
                </span>
              </div>
              <p
                style={{
                  color: "#b45309",
                  fontSize: "0.9rem",
                  margin: "0.5rem 0",
                }}
              >
                ⚠️ Recuerde vaciar la memoria del datáfono antes de iniciar.
              </p>
              {canCreateDriver && (
                <div
                  style={{
                    display: "flex",
                    gap: "0.5rem",
                    marginTop: "0.5rem",
                  }}
                >
                  <button
                    className={styles.primaryButton}
                    type="button"
                    onClick={abrirCaja}
                  >
                    Iniciar Caja
                  </button>
                  <button
                    className={styles.button}
                    type="button"
                    onClick={() => setShowAbrirCajaModal(false)}
                  >
                    Cancelar
                  </button>
                </div>
              )}
            </div>
          </div>
        )}
        {showDriversModal && (
          <div className={styles.modalBackdrop}>
            <div className={styles.modal}>
              <header className={styles.modalHeader}>
                <h3>Choferes</h3>
                <button
                  className={styles.button}
                  type="button"
                  onClick={() => setShowDriversModal(false)}
                >
                  Cerrar <kbd>Esc</kbd>
                </button>
              </header>

              <div
                style={{
                  display: "grid",
                  gridTemplateColumns: "1fr 140px auto",
                  gap: "0.5rem",
                  marginBottom: "0.75rem",
                }}
              >
                <input
                  value={driverNameDraft}
                  onChange={(e) => setDriverNameDraft(e.target.value)}
                  placeholder="Nombre del chofer"
                />
                <select
                  value={driverStatusDraft}
                  onChange={(e) =>
                    setDriverStatusDraft(e.target.value as DriverStatus)
                  }
                >
                  <option value="ACTIVE">Activo</option>
                  <option value="INACTIVE">Inactivo</option>
                </select>
                <button
                  className={styles.primaryButton}
                  type="button"
                  onClick={() => void onCreateDriver()}
                >
                  Crear
                </button>
              </div>

              <div
                style={{
                  maxHeight: 260,
                  overflowY: "auto",
                  border: "1px solid #e5e7eb",
                  borderRadius: 8,
                }}
              >
                <table className={styles.table}>
                  <thead>
                    <tr>
                      <th>Nombre</th>
                      <th>Estado</th>
                      <th>Acciones</th>
                    </tr>
                  </thead>
                  <tbody>
                    {drivers.map((driver) => (
                      <tr key={driver.id}>
                        <td>{driver.name}</td>
                        <td>
                          {driver.status === "ACTIVE" ? "Activo" : "Inactivo"}
                        </td>
                        <td>
                          <div className={styles.rowActions}>
                            <button
                              className={styles.button}
                              type="button"
                              onClick={() => void onToggleDriverStatus(driver)}
                            >
                              Editar
                            </button>
                            {canDeleteDriver && (
                              <button
                                className={styles.dangerButton}
                                type="button"
                                onClick={() => void onDeleteDriver(driver)}
                              >
                                Eliminar
                              </button>
                            )}
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        )}
        {saleToPrint && !cierreToPrint && (
          <TicketPrint
            sale={saleToPrint}
            client={clientsById.get(saleToPrint.clientId)}
            productsById={productsById}
            routeTicket
          />
        )}
        {!saleToPrint && cierreToPrint && (
          <CierreCajaPrint data={cierreToPrint} />
        )}
        {whatsappModal?.show && (
          <div className={styles.modalBackdrop}>
            <div className={styles.modal}>
              <header className={styles.modalHeader}>
                <h3>Enviar por WhatsApp</h3>
                <button
                  className={styles.button}
                  type="button"
                  onClick={() => setWhatsappModal(null)}
                >
                  Cerrar <kbd>Esc</kbd>
                </button>
              </header>
              <div className={styles.field}>
                <label>Número de teléfono</label>
                <input
                  type="text"
                  value={whatsappModal.telefono}
                  onChange={(e) =>
                    setWhatsappModal((prev) =>
                      prev ? { ...prev, telefono: e.target.value } : null,
                    )
                  }
                  placeholder="Ej: 88888888"
                />
              </div>
              <div className={styles.field} style={{ marginTop: "0.75rem" }}>
                <label>Mensaje</label>
                <textarea
                  rows={4}
                  value={whatsappModal.mensaje}
                  onChange={(e) =>
                    setWhatsappModal((prev) =>
                      prev ? { ...prev, mensaje: e.target.value } : null,
                    )
                  }
                  style={{
                    resize: "none",
                    border: "1px solid #d1d5db",
                    borderRadius: 8,
                    padding: "0.5rem",
                    font: "inherit",
                  }}
                />
              </div>
              <div
                style={{ display: "flex", gap: "0.5rem", marginTop: "1rem" }}
              >
                <button
                  className={styles.primaryButton}
                  type="button"
                  onClick={confirmarEnvioWhatsApp}
                >
                  Enviar <kbd>Enter</kbd>
                </button>
                <button
                  className={styles.button}
                  type="button"
                  onClick={() => setWhatsappModal(null)}
                >
                  Cancelar
                </button>
              </div>
            </div>
          </div>
        )}
        {showModificarModal && (
          <div className={styles.modalBackdrop}>
            <div className={styles.modal}>
              <header className={styles.modalHeader}>
                <h3>Modificar Factura</h3>
                <button
                  className={styles.button}
                  type="button"
                  onClick={() => setShowModificarModal(false)}
                >
                  Cerrar
                </button>
              </header>
              <div className={styles.field}>
                <label>Número de factura</label>
                <input
                  type="number"
                  autoFocus
                  value={modificarInvoiceInput}
                  onChange={(e) => setModificarInvoiceInput(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter") void onConfirmarModificar();
                    if (e.key === "Escape") setShowModificarModal(false);
                  }}
                  placeholder="Ej: 123"
                />
              </div>
              <div
                style={{ display: "flex", gap: "0.5rem", marginTop: "1rem" }}
              >
                <button
                  className={styles.primaryButton}
                  type="button"
                  onClick={() => void onConfirmarModificar()}
                >
                  Abrir
                </button>
                <button
                  className={styles.button}
                  type="button"
                  onClick={() => setShowModificarModal(false)}
                >
                  Cancelar
                </button>
              </div>
            </div>
          </div>
        )}
      </section>
    </div>
  );
}

function mapStatus(status: SaleStatus): string {
  if (status === "PAID") {
    return "Pagada";
  }
  if (status === "CANCELLED") {
    return "Cancelada";
  }
  return "Pendiente";
}
