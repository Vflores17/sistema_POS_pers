import { useState } from "react";
import { login } from "../api/auth";
import { useNavigate } from "react-router-dom";
import styles from "./Login.module.css";

function Login() {
    const [username, setUsername] = useState("");
    const [password, setPassword] = useState("");
    const [error, setError] = useState(false);
    const [loading, setLoading] = useState(false);
    const navigate = useNavigate();

    const handleLogin = async () => {
        setError(false);
        setLoading(true);
        try {
            const data = await login(username, password);
            localStorage.setItem("token", data.data.accessToken);
            navigate("/dashboard");
        } catch (err) {
            console.error(err);
            setError(true);
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className={styles.wrapper}>
            <div className={styles.card}>
                <div className={styles.header}>
                    <div className={styles.logo}>🌿</div>
                    <h1 className={styles.title}>Vivero Hermanos Flores</h1>
                    <p className={styles.subtitle}>Sistema POS</p>
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
                        <div className={styles.error}>
                            ❌ Usuario o contraseña incorrectos
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
        </div>
    );
}

export default Login;
