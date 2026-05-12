import type { ChangeEvent, FormEvent, ReactElement } from "react";
import { useEffect, useMemo, useState } from "react";
import {
  createUser,
  deleteUser,
  listRoles,
  listUsers,
  updateUser,
  type CreateUserPayload,
  type RoleOption,
  type UpdateUserPayload,
  type User,
  type UserStatus,
} from "../api/users";
import styles from "./Users.module.css";

import { useNavigate } from "react-router-dom";
import { isAdmin } from "../api/auth-utils";


interface UserFormState {
  username: string;
  email: string;
  fullName: string;
  password: string;
  status: UserStatus;
  roleIds: string[];
}

const INITIAL_FORM: UserFormState = {
  username: "",
  email: "",
  fullName: "",
  password: "",
  status: "ACTIVE",
  roleIds: [],
};

export default function Users(): ReactElement {
  const [users, setUsers] = useState<User[]>([]);
  const [roles, setRoles] = useState<RoleOption[]>([]);
  const [form, setForm] = useState<UserFormState>(INITIAL_FORM);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string>("");


  const navigate = useNavigate();
  const [modal, setModal] = useState<{
    show: boolean;
    message: string;
    onConfirm?: () => void;
  }>({ show: false, message: "" });

  useEffect(() => {
    void bootstrap();
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
    () => (editingId ? "Guardar cambios" : "Crear usuario"),
    [editingId]
  );

  async function bootstrap(): Promise<void> {
    setLoading(true);
    setError("");
    try {
      const [usersData, rolesData] = await Promise.all([listUsers(), listRoles()]);
      setUsers(usersData);
      setRoles(rolesData);
    } catch (err) {
      setError(readError(err, "No se pudieron cargar usuarios o roles."));
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

  function onRolesChange(event: ChangeEvent<HTMLSelectElement>): void {
    const selected = Array.from(event.target.selectedOptions).map(
      (option) => option.value
    );
    setForm((prev) => ({ ...prev, roleIds: selected }));
  }

  async function onSubmit(event: FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault();
    setError("");

    if (!form.username.trim() && !editingId) {
      setError("El username es obligatorio.");
      return;
    }
    if (!form.email.trim()) {
      setError("El email es obligatorio.");
      return;
    }
    if (!form.fullName.trim()) {
      setError("El nombre completo es obligatorio.");
      return;
    }
    if (!editingId && form.password.trim().length < 8) {
      setError("La contraseña debe tener al menos 8 caracteres.");
      return;
    }
    if (form.roleIds.length === 0) {
      setError("Selecciona al menos un rol.");
      return;
    }

    try {
      if (editingId) {
        const payload: UpdateUserPayload = {
          email: form.email.trim(),
          fullName: form.fullName.trim(),
          status: form.status,
          roleIds: form.roleIds,
        };
        const updated = await updateUser(editingId, payload);
        setUsers((prev) =>
          prev.map((user) => (user.id === editingId ? updated : user))

        );
        setModal({ show: true, message: "Usuario actualizado correctamente." });
      } else {
        const payload: CreateUserPayload = {
          username: form.username.trim(),
          email: form.email.trim(),
          fullName: form.fullName.trim(),
          password: form.password.trim(),
          status: form.status,
          roleIds: form.roleIds,
        };
        const created = await createUser(payload);
        setUsers((prev) => [created, ...prev]);
        setModal({ show: true, message: "Usuario creado correctamente." });
      }
      resetForm();
    } catch (err) {
      setError(readError(err, "No se pudo guardar el usuario."));
    }
  }

  function onEdit(user: User): void {
    setEditingId(user.id);
    setForm({
      username: user.username,
      email: user.email,
      fullName: user.fullName,
      password: "",
      status: user.status,
      roleIds: user.roles.map((role) => role.id),
    });
  }

  async function onDelete(userId: string): Promise<void> {
    setModal({
      show: true,
      message: "¿Estás seguro que deseas eliminar este usuario?",
      onConfirm: async () => {
        setModal({ show: false, message: "" });
        try {
          await deleteUser(userId);
          setUsers((prev) => prev.filter((user) => user.id !== userId));
          if (editingId === userId) resetForm();
        } catch (err) {
          setError(readError(err, "No se pudo eliminar el usuario."));
        }
      },
    });
  }



  function resetForm(): void {
    setForm(INITIAL_FORM);
    setEditingId(null);
  }

  return (
    <div style={{ background: "#f0f4f0", minHeight: "100vh", display: "flex", justifyContent: "center", alignItems: "flex-start", padding: "1rem" }}>
      <section className={styles.container}>
        <header className={styles.header}>
          <h2 className={styles.title}>Usuarios</h2>
          <button className={styles.buttonSecondary} type="button" onClick={() => navigate("/dashboard")}>
            <u>S</u>alir
          </button>
        </header>

        <div className={styles.card}>
          <form className={styles.form} onSubmit={onSubmit}>
            <div className={styles.field}>
              <label htmlFor="username">Username</label>
              <input id="username" name="username" value={form.username} onChange={onInputChange} required={!editingId} disabled={Boolean(editingId)} />
            </div>
            <div className={styles.field}>
              <label htmlFor="email">Email</label>
              <input id="email" name="email" type="email" value={form.email} onChange={onInputChange} required />
            </div>
            <div className={styles.field}>
              <label htmlFor="fullName">Nombre completo</label>
              <input id="fullName" name="fullName" value={form.fullName} onChange={onInputChange} required />
            </div>
            <div className={styles.field}>
              <label htmlFor="password">Contraseña</label>
              <input id="password" name="password" type="password" value={form.password} onChange={onInputChange} required={!editingId} placeholder={editingId ? "No editable en actualización" : ""} disabled={Boolean(editingId)} />
            </div>
            <div className={styles.field}>
              <label htmlFor="status">Estado</label>
              <select id="status" name="status" value={form.status} onChange={onInputChange}>
                <option value="ACTIVE">Activo</option>
                <option value="BLOCKED">Bloqueado</option>
                <option value="INACTIVE">Inactivo</option>
              </select>
            </div>
            <div className={styles.field}>
              <label htmlFor="roleIds">Roles</label>
              <select id="roleIds" multiple className={styles.roles} value={form.roleIds} onChange={onRolesChange}>
                {roles.map((role) => (
                  <option key={role.id} value={role.id}>{role.name}</option>
                ))}
              </select>
            </div>
            <div className={styles.actions}>
              <button className={`${styles.button} ${styles.primary}`} type="submit">{submitLabel}</button>
              {editingId ? (
                <button className={`${styles.button} ${styles.secondary}`} type="button" onClick={resetForm}>Cancelar</button>
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
                  <th>Username</th>
                  <th>Email</th>
                  <th>Nombre completo</th>
                  <th>Estado</th>
                  <th>Roles</th>
                  <th>Acciones</th>
                </tr>
              </thead>
              <tbody>
                {users.map((user) => (
                  <tr key={user.id}>
                    <td>{user.username}</td>
                    <td>{user.email}</td>
                    <td>{user.fullName}</td>
                    <td>
                      <span className={`${styles.status} ${user.status === "ACTIVE" ? styles.active : user.status === "BLOCKED" ? styles.blocked : styles.inactive}`}>
                        {user.status === "ACTIVE" ? "Activo" : user.status === "BLOCKED" ? "Bloqueado" : "Inactivo"}
                      </span>
                    </td>
                    <td>
                      <div className={styles.rolesWrap}>
                        {user.roles.map((role) => (
                          <span key={role.id} className={styles.roleTag}>{role.name}</span>
                        ))}
                      </div>
                    </td>
                    <td>
                      <div className={styles.rowActions}>
                        {isAdmin() && (
                          <button className={`${styles.button} ${styles.secondary}`} type="button" onClick={() => onEdit(user)}>
                            Editar
                          </button>
                        )}
                        {isAdmin() && (
                          <button className={`${styles.button} ${styles.danger}`} type="button" onClick={() => void onDelete(user.id)}>
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
          {!loading && users.length === 0 ? <p className={styles.empty}>No hay usuarios registrados.</p> : null}
        </div>

        {modal.show && (
          <div style={{ position: "fixed", inset: 0, background: "rgba(0,0,0,0.5)", display: "flex", alignItems: "center", justifyContent: "center", zIndex: 100 }}>
            <div style={{ background: "white", borderRadius: 12, padding: "1.5rem", maxWidth: 400, width: "90%", textAlign: "center" }}>
              <p style={{ fontSize: "2rem", margin: 0 }}>{modal.onConfirm ? "⚠️" : "✅"}</p>
              <h3 style={{ margin: "0.5rem 0" }}>{modal.onConfirm ? "Confirmar" : editingId ? "Usuario actualizado" : "Usuario creado"}</h3>
              <p>{modal.message}</p>
              {modal.onConfirm ? (
                <div style={{ display: "flex", gap: "0.5rem", justifyContent: "center" }}>
                  <button className={`${styles.button} ${styles.danger}`} type="button" onClick={modal.onConfirm}>
                    Eliminar <kbd>Enter</kbd>
                  </button>
                  <button className={`${styles.button} ${styles.secondary}`} type="button" onClick={() => setModal({ show: false, message: "" })}>
                    Cancelar <kbd>Esc</kbd>
                  </button>
                </div>
              ) : (
                <button className={`${styles.button} ${styles.primary}`} type="button" onClick={() => setModal({ show: false, message: "" })}>
                  Aceptar <kbd>Enter</kbd>
                </button>
              )}
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
