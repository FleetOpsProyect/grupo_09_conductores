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

Toda la configuración llega por variables de entorno (DEV_04). La columna **Local** es el valor de
`.env.example`; la columna **Producción** es el de `.env.prod.example`, que se copia a `.env` en la EC2.
`<cuenta>` es el id de la cuenta de AWS.

| Variable | Descripción | Local | Producción | Estado |
| :--- | :--- | :--- | :--- | :--- |
| `SERVER_PORT` | Puerto HTTP de la API. | `8085` | `8085` | Implementada |
| `MANAGEMENT_PORT` | Puerto de `/health`, `/metrics` e `/info` (Actuator), separado del de la API para no exponer las métricas por el puerto público. | `18001` | `18001` | Implementada |
| `FIREBASE_PROJECT_ID` | Proyecto de Firebase/Firestore. | `demo-conductores` | id del proyecto real de Firebase | Implementada |
| `FIREBASE_EMULATOR_HOST` | `host:puerto` del emulador de Firestore. | `localhost:8180` (Compose usa `firestore-emulator:8080`) | vacío | Implementada |
| `GOOGLE_APPLICATION_CREDENTIALS` | Ruta, dentro del contenedor, al JSON de la cuenta de servicio de Firebase. | sin definir (emulador) | `/secrets/firebase-adminsdk.json` | Implementada |
| `FIREBASE_CREDENTIALS_PATH` | Ruta en el host (EC2) al JSON de Firebase que se monta como volumen de solo lectura. | — | `/home/ec2-user/credentials/firebase-adminsdk.json` | Infra |
| `FIRESTORE_TIMEOUT` | Espera máxima de cada llamada a Firestore y del chequeo de `/health` (formato Duration de Spring). | `2s` | `2s` | Implementada |
| `LOG_FORMAT` | Formato del log en consola (TEC_03). Sin definir: JSON con esquema ECS. Vacía: texto plano, solo para desarrollo local (sin enmascarar datos de contacto). | sin definir (`ecs`); `LOG_FORMAT=` para texto plano | sin definir (`ecs`) | Implementada |
| `AWS_REGION` | Región de AWS para SNS/SQS. | `us-east-1` | `us-east-1` | Implementada |
| `AWS_ACCESS_KEY_ID` | Credencial para LocalStack. | `test` | sin definir (rol IAM de la EC2) | Implementada |
| `AWS_SECRET_ACCESS_KEY` | Credencial para LocalStack. | `test` | sin definir (rol IAM de la EC2) | Implementada |
| `SPRING_CLOUD_AWS_ENDPOINT` | Endpoint de AWS. | `http://localhost:4566` (LocalStack) | vacío (AWS real) | Implementada |
| `FLEETOPS_DRIVER_TOPIC_ARN` | ARN del topic `fleetops_driver_events`. | `arn:aws:sns:us-east-1:000000000000:fleetops_driver_events` | `arn:aws:sns:us-east-1:<cuenta>:fleetops_driver_events` | Implementada |
| `FLEETOPS_DRIVER_QUEUE_URL` | URL de la cola `fleetops_driver_queue`. | `http://localhost:4566/000000000000/fleetops_driver_queue` | `https://sqs.us-east-1.amazonaws.com/<cuenta>/fleetops_driver_queue` | Implementada |
| `FLEETOPS_DRIVER_DLQ_URL` | URL de la DLQ `fleetops_driver_dlq`. | `http://localhost:4566/000000000000/fleetops_driver_dlq` | `https://sqs.us-east-1.amazonaws.com/<cuenta>/fleetops_driver_dlq` | Implementada |
| `FLEETOPS_CONSUMER_ENABLED` | Habilita el consumidor SQS de `fleetops_driver_queue`. Las pruebas lo apagan, salvo la de LocalStack. | `true` | `true` | Implementada |
| `OUTBOX_PUBLISH_INTERVAL_MS` | Intervalo del publicador del outbox. | `2000` (2 s) | `2000` (2 s) | Implementada |
| `OUTBOX_BATCH_SIZE` | Máximo de eventos pendientes por ciclo del publicador. | `50` | `50` | Implementada |
| `OUTBOX_PUBLISHER_ENABLED` | Habilita el publicador periódico del outbox. | `true` | `true` | Implementada |
| `DOCUMENTACION_URL` | URL del servicio de Documentación (G1). | pendiente: la entrega el equipo de Documentación | pendiente: la entrega el equipo de Documentación | Implementada (HU-07) |
| `DOCUMENTACION_TIMEOUT_MS` | Timeout hacia Documentación. | `2000` (2 s) | `2000` (2 s) | Implementada (HU-07) |
| `CIRCUIT_BREAKER_MAX_CONSECUTIVE_FAILURES` | Fallos consecutivos para abrir el Circuit Breaker. | `5` | `5` | Implementada (HU-07) |
| `CIRCUIT_BREAKER_WAIT_DURATION_MS` | Espera con el circuito abierto. | `30000` (30 s) | `30000` (30 s) | Implementada (HU-07) |
| `JAVA_TOOL_OPTIONS` | Opciones JVM del contenedor. Viene fijada en el `Dockerfile`; solo se define para cambiarla. | `-XX:MaxRAMPercentage=75.0` (Dockerfile) | `-XX:MaxRAMPercentage=75.0` (Dockerfile) | Implementada |
| `DOCKERHUB_USER` | Usuario de Docker Hub (secreto `DOCKERHUB_USERNAME` en CI/CD). | — | usuario de Docker Hub del equipo | Infra |
| `IMAGE_NAME` | Nombre de la imagen Docker. | — | `ms-conductores` | Infra |
| `IMAGE_TAG` | Etiqueta de la imagen. El CD publica el SHA del commit y `latest` (desde `main`), y en cada despliegue la reemplaza por el SHA. | — | `latest` | Infra |

*Infra*: la usa `docker-compose.prod.yml`, no la aplicación.

En producción el JSON de Firebase se monta como volumen de solo lectura en
`/secrets/firebase-adminsdk.json` (ver `docker-compose.prod.yml`).

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

Nota: se fija `localstack/localstack:4.14.0` (última imagen Community); el tag `latest`
(≥ 2026.03) requiere `LOCALSTACK_AUTH_TOKEN` y no arranca sin él.

## Endpoints

| Método | Ruta | Descripción |
| :--- | :--- | :--- |
| `POST` | `/drivers` | Registra un conductor. Responde `201`, `400`, `409` o `503` (y `401`/`403` desde API Gateway). Solo `FLEET_ADMIN`. |
| `GET` | `/drivers/{id}` | Consulta un conductor por UUID. Responde `200`, `404` (también `404` si el id no es un UUID) o `503` (y `401`/`403` desde API Gateway). `FLEET_ADMIN` cualquier id; `DRIVER` solo el suyo (HU-03). |
| `GET` | `/drivers` | Lista conductores con filtros opcionales `status`, `page` y `size` (`size` entre 1 y 100). Responde `200`, `400` o `503` (y `401`/`403` desde API Gateway). Solo `FLEET_ADMIN`. |
| `PUT` | `/drivers/{id}` | Actualiza `full_name` y `email` (obligatorios) y `phone`: sin la clave se mantiene, con `null` se borra, con un valor se reemplaza. `document_number` no es editable (`400` con `NOT_EDITABLE` si llega con otro valor) y `status` se ignora. Si algo cambia, guarda `driver_updated_event` en el outbox. Responde `200`, `400`, `404` o `503` (y `401`/`403` desde API Gateway). Solo `FLEET_ADMIN`. |
| `POST` | `/drivers/{id}/medical-release` | Registra el alta médica de un conductor (HU-10). Responde `200`, `400`, `404`, `409` o `503` (y `401`/`403` desde API Gateway). Solo `FLEET_ADMIN`. |
| `GET` | `/health` | Salud del servicio y de Firestore. Responde `200` o `503`. Puerto de gestión (`18001`). |
| `GET` | `/metrics` | Métricas en formato Prometheus. Puerto de gestión (`18001`). |
| `GET` | `/info` | Información del servicio. Puerto de gestión (`18001`). |
| `GET` | `/v3/api-docs` | Especificación OpenAPI en JSON, con propiedades en snake_case. |
| `GET` | `/swagger-ui.html` | Interfaz Swagger UI. |

### Autenticación y roles

El servicio no valida tokens ni roles. Lo hace el authorizer `fleetops-authorizer` (Lambda) del HTTP
API `fleetops-api` en API Gateway, antes de que la petición llegue a la EC2:

- Verifica el **ID token** de Cognito: firma RS256, emisor, audiencia (`APP_CLIENT_ID`), expiración y
  `token_use = id`.
- Busca la ruta en un catálogo de grupos de Cognito permitidos. Una ruta que no está en el catálogo se
  rechaza.

| Ruta | Grupos permitidos |
| :--- | :--- |
| `POST /drivers` | `FLEET_ADMIN` |
| `GET /drivers` (con o sin `valid_until`) | `FLEET_ADMIN` |
| `GET /drivers/{id}` | `FLEET_ADMIN`; `DRIVER` solo si su `custom:driver_id` es igual al `{id}` |
| `PUT /drivers/{id}` | `FLEET_ADMIN` |
| `POST /drivers/{id}/medical-release` | `FLEET_ADMIN` |

Los grupos `EMPLOYEE`, `MAINTENANCE_TECHNICIAN` y `MANAGEMENT` no tienen acceso a estas rutas.

Respuestas de API Gateway (no siguen el formato común de error; las genera el Gateway):

| Caso | Respuesta |
| :--- | :--- |
| Sin encabezado `Authorization` | `401 {"message":"Unauthorized"}` |
| Token inválido, alterado o expirado | `403 {"message":"Forbidden"}` |
| Grupo no permitido, o `DRIVER` con un `{id}` que no es el suyo | `403 {"message":"Forbidden"}` |
| La Lambda falla o supera su tiempo | `500` |

Las llamadas internas de otros microservicios (Asignaciones, Telemetría) van directas a la EC2, sin JWT,
y no pasan por el authorizer.

Para verificar el catálogo después de cada despliegue, `scripts/verify-authorizer.sh` pide los tokens de
los usuarios de prueba a Cognito, llama a todas las rutas por API Gateway y compara cada código con el
esperado:

```bash
export API='<Invoke URL del stage>' CLIENT_ID='<app client id>' TEST_PASSWORD='<contraseña>'
export EC2_URL='http://<elastic-ip>:8085'   # opcional: comprueba también una llamada interna
./scripts/verify-authorizer.sh
```

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

### Consulta de conductores con licencia vigente (HU-07)

El endpoint `GET /drivers` admite el parámetro opcional `valid_until=AAAA-MM-DD` (por ejemplo, `GET /drivers?status=ACTIVE&valid_until=2026-12-31`) para listar conductores cuya licencia de conducción está vigente hasta esa fecha:

- **Validación**: `valid_until` debe cumplir el formato calendario `AAAA-MM-DD` (`400 INVALID_DATA` con `INVALID_FORMAT`) y no ser anterior a la fecha actual en GMT-5 / `America/Bogota` (`400 INVALID_DATA` con `VALUE_NOT_ALLOWED`). Cuando `valid_until` está presente, `page` y `size` se ignoran sin validarse.
- **Candidatos y lotes**: se consultan en Firestore los conductores que cumplen `status`, excluyendo siempre `MEDICAL_HOLD_INCIDENT`, y se envían en lotes de hasta **100** IDs a Documentación (`GET /documents/validity-check?owner_type=DRIVER&valid_until=...&owner_ids=...&size=100`) propagando `X-Correlation-Id`.
- **Respuesta**: `{"content": [...], "total": n, "truncated": bool}` ordenada ascendentemente por `created_at` y sin paginar. `content` tiene un máximo de **500** elementos; `total` indica el total de conductores vigentes encontrados y `truncated` es `true` cuando supera los 500.
- **Configuración de Documentación y Circuit Breaker (Resilience4j)**:
  - `DOCUMENTACION_URL`: URL base del servicio de Documentación.
  - `DOCUMENTACION_TIMEOUT_MS`: timeout HTTP hacia Documentación (por defecto `2000` ms).
  - `CIRCUIT_BREAKER_MAX_CONSECUTIVE_FAILURES`: fallos consecutivos necesarios para abrir el circuito (por defecto `5`).
  - `CIRCUIT_BREAKER_WAIT_DURATION_MS`: tiempo que permanece abierto antes de pasar a *half-open* (por defecto `30000` ms).
- **Fallo de dependencia (`503 DEPENDENCY_UNAVAILABLE`)**: si Documentación tarda más del timeout, responde 5xx o el circuito está abierto en cualquier lote, toda la petición falla inmediatamente con `503 DEPENDENCY_UNAVAILABLE` (`"No fue posible validar la vigencia de las licencias, intente nuevamente"`), sin devolver listas parciales.

Los endpoints de gestión usan la raíz del servicio (`/health`, `/metrics` e `/info`, no `/actuator/*`)
y un puerto propio, `MANAGEMENT_PORT` (`18001`), separado del de la API (`8085`). Así el puerto de la API
puede ser público sin exponer las métricas. Por ejemplo:
`curl http://localhost:18001/health` y `curl http://localhost:18001/metrics`. En el puerto `8085`,
esas rutas responden `404`.

En `/metrics`, las métricas de eventos son:

**Publicación:**
- `drivers_events_published_total{event_type}`: eventos publicados y marcados `SENT`.
- `drivers_events_publish_failures_total{event_type}`: intentos fallidos de publicación en SNS.
- `drivers_outbox_pending`: eventos que siguen pendientes en Firestore.

**Consumo SQS:**
- `drivers_events_consumed_total{event_type, result}`: eventos consumidos con `result = processed`, `duplicate` o `ignored` (documento que no es de conductor, por ejemplo, evento que iba dirigido a vehículs).
- `drivers_events_dlq_total{event_type, reason}`: eventos enviados o derivados a la DLQ con `reason = invalid_message`, `driver_not_found` o `max_retries`.

### Publicador del outbox

El registro de un conductor, el alta médica, la modificación de sus datos (`driver_updated_event`, solo si
algún campo cambia) y el vencimiento de su licencia (`driver_blocked_event`, solo si estaba `ACTIVE`; lo
guarda el consumidor junto con `processed_events`), y la renovación de su documento (`driver_activated_event`, solo si el conductor queda `ACTIVE`; también lo guarda el consumidor junto con `processed_events`) guardan el evento en `outbox_events` en la misma
transacción de Firestore, con estado `PENDING`, y responde sin esperar a SNS. Un proceso programado,
cada `OUTBOX_PUBLISH_INTERVAL_MS` (2 s), lee hasta `OUTBOX_BATCH_SIZE` eventos `PENDING` en orden de
`created_at`, los publica en `fleetops_driver_events` con el message attribute `event_type` y los marca
`SENT` cuando SNS confirma.

Si SNS no responde, el evento sigue `PENDING`, suma `attempts` y se intenta de nuevo en la siguiente
pasada. `OutboxSnsOutageIntegrationTest` lo prueba de punta a punta con un SNS simulado caído:
`POST /drivers` responde `201`, el evento queda `PENDING` con `attempts > 0` y, cuando SNS vuelve, se
publica y queda `SENT`.

### Consumidor SQS

El consumidor escucha en la cola `fleetops_driver_queue` (conectada por suscripciones SNS a eventos
de documentación e incidentes). Procesa:
- `document_expired_event`: aplica bloqueo de documentos al conductor (`INACTIVE_EXPIRED_DOCUMENTS`).
- `document_renewed_event`: retira el bloqueo de documentos; el conductor queda `ACTIVE` si no tiene bloqueo médico (si lo tiene, sigue en `MEDICAL_HOLD_INCIDENT`).
- `severe_incident_reported_event`: aplica bloqueo médico (`MEDICAL_HOLD_INCIDENT`).

Acepta el mensaje tal cual o envuelto en la notificación de SNS, y valida el sobre estándar:
`event_id` y `correlation_id` (UUID), `event_type`, `source` y `occurred_at` (ISO 8601 con offset o `Z`).
En `data`, `document_expired_event` debe traer `document_id`, `document_type`, `owner_type`, `owner_id`
y `expiration_date`, y el conductor es `owner_id` (UUID). `document_renewed_event` debe traer `document_id`, `document_type`, `owner_type` y `owner_id` (sin `expiration_date`). Solo `severe_incident_reported_event` trae `driver_id` (UUID).
Los campos adicionales se ignoran.

| Caso | Qué hace | Log | Métrica |
| :--- | :--- | :--- | :--- |
| Evento válido | Cambia el estado del conductor y confirma el mensaje. | INFO con `owner_id` y `document_id` en `document_expired_event`, y en `document_renewed_event` cuando se quita el bloqueo | `drivers_events_consumed_total{result="processed"}` |
| `event_id` ya procesado | Confirma el mensaje sin cambios. | — | `drivers_events_consumed_total{result="duplicate"}` |
| Documento que no es de un conductor (`owner_type` distinto de `DRIVER`) | Confirma el mensaje sin cambios, sin filtrar por `document_type`. | DEBUG | `drivers_events_consumed_total{result="ignored"}` |
| JSON inválido, campo faltante o `event_type` desconocido | Lo envía a la DLQ sin reintentos, con los atributos `event_type` y `reason`. | ERROR con `event_id` y el motivo | `drivers_events_dlq_total{reason="invalid_message"}` |
| Conductor inexistente | Lo envía a la DLQ sin reintentos. | WARN con `owner_id` (o `driver_id`) y `event_id` | `drivers_events_dlq_total{reason="driver_not_found"}` |
| Firestore no responde | No confirma el mensaje; SQS lo reentrega y, al 3.er intento fallido, la redrive policy (`maxReceiveCount = 3`) lo mueve a la DLQ. | ERROR en el 3.er intento | `drivers_events_dlq_total{reason="max_retries"}` |

El número de intento sale del atributo `ApproximateReceiveCount` de SQS, que Spring Cloud AWS expone
como la cabecera `Sqs_Msa_ApproximateReceiveCount`.

**Idempotencia:** el cambio del conductor y el documento `processed_events/{event_id}` se guardan en
una sola transacción de Firestore. Si el `event_id` ya existe, la transacción no escribe nada, así que
dos entregas del mismo evento no pueden cambiar el conductor dos veces.

`DriverEventListenerLocalStackIntegrationTest` prueba los casos de `document_expired_event` y `document_renewed_event` contra LocalStack y el emulador de
Firestore.

## Firestore

`firestore.indexes.json` versiona el índice compuesto de la colección `drivers`:

- `status` ascendente.
- `created_at` ascendente.

Este índice cubre el listado con filtro por estado y orden por fecha de creación.

El emulador no exige este índice, pero Firestore real sí. Despliégalo con
`firebase deploy --only firestore:indexes`; de lo contrario, el listado filtrado por `status`
falla.

### Retención de `processed_events` (TTL)

Cada documento de `processed_events` guarda `event_id`, `event_type`, `driver_id`, `processed_at` y
`expire_at = processed_at + 30 días`. Una política TTL de Firestore sobre `expire_at` borra cada
documento cuando vence; 30 días cubre de sobra las reentregas de SQS.

La política TTL no se versiona en `firestore.indexes.json`. Se crea una vez por proyecto:

- Consola: Firestore → **TTL** → **Create policy** → colección `processed_events`, campo `expire_at`.
- CLI: `gcloud firestore fields ttls update expire_at --collection-group=processed_events --enable-ttl`

Firestore borra los documentos vencidos normalmente dentro de las 24 horas siguientes a `expire_at`.
El emulador no aplica TTL.

**Limitación actual:** el proyecto de Firebase está en el plan Spark, que no ejecuta borrados por TTL,
así que la política no está activa y la colección crece sin límite. Cada documento ocupa unos 200 bytes,
así que el impacto es bajo.

### Firestore no disponible

Si Firestore no responde, `POST /drivers`, `GET /drivers/{id}`, `GET /drivers`,
`PUT /drivers/{id}` y `POST /drivers/{id}/medical-release` responden `503` con el formato común de error:

```json
{
  "code": "SERVICE_UNAVAILABLE",
  "message": "Servicio temporalmente no disponible, intente nuevamente",
  "timestamp": "2026-11-05T15:56:54.276-05:00",
  "path": "/drivers"
}
```

- Cada llamada a Firestore espera como máximo el tiempo de la variable de entorno
  `FIRESTORE_TIMEOUT` (propiedad `app.firestore.timeout`, por defecto `2s`). El mismo valor limita
  los reintentos internos del cliente (`FirestoreConfig`), que por defecto llegan a 60 s, y la
  comprobación de `GET /health`. Si el valor es menor a 1 ms o está vacío, el servicio no arranca.
  **Es un valor inicial pendiente de validar en producción**: una transacción de
  varias operaciones con latencia normal podría acercarse a ese límite.
- Un fallo de Firestore nunca se responde como `404`.
- El fallo se registra una sola vez, con nivel `ERROR`, en `GlobalExceptionHandler`.
- Errores no transitorios del SDK (permisos, credenciales, índice faltante) siguen siendo
  `500 INTERNAL_ERROR`, porque reintentar no los resuelve.
- Al volver Firestore, el servicio se recupera solo, sin reiniciarlo.

#### Riesgos

- Una transacción cancelada por timeout (por ejemplo, el registro de `POST /drivers`) puede
  confirmarse en el servidor después de que el cliente recibió `503`. Si el cliente reintenta,
  recibirá `409` porque el conductor ya existe.
- La primera llamada tras arrancar el servicio (o el emulador) abre la conexión y puede pasar de
  2 s; en la prueba manual el primer `POST` respondió `503` con el emulador recién levantado.

#### Prueba manual

Con el servicio en `http://localhost:8085` (ver [Ejecución local](#ejecución-local)) y un
conductor creado con id `$ID`:

```bash
docker stop conductores_firestore

curl -i -X POST http://localhost:8085/drivers -H 'Content-Type: application/json' \
  -d '{"full_name":"Ana Gómez","document_number":"1144056790","email":"ana@correo.com"}'
curl -i http://localhost:8085/drivers/$ID
curl -i "http://localhost:8085/drivers?page=0&size=10"
curl -i -X PUT http://localhost:8085/drivers/$ID -H 'Content-Type: application/json' \
  -d '{"full_name":"Ana M. Gómez","email":"ana@correo.com"}'
curl -i -X POST http://localhost:8085/drivers/$ID/medical-release -H 'Content-Type: application/json' \
  -d '{"notes":"Alta médica otorgada"}'

docker start conductores_firestore
```

Resultado esperado con el emulador detenido: los cuatro responden `503 SERVICE_UNAVAILABLE` en
unos 2 s, `GET /health` responde `503` y el log muestra una línea `ERROR` por petición. Tras
`docker start`, los endpoints vuelven a `201`/`200` en unos segundos sin reiniciar el servicio.
El emulador guarda los datos en memoria: los conductores creados antes de `docker stop` ya no
existen y su `GET` responde `404`, que es correcto.

## Producción (EC2)

La composición `docker-compose.prod.yml` usa la imagen publicada en Docker Hub (sin `build:`). En
la EC2:

```bash
cp .env.prod.example .env
# Completa .env con los valores reales (los secretos nunca se versionan).
docker compose -f docker-compose.prod.yml up -d
```

Requisitos de la EC2:

- Un rol IAM que permita publicar en `fleetops_driver_events` y consumir
  `fleetops_driver_queue` y `fleetops_driver_dlq`. No se montan llaves de AWS: el SDK usa el
  perfil de instancia.
- El JSON de la cuenta de servicio de Firebase ubicado en la ruta indicada por
  `FIREBASE_CREDENTIALS_PATH`; se monta como volumen de solo lectura en
  `/secrets/firebase-adminsdk.json`.

## Observabilidad

En la EC2 solo corren
exporters, mientras que Prometheus y Grafana corren en la máquina de quien monitorea y leen esos exporters por
HTTP (modelo pull). Así no se instala en producción más de lo necesario.

| Componente | Dónde | Puerto | Qué mide |
| :--- | :--- | :--- | :--- |
| Métricas del servicio (Actuator) | EC2, contenedor `backend` | `18001` | HTTP por endpoint (con histograma para el p95), eventos consumidos y publicados, outbox, JVM |
| node_exporter v1.12.1 | EC2, `docker-compose.prod.yml` | `19100` | CPU, memoria, disco y red del sistema operativo |
| cAdvisor v0.55.1 | EC2, `docker-compose.prod.yml` | `18080` | CPU, memoria, red y disco de cada contenedor |
| Prometheus v3.16.0 | Máquina local, `observability/docker-compose.yml` | `19090` | Lee los tres puertos de la EC2 cada 15 s |
| Grafana 13.3.1 | Máquina local, `observability/docker-compose.yml` | `13000` | Dashboards |

Los puertos no estándar siguen la recomendación de OWASP. Las métricas del servicio están en
el puerto de gestión `18001`, no en el `8085` de la API: el `8085` puede ser público
sin exponer las métricas.

### Logs en JSON y correlation_id (TEC_03)

El servicio escribe en la salida estándar un objeto JSON por línea con el esquema ECS (Elastic Common
Schema) nativo de Spring Boot (`logging.structured.format.console` en `application.yaml`). La variable
`LOG_FORMAT` elige el formato: sin definir usa `ecs`; vacía (`LOG_FORMAT=`) vuelve al texto plano de
Spring Boot, pensado solo para desarrollo local. En producción no se define. Ejemplo de un error en una
petición:

```json
{"@timestamp":"2026-11-20T15:15:02.114Z","log":{"level":"ERROR","logger":"co.edu.univalle.conductores.adapter.in.web.GlobalExceptionHandler"},
 "process":{"pid":1,"thread":{"name":"http-nio-8085-exec-3"}},"service":{"name":"ms-conductores"},
 "message":"Unexpected error on GET /drivers","correlation_id":"0f8fad5b-d9cb-469f-a165-70867728950e",
 "http_method":"GET","http_path":"/drivers",
 "error":{"type":"java.lang.RuntimeException","message":"...","stack_trace":"java.lang.RuntimeException: ..."},
 "ecs":{"version":"8.11"}}
```

| Origen | Campos de contexto |
| :--- | :--- |
| Petición HTTP | `correlation_id`, `http_method`, `http_path` |
| Evento consumido de `fleetops_driver_queue` | `correlation_id`, `event_id`, `event_type` (los del sobre) |
| Evento publicado por el outbox | `correlation_id`, `event_id`, `event_type` |

- **correlation_id en HTTP:** `CorrelationIdFilter` lo toma del encabezado `X-Correlation-Id` si es un
  UUID y lo genera si no llega o no es válido. Se devuelve en el encabezado `X-Correlation-Id` de todas
  las respuestas, también en los errores, y es el mismo que se guarda en los eventos del outbox.
- **Llamadas a Documentación:** `CorrelationIdClientInterceptor` añade `X-Correlation-Id` con el
  correlation_id de la operación en curso. El cliente de HU-07 debe registrarlo con
  `RestClient.builder().requestInterceptor(interceptor)`.
- **Sin datos de contacto completos:** `ContactDataMaskingCustomizer` enmascara cualquier email
  (`c***@correo.com`) y teléfono móvil de 10 dígitos (`******4567`) en todos los campos del JSON, incluidos
  `error.message` y `error.stack_trace`, aunque el código los incluya por error. No toca UUIDs, números de
  documento ni fechas. En texto plano (`LOG_FORMAT=`) no se enmascara: no lo uses en producción.
- Para un log nuevo con contexto propio, usa las claves de `LogContext` (`application/logging`).

Para leer los logs en la EC2 de forma legible:

```bash
docker compose -f docker-compose.prod.yml logs --no-log-prefix backend | jq -R "fromjson? // empty" | jq -r '[.["@timestamp"], .log.level, .correlation_id, .message] | @tsv'
```

### Security group

Los puertos de métricas nunca se abren a `0.0.0.0/0`. En el security group de la EC2, añade una regla
por puerto con origen **"Mi IP"** de quien vaya a monitorear:

| Puerto | Uso | Origen |
| :--- | :--- | :--- |
| `19100` | node_exporter | Mi IP |
| `18080` | cAdvisor | Mi IP |
| `18001` | Métricas y salud del servicio | Mi IP |

Si tu IP cambia (pasa a menudo en conexiones domésticas), actualiza las reglas. Comprueba el acceso
desde tu máquina:

```bash
curl -s http://<EC2_IP>:19100/metrics | head -5
curl -s http://<EC2_IP>:18080/metrics | head -5
curl -s http://<EC2_IP>:18001/metrics | head -5
```

Un timeout significa que falta la regla o que el contenedor no está corriendo (`docker ps` en la EC2).

### Exporters en la EC2

El CD los despliega junto con el backend (ver "Despliegue continuo"). Para levantarlos a mano en la
EC2:

```bash
cd ~/app
docker compose -f docker-compose.prod.yml up -d node-exporter cadvisor
docker compose -f docker-compose.prod.yml ps
```

node_exporter monta `/proc`, `/sys` y la raíz del host en solo lectura, sin capacidades y con
`no-new-privileges`. cAdvisor monta en solo lectura los directorios que indica el dojo y se limita a
contenedores Docker (`--docker_only`), revisando cada 30 s para reducir su consumo. La instancia es una
`t3.small` (2 GiB de RAM); revisa el margen con `free -h` y `docker stats --no-stream` después de
desplegar.

### Prometheus y Grafana en tu máquina

Desde la carpeta `observability/`:

```bash
cp .env.example .env     # pon en EC2_IP la Elastic IP de la EC2
docker compose up -d
```

- **Prometheus:** http://localhost:19090. En **Status → Target health**, los jobs `ms-conductores`,
  `node-exporter` y `cadvisor` deben estar en *UP*, y la consulta `up` debe devolver `1` en cada uno.
- **Grafana:** http://localhost:13000. La primera vez entra con `admin` / `admin` y Grafana pide
  cambiar la contraseña. El data source de Prometheus y los dashboards se cargan solos, en la carpeta
  **FleetOps**.

Los targets de `prometheus/prometheus.yml` usan el nombre `ec2-conductores`, que el compose resuelve
con `EC2_IP`. Así nadie tiene que editar la configuración cuando cambia la IP. Para probar todo contra
tu propia máquina, usa `EC2_IP=host-gateway` y levanta en local el backend (`docker compose up -d` en
la raíz) y los exporters (`docker compose -f docker-compose.prod.yml up -d node-exporter cadvisor`).

Prometheus guarda hasta 7 días o 512 MB en un volumen de Docker. Solo hay datos mientras tu Prometheus
local está encendido; el histórico queda en tu máquina. `docker compose down -v` borra esos datos.

### Dashboards

| Dashboard | Archivo | Contenido |
| :--- | :--- | :--- |
| FleetOps - ms-conductores | `grafana/dashboards/fleetops-ms-conductores.json` | Peticiones por segundo por endpoint, porcentaje de errores, CPU y memoria de la EC2, y p95 de latencia por endpoint con la meta de 2 s |
| Node Exporter Full (ID 1860) | `grafana/dashboards/node-exporter-full-1860.json` | Dashboard de la comunidad que propone el dojo: CPU, memoria, disco, red y sistema. Revisión 46 descargada de grafana.com (licencia LGPL-3.0, autor rfmoz) |

El p95 se calcula por ruta y método con el histograma de `http.server.requests`; cumplir la meta
requiere un p95 estrictamente menor a 2 s. Sin tráfico reciente no hay una estimación válida: genera
peticiones a `/drivers` y espera varias ventanas de scraping. Los 401/403 que responde API Gateway no
llegan al servicio y no se cuentan.

Consultas útiles en Prometheus:

```promql
# CPU ocupada de la EC2 (%)
100 * (1 - avg(rate(node_cpu_seconds_total{job="node-exporter", mode="idle"}[5m])))

# Memoria usada de la EC2 (%)
100 * (1 - node_memory_MemAvailable_bytes{job="node-exporter"}
           / node_memory_MemTotal_bytes{job="node-exporter"})

# Espacio libre en disco
node_filesystem_avail_bytes{job="node-exporter"}

# CPU por contenedor
rate(container_cpu_usage_seconds_total{job="cadvisor", name!=""}[5m])

# Estado de todos los targets
up
```

## Publicación en Docker Hub

Publicación manual. Sustituye `<usuario>` por
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

Los secretos necesarios se listan en la sección Despliegue continuo (CD).

## Despliegue continuo (CD)

El workflow `.github/workflows/cd.yml` se dispara con un push/merge a `main` (o manualmente con
`workflow_dispatch`). La publicación y el despliegue solo se ejecutan desde `main`; un
`workflow_dispatch` desde otro branch solo corre las pruebas. Flujo:

1. **Pruebas:** `./gradlew clean build` (Checkstyle, pruebas y cobertura).
2. **SonarCloud:** `./gradlew sonar`, solo en `main` (el plan gratuito no analiza otras ramas); si
   el Quality Gate falla, se detiene la publicación.
3. **Imagen:** se construye y publica en Docker Hub con dos etiquetas: el SHA completo del commit
   y `latest`.
4. **Sincronización y prevalidación:** por SSH/SCP, copia `docker-compose.prod.yml` a un directorio
   temporal bajo `~/app`. Comprueba que existe `.env`, que `SERVER_PORT=8085` y que `MANAGEMENT_PORT`
   es `18001` o no está definido. Valida la configuración temporal con `docker compose config --quiet`
   y solo entonces reemplaza `docker-compose.prod.yml` en producción.
5. **Backend:** despliega la imagen etiquetada con `github.sha`, comprueba `/health` en el puerto de
   gestión (`18001`) y vuelve al tag anterior si falla. Si la descarga de la imagen falla, el backend
   no se recrea.
6. **Exporters:** actualiza `node-exporter` y `cadvisor` sin recrear el backend. Con
   `--remove-orphans` elimina los contenedores de servicios que ya no están en el compose (por
   ejemplo, el Prometheus y el Grafana de la configuración anterior, que ahora corren en local).
   Después comprueba que el `19100` publica CPU y disco, que el `18080` publica CPU por contenedor y
   que el `18001` publica métricas del servicio (`process_uptime_seconds`; las métricas HTTP solo
   aparecen después de la primera petición a la API). Un fallo marca el workflow como fallido, pero
   no revierte un backend que ya quedó saludable.

Secretos de GitHub necesarios:

- `DOCKERHUB_USERNAME` y `DOCKERHUB_TOKEN` (token de acceso de Docker Hub, no la contraseña).
- `EC2_HOST`, `EC2_USER` y `EC2_SSH_KEY` (clave privada de un par dedicado al pipeline, no una
  clave personal).

Antes de habilitar este workflow, en la EC2 deben existir el `.env` real y el JSON de la cuenta de
servicio de Firebase (permisos `600`, montado como volumen de solo lectura). El workflow nunca copia
`.env` ni credenciales; solo sincroniza `docker-compose.prod.yml`. Los puertos `18001`, `19100` y
`18080` solo se abren a "Mi IP" de quien monitorea (ver "Observabilidad").

Rollback manual: define `IMAGE_TAG=<SHA anterior>` en el `.env` de la EC2 y ejecuta
`docker compose -f docker-compose.prod.yml up -d --no-deps backend`.

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
- **Título:** similar al formato de un commit, con la HU o tarea como alcance, en menos de 70
  caracteres. Ejemplo: `Feat/hu 06 filter drivers by status`.
- **Descripción:** se llena la plantilla `.github/pull_request_template.md`. Las secciones marcadas
  con `*` son obligatorias.
- **Antes de pedir revisión:** el CI debe pasar (Checkstyle, pruebas, cobertura y SonarCloud). Los
  checks obligatorios en la protección de la rama son `Build, Test y Calidad` y
  `SonarCloud Analysis`; la revisión automática de Qodo es una ayuda, no un bloqueo.
- Si el PR resuelve una limitación de la lista de abajo, se borra de esa lista en el mismo PR.
 

## Infraestructura AWS

### Recursos SNS / SQS (us-east-1)

| Recurso | Nombre | Tipo |
| :--- | :--- | :--- |
| Topic SNS | `fleetops_driver_events` | Standard |
| Cola SQS | `fleetops_driver_queue` | Standard |
| DLQ SQS | `fleetops_driver_dlq` | Standard |

La cola tiene una redrive policy hacia la DLQ con `maxReceiveCount = 3`.

Suscripciones externas (de otros equipos):

| Topic origen | Endpoint | Raw delivery | Filter policy |
| :--- | :--- | :--- | :--- |
| `fleetops_document_events` | `fleetops_driver_queue` | ✅ | `{"event_type": ["document_expired_event", "document_renewed_event"]}` |
| `fleetops_incident_events` | `fleetops_driver_queue` | ✅ | `{"event_type": ["severe_incident_reported_event"]}` |

> Las suscripciones se crean cuando los topics de Documentación e Incidentes existan.

### Rol IAM de la EC2

Rol: `fleetops-conductores-ec2-role`. Política inline `fleetops-conductores-policy`:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "PublishDriverEvents",
      "Effect": "Allow",
      "Action": "sns:Publish",
      "Resource": "arn:aws:sns:us-east-1:<ACCOUNT_ID>:fleetops_driver_events"
    },
    {
      "Sid": "ConsumeDriverQueue",
      "Effect": "Allow",
      "Action": [
        "sqs:ReceiveMessage",
        "sqs:DeleteMessage",
        "sqs:GetQueueAttributes",
        "sqs:GetQueueUrl",
        "sqs:ChangeMessageVisibility"
      ],
      "Resource": "arn:aws:sqs:us-east-1:<ACCOUNT_ID>:fleetops_driver_queue"
    },
    {
      "Sid": "SendToDLQ",
      "Effect": "Allow",
      "Action": "sqs:SendMessage",
      "Resource": "arn:aws:sqs:us-east-1:<ACCOUNT_ID>:fleetops_driver_dlq"
    }
  ]
}
```

El servicio no usa `AWS_ACCESS_KEY_ID` ni `AWS_SECRET_ACCESS_KEY`: el SDK toma las credenciales del
perfil de instancia (rol IAM).

### Security group de la EC2

| Puerto | Protocolo | Fuente | Nota |
| :--- | :--- | :--- | :--- |
| 22 | TCP | `0.0.0.0/0` | SSH del workflow CD y del equipo. Excepción documentada: las IPs de GitHub Actions cambian |
| 8085 | TCP | `0.0.0.0/0` | **Limitación**: API Gateway llega sin VPC Link, así que no se puede limitar a su origen |
| 18001 | TCP | Mi IP | Métricas y salud del servicio, para el Prometheus local (DEV_08) |
| 19100 | TCP | Mi IP | node_exporter, para el Prometheus local (DEV_08) |
| 18080 | TCP | Mi IP | cAdvisor, para el Prometheus local (DEV_08) |

Las reglas de 22 y 8085 están abiertas a Internet y son limitaciones conocidas; las de métricas solo
admiten la IP de quien monitorea. Para cerrar el 8085 hace falta un
VPC Link entre API Gateway y la EC2; para cerrar el 22, desplegar con AWS Systems Manager en vez de SSH.

### Elastic IP

Se asignó una Elastic IP a la instancia para que la dirección no cambie al reiniciar. El secret
`EC2_HOST` del pipeline apunta a esta IP.

### Firestore de producción

- Proyecto Firebase con Firestore en modo **Native**.
- Cuenta de servicio con rol `Cloud Datastore User`.
- Índices desplegados con `firebase deploy --only firestore:indexes --project <project-id>`.
- El JSON de la cuenta de servicio está en la EC2 con permisos `600`, montado como volumen de solo
  lectura, fuera del repositorio.

## Limitaciones conocidas a corregir/implementar en futuro cercano

- No están implementados la integración con Documentación ni el Circuit Breaker: URL del servicio,
  timeout de 2 s, 5 fallos consecutivos y 30 s con el circuito abierto.
- Faltan el listado de elegibles, el alta médica y el borrado lógico.
- Los errores de validación usan códigos y estructura parcialmente distintos a los propuestos en el documento de historias de usuario que tenemos como grupo
  (`errors[].message` en lugar de `errors[].code`).
- Las rutas desconocidas (por ejemplo, `/drivres`) devuelven el cuerpo de error por defecto de
  Spring en lugar del formato común de error (TEC_04). Se puede añadir un formato de error adicional al catalogo en la documentación.
- `document_number` no se recorta antes de validar/guardar y `document_numbers` no guarda
  `created_at`.
- Las fechas se guardan en Firestore como texto ISO 8601, no como timestamps nativos.
