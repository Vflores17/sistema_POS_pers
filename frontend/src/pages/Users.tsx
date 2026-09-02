import type { ChangeEvent, FormEvent, ReactElement } from "react";
import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { usePermissions } from "../auth/PermissionContext";
import {
  clearUserPermissionOverrides, createUser, deleteUser,
  getUserPermissions, listPermissions, listRoles, listUsers,
  replaceUserPermissionOverrides, updateUser,
  type CreateUserPayload, type Permission, type PermissionOverrideEffect,
  type RoleOption, type UpdateUserPayload, type User, type UserPermissions, type UserStatus,
} from "../api/users";
import styles from "./Users.module.css";
import ModuleLoadingSkeleton from "../components/ModuleLoadingSkeleton";
import SkeletonBlock from "../components/SkeletonBlock";
import { isGloballyReportedError } from "../api/errors";

interface UserFormState {
  username: string;
  email: string;
  fullName: string;
  password: string;
  status: UserStatus;
  roleIds: string[];
}

type OverrideChoice = "INHERITED" | PermissionOverrideEffect;

const INITIAL_FORM: UserFormState = {
  username: "", email: "", fullName: "", password: "", status: "ACTIVE", roleIds: [],
};

const MODULES = [
  ["SALES", "Ventas"], ["CLIENTS", "Clientes"], ["PRODUCTS", "Productos"],
  ["PRICES", "Precios"], ["ROUTES", "Rutas"], ["DRIVERS", "Choferes"],
  ["USERS", "Usuarios"], ["ROLES", "Roles"], ["PERMISSIONS", "Permisos"],
] as const;

export default function Users(): ReactElement {
  const { hasPermission, hasAllPermissions } = usePermissions();
  const canCreate = hasAllPermissions("USER_CREATE", "USER_ASSIGN_ROLE");
  const canUpdate = hasAllPermissions("USER_UPDATE", "USER_ASSIGN_ROLE");
  const canDelete = hasPermission("USER_DELETE");
  const canAssignPermissions = hasPermission("USER_ASSIGN_PERMISSION");
  const canReadPermissionCatalog = hasPermission("PERMISSION_READ");
  const canReadRoles = hasPermission("ROLE_READ");
  const [users, setUsers] = useState<User[]>([]);
  const [roles, setRoles] = useState<RoleOption[]>([]);
  const [permissions, setPermissions] = useState<Permission[]>([]);
  const [form, setForm] = useState<UserFormState>(INITIAL_FORM);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [permissionInfo, setPermissionInfo] = useState<UserPermissions | null>(null);
  const [choices, setChoices] = useState<Record<string, OverrideChoice>>({});
  const [loading, setLoading] = useState(true);
  const [loadingPermissions, setLoadingPermissions] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const navigate = useNavigate();
  const [modal, setModal] = useState<{ show: boolean; message: string; onConfirm?: () => void }>({
    show: false, message: "",
  });

  useEffect(() => { void bootstrap(); }, []);

  useEffect(() => {
    function handleKeyDown(event: KeyboardEvent): void {
      if (event.altKey && event.key.toLowerCase() === "s") {
        event.preventDefault();
        navigate("/dashboard");
      }
      if (event.key === "Enter" && modal.show && !modal.onConfirm) setModal({ show: false, message: "" });
      if (event.key === "Escape" && modal.show) setModal({ show: false, message: "" });
    }
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [navigate, modal]);

  const submitLabel = editingId ? "Guardar cambios" : "Crear usuario";
  const groupedPermissions = useMemo(() => MODULES.map(([code, label]) => ({
    code, label,
    items: permissions.filter((permission) => permission.module.toUpperCase() === code)
      .sort((left, right) => left.code.localeCompare(right.code)),
  })).filter((group) => group.items.length > 0), [permissions]);

  async function bootstrap(): Promise<void> {
    setLoading(true);
    setError("");
    try {
      const [usersData, rolesData] = await Promise.all([
        listUsers(), canReadRoles ? listRoles() : Promise.resolve([]),
      ]);
      setUsers(usersData);
      setRoles(rolesData);
      if (canAssignPermissions && canReadPermissionCatalog) setPermissions(await listPermissions());
    } catch (caught) {
      setError(readError(caught, "No se pudieron cargar usuarios, roles o permisos."));
    } finally {
      setLoading(false);
    }
  }

  function onInputChange(event: ChangeEvent<HTMLInputElement | HTMLSelectElement>): void {
    const { name, value } = event.target;
    setForm((current) => ({ ...current, [name]: value }));
  }

  function onRolesChange(event: ChangeEvent<HTMLSelectElement>): void {
    const roleIds = Array.from(event.target.selectedOptions).map((option) => option.value);
    setForm((current) => ({ ...current, roleIds }));
  }

  function choose(permissionCode: string, choice: OverrideChoice): void {
    setChoices((current) => ({ ...current, [permissionCode]: choice }));
  }

  function buildOverrides(): Array<{ permissionId: string; effect: PermissionOverrideEffect }> {
    return permissions.flatMap((permission) => {
      const effect = choices[permission.code] ?? "INHERITED";
      return effect === "INHERITED" ? [] : [{ permissionId: permission.id, effect }];
    });
  }

  async function onSubmit(event: FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault();
    setError("");
    if (editingId ? !canUpdate : !canCreate) return;
    if (!editingId && !form.username.trim()) return setError("El username es obligatorio.");
    if (!form.email.trim()) return setError("El email es obligatorio.");
    if (!form.fullName.trim()) return setError("El nombre completo es obligatorio.");
    if (!editingId && form.password.trim().length < 8) return setError("La contraseña debe tener al menos 8 caracteres.");
    if (form.roleIds.length === 0) return setError("Selecciona al menos un rol.");

    setSaving(true);
    try {
      let saved: User;
      if (editingId) {
        const payload: UpdateUserPayload = {
          email: form.email.trim(), fullName: form.fullName.trim(), status: form.status, roleIds: form.roleIds,
        };
        saved = await updateUser(editingId, payload);
      } else {
        const payload: CreateUserPayload = {
          username: form.username.trim(), email: form.email.trim(), fullName: form.fullName.trim(),
          password: form.password.trim(), status: form.status, roleIds: form.roleIds,
        };
        saved = await createUser(payload);
      }

      const overrides = buildOverrides();
      if (canAssignPermissions && (editingId !== null || overrides.length > 0)) {
        await replaceUserPermissionOverrides(saved.id, overrides);
      }
      setUsers((current) => editingId
        ? current.map((user) => user.id === saved.id ? saved : user)
        : [saved, ...current]);
      setModal({ show: true, message: editingId ? "Usuario actualizado correctamente." : "Usuario creado correctamente." });
      resetForm();
    } catch (caught) {
      setError(readError(caught, "No se pudo guardar el usuario."));
    } finally {
      setSaving(false);
    }
  }

  async function onEdit(user: User): Promise<void> {
    setEditingId(user.id);
    setForm({
      username: user.username, email: user.email, fullName: user.fullName, password: "",
      status: user.status, roleIds: user.roles.map((role) => role.id),
    });
    setPermissionInfo(null);
    setChoices({});
    if (!canAssignPermissions) return;
    setLoadingPermissions(true);
    try {
      const info = await getUserPermissions(user.id);
      const nextChoices: Record<string, OverrideChoice> = {};
      info.allowedPermissions.forEach((code) => { nextChoices[code] = "ALLOW"; });
      info.deniedPermissions.forEach((code) => { nextChoices[code] = "DENY"; });
      setPermissionInfo(info);
      setChoices(nextChoices);
    } catch (caught) {
      setError(readError(caught, "No se pudieron cargar los permisos del usuario."));
    } finally {
      setLoadingPermissions(false);
    }
  }

  async function resetAllOverrides(): Promise<void> {
    if (!editingId) {
      setChoices({});
      return;
    }
    setLoadingPermissions(true);
    try {
      setPermissionInfo(await clearUserPermissionOverrides(editingId));
      setChoices({});
    } catch (caught) {
      setError(readError(caught, "No se pudieron restablecer los permisos."));
    } finally {
      setLoadingPermissions(false);
    }
  }

  function onDelete(userId: string): void {
    setModal({
      show: true,
      message: "¿Estás seguro que deseas eliminar este usuario?",
      onConfirm: () => void (async () => {
        setModal({ show: false, message: "" });
        try {
          await deleteUser(userId);
          setUsers((current) => current.filter((user) => user.id !== userId));
          if (editingId === userId) resetForm();
        } catch (caught) {
          setError(readError(caught, "No se pudo eliminar el usuario."));
        }
      })(),
    });
  }

  function resetForm(): void {
    setForm(INITIAL_FORM);
    setEditingId(null);
    setPermissionInfo(null);
    setChoices({});
  }

  function inherited(code: string): boolean {
    return permissionInfo?.inheritedPermissions.includes(code) ?? false;
  }

  function effective(code: string): boolean | null {
    const choice = choices[code] ?? "INHERITED";
    if (choice === "ALLOW") return true;
    if (choice === "DENY") return false;
    return editingId && permissionInfo ? inherited(code) : null;
  }

  if (loading) return <ModuleLoadingSkeleton columns={6} formFields={6} />;

  return (
    <div className={styles.pageShell}>
      <section className={styles.container}>
        <header className={styles.header}>
          <div><h2 className={styles.title}>Usuarios</h2><p className={styles.subtitle}>Roles como base y excepciones individuales.</p></div>
          <button className={styles.buttonSecondary} type="button" onClick={() => navigate("/dashboard")}><u>S</u>alir</button>
        </header>

        {canReadRoles && (canCreate || (editingId !== null && canUpdate)) && <div className={styles.card}>
          <form className={styles.form} onSubmit={onSubmit}>
            <Field label="Username"><input name="username" value={form.username} onChange={onInputChange} required={!editingId} disabled={Boolean(editingId)} /></Field>
            <Field label="Email"><input name="email" type="email" value={form.email} onChange={onInputChange} required /></Field>
            <Field label="Nombre completo"><input name="fullName" value={form.fullName} onChange={onInputChange} required /></Field>
            <Field label="Contraseña"><input name="password" type="password" value={form.password} onChange={onInputChange} required={!editingId} disabled={Boolean(editingId)} placeholder={editingId ? "No editable" : ""} /></Field>
            <Field label="Estado"><select name="status" value={form.status} onChange={onInputChange}><option value="ACTIVE">Activo</option><option value="BLOCKED">Bloqueado</option><option value="INACTIVE">Inactivo</option></select></Field>
            <Field label="Roles"><select name="roleIds" multiple className={styles.roles} value={form.roleIds} onChange={onRolesChange}>{roles.map((role) => <option key={role.id} value={role.id}>{role.name}</option>)}</select></Field>

            {canAssignPermissions ? (
              <section className={styles.permissionsPanel}>
                <div className={styles.permissionsHeader}>
                  <div><h3>Permisos individuales</h3><p>{editingId ? "La herencia refleja los roles guardados; cambios de rol se recalculan al guardar." : "La herencia final se calculará después de crear el usuario."}</p></div>
                  <button className={`${styles.button} ${styles.secondary}`} type="button" onClick={() => void resetAllOverrides()} disabled={loadingPermissions}>Restablecer todos</button>
                </div>
                <div className={styles.legend}><span>Heredado por rol</span><span>ALLOW explícito</span><span>DENY explícito</span><span>Efectivo final</span></div>
                {loadingPermissions ? (
                  <div aria-label="Cargando permisos" aria-busy="true" style={{ display: "grid", gap: "0.55rem" }}>
                    {Array.from({ length: 5 }, (_, index) => (
                      <SkeletonBlock key={index} style={{ height: 54, borderRadius: 6 }} />
                    ))}
                  </div>
                ) : (
                  <div className={styles.permissionGroups}>{groupedPermissions.map((group) => (
                    <section className={styles.permissionGroup} key={group.code}>
                      <h4>{group.label}</h4>
                      <div className={styles.permissionRows}>{group.items.map((permission) => {
                        const choice = choices[permission.code] ?? "INHERITED";
                        const finalState = effective(permission.code);
                        return <div className={styles.permissionRow} key={permission.id}>
                          <div className={styles.permissionIdentity}><strong>{permission.code}</strong><span>{permission.description || "Sin descripción"}</span></div>
                          <span className={`${styles.inheritedBadge} ${inherited(permission.code) ? styles.inheritedOn : ""}`}>{!editingId || !permissionInfo ? "Por calcular" : inherited(permission.code) ? "Heredado" : "No heredado"}</span>
                          <div className={styles.tristate}>{(["INHERITED", "ALLOW", "DENY"] as const).map((option) => <button key={option} type="button" aria-pressed={choice === option} className={`${styles.stateButton} ${choice === option ? styles[`state${option}`] : ""}`} onClick={() => choose(permission.code, option)}>{option === "INHERITED" ? "Heredado" : option}</button>)}</div>
                          <span className={`${styles.effectiveBadge} ${finalState === true ? styles.effectiveOn : finalState === false ? styles.effectiveOff : styles.effectivePending}`}>{finalState === true ? "Efectivo" : finalState === false ? "Sin acceso" : "Al guardar"}</span>
                        </div>;
                      })}</div>
                    </section>
                  ))}</div>
                )}
              </section>
            ) : null}

            <div className={styles.actions}><button className={`${styles.button} ${styles.primary}`} type="submit" disabled={saving || loading}>{saving ? "Guardando..." : submitLabel}</button>{editingId ? <button className={`${styles.button} ${styles.secondary}`} type="button" onClick={resetForm}>Cancelar</button> : null}</div>
          </form>
        </div>}

        <div className={styles.card}>
          {error ? <p className={styles.error}>{error}</p> : null}
          <div className={styles.tableWrap}><table className={styles.table}><thead><tr><th>Username</th><th>Email</th><th>Nombre completo</th><th>Estado</th><th>Roles</th><th>Acciones</th></tr></thead><tbody>{users.map((user) => <tr key={user.id}><td>{user.username}</td><td>{user.email}</td><td>{user.fullName}</td><td><span className={`${styles.status} ${user.status === "ACTIVE" ? styles.active : user.status === "BLOCKED" ? styles.blocked : styles.inactive}`}>{user.status === "ACTIVE" ? "Activo" : user.status === "BLOCKED" ? "Bloqueado" : "Inactivo"}</span></td><td><div className={styles.rolesWrap}>{user.roles.map((role) => <span key={role.id} className={styles.roleTag}>{role.name}</span>)}</div></td><td><div className={styles.rowActions}>{canUpdate ? <button className={`${styles.button} ${styles.secondary}`} type="button" onClick={() => void onEdit(user)}>Editar</button> : null}{canDelete ? <button className={`${styles.button} ${styles.danger}`} type="button" onClick={() => onDelete(user.id)}>Eliminar</button> : null}</div></td></tr>)}</tbody></table></div>
          {!loading && users.length === 0 ? <p className={styles.empty}>No hay usuarios registrados.</p> : null}
        </div>

        {modal.show ? <div className={styles.modalBackdrop}><div className={styles.modalCard}><h3>{modal.onConfirm ? "Confirmar" : "Operación completada"}</h3><p>{modal.message}</p>{modal.onConfirm ? <div className={styles.modalActions}><button className={`${styles.button} ${styles.danger}`} type="button" onClick={modal.onConfirm}>Eliminar</button><button className={`${styles.button} ${styles.secondary}`} type="button" onClick={() => setModal({ show: false, message: "" })}>Cancelar</button></div> : <button className={`${styles.button} ${styles.primary}`} type="button" onClick={() => setModal({ show: false, message: "" })}>Aceptar</button>}</div></div> : null}
      </section>
    </div>
  );
}

function Field({ label, children }: { label: string; children: ReactElement }): ReactElement {
  return <label className={styles.field}><span>{label}</span>{children}</label>;
}

function readError(error: unknown, fallback: string): string {
  if (isGloballyReportedError(error)) return "";
  return error instanceof Error && error.message.trim() ? error.message : fallback;
}
