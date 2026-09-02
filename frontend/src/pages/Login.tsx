import { useState } from "react";
import { login } from "../api/auth";
import { useNavigate } from "react-router-dom";
import styles from "./Login.module.css";
import { usePermissions } from "../auth/PermissionContext";
import { isGloballyReportedError } from "../api/errors";

function Login() {
    const [username, setUsername] = useState("");
    const [password, setPassword] = useState("");
    const [error, setError] = useState(false);
    const [loading, setLoading] = useState(false);
    const navigate = useNavigate();
    const { reloadPermissions } = usePermissions();

    const handleLogin = async () => {
        setError(false);
        setLoading(true);
        try {
            const data = await login(username, password);
            localStorage.setItem("token", data.data.accessToken);
            if (data.data.refreshToken) localStorage.setItem("refreshToken", data.data.refreshToken);
            await reloadPermissions();
            navigate("/dashboard");
        } catch (err) {
            if (!isGloballyReportedError(err)) setError(true);
        } finally {
            setLoading(false);
        }
    };

    return (
        <main className={styles.wrapper}>
            <section className={styles.loginShell}>
                <div className={styles.botanicalPanel} aria-hidden="true">
                    <div className={styles.brandMark}>
                        <span className={styles.brandStem} />
                        <span className={`${styles.brandLeaf} ${styles.brandLeafLeft}`} />
                        <span className={`${styles.brandLeaf} ${styles.brandLeafRight}`} />
                    </div>
                    <div className={styles.botanicalCopy}>
                        <span className={styles.panelEyebrow}>Gestión que crece contigo</span>
                        <p>Ventas, inventario y clientes en un mismo lugar.</p>
                    </div>
                    <div className={styles.plantScene}>
                        <span className={`${styles.sceneLeaf} ${styles.sceneLeafOne}`} />
                        <span className={`${styles.sceneLeaf} ${styles.sceneLeafTwo}`} />
                        <span className={`${styles.sceneLeaf} ${styles.sceneLeafThree}`} />
                        <span className={`${styles.sceneLeaf} ${styles.sceneLeafFour}`} />
                        <span className={styles.sceneStem} />
                        <span className={styles.scenePotRim} />
                        <span className={styles.scenePot} />
                    </div>
                </div>

                <div className={styles.formPanel}>
                    <div className={styles.header}>
                        <div className={styles.mobileMark} aria-hidden="true">
                            <span />
                        </div>
                        <p className={styles.subtitle}>Sistema POS</p>
                        <h1 className={styles.title}>Vivero Hermanos Flores</h1>
                        <p className={styles.welcome}>Ingresa tus credenciales para continuar.</p>
                    </div>

                    <div className={styles.form}>
                    <div className={styles.field}>
                        <label className={styles.label}>Usuario</label>
                        <input
                            type="text"
                            placeholder="Ingresa tu usuario"
                            onChange={e => setUsername(e.target.value)}
                            className={styles.input}
                        />
                    </div>

                    <div className={styles.field}>
                        <label className={styles.label}>Contraseña</label>
                        <input
                            type="password"
                            placeholder="Ingresa tu contraseña"
                            onChange={e => setPassword(e.target.value)}
                            onKeyDown={e => e.key === "Enter" && handleLogin()}
                            className={styles.input}
                        />
                    </div>

                    {error && (
                        <div className={styles.error} role="alert">
                            Usuario o contraseña incorrectos
                        </div>
                    )}

                    <button
                        onClick={handleLogin}
                        disabled={loading}
                        className={styles.button}
                    >
                        {loading ? "Ingresando..." : "Iniciar sesión"}
                    </button>
                    </div>
                </div>
            </section>
        </main>
    );
}

export default Login;
