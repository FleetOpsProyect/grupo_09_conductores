package co.edu.univalle.conductores.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.edu.univalle.conductores.application.port.in.ProcessDriverEvent.Command;
import co.edu.univalle.conductores.application.port.in.ProcessDriverEvent.EventProcessingResult;
import co.edu.univalle.conductores.application.port.out.ConsumerMetricsPort;
import co.edu.univalle.conductores.application.port.out.DriverRepository;
import co.edu.univalle.conductores.application.port.out.ProcessedEventRepositoryPort;
import co.edu.univalle.conductores.domain.Driver;
import co.edu.univalle.conductores.domain.DriverNotFoundException;
import co.edu.univalle.conductores.domain.DriverStatus;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProcessDriverEventServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-14T10:00:00-05:00");
    private static final UUID DRIVER_ID = UUID.fromString("96b65376-7878-4390-bfba-cbdfa67b57c6");
    private static final UUID EVENT_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID CORRELATION_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    @Mock
    private DriverRepository driverRepository;

    @Mock
    private ProcessedEventRepositoryPort processedEventRepository;

    @Mock
    private ConsumerMetricsPort consumerMetrics;

    private ProcessDriverEventService service;

    @BeforeEach
    void setUp() {
        service = new ProcessDriverEventService(driverRepository, processedEventRepository, consumerMetrics);
    }

    @Test
    void rejectsNullDependencies() {
        assertThatThrownBy(() -> new ProcessDriverEventService(null, processedEventRepository, consumerMetrics))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ProcessDriverEventService(driverRepository, null, consumerMetrics))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ProcessDriverEventService(driverRepository, processedEventRepository, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void returnsDuplicateWhenEventAlreadyProcessed() {
        when(processedEventRepository.isProcessed(EVENT_ID)).thenReturn(true);

        Command command = new Command(EVENT_ID, ProcessDriverEventService.DOCUMENT_EXPIRED_EVENT,
                DRIVER_ID, NOW, CORRELATION_ID);

        EventProcessingResult result = service.process(command);

        assertThat(result).isEqualTo(EventProcessingResult.DUPLICATE);
        verify(consumerMetrics).recordConsumed(ProcessDriverEventService.DOCUMENT_EXPIRED_EVENT, "duplicate");
        verify(driverRepository, never()).findById(any());
        verify(driverRepository, never()).save(any());
        verify(processedEventRepository, never()).saveProcessed(any(), any(), any());
    }

    @Test
    void throwsDriverNotFoundWhenDriverDoesNotExist() {
        when(processedEventRepository.isProcessed(EVENT_ID)).thenReturn(false);
        when(driverRepository.findById(DRIVER_ID)).thenReturn(Optional.empty());

        Command command = new Command(EVENT_ID, ProcessDriverEventService.DOCUMENT_EXPIRED_EVENT,
                DRIVER_ID, NOW, CORRELATION_ID);

        assertThatThrownBy(() -> service.process(command))
                .isInstanceOf(DriverNotFoundException.class);

        verify(driverRepository, never()).save(any());
        verify(processedEventRepository, never()).saveProcessed(any(), any(), any());
        verify(consumerMetrics, never()).recordConsumed(any(), any());
    }

    @Test
    void processesDocumentExpiredEventAndAppliesBlock() {
        Driver driver = Driver.register("Carlos Pérez", "1144056789", "carlos@correo.com", null, NOW);
        when(processedEventRepository.isProcessed(EVENT_ID)).thenReturn(false);
        when(driverRepository.findById(DRIVER_ID)).thenReturn(Optional.of(driver));

        OffsetDateTime expiredAt = NOW.plusHours(1);
        Command command = new Command(EVENT_ID, ProcessDriverEventService.DOCUMENT_EXPIRED_EVENT,
                DRIVER_ID, expiredAt, CORRELATION_ID);

        EventProcessingResult result = service.process(command);

        assertThat(result).isEqualTo(EventProcessingResult.PROCESSED);
        assertThat(driver.isDocumentBlock()).isTrue();
        assertThat(driver.getStatus()).isEqualTo(DriverStatus.INACTIVE_EXPIRED_DOCUMENTS);
        assertThat(driver.getUpdatedAt()).isEqualTo(expiredAt);

        verify(driverRepository).save(driver);
        verify(processedEventRepository).saveProcessed(
                EVENT_ID, ProcessDriverEventService.DOCUMENT_EXPIRED_EVENT, DRIVER_ID);
        verify(consumerMetrics).recordConsumed(ProcessDriverEventService.DOCUMENT_EXPIRED_EVENT, "processed");
    }

    @Test
    void processesDocumentRenewedEventAndRemovesBlock() {
        Driver driver = Driver.restore(DRIVER_ID, "1144056789", "Carlos Pérez", "carlos@correo.com", null,
                true, false, NOW, NOW);
        when(processedEventRepository.isProcessed(EVENT_ID)).thenReturn(false);
        when(driverRepository.findById(DRIVER_ID)).thenReturn(Optional.of(driver));

        OffsetDateTime renewedAt = NOW.plusHours(2);
        Command command = new Command(EVENT_ID, ProcessDriverEventService.DOCUMENT_RENEWED_EVENT,
                DRIVER_ID, renewedAt, CORRELATION_ID);

        EventProcessingResult result = service.process(command);

        assertThat(result).isEqualTo(EventProcessingResult.PROCESSED);
        assertThat(driver.isDocumentBlock()).isFalse();
        assertThat(driver.getStatus()).isEqualTo(DriverStatus.ACTIVE);
        assertThat(driver.getUpdatedAt()).isEqualTo(renewedAt);

        verify(driverRepository).save(driver);
        verify(processedEventRepository).saveProcessed(
                EVENT_ID, ProcessDriverEventService.DOCUMENT_RENEWED_EVENT, DRIVER_ID);
        verify(consumerMetrics).recordConsumed(ProcessDriverEventService.DOCUMENT_RENEWED_EVENT, "processed");
    }

    @Test
    void processesSevereIncidentReportedEventAndAppliesMedicalBlock() {
        Driver driver = Driver.register("Carlos Pérez", "1144056789", "carlos@correo.com", null, NOW);
        when(processedEventRepository.isProcessed(EVENT_ID)).thenReturn(false);
        when(driverRepository.findById(DRIVER_ID)).thenReturn(Optional.of(driver));

        OffsetDateTime incidentAt = NOW.plusHours(1);
        Command command = new Command(EVENT_ID, ProcessDriverEventService.SEVERE_INCIDENT_REPORTED_EVENT,
                DRIVER_ID, incidentAt, CORRELATION_ID);

        EventProcessingResult result = service.process(command);

        assertThat(result).isEqualTo(EventProcessingResult.PROCESSED);
        assertThat(driver.isMedicalBlock()).isTrue();
        assertThat(driver.getStatus()).isEqualTo(DriverStatus.MEDICAL_HOLD_INCIDENT);
        assertThat(driver.getUpdatedAt()).isEqualTo(incidentAt);

        verify(driverRepository).save(driver);
        verify(processedEventRepository).saveProcessed(
                EVENT_ID, ProcessDriverEventService.SEVERE_INCIDENT_REPORTED_EVENT, DRIVER_ID);
        verify(consumerMetrics).recordConsumed(ProcessDriverEventService.SEVERE_INCIDENT_REPORTED_EVENT, "processed");
    }

    @Test
    void rejectsUnsupportedEventType() {
        Driver driver = Driver.register("Carlos Pérez", "1144056789", "carlos@correo.com", null, NOW);
        when(processedEventRepository.isProcessed(EVENT_ID)).thenReturn(false);
        when(driverRepository.findById(DRIVER_ID)).thenReturn(Optional.of(driver));

        Command command = new Command(EVENT_ID, "unknown_event", DRIVER_ID, NOW, CORRELATION_ID);

        assertThatThrownBy(() -> service.process(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tipo de evento no soportado");

        verify(driverRepository, never()).save(any());
        verify(processedEventRepository, never()).saveProcessed(any(), any(), any());
        verify(consumerMetrics, never()).recordConsumed(any(), any());
    }
}

