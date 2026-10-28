# ms-conductores

Microservicio REST para la gestión de conductores de FleetOps (Grupo 09). Expone el CRUD de
conductores, persiste en Firestore, publica salud y métricas para Prometheus y documenta la API
con OpenAPI.

## Stack

- Java 21 y Spring Boot 4.1.1, construido con el Gradle Wrapper 9.8.2.
- Firebase Admin 9.8.0 para Firestore.
- Spring Boot Actuator y Micrometer con registro Prometheus.
- springdoc-openapi 3.1.1 para Swagger UI.
- JUnit Jupiter, Mockito y Testcontainers.
- Spring Cloud AWS (SNS + SQS) para eventos.

## Requisitos

- JDK 21.
- Docker y Docker Compose.
- Copiar `.env.example` a `.env` antes de ejecutar el servicio.

## Configuración

El archivo `.env` no se versiona. En ejecución local Spring lo importa automáticamente; en Docker
Compose las variables llegan al contenedor mediante `env_file`.

| Variable | Local (`bootRun`) | Docker Compose | Descripción |
| :--- | :--- | :--- | :--- |
| `SERVER_PORT` | `8085` | `8085` | Puerto HTTP. La imagen usa `8085` por defecto y permite sobrescribirlo. |
| `FIREBASE_PROJECT_ID` | `demo-conductores` | `demo-conductores` | Proyecto de Firebase/Firestore. |
| `FIREBASE_EMULATOR_HOST` | `localhost:8180` | `firestore-emulator:8080` | Emulador local. En producción debe quedar vacío. |
| `GOOGLE_APPLICATION_CREDENTIALS` | — | — | Ruta al JSON de la cuenta de servicio en producción. No se versiona. |
| `AWS_REGION` | `us-east-1` | `us-east-1` | Región de AWS para SNS y SQS; solo se usa cuando se implementen los eventos. |
| `JAVA_OPTS` | — | `-Xms256m -Xmx512m` | Opciones de la JVM en la imagen Docker; se pueden sobrescribir. |

`GOOGLE_APPLICATION_CREDENTIALS` debe definirse como variable de entorno real del contenedor o del
host; no debe agregarse a `.env`, porque ese archivo se importa como propiedades de Spring y no
exporta variables al proceso.

La integración con Documentación todavía no está implementada. Los valores previstos en el DAS
son: URL del servicio, timeout de 2 s, 5 fallos consecutivos para abrir el Circuit Breaker y 30 s
de espera con el circuito abierto.

## Ejecución local

```bash
cp .env.example .env
docker compose up -d firestore-emulator
./gradlew bootRun
```

El servicio queda en `http://localhost:8085` y el emulador de Firestore en
`http://localhost:8180`. No levantes el servicio `backend` de Compose al mismo tiempo, porque
también ocupa el puerto `8085`.

## Ejecución con Docker Compose

```bash
cp .env.example .env
docker compose up --build
```

Compose levanta el emulador de Firestore y el servicio `backend`. El backend espera a que Firestore
esté saludable y sobrescribe `FIREBASE_EMULATOR_HOST` con `firestore-emulator:8080`.

## Endpoints

| Método | Ruta | Descripción |
| :--- | :--- | :--- |
| `POST` | `/drivers` | Registra un conductor. Responde `201`, `400` o `409`. |
| `GET` | `/drivers/{id}` | Consulta un conductor por UUID. Responde `200`, `400` o `404`. |
| `GET` | `/drivers` | Lista conductores con filtros opcionales `status`, `page` y `size` (`size` entre 1 y 100). |
| `PUT` | `/drivers/{id}` | Actualiza `full_name`, `email` y `phone`. Responde `200`, `400` o `404`. |
| `GET` | `/health` | Salud del servicio y de Firestore. Responde `200` o `503`. |
| `GET` | `/metrics` | Métricas en formato Prometheus. |
| `GET` | `/info` | Información del servicio. |
| `GET` | `/v3/api-docs` | Especificación OpenAPI en JSON, con propiedades en snake_case. |
| `GET` | `/swagger-ui.html` | Interfaz Swagger UI. |

Ejemplo de registro:

```bash
curl -X POST http://localhost:8085/drivers \
  -H 'Content-Type: application/json' \
  -d '{
    "full_name": "Carlos Pérez",
    "document_number": "1234567890",
    "email": "carlos@correo.com",
    "phone": "3001234567"
  }'
```

Los endpoints de gestión usan la raíz del servicio: `/health`, `/metrics` e `/info`, no
`/actuator/*`.

## Firestore

`firestore.indexes.json` versiona el índice compuesto de la colección `drivers`:

- `status` ascendente.
- `created_at` ascendente.

Este índice cubre el listado con filtro por estado y orden por fecha de creación.

El emulador no exige este índice, pero Firestore real sí. Despliégalo con
`firebase deploy --only firestore:indexes`; de lo contrario, el listado filtrado por `status`
falla.

## Pruebas y calidad

```bash
# Pruebas unitarias, sin Docker
./gradlew test --tests 'co.edu.univalle.conductores.domain.*' \
  --tests 'co.edu.univalle.conductores.application.service.*'

# Suite completa, requiere Docker
./gradlew test

# Todas las verificaciones del build
./gradlew build

# Reporte de cobertura
./gradlew jacocoTestReport
```

La suite completa usa Testcontainers y requiere Docker. `build` ejecuta pruebas, Checkstyle, JaCoCo
y la verificación de cobertura. El reporte de cobertura se genera en
`build/jacocoHtml/index.html` (HTML) y `build/reports/jacoco/test/jacocoTestReport.xml` (XML).

## Convenciones de commits y pull requests
 
### Commits
 
Los mensajes de commit siguen [Conventional Commits](https://www.conventionalcommits.org/) y se
escriben en inglés, en imperativo y sin punto final:
 
```text
<tipo>(<alcance opcional>): <descripción corta>
```
 
Tipos permitidos (los mismos de la plantilla de PR):
 
| Tipo | Cuándo usarlo |
| :--- | :--- |
| `feat` | Una nueva característica. |
| `fix` | Se solucionó un bug. |
| `docs` | Se realizaron cambios en la documentación. |
| `style` | Se aplicó formato, comas y puntos faltantes, etc.; sin cambios en el código. |
| `refactor` | Refactorización del código en producción. |
| `test` | Se añadieron pruebas o se refactorizaron; sin cambios en el código. |
| `chore` | Tareas de build, configuración del administrador de paquetes, Docker, Compose o pipeline; sin cambios en el código. |
 
Ejemplos:
 
```text
feat(HU-01): return 503 when Firestore is unavailable
fix(HU-02): return 404 for non-UUID driver id
test: cover phone validation boundaries
chore: pin Firestore emulator image
```
 
Cada commit tiene un solo propósito y debe dejar el proyecto compilando con `./gradlew build`.
 
### Pull requests
 
- Todo cambio llega a `develop` por pull request; no se hace push directo.
- **Título:** mismo formato que un commit, con la HU o tarea como alcance, en menos de 70
  caracteres. Ejemplo: `feat(HU-06): filter drivers by status`.
- **Descripción:** se llena la plantilla `.github/pull_request_template.md`. Las secciones marcadas
  con `*` son obligatorias.
- **Antes de pedir revisión:** el CI debe pasar (Checkstyle, pruebas, cobertura y SonarCloud). La
  revisión automática de Qodo es una ayuda, no un bloqueo.
- Si el PR resuelve una limitación de la lista de abajo, se borra de esa lista en el mismo PR.
 

## Limitaciones conocidas a corregir/implementar en futuro cercano

- La API responde `201 Created`, pero la especificación OpenAPI aún publica `200`; alinear el
  contrato es trabajo de TEC_02.
- El título de la API sigue siendo el valor por defecto `OpenAPI definition`; el título definitivo
  es trabajo de TEC_02.
- No están implementados la integración con Documentación ni el Circuit Breaker: URL del servicio,
  timeout de 2 s, 5 fallos consecutivos y 30 s con el circuito abierto.
- Los eventos no están implementados todavía: el broker es AWS SNS + SQS con Transactional Outbox
  (TEC_05); LocalStack para pruebas locales y de integración llega con DEV_09.
- Faltan el listado de elegibles, el alta médica y el borrado lógico.
- Los errores de validación usan códigos y estructura parcialmente distintos a los propuestos en el documento de historias de usuario que tenemos como grupo
  (`errors[].message` en lugar de `errors[].code`).
- Cuando Firestore no está disponible, la API responde `500`; en la documentación planteamos
  `503 SERVICE_UNAVAILABLE`.
- Las rutas desconocidas (por ejemplo, `/drivres`) devuelven el cuerpo de error por defecto de
  Spring en lugar del formato común de error (TEC_04). Se puede añadir un formato de error adicional al catalogo en la documentación.
- `document_number` no se recorta antes de validar/guardar y `document_numbers` no guarda
  `created_at`.
- Un UUID mal formado responde `400`; en la documentación lo proponemos como `404`.
- Las fechas se guardan en Firestore como texto ISO 8601, no como timestamps nativos.
- No hay logs estructurados JSON, pipeline de CD ni dashboards de Grafana por ahora.
- La especificación OpenAPI no indica que los roles y la regla “solo el propio conductor” (HU-03) son de fase 2; en el MVP cualquier token válido tiene acceso completo (TEC_02).
