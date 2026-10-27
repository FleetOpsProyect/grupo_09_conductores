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
- AWS SNS + SQS con Spring Cloud AWS 4.1.1; LocalStack en local.

## Requisitos

- JDK 21.
- Docker y Docker Compose.
- Copiar `.env.example` a `.env` antes de ejecutar el servicio.

## Configuración

El archivo `.env` no se versiona. En ejecución local Spring lo importa automáticamente; en Docker
Compose las variables llegan al contenedor mediante `env_file`.

### Variables de entorno

| Variable | Descripción | Valor por defecto | Estado |
| :--- | :--- | :--- | :--- |
| `SERVER_PORT` | Puerto HTTP del servicio. | `8085` | Implementada |
| `FIREBASE_PROJECT_ID` | Proyecto de Firebase/Firestore. | `demo-conductores` (local) | Implementada |
| `FIREBASE_EMULATOR_HOST` | `host:puerto` del emulador de Firestore. Vacío en producción. | vacío | Implementada |
| `GOOGLE_APPLICATION_CREDENTIALS` | Ruta al JSON de la cuenta de servicio. En producción apunta a `/secrets/firebase-adminsdk.json` dentro del contenedor. | — | Implementada (SDK) |
| `AWS_REGION` | Región de AWS para SNS/SQS. | `us-east-1` | Implementada |
| `AWS_ACCESS_KEY_ID` | Credencial falsa para LocalStack. No se define en producción (rol IAM). | `test` (local) | Implementada |
| `AWS_SECRET_ACCESS_KEY` | Credencial falsa para LocalStack. No se define en producción (rol IAM). | `test` (local) | Implementada |
| `SPRING_CLOUD_AWS_ENDPOINT` | Endpoint de AWS (LocalStack en local). Vacío en producción para usar AWS real. | `http://localhost:4566` (local) | Implementada |
| `JAVA_TOOL_OPTIONS` | Opciones JVM del contenedor (por ejemplo `-XX:MaxRAMPercentage=75.0`). | `-XX:MaxRAMPercentage=75.0` | Implementada |
| `FLEETOPS_DRIVER_TOPIC_ARN` | ARN del topic `fleetops_driver_events`. | — | Prevista |
| `FLEETOPS_DRIVER_QUEUE_URL` | URL de la cola `fleetops_driver_queue`. | — | Prevista |
| `FLEETOPS_DRIVER_DLQ_URL` | URL de la DLQ `fleetops_driver_dlq`. | — | Prevista |
| `OUTBOX_PUBLISH_INTERVAL_MS` | Intervalo del publicador del outbox. | `2000` (2 s) | Prevista |
| `DOCUMENTACION_URL` | URL del servicio de Documentación. | — | Prevista |
| `DOCUMENTACION_TIMEOUT_MS` | Timeout hacia Documentación. | `2000` (2 s) | Prevista |
| `CIRCUIT_BREAKER_MAX_CONSECUTIVE_FAILURES` | Fallos consecutivos para abrir el Circuit Breaker. | `5` | Prevista |
| `CIRCUIT_BREAKER_WAIT_DURATION_MS` | Espera con el circuito abierto. | `30000` (30 s) | Prevista |
| `FIREBASE_CREDENTIALS_PATH` | Ruta en el host (EC2) al JSON de Firebase que se monta como volumen. | `./credentials/firebase-adminsdk.json` | Infra |
| `DOCKERHUB_USER` | Usuario de Docker Hub (secreto `DOCKERHUB_USERNAME` en CI/CD). | — | Infra |
| `IMAGE_NAME` | Nombre de la imagen Docker. | `ms-conductores` | Infra |
| `IMAGE_TAG` | Etiqueta de la imagen (SHA corto del commit en CD; `latest` solo en main). | `latest` | Infra |

Las variables marcadas como *Prevista* documentan valores que el código todavía no lee: la
integración con Documentación, el Circuit Breaker y el outbox de eventos están pendientes de
implementar. Ninguno de estos valores está escrito a mano en el código.

En producción `GOOGLE_APPLICATION_CREDENTIALS` apunta a `/secrets/firebase-adminsdk.json` (dentro
del contenedor) y el archivo se monta como volumen de solo lectura (ver
`docker-compose.prod.yml`).

## Ejecución local

```bash
cp .env.example .env
docker compose up -d firestore-emulator localstack
./gradlew bootRun
```

El servicio queda en `http://localhost:8085`, el emulador de Firestore en `http://localhost:8180`
y LocalStack en `http://localhost:4566`. No levantes el servicio `backend` de Compose al mismo
tiempo, porque también ocupa el puerto `8085`.

## Ejecución con Docker Compose

```bash
cp .env.example .env
docker compose up --build
```

Compose levanta el emulador de Firestore, LocalStack (SNS + SQS) y el servicio `backend`. El
backend espera a que Firestore y LocalStack estén saludables, y sobrescribe
`FIREBASE_EMULATOR_HOST` con `firestore-emulator:8080` y `SPRING_CLOUD_AWS_ENDPOINT` con
`http://localstack:4566`.

`scripts/init-localstack.sh` crea de forma idempotente:

- el topic `fleetops_driver_events`;
- la cola `fleetops_driver_queue` con la DLQ `fleetops_driver_dlq` (redrive con
  `maxReceiveCount=3`);
- los topics `fleetops_document_events` y `fleetops_incident_events`;
- las suscripciones de `fleetops_driver_queue` a esos topics, con filter policy por `event_type`
  y raw message delivery.

Para detener todo:

```bash
docker compose down
```

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

## Producción (EC2)

La composición `docker-compose.prod.yml` usa la imagen publicada en Docker Hub (sin `build:`). En
la EC2:

```bash
cp .env.prod.example .env
# Completa .env con los valores reales (los secretos nunca se versionan).
docker compose -f docker-compose.prod.yml up -d
```

Requisitos de la EC2 (paso 11, fuera de este repositorio):

- Un rol IAM que permita publicar en `fleetops_driver_events` y consumir
  `fleetops_driver_queue` y `fleetops_driver_dlq`. No se montan llaves de AWS: el SDK usa el
  perfil de instancia.
- El JSON de la cuenta de servicio de Firebase ubicado en la ruta indicada por
  `FIREBASE_CREDENTIALS_PATH`; se monta como volumen de solo lectura en
  `/secrets/firebase-adminsdk.json`.

## Publicación en Docker Hub

Publicación manual (los pasos 13/14 la automatizan en GitHub Actions). Sustituye `<usuario>` por
el usuario de Docker Hub:

```bash
export DOCKERHUB_USER=<usuario>
export IMAGE_NAME=ms-conductores
export IMAGE_TAG=$(git rev-parse --short=7 HEAD)

docker login -u "$DOCKERHUB_USER"

# La EC2 es x86_64. Si compilas desde Apple Silicon, añade --platform linux/amd64.
docker buildx build --platform linux/amd64 \
  -t "$DOCKERHUB_USER/$IMAGE_NAME:$IMAGE_TAG" \
  --push .

# La etiqueta latest solo se publica desde main.
if [ "$(git branch --show-current)" = "main" ]; then
  docker buildx build --platform linux/amd64 \
    -t "$DOCKERHUB_USER/$IMAGE_NAME:latest" \
    --push .
fi
```

Secretos que deben existir para los workflows (pasos 13/14): `DOCKERHUB_USERNAME` y
`DOCKERHUB_TOKEN`.

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
- No están implementados aún el outbox, la publicación/consumo de eventos, `processed_events` ni
  la DLQ. La infraestructura SNS/SQS queda lista (LocalStack en local, recursos de AWS en
  producción); el código productor/consumidor es trabajo pendiente.
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
