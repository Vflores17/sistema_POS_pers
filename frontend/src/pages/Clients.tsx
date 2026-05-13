import type { ChangeEvent, FormEvent, ReactElement } from "react";
import { useEffect, useMemo, useState } from "react";
import {
  createClient,
  deleteClient,
  listClients,
  updateClient,
  type Client,
  type ClientPayload,
  type ClientStatus,
  type ClientType,
} from "../api/clients";
import styles from "./Clients.module.css";
import { useNavigate } from "react-router-dom"; // 👈
import { isAdmin } from "../api/auth-utils";

interface ClientFormState {
  name: string;
  phone: string;
  type: ClientType;
  status: ClientStatus;
}

const INITIAL_FORM: ClientFormState = {
  name: "",
  phone: "",
  type: "DETAIL",
  status: "ACTIVE",
};

export default function Clients(): ReactElement {
  const [clients, setClients] = useState<Client[]>([]);
  const [form, setForm] = useState<ClientFormState>(INITIAL_FORM);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string>("");

  const navigate = useNavigate();
  const [modal, setModal] = useState<{ show: boolean; message: string }>({ show: false, message: "" });

  useEffect(() => {
    void loadClients();
  }, []);

  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent): void {
      if (e.altKey && e.key.toLowerCase() === "s") {
        e.preventDefault();
        navigate("/dashboard");
      }
      if (e.key === "Enter" && modal.show) {
        setModal({ show: false, message: "" });
      }
    }
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [navigate, modal.show]);

  const submitLabel = useMemo(
    () => (editingId ? "Guardar cambios" : "Crear cliente"),
    [editingId]
  );

  async function loadClients(): Promise<void> {
    setLoading(true);
    setError("");
    try {
      const data = await listClients();
      setClients(data);
    } catch (err) {
      setError(readError(err, "No se pudieron cargar los clientes."));
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

    const payload: ClientPayload = {
      name: form.name.trim(),
      phone: form.phone.trim(),
      type: form.type,
      status: form.status,
    };

    if (!payload.name) {
      setError("El nombre es obligatorio.");
      return;
    }

    try {
      if (editingId) {
        const updated = await updateClient(editingId, payload);
        setClients((prev) =>
          prev.map((client) => (client.id === editingId ? updated : client))
        );
        setModal({ show: true, message: editingId ? "Cliente actualizado correctamente." : "Cliente creado correctamente." });
      } else {
        const created = await createClient(payload);
        setClients((prev) => [created, ...prev]);
        setModal({ show: true, message: editingId ? "Cliente actualizado correctamente." : "Cliente creado correctamente." });
      }
      resetForm();
    } catch (err) {
      setError(readError(err, "No se pudo guardar el cliente."));
    }
  }

  function onEdit(client: Client): void {
    setEditingId(client.id);
    setForm({
      name: client.name ?? "",
      phone: client.phone ?? "",
      type: client.type ?? "DETAIL",
      status: client.status ?? "ACTIVE",
    });
  }

  async function onDelete(clientId: string): Promise<void> {
    const confirmed = window.confirm("¿Eliminar este cliente?");
    if (!confirmed) {
      return;
    }

    try {
      await deleteClient(clientId);
      setClients((prev) => prev.filter((client) => client.id !== clientId));

      if (editingId === clientId) {
        resetForm();
      }
    } catch (err) {
      setError(readError(err, "No se pudo eliminar el cliente."));
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
          <h2 className={styles.title}>Clientes</h2>
          <button className={styles.buttonSecondary} type="button" onClick={() => navigate("/dashboard")}>
            <u>S</u>alir
          </button>
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
              <label htmlFor="phone">Teléfono</label>
              <input
                id="phone"
                name="phone"
                value={form.phone}
                onChange={onInputChange}
              />
            </div>

            <div className={styles.field}>
              <label htmlFor="type">Tipo</label>
              <select id="type" name="type" value={form.type} onChange={onInputChange}>
                <option value="DETAIL">Detalle</option>
                <option value="WHOLESALE">Mayorista</option>
                <option value="NEW">Precios Nuevos</option>
              </select>
            </div>

            <div className={styles.field}>
              <label htmlFor="status">Estado</label>
              <select
                id="status"
                name="status"
                value={form.status}
                onChange={onInputChange}
                disabled={!editingId}               >
                <option value="ACTIVE">Activo</option>
                <option value="INACTIVE">Inactivo</option>
              </select>
            </div>

            <div className={styles.actions}>
              <button className={`${styles.button} ${styles.primary}`} type="submit">
                {submitLabel}
              </button>
              {editingId ? (
                <button className={`${styles.button} ${styles.secondary}`} type="button" onClick={resetForm}>
                  Cancelar
                </button>
              ) : null}
            </div>
          </form>
        </div>

        <div className={styles.card}>
          {error ? <p className={styles.error}>{error}</p> : null}
          <div style={{ maxHeight: "450px", overflowY: "auto", border: "1px solid #e5e7eb", borderRadius: 10 }}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th>Nombre</th>
                  <th>Teléfono</th>
                  <th>Tipo</th>
                  <th>Estado</th>
                  <th>Acciones</th>
                </tr>
              </thead>
              <tbody>
                {clients.map((client) => (
                  <tr key={client.id}>
                    <td>{client.name}</td>
                    <td>{client.phone || "-"}</td>
                    <td>
                      <span className={`${styles.badge} ${client.type === "DETAIL" ? styles.detail : client.type === "WHOLESALE" ? styles.wholesale : styles.new}`}>
                        {client.type === "DETAIL" ? "Detalle" : client.type === "WHOLESALE" ? "Mayorista" : "Nuevo"}
                      </span>
                    </td>
                    <td>
                      <span className={`${styles.badge} ${client.status === "ACTIVE" ? styles.active : styles.inactive}`}>
                        {client.status === "ACTIVE" ? "Activo" : "Inactivo"}
                      </span>
                    </td>
                    <td>
                      <div className={styles.rowActions}>
                        {isAdmin() && (
                          <button className={`${styles.button} ${styles.secondary}`} type="button" onClick={() => onEdit(client)}>
                            Editar
                          </button>
                        )}
                        {isAdmin() && (
                          <button className={`${styles.button} ${styles.danger}`} type="button" onClick={() => void onDelete(client.id)}>
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

          {!loading && clients.length === 0 ? (
            <p className={styles.empty}>No hay clientes registrados.</p>
          ) : null}
        </div>

        {modal.show && (
          <div style={{ position: "fixed", inset: 0, background: "rgba(0,0,0,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 100 }}>
            <div style={{ background: "white", borderRadius: 12, padding: "1.5rem", maxWidth: 400, width: "90%", textAlign: "center" }}>
              <p style={{ fontSize: "2rem", margin: 0 }}>✅</p>
              <h3 style={{ margin: "0.5rem 0" }}>{editingId ? "Cliente actualizado" : "Cliente creado"}</h3>
              <p>{modal.message}</p>
              <button className={`${styles.button} ${styles.primary}`} type="button" onClick={() => setModal({ show: false, message: "" })}>
                Aceptar <kbd>Enter</kbd>
              </button>
            </div>
          </div>
        )}

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
