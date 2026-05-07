import type { ChangeEvent, FormEvent, ReactElement } from "react";
import { useEffect, useMemo, useState } from "react";
import {
  createProduct,
  deleteProduct,
  listProducts,
  updateProduct,
  type Product,
  type ProductPayload,
  type ProductStatus,
} from "../api/products";
import styles from "./Products.module.css";

interface ProductFormState {
  name: string;
  description: string;
  stock: string;
  price: string;
  status: ProductStatus;
}

const INITIAL_FORM: ProductFormState = {
  name: "",
  description: "",
  stock: "0",
  price: "0",
  status: "ACTIVE",
};

export default function Products(): ReactElement {
  const [products, setProducts] = useState<Product[]>([]);
  const [form, setForm] = useState<ProductFormState>(INITIAL_FORM);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string>("");

  useEffect(() => {
    void loadProducts();
  }, []);

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
      price: Number(form.price),
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
    if (Number.isNaN(payload.price) || payload.price <= 0) {
      setError("El precio debe ser mayor a 0.");
      return;
    }

    try {
      if (editingId) {
        const updated = await updateProduct(editingId, payload);
        setProducts((prev) =>
          prev.map((product) => (product.id === editingId ? updated : product))
        );
      } else {
        const created = await createProduct(payload);
        setProducts((prev) => [created, ...prev]);
      }
      resetForm();
    } catch (err) {
      setError(readError(err, "No se pudo guardar el producto."));
    }
  }

  function onEdit(product: Product): void {
    setEditingId(product.id);
    setForm({
      name: product.name ?? "",
      description: product.description ?? "",
      stock: String(product.stock ?? 0),
      price: String(product.price ?? 0),
      status: product.status ?? "ACTIVE",
    });
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
    <section className={styles.page}>
      <header className={styles.header}>
        <h2 className={styles.title}>Productos</h2>
      </header>

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
            />
          </div>

          <div className={styles.field}>
            <label htmlFor="price">Precio</label>
            <input
              id="price"
              name="price"
              type="number"
              min="0.01"
              step="0.01"
              value={form.price}
              onChange={onInputChange}
              required
            />
          </div>

          <div className={styles.field}>
            <label htmlFor="status">Estado</label>
            <select
              id="status"
              name="status"
              value={form.status}
              onChange={onInputChange}
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
        <div className={styles.tableWrap}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th>Nombre</th>
                <th>Descripción</th>
                <th>Stock</th>
                <th>Precio</th>
                <th>Estado</th>
                <th>Acciones</th>
              </tr>
            </thead>
            <tbody>
              {products.map((product) => (
                <tr key={product.id}>
                  <td>{product.name}</td>
                  <td>{product.description ?? "-"}</td>
                  <td>{product.stock}</td>
                  <td>{Number(product.price).toFixed(2)}</td>
                  <td>
                    <span
                      className={`${styles.status} ${
                        product.status === "ACTIVE" ? styles.active : styles.inactive
                      }`}
                    >
                      {product.status}
                    </span>
                  </td>
                  <td>
                    <div className={styles.rowActions}>
                      <button
                        className={`${styles.button} ${styles.secondary}`}
                        type="button"
                        onClick={() => onEdit(product)}
                      >
                        Editar
                      </button>
                      <button
                        className={`${styles.button} ${styles.danger}`}
                        type="button"
                        onClick={() => void onDelete(product.id)}
                      >
                        Eliminar
                      </button>
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
      </div>
    </section>
  );
}

function readError(error: unknown, fallback: string): string {
  if (error instanceof Error && error.message.trim().length > 0) {
    return error.message;
  }
  return fallback;
}
