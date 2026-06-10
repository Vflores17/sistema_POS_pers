# Sistema POS — Punto de Venta Local
 
Sistema de punto de venta (POS) de uso local, desarrollado con una arquitectura fullstack desacoplada. Diseñado para gestionar ventas, clientes, productos e inventario de forma eficiente desde una red local.
 
---
 
## Stack Tecnológico
 
| Capa | Tecnología |
|------|-----------|
| Frontend | React 19 + TypeScript + Vite |
| Backend | Java 21 + Spring Boot 3.3 |
| Base de datos | PostgreSQL |
| Autenticación | Spring Security + JWT |
| ORM | Spring Data JPA |
| Exportación | html2pdf.js + xlsx |
 
---
 
## Funcionalidades
 
- **Autenticación** con JWT y rutas privadas protegidas
- **Dashboard** con resumen general del negocio
- **Gestión de ventas** — crear, editar, ver y listar ventas
- **Ventas por ruta** — soporte para vendedores con rutas asignadas
- **Gestión de clientes**
- **Gestión de productos** — CRUD completo con precios múltiples por producto, paginación y filtros
- **Gestión de usuarios**
- **Exportación** de datos a PDF y Excel
---
 
## Arquitectura
 
```
sistema_POS_pers/
├── backend/          # API REST — Spring Boot
│   └── src/
│       └── com/vflores/pos/
│           ├── products/     # Módulo de productos
│           ├── sales/        # Módulo de ventas
│           ├── clients/      # Módulo de clientes
│           ├── users/        # Módulo de usuarios
│           └── shared/       # Utilidades compartidas (ApiResponse, PageMeta)
└── frontend/         # SPA — React + TypeScript
    └── src/
        ├── pages/    # Login, Dashboard, Sales, Products, Clients, Users, RouteSales
        └── routes/   # PrivateRoute (protección de rutas)
```
 
El backend expone una API RESTful versionada (`/api/v1/...`) con paginación, ordenamiento y búsqueda. El frontend consume la API y protege las rutas autenticadas con un guard basado en JWT.
 
---
 
## Requisitos previos
 
- Java 21+
- Node.js 18+
- PostgreSQL (instancia local)
- Maven
---
 
## Instalación y ejecución
 
### Backend
 
```bash
cd backend
 
# Configurar base de datos en src/main/resources/application.properties
# spring.datasource.url=jdbc:postgresql://localhost:5432/pos_db
# spring.datasource.username=tu_usuario
# spring.datasource.password=tu_contraseña
 
mvn spring-boot:run
```
 
El servidor inicia en `http://localhost:8080`
 
### Frontend
 
```bash
cd frontend
npm install
npm run dev
```
 
La app inicia en `http://localhost:5173`
 
---
 
## API — Ejemplo de endpoints
 
| Método | Endpoint | Descripción |
|--------|----------|-------------|
| GET | `/api/v1/products` | Listar productos (paginado, filtrable) |
| POST | `/api/v1/products` | Crear producto |
| PUT | `/api/v1/products/:id` | Actualizar producto |
| DELETE | `/api/v1/products/:id` | Eliminar producto |
| GET | `/api/v1/products/:id/prices` | Precios del producto |
| POST | `/api/v1/products/:id/prices` | Agregar precio |
 
---
 
## Notas
 
- Este proyecto fue desarrollado como práctica personal con enfoque en arquitectura limpia y buenas prácticas (separación de capas, DTOs, validaciones, manejo de respuestas estandarizado).
- La base de datos corre localmente; no se incluyen credenciales en el repositorio.
