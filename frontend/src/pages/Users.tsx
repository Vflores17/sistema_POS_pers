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

  useEffect(() => {
    void bootstrap();
  }, []);

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
    const confirmed = window.confirm("¿Eliminar este usuario?");
    if (!confirmed) {
      return;
    }

    try {
      await deleteUser(userId);
      setUsers((prev) => prev.filter((user) => user.id !== userId));

      if (editingId === userId) {
        resetForm();
      }
    } catch (err) {
      setError(readError(err, "No se pudo eliminar el usuario."));
    }
  }

  async function onToggleBlock(user: User): Promise<void> {
    try {
      const nextStatus: UserStatus = user.status === "BLOCKED" ? "ACTIVE" : "BLOCKED";
      const updated = await updateUser(user.id, {
        email: user.email,
        fullName: user.fullName,
        status: nextStatus,
        roleIds: user.roles.map((role) => role.id),
      });
      setUsers((prev) => prev.map((item) => (item.id === user.id ? updated : item)));

      if (editingId === user.id) {
        setForm((prev) => ({ ...prev, status: nextStatus }));
      }
    } catch (err) {
      setError(readError(err, "No se pudo cambiar el estado del usuario."));
    }
  }

  function resetForm(): void {
    setForm(INITIAL_FORM);
    setEditingId(null);
  }

  return (
    <section className={styles.page}>
      <header className={styles.header}>
        <h2 className={styles.title}>Usuarios</h2>
      </header>

      <div className={styles.card}>
        <form className={styles.form} onSubmit={onSubmit}>
          <div className={styles.field}>
            <label htmlFor="username">Username</label>
            <input
              id="username"
              name="username"
              value={form.username}
              onChange={onInputChange}
              required={!editingId}
              disabled={Boolean(editingId)}
            />
          </div>

          <div className={styles.field}>
            <label htmlFor="email">Email</label>
            <input
              id="email"
              name="email"
              type="email"
              value={form.email}
              onChange={onInputChange}
              required
            />
          </div>

          <div className={styles.field}>
            <label htmlFor="fullName">Nombre completo</label>
            <input
              id="fullName"
              name="fullName"
              value={form.fullName}
              onChange={onInputChange}
              required
            />
          </div>

          <div className={styles.field}>
            <label htmlFor="password">Contraseña</label>
            <input
              id="password"
              name="password"
              type="password"
              value={form.password}
              onChange={onInputChange}
              required={!editingId}
              placeholder={editingId ? "No editable en actualización" : ""}
              disabled={Boolean(editingId)}
            />
          </div>

          <div className={styles.field}>
            <label htmlFor="status">Estado</label>
            <select id="status" name="status" value={form.status} onChange={onInputChange}>
              <option value="ACTIVE">ACTIVE</option>
              <option value="BLOCKED">BLOCKED</option>
              <option value="INACTIVE">INACTIVE</option>
            </select>
          </div>

          <div className={styles.field}>
            <label htmlFor="roleIds">Roles</label>
            <select
              id="roleIds"
              multiple
              className={styles.roles}
              value={form.roleIds}
              onChange={onRolesChange}
            >
              {roles.map((role) => (
                <option key={role.id} value={role.id}>
                  {role.name}
                </option>
              ))}
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
                    <span
                      className={`${styles.status} ${
                        user.status === "ACTIVE"
                          ? styles.active
                          : user.status === "BLOCKED"
                          ? styles.blocked
                          : styles.inactive
                      }`}
                    >
                      {user.status}
                    </span>
                  </td>
                  <td>
                    <div className={styles.rolesWrap}>
                      {user.roles.map((role) => (
                        <span key={role.id} className={styles.roleTag}>
                          {role.name}
                        </span>
                      ))}
                    </div>
                  </td>
                  <td>
                    <div className={styles.rowActions}>
                      <button
                        className={`${styles.button} ${styles.secondary}`}
                        type="button"
                        onClick={() => onEdit(user)}
                      >
                        Editar
                      </button>
                      <button
                        className={`${styles.button} ${styles.warning}`}
                        type="button"
                        onClick={() => void onToggleBlock(user)}
                      >
                        {user.status === "BLOCKED" ? "Activar" : "Bloquear"}
                      </button>
                      <button
                        className={`${styles.button} ${styles.danger}`}
                        type="button"
                        onClick={() => void onDelete(user.id)}
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

        {!loading && users.length === 0 ? (
          <p className={styles.empty}>No hay usuarios registrados.</p>
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
