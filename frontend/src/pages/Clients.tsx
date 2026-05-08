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

  useEffect(() => {
    void loadClients();
  }, []);

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
      } else {
        const created = await createClient(payload);
        setClients((prev) => [created, ...prev]);
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
    <section className={styles.page}>
      <header className={styles.header}>
        <h2 className={styles.title}>Clientes</h2>
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
              <option value="DETAIL">DETAIL</option>
              <option value="WHOLESALE">WHOLESALE</option>
              <option value="NEW">NEW</option>
            </select>
          </div>

          <div className={styles.field}>
            <label htmlFor="status">Estado</label>
            <select
              id="status"
              name="status"
              value={form.status}
              onChange={onInputChange}
            >
              <option value="ACTIVE">ACTIVE</option>
              <option value="INACTIVE">INACTIVE</option>
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
                    <span
                      className={`${styles.badge} ${client.type === "DETAIL" ? styles.detail : styles.wholesale
                        }`}
                    >
                      {client.type}
                    </span>
                  </td>
                  <td>
                    <span
                      className={`${styles.badge} ${client.status === "ACTIVE" ? styles.active : styles.inactive
                        }`}
                    >
                      {client.status}
                    </span>
                  </td>
                  <td>
                    <div className={styles.rowActions}>
                      <button
                        className={`${styles.button} ${styles.secondary}`}
                        type="button"
                        onClick={() => onEdit(client)}
                      >
                        Editar
                      </button>
                      <button
                        className={`${styles.button} ${styles.danger}`}
                        type="button"
                        onClick={() => void onDelete(client.id)}
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

        {!loading && clients.length === 0 ? (
          <p className={styles.empty}>No hay clientes registrados.</p>
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
