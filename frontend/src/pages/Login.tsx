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
      if (data.data.refreshToken)
        localStorage.setItem("refreshToken", data.data.refreshToken);
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
            <span className={styles.panelEyebrow}>
              Gestión que crece contigo
            </span>
            <p>Ventas, inventario y clientes en un mismo lugar.</p>
          </div>
          <div className={styles.plantScene}>
  <span className={styles.sceneAura} />

  <svg
    className={styles.treeSvg}
    viewBox="0 0 360 360"
    aria-hidden="true"
  >
    <defs>
      {/* Tronco */}
      <linearGradient id="trunkGradient" x1="0" y1="0" x2="1" y2="0">
        <stop offset="0%" stopColor="#34281f" />
        <stop offset="28%" stopColor="#725744" />
        <stop offset="52%" stopColor="#9a7a5d" />
        <stop offset="72%" stopColor="#604938" />
        <stop offset="100%" stopColor="#2f241c" />
      </linearGradient>

      {/* Hoja reutilizable */}
      <linearGradient id="leafGradient" x1="0" y1="0" x2="1" y2="1">
        <stop offset="0%" stopColor="#b8d88e" />
        <stop offset="45%" stopColor="#7fa85f" />
        <stop offset="100%" stopColor="#416c43" />
      </linearGradient>

      <linearGradient id="leafDarkGradient" x1="0" y1="0" x2="1" y2="1">
        <stop offset="0%" stopColor="#789b5d" />
        <stop offset="100%" stopColor="#2e5636" />
      </linearGradient>

      <filter id="treeShadow" x="-30%" y="-30%" width="160%" height="160%">
        <feDropShadow
          dx="0"
          dy="4"
          stdDeviation="4"
          floodColor="#14271a"
          floodOpacity="0.22"
        />
      </filter>

      <path
        id="leaf"
        d="
          M0 0
          C4 -8 13 -11 21 -7
          C16 1 9 6 0 0
          Z
        "
      />

      <path
        id="smallLeaf"
        d="
          M0 0
          C3 -6 10 -8 16 -5
          C12 1 6 4 0 0
          Z
        "
      />
    </defs>

    <g filter="url(#treeShadow)">
      {/* ==================== RAÍCES ==================== */}
      <g className={styles.treeRoots}>
        <path d="M177 326 C154 326 139 332 119 343" />
        <path d="M182 326 C204 327 221 335 243 345" />
        <path d="M174 326 C162 337 157 345 151 354" />
        <path d="M184 326 C190 336 196 345 207 352" />
      </g>

      {/* ==================== TRONCO ==================== */}
      <path
        className={styles.mainTrunk}
        d="
          M165 329
          C169 306 168 287 165 265
          C161 242 160 223 165 202
          C170 183 177 166 180 145
          C183 126 180 106 185 86

          C189 106 188 126 188 146
          C190 167 184 188 184 205
          C185 227 188 244 187 264
          C186 289 191 309 194 329
          Z
        "
      />

      {/* detalle del tronco */}
      <path
        className={styles.trunkHighlight}
        d="
          M179 318
          C181 289 174 270 177 244
          C179 217 175 201 181 179
          C186 159 184 137 186 111
        "
      />

      {/* ==================== RAMAS PRINCIPALES ==================== */}
      <g className={styles.branchStructure}>
        {/* izquierda baja */}
        <path d="M172 248 C149 232 125 222 94 219 C73 216 55 207 37 193" />

        {/* izquierda media */}
        <path d="M169 222 C146 204 133 188 112 172 C91 156 73 150 54 142" />

        {/* izquierda alta */}
        <path d="M174 192 C156 170 146 151 136 126 C128 106 114 91 98 80" />

        {/* izquierda superior */}
        <path d="M179 163 C166 138 163 112 164 87 C165 67 157 51 148 38" />

        {/* centro */}
        <path d="M182 169 C183 141 183 111 187 80 C189 62 193 46 199 29" />

        {/* derecha alta */}
        <path d="M184 187 C201 159 209 133 224 109 C237 89 250 77 265 64" />

        {/* derecha media */}
        <path d="M183 215 C204 196 222 178 248 166 C268 157 287 150 310 135" />

        {/* derecha baja */}
        <path d="M185 245 C209 228 230 218 256 215 C279 211 299 202 323 190" />
      </g>

      {/* ==================== RAMITAS FINAS ==================== */}
      <g className={styles.twigStructure}>
        <path d="M91 218 C78 207 68 196 60 182" />
        <path d="M95 218 C82 225 70 233 58 242" />

        <path d="M110 171 C99 158 94 144 92 131" />
        <path d="M113 173 C98 176 85 182 74 191" />

        <path d="M137 126 C126 118 116 107 108 95" />
        <path d="M137 127 C126 134 118 144 112 154" />

        <path d="M164 88 C153 79 145 68 139 55" />
        <path d="M164 86 C174 74 177 61 178 48" />

        <path d="M187 80 C178 70 173 58 172 45" />
        <path d="M188 78 C198 67 205 57 210 45" />

        <path d="M225 108 C220 94 220 82 222 71" />
        <path d="M225 108 C239 103 250 96 260 87" />

        <path d="M248 166 C253 151 263 140 276 131" />
        <path d="M250 166 C265 170 280 173 294 181" />

        <path d="M257 215 C269 204 281 196 295 192" />
        <path d="M256 216 C270 224 282 232 291 244" />
      </g>

      {/* ==================== FOLLAJE IZQUIERDO ==================== */}
      <g className={`${styles.leafGroup} ${styles.leafGroupLeft}`}>
        <use href="#leaf" x="32" y="183" transform="rotate(-18 32 183)" />
        <use href="#leaf" x="48" y="198" transform="rotate(15 48 198)" />
        <use href="#smallLeaf" x="56" y="177" transform="rotate(-48 56 177)" />
        <use href="#leaf" x="61" y="225" transform="rotate(18 61 225)" />

        <use href="#leaf" x="47" y="132" transform="rotate(-20 47 132)" />
        <use href="#smallLeaf" x="69" y="144" transform="rotate(23 69 144)" />
        <use href="#leaf" x="78" y="120" transform="rotate(-42 78 120)" />
        <use href="#leaf" x="82" y="164" transform="rotate(20 82 164)" />

        <use href="#smallLeaf" x="96" y="86" transform="rotate(-25 96 86)" />
        <use href="#leaf" x="108" y="99" transform="rotate(28 108 99)" />
        <use href="#smallLeaf" x="119" y="70" transform="rotate(-36 119 70)" />

        <use href="#leaf" x="128" y="43" transform="rotate(-22 128 43)" />
        <use href="#smallLeaf" x="143" y="56" transform="rotate(28 143 56)" />
        <use href="#leaf" x="145" y="28" transform="rotate(-53 145 28)" />

        <use href="#smallLeaf" x="97" y="196" transform="rotate(-20 97 196)" />
        <use href="#leaf" x="115" y="210" transform="rotate(31 115 210)" />
        <use href="#smallLeaf" x="126" y="185" transform="rotate(-33 126 185)" />
      </g>

      {/* ==================== CENTRO ==================== */}
      <g className={`${styles.leafGroup} ${styles.leafGroupCenter}`}>
        <use href="#leaf" x="165" y="35" transform="rotate(-25 165 35)" />
        <use href="#smallLeaf" x="181" y="20" transform="rotate(10 181 20)" />
        <use href="#leaf" x="194" y="38" transform="rotate(34 194 38)" />

        <use href="#leaf" x="149" y="72" transform="rotate(-22 149 72)" />
        <use href="#smallLeaf" x="175" y="66" transform="rotate(17 175 66)" />
        <use href="#leaf" x="196" y="70" transform="rotate(32 196 70)" />

        <use href="#smallLeaf" x="148" y="104" transform="rotate(-33 148 104)" />
        <use href="#leaf" x="177" y="102" transform="rotate(12 177 102)" />
        <use href="#smallLeaf" x="199" y="111" transform="rotate(41 199 111)" />

        <use href="#leaf" x="137" y="146" transform="rotate(-30 137 146)" />
        <use href="#smallLeaf" x="160" y="137" transform="rotate(18 160 137)" />
        <use href="#leaf" x="195" y="143" transform="rotate(40 195 143)" />

        <use href="#smallLeaf" x="143" y="180" transform="rotate(-22 143 180)" />
        <use href="#leaf" x="193" y="181" transform="rotate(25 193 181)" />
      </g>

      {/* ==================== DERECHA ==================== */}
      <g className={`${styles.leafGroup} ${styles.leafGroupRight}`}>
        <use href="#leaf" x="210" y="41" transform="rotate(25 210 41)" />
        <use href="#smallLeaf" x="229" y="58" transform="rotate(-12 229 58)" />
        <use href="#leaf" x="248" y="48" transform="rotate(41 248 48)" />
        <use href="#smallLeaf" x="264" y="72" transform="rotate(-15 264 72)" />

        <use href="#leaf" x="218" y="88" transform="rotate(15 218 88)" />
        <use href="#smallLeaf" x="241" y="102" transform="rotate(39 241 102)" />
        <use href="#leaf" x="268" y="92" transform="rotate(-19 268 92)" />

        <use href="#leaf" x="267" y="123" transform="rotate(28 267 123)" />
        <use href="#smallLeaf" x="287" y="136" transform="rotate(-25 287 136)" />
        <use href="#leaf" x="307" y="125" transform="rotate(34 307 125)" />

        <use href="#leaf" x="245" y="160" transform="rotate(-21 245 160)" />
        <use href="#smallLeaf" x="272" y="170" transform="rotate(21 272 170)" />
        <use href="#leaf" x="296" y="178" transform="rotate(39 296 178)" />

        <use href="#leaf" x="250" y="205" transform="rotate(-20 250 205)" />
        <use href="#smallLeaf" x="276" y="216" transform="rotate(27 276 216)" />
        <use href="#leaf" x="302" y="198" transform="rotate(42 302 198)" />
      </g>
    </g>

    {/* ==================== MACETA ==================== */}
    <g className={styles.pot}>
      <ellipse
        cx="180"
        cy="331"
        rx="53"
        ry="8"
        fill="rgba(20,35,23,.30)"
      />

      <path
        d="M136 326 L224 326 L214 358 L146 358 Z"
        fill="#8c5543"
      />

      <path
        d="M146 326 L214 326 L207 354 L156 354 Z"
        fill="#a96850"
        opacity=".55"
      />

      <rect
        x="130"
        y="319"
        width="100"
        height="12"
        rx="4"
        fill="#a7674f"
      />

      <rect
        x="135"
        y="320"
        width="90"
        height="3"
        rx="2"
        fill="#c48165"
        opacity=".45"
      />
    </g>
  </svg>

  <span
    className={`${styles.floatingLeaf} ${styles.floatingLeafOne}`}
  />

  <span
    className={`${styles.floatingLeaf} ${styles.floatingLeafTwo}`}
  />
</div>
        </div>

        <div className={styles.formPanel}>
          <div className={styles.header}>
            <div className={styles.mobileMark} aria-hidden="true">
              <span />
            </div>
            <p className={styles.subtitle}>Sistema POS</p>
            <h1 className={styles.title}>Vivero Hermanos Flores</h1>
            <p className={styles.welcome}>
              Ingresa tus credenciales para continuar.
            </p>
          </div>

          <div className={styles.form}>
            <div className={styles.field}>
              <label className={styles.label}>Usuario</label>
              <input
                type="text"
                placeholder="Ingresa tu usuario"
                onChange={(e) => setUsername(e.target.value)}
                className={styles.input}
              />
            </div>

            <div className={styles.field}>
              <label className={styles.label}>Contraseña</label>
              <input
                type="password"
                placeholder="Ingresa tu contraseña"
                onChange={(e) => setPassword(e.target.value)}
                onKeyDown={(e) => e.key === "Enter" && handleLogin()}
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
