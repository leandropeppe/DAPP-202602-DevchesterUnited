# Devchester United

Aplicación web desarrollada para la materia **Desarrollo de Aplicaciones** de la Universidad Nacional de Quilmes.

Devchester United simula un mercado de jugadores de fútbol tokenizados. La aplicación permite consultar jugadores y sus estadísticas, operar dentro del mercado, administrar un portfolio y utilizar distintas estrategias de valorización para calcular cotizaciones.

## Tecnologías

### Backend
- Java 21
- Spring Boot
- PostgreSQL
- Flyway
- JWT
- Swagger / OpenAPI
- JUnit 5
- Testcontainers

### Frontend
- React
- Vite
- Vitest

## Funcionalidades principales

- Registro e inicio de sesión.
- Catálogo y detalle de jugadores.
- Estadísticas de jugadores.
- Cotización y valorización de jugadores.
- Estrategias de valorización general y posicional.
- Simulador de cotización futura.
- Compra primaria de tokens.
- Mercado P2P entre usuarios.
- Portfolio de jugadores.
- Historial de transacciones.
- Visualización de datos mediante gráficos.

## Ejecución

### Backend

```bash
cd backend
./gradlew bootRun
```

### Frontend

```bash
cd frontend
npm install
npm run dev
```

## Tests

### Backend

```bash
cd backend
./gradlew test
```

### Frontend

```bash
cd frontend
npm run test:coverage
```

## Proyecto académico

Trabajo práctico desarrollado para **Desarrollo de Aplicaciones – UNQ, 2026**.
