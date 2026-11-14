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
| `AWS_ACCESS_KEY_ID` | Credencial falsa para LocalStack. No se define en producción (rol IAM). | `test` (local) | Prevista |
| `AWS_SECRET_ACCESS_KEY` | Credencial falsa para LocalStack. No se define en producción (rol IAM). | `test` (local) | Prevista |
| `SPRING_CLOUD_AWS_ENDPOINT` | Endpoint de AWS (LocalStack en local). Vacío en producción para usar AWS real. | `http://localhost:4566` (local) | Prevista |
| `JAVA_TOOL_OPTIONS` | Opciones JVM del contenedor (por ejemplo `-XX:MaxRAMPercentage=75.0`). | `-XX:MaxRAMPercentage=75.0` | Implementada |
| `FLEETOPS_DRIVER_TOPIC_ARN` | ARN del topic `fleetops_driver_events`. | LocalStack local; ARN real en producción. | Implementada |
| `FLEETOPS_DRIVER_QUEUE_URL` | URL de la cola `fleetops_driver_queue`. | — | Prevista |
| `FLEETOPS_DRIVER_DLQ_URL` | URL de la DLQ `fleetops_driver_dlq`. | — | Prevista |
| `OUTBOX_PUBLISH_INTERVAL_MS` | Intervalo del publicador del outbox. | `2000` (2 s) | Implementada |
| `OUTBOX_BATCH_SIZE` | Máximo de eventos pendientes por ciclo del publicador. | `50` | Implementada |
| `OUTBOX_PUBLISHER_ENABLED` | Habilita o deshabilita el publicador periódico. | `true` | Implementada |
| `DOCUMENTACION_URL` | URL del servicio de Documentación. | — | Prevista |
| `DOCUMENTACION_TIMEOUT_MS` | Timeout hacia Documentación. | `2000` (2 s) | Prevista |
| `CIRCUIT_BREAKER_MAX_CONSECUTIVE_FAILURES` | Fallos consecutivos para abrir el Circuit Breaker. | `5` | Prevista |
| `CIRCUIT_BREAKER_WAIT_DURATION_MS` | Espera con el circuito abierto. | `30000` (30 s) | Prevista |
| `FIREBASE_CREDENTIALS_PATH` | Ruta en el host (EC2) al JSON de Firebase que se monta como volumen. | `./credentials/firebase-adminsdk.json` | Infra |
| `DOCKERHUB_USER` | Usuario de Docker Hub (secreto `DOCKERHUB_USERNAME` en CI/CD). | — | Infra |
| `IMAGE_NAME` | Nombre de la imagen Docker. | `ms-conductores` | Infra |
| `IMAGE_TAG` | Etiqueta de la imagen (SHA corto del commit en CD; `latest` solo en main). | `latest` | Infra |

Las variables marcadas como *Prevista* documentan integraciones pendientes, como el consumo SQS,
Documentación y el Circuit Breaker. El publisher SNS usa el outbox de Firestore: guarda el evento
con el conductor y publica los pendientes de forma periódica.

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

Nota: se fija `localstack/localstack:4.14.0` (última imagen Community); el tag `latest`
(≥ 2026.03) requiere `LOCALSTACK_AUTH_TOKEN` y no arranca sin él.

## Endpoints

| Método | Ruta | Descripción |
| :--- | :--- | :--- |
| `POST` | `/drivers` | Registra un conductor. Responde `201`, `400`, `409` o `503`. |
| `GET` | `/drivers/{id}` | Consulta un conductor por UUID. Responde `200`, `404` (también `404` si el id no es un UUID) o `503`. |
| `GET` | `/drivers` | Lista conductores con filtros opcionales `status`, `page` y `size` (`size` entre 1 y 100). Responde `200`, `400` o `503`. |
| `PUT` | `/drivers/{id}` | Actualiza `full_name`, `email` y `phone`. Responde `200`, `400`, `404` o `503`. |
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

En `/metrics`, las métricas de publicación de eventos son:

- `fleetops_driver_events_published_total{event_type}`: eventos publicados y marcados `SENT`.
- `fleetops_driver_event_publish_failures_total{event_type}`: intentos fallidos de publicación en SNS.
- `fleetops_outbox_pending_events`: eventos que siguen pendientes en Firestore.

## Firestore

`firestore.indexes.json` versiona el índice compuesto de la colección `drivers`:

- `status` ascendente.
- `created_at` ascendente.

Este índice cubre el listado con filtro por estado y orden por fecha de creación.

El emulador no exige este índice, pero Firestore real sí. Despliégalo con
`firebase deploy --only firestore:indexes`; de lo contrario, el listado filtrado por `status`
falla.

### Firestore no disponible

Si Firestore no responde, `POST /drivers`, `GET /drivers/{id}`, `GET /drivers` y
`PUT /drivers/{id}` responden `503` con el formato común de error:

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

Los secretos necesarios se listan en la sección Despliegue continuo (CD).

## Despliegue continuo (CD)

El workflow `.github/workflows/cd.yml` se dispara con un push/merge a `main` (o manualmente con
`workflow_dispatch`). La publicación y el despliegue solo se ejecutan desde `main`; un
`workflow_dispatch` desde otro branch solo corre las pruebas. Flujo:

1. **Pruebas:** `./gradlew clean build` (Checkstyle, pruebas y cobertura).
2. **SonarCloud:** `./gradlew sonar`; si el Quality Gate falla, se detiene la publicación.
3. **Imagen:** se construye y publica en Docker Hub con dos etiquetas: el SHA completo del commit
   y `latest`.
4. **Despliegue:** por SSH a la EC2, `docker compose -f docker-compose.prod.yml up -d` con la
   nueva etiqueta.
5. **Verificación:** se consulta `/health`; si no responde `200`, se revierte automáticamente a la
   etiqueta anterior.

Secretos de GitHub necesarios:

- `DOCKERHUB_USERNAME` y `DOCKERHUB_TOKEN` (token de acceso de Docker Hub, no la contraseña).
- `EC2_HOST`, `EC2_USER` y `EC2_SSH_KEY` (clave privada de un par dedicado al pipeline, no una
  clave personal).

En la EC2, dentro de `~/app`, deben existir: `docker-compose.prod.yml`, un `.env` creado desde
`.env.prod.example` y el JSON de la cuenta de servicio de Firebase (permisos `600`, montado como
volumen de solo lectura). Ninguno de estos archivos se versiona.

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
- **Título:** mismo formato que un commit, con la HU o tarea como alcance, en menos de 70
  caracteres. Ejemplo: `feat(HU-06): filter drivers by status`.
- **Descripción:** se llena la plantilla `.github/pull_request_template.md`. Las secciones marcadas
  con `*` son obligatorias.
- **Antes de pedir revisión:** el CI debe pasar (Checkstyle, pruebas, cobertura y SonarCloud). Los
  checks obligatorios en la protección de la rama son `Build, Test y Calidad` y
  `SonarCloud Analysis`; la revisión automática de Qodo es una ayuda, no un bloqueo.
- Si el PR resuelve una limitación de la lista de abajo, se borra de esa lista en el mismo PR.
 

## Infraestructura AWS (DEV_10)

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
| 22 | TCP | IP del pipeline (SSH) | Acceso del workflow CD |
| 8085 | TCP | `0.0.0.0/0` | **Temporal**: se restringe al API Gateway, servicios internos y Prometheus cuando el Gateway exista |

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
- Los eventos no están implementados todavía: el broker es AWS SNS + SQS con Transactional Outbox
  (TEC_05); LocalStack para pruebas locales y de integración llega con DEV_09.
- Faltan el listado de elegibles, el alta médica y el borrado lógico.
- Los errores de validación usan códigos y estructura parcialmente distintos a los propuestos en el documento de historias de usuario que tenemos como grupo
  (`errors[].message` en lugar de `errors[].code`).
- Las rutas desconocidas (por ejemplo, `/drivres`) devuelven el cuerpo de error por defecto de
  Spring en lugar del formato común de error (TEC_04). Se puede añadir un formato de error adicional al catalogo en la documentación.
- `document_number` no se recorta antes de validar/guardar y `document_numbers` no guarda
  `created_at`.
- Las fechas se guardan en Firestore como texto ISO 8601, no como timestamps nativos.
- No hay logs estructurados JSON ni dashboards de Grafana por ahora.
- `PUT /drivers/{id}` no acepta `document_number`, así que no responde
  `400 NOT_EDITABLE` cuando llega con un valor distinto (HU-08, criterio 2); por eso tampoco
  aparece en la especificación OpenAPI.
