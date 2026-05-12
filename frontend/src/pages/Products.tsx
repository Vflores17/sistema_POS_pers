import type { ChangeEvent, FormEvent, ReactElement } from "react";
import { useEffect, useMemo, useState } from "react";
import {
  createProduct,
  deleteProduct,
  listProducts,
  updateProduct,
  getProductPrices,
  createProductPrice,
  type Product,
  type ProductPayload,
  type ProductStatus,
} from "../api/products";
import styles from "./Products.module.css";
import { useNavigate } from "react-router-dom";
import { isAdmin } from "../api/auth-utils";

interface ProductFormState {
  name: string;
  description: string;
  stock: string;
  price: string;
  priceDetail: string;
  priceWholesale: string;
  priceNew: string;
  status: ProductStatus;
}

const INITIAL_FORM: ProductFormState = {
  name: "",
  description: "",
  stock: "999999",
  price: "0",
  priceDetail: "0",
  priceWholesale: "0",
  priceNew: "0",
  status: "ACTIVE",
};

export default function Products(): ReactElement {
  const [products, setProducts] = useState<Product[]>([]);
  const [form, setForm] = useState<ProductFormState>(INITIAL_FORM);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string>("");
  const [modal, setModal] = useState<{ show: boolean; message: string }>({ show: false, message: "" });
  const navigate = useNavigate();
  const [productPrices, setProductPrices] = useState<Record<string, { detail: number; wholesale: number; new: number }>>({});

  useEffect(() => {
    void loadProducts();
  }, []);

  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent): void {
      if (e.altKey && e.key.toLowerCase() === "s") {
        e.preventDefault();
        navigate("/dashboard");
      }
    }
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [navigate]);

  useEffect(() => {
    if (!modal.show) return;
    function handleKeyDown(e: KeyboardEvent): void {
      if (e.key === "Enter") setModal({ show: false, message: "" });
    }
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [modal.show]);

  const submitLabel = useMemo(
    () => (editingId ? "Guardar cambios" : "Crear producto"),
    [editingId]
  );

  async function loadProducts(): Promise<void> {
    setLoading(true);
    setError("");
    try {
      const data = await listProducts();
      setProducts(data);

      // 👈 cargar precios de todos los productos
      const pricesMap: Record<string, { detail: number; wholesale: number; new: number }> = {};
      await Promise.all(data.map(async (product) => {
        try {
          const prices = await getProductPrices(product.id);
          pricesMap[product.id] = {
            detail: prices.find(p => p.type === "DETAIL")?.price ?? 0,
            wholesale: prices.find(p => p.type === "WHOLESALE")?.price ?? 0,
            new: prices.find(p => p.type === "NEW")?.price ?? 0,
          };
        } catch {
          pricesMap[product.id] = { detail: 0, wholesale: 0, new: 0 };
        }
      }));
      setProductPrices(pricesMap);

    } catch (err) {
      setError(readError(err, "No se pudieron cargar los productos."));
    } finally {
      setLoading(false);
    }
  }

  function onInputChange(
    event: ChangeEvent<HTMLInputElement | HTMLSelectElement>
  ): void {
    const { name, value } = event.target;
    setForm((prev) => ({ ...prev, [name]: value }));
  }

  async function onSubmit(event: FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault();
    setError("");

    const payload: ProductPayload = {
      name: form.name.trim(),
      description: form.description.trim(),
      stock: Number(form.stock),
      price: Number(form.priceDetail),
      status: form.status,
    };

    if (!payload.name) {
      setError("El nombre es obligatorio.");
      return;
    }
    if (Number.isNaN(payload.stock) || payload.stock < 0) {
      setError("El stock debe ser un número mayor o igual a 0.");
      return;
    }
    if (Number.isNaN(payload.price) || payload.price < 0) {
      setError("El precio no puede ser negativo.");
      return;
    }

    try {
      if (editingId) {
        const updated = await updateProduct(editingId, payload);
        // Actualizar precios
        await createProductPrice(editingId, "DETAIL", Number(form.priceDetail));
        await createProductPrice(editingId, "WHOLESALE", Number(form.priceWholesale));
        await createProductPrice(editingId, "NEW", Number(form.priceNew));
        setProducts((prev) =>
          prev.map((product) => (product.id === editingId ? updated : product))
        );
        setModal({ show: true, message: "Producto actualizado correctamente." });

      } else {
        const created = await createProduct(payload);
        // Crear precios
        await createProductPrice(created.id, "DETAIL", Number(form.priceDetail));
        await createProductPrice(created.id, "WHOLESALE", Number(form.priceWholesale));
        await createProductPrice(created.id, "NEW", Number(form.priceNew));
        setProducts((prev) => [created, ...prev]);
        setModal({ show: true, message: "Producto creado correctamente." });

      }
      await loadProducts();

      resetForm();
    } catch (err) {
      setError(readError(err, "No se pudo guardar el producto."));
    }
  }

  async function onEdit(product: Product): Promise<void> {
    setEditingId(product.id);
    setForm({
      name: product.name ?? "",
      description: product.description ?? "",
      stock: String(product.stock ?? 0),
      price: String(product.price ?? 0),
      priceDetail: "0",
      priceWholesale: "0",
      priceNew: "0",
      status: product.status ?? "ACTIVE",
    });

    try {
      const prices = await getProductPrices(product.id);
      setForm((prev) => ({
        ...prev,
        priceDetail: String(prices.find(p => p.type === "DETAIL")?.price ?? 0),
        priceWholesale: String(prices.find(p => p.type === "WHOLESALE")?.price ?? 0),
        priceNew: String(prices.find(p => p.type === "NEW")?.price ?? 0),
      }));
    } catch {
      // si falla deja en 0
    }
  }

  async function onDelete(productId: string): Promise<void> {
    const confirmed = window.confirm("¿Eliminar este producto?");
    if (!confirmed) {
      return;
    }

    try {
      await deleteProduct(productId);
      setProducts((prev) => prev.filter((product) => product.id !== productId));

      if (editingId === productId) {
        resetForm();
      }
    } catch (err) {
      setError(readError(err, "No se pudo eliminar el producto."));
    }
  }

  function resetForm(): void {
    setForm(INITIAL_FORM);
    setEditingId(null);
  }

  return (
    <div style={{ background: "#f0f4f0", minHeight: "100vh", display: "flex", justifyContent: "center", alignItems: "flex-start", padding: "1rem" }}>
      <section className={styles.container}>
        <header className={styles.header}>
          <h2 className={styles.title}>Productos</h2>
          <button className={styles.buttonSecondary} type="button" onClick={() => navigate("/dashboard")}>
            <u>S</u>alir
          </button>
        </header>
        <section className={styles.page}>

          <div className={styles.card}>
            <form className={styles.form} onSubmit={onSubmit}>
              <div className={styles.field}>
                <label htmlFor="name">Nombre</label>
                <input
                  id="name"
                  name="name"
                  value={form.name}
                  onChange={onInputChange}
                  required
                />
              </div>

              <div className={styles.field}>
                <label htmlFor="description">Descripción</label>
                <input
                  id="description"
                  name="description"
                  value={form.description}
                  onChange={onInputChange}
                  placeholder="Opcional"
                />
              </div>

              <div className={styles.field}>
                <label htmlFor="stock">Stock</label>
                <input
                  id="stock"
                  name="stock"
                  type="number"
                  min="0"
                  step="1"
                  value={form.stock}
                  onChange={onInputChange}
                  required
                  disabled={!editingId}
                />
              </div>

              <div className={styles.field}>
                <label htmlFor="priceDetail">Precio Detalle</label>
                <input
                  id="priceDetail"
                  name="priceDetail"
                  type="number"
                  min="0"
                  step="0.01"
                  value={form.priceDetail}
                  onChange={onInputChange}
                />
              </div>

              <div className={styles.field}>
                <label htmlFor="priceWholesale">Precio Mayorista</label>
                <input
                  id="priceWholesale"
                  name="priceWholesale"
                  type="number"
                  min="0"
                  step="0.01"
                  value={form.priceWholesale}
                  onChange={onInputChange}
                />
              </div>

              <div className={styles.field}>
                <label htmlFor="priceNew">Precio Nuevo</label>
                <input
                  id="priceNew"
                  name="priceNew"
                  type="number"
                  min="0"
                  step="0.01"
                  value={form.priceNew}
                  onChange={onInputChange}
                />
              </div>

              <div className={styles.field}>
                <label htmlFor="status">Estado</label>
                <select
                  id="status"
                  name="status"
                  value={form.status}
                  onChange={onInputChange}
                  disabled={!editingId}
                >
                  <option value="ACTIVE">Activo</option>
                  <option value="INACTIVE">Inactivo</option>
                </select>
              </div>

              <div className={styles.actions}>
                <button className={`${styles.button} ${styles.primary}`} type="submit">
                  {submitLabel}
                </button>
                {editingId ? (
                  <button
                    className={`${styles.button} ${styles.secondary}`}
                    type="button"
                    onClick={resetForm}
                  >
                    Cancelar
                  </button>
                ) : null}
              </div>
            </form>
          </div>

          <div className={styles.card}>
            {error ? <p className={styles.error}>{error}</p> : null}
            <div style={{ maxHeight: "625px", overflowY: "auto", border: "1px solid #e5e7eb", borderRadius: 10 }}>
              <table className={styles.table}>
                <thead>
                  <tr>
                    <th>Nombre</th>
                    <th>Descripción</th>
                    <th>P. Detalle</th>
                    <th>P. Mayorista</th>
                    <th>P. Nuevo</th>
                    <th>Estado</th>
                    <th>Acciones</th>
                  </tr>
                </thead>
                <tbody>
                  {products.map((product) => (
                    <tr key={product.id}>
                      <td>{product.name}</td>
                      <td>{product.description ?? "-"}</td>
                      <td>₡{(productPrices[product.id]?.detail ?? 0).toLocaleString('es-CR')}</td>
                      <td>₡{(productPrices[product.id]?.wholesale ?? 0).toLocaleString('es-CR')}</td>
                      <td>₡{(productPrices[product.id]?.new ?? 0).toLocaleString('es-CR')}</td>
                      <td>
                        <span className={`${styles.status} ${product.status === "ACTIVE" ? styles.active : styles.inactive}`}>
                          {product.status === "ACTIVE" ? "Activo" : "Inactivo"}
                        </span>
                      </td>
                      <td>
                        <div className={styles.rowActions}>
                          {isAdmin() && (
                            <button className={`${styles.button} ${styles.secondary}`} type="button" onClick={() => onEdit(product)}>
                              Editar
                            </button>
                          )}
                          {isAdmin() && (
                            <button className={`${styles.button} ${styles.danger}`} type="button" onClick={() => void onDelete(product.id)}>
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

            {!loading && products.length === 0 ? (
              <p className={styles.empty}>No hay productos registrados.</p>
            ) : null}
            {modal.show && (
              <div style={{ position: "fixed", inset: 0, background: "rgba(0,0,0,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 100 }}>
                <div style={{ background: "white", borderRadius: 12, padding: "1.5rem", maxWidth: 400, width: "90%", textAlign: "center" }}>
                  <p style={{ fontSize: "2rem", margin: 0 }}>✅</p>
                  <h3 style={{ margin: "0.5rem 0" }}>{editingId ? "Producto actualizado" : "Producto creado"}</h3>
                  <p>{modal.message}</p>
                  <button
                    className={`${styles.button} ${styles.primary}`}
                    type="button"
                    onClick={() => setModal({ show: false, message: "" })}
                  >
                    Aceptar
                  </button>
                </div>
              </div>
            )}
          </div>
        </section>
      </section>
    </div>
  );
}

function readError(error: unknown, fallback: string): string {
  if (error instanceof Error && error.message.trim().length > 0) {
    return error.message;
  }
  return fallback;
}
