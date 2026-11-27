package co.edu.univalle.conductores.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.edu.univalle.conductores.application.port.in.ListValidDrivers.Query;
import co.edu.univalle.conductores.application.port.in.ListValidDrivers.ValidDriversResult;
import co.edu.univalle.conductores.application.port.out.DocumentValidityPort;
import co.edu.univalle.conductores.application.port.out.DriverRepository;
import co.edu.univalle.conductores.domain.DocumentationUnavailableException;
import co.edu.univalle.conductores.domain.Driver;
import co.edu.univalle.conductores.domain.DriverStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListValidDriversServiceTest {

    private static final LocalDate VALID_UNTIL = LocalDate.of(2026, 12, 31);
    private static final OffsetDateTime BASE_TIME = OffsetDateTime.of(2026, 10, 1, 8, 0, 0, 0, ZoneOffset.ofHours(-5));

    @Mock
    private DriverRepository repository;

    @Mock
    private DocumentValidityPort documentValidityPort;

    private ListValidDriversService service;

    @BeforeEach
    void setUp() {
        service = new ListValidDriversService(repository, documentValidityPort);
    }

    @ParameterizedTest
    @CsvSource({
        "50, 1",
        "100, 1",
        "101, 2",
        "150, 2",
        "250, 3"
    })
    void splitsCandidatesIntoBatchesOfAtMost100(int candidateCount, int expectedCalls) {
        List<Driver> candidates = buildDrivers(candidateCount, false, false);
        when(repository.findCandidates(DriverStatus.ACTIVE)).thenReturn(candidates);
        when(documentValidityPort.findDriversWithValidLicense(any(), eq(VALID_UNTIL)))
                .thenAnswer(invocation -> new HashSet<>(invocation.<List<UUID>>getArgument(0)));

        ValidDriversResult result = service.listValid(new Query(DriverStatus.ACTIVE, VALID_UNTIL));

        assertThat(result.total()).isEqualTo(candidateCount);
        assertThat(result.truncated()).isFalse();
        assertThat(result.content()).hasSize(candidateCount);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UUID>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(documentValidityPort, times(expectedCalls))
                .findDriversWithValidLicense(batchCaptor.capture(), eq(VALID_UNTIL));
        batchCaptor.getAllValues().forEach(batch -> assertThat(batch.size()).isBetween(1, 100));
    }

    @Test
    void returnsEmptyWithoutCallingPortWhenNoCandidates() {
        when(repository.findCandidates(DriverStatus.ACTIVE)).thenReturn(List.of());

        ValidDriversResult result = service.listValid(new Query(DriverStatus.ACTIVE, VALID_UNTIL));

        assertThat(result.content()).isEmpty();
        assertThat(result.total()).isZero();
        assertThat(result.truncated()).isFalse();
        verifyNoInteractions(documentValidityPort);
    }

    @Test
    void returnsEmptyWhenPortFindsNoValidLicensesAcrossBatches() {
        List<Driver> candidates = buildDrivers(120, false, false);
        when(repository.findCandidates(null)).thenReturn(candidates);
        when(documentValidityPort.findDriversWithValidLicense(any(), eq(VALID_UNTIL))).thenReturn(Set.of());

        ValidDriversResult result = service.listValid(new Query(null, VALID_UNTIL));

        assertThat(result.content()).isEmpty();
        assertThat(result.total()).isZero();
        assertThat(result.truncated()).isFalse();
        verify(documentValidityPort, times(2)).findDriversWithValidLicense(any(), eq(VALID_UNTIL));
    }

    @Test
    void returnsExactly500WithoutTruncation() {
        List<Driver> candidates = buildDrivers(500, false, false);
        when(repository.findCandidates(DriverStatus.ACTIVE)).thenReturn(candidates);
        when(documentValidityPort.findDriversWithValidLicense(any(), eq(VALID_UNTIL)))
                .thenAnswer(invocation -> new HashSet<>(invocation.<List<UUID>>getArgument(0)));

        ValidDriversResult result = service.listValid(new Query(DriverStatus.ACTIVE, VALID_UNTIL));

        assertThat(result.content()).hasSize(500);
        assertThat(result.total()).isEqualTo(500);
        assertThat(result.truncated()).isFalse();
    }

    @Test
    void truncatesContentAt500AndMarksTruncatedWhenMoreThan500Valid() {
        List<Driver> candidates = buildDrivers(501, false, false);
        when(repository.findCandidates(DriverStatus.ACTIVE)).thenReturn(candidates);
        when(documentValidityPort.findDriversWithValidLicense(any(), eq(VALID_UNTIL)))
                .thenAnswer(invocation -> new HashSet<>(invocation.<List<UUID>>getArgument(0)));

        ValidDriversResult result = service.listValid(new Query(DriverStatus.ACTIVE, VALID_UNTIL));

        assertThat(result.content()).hasSize(500);
        assertThat(result.total()).isEqualTo(501);
        assertThat(result.truncated()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
        "1, 1",
        "2, 2",
        "3, 3"
    })
    void failsWholeRequestWithoutPartialResultsWhenAnyBatchFails(int failingBatch, int expectedInvocations) {
        List<Driver> candidates = buildDrivers(250, false, false);
        when(repository.findCandidates(DriverStatus.ACTIVE)).thenReturn(candidates);
        AtomicInteger callCount = new AtomicInteger();
        when(documentValidityPort.findDriversWithValidLicense(any(), eq(VALID_UNTIL)))
                .thenAnswer(invocation -> {
                    if (callCount.incrementAndGet() == failingBatch) {
                        throw new DocumentationUnavailableException("Documentation down");
                    }
                    return new HashSet<>(invocation.<List<UUID>>getArgument(0));
                });

        assertThatThrownBy(() -> service.listValid(new Query(DriverStatus.ACTIVE, VALID_UNTIL)))
                .isInstanceOf(DocumentationUnavailableException.class);
        verify(documentValidityPort, times(expectedInvocations))
                .findDriversWithValidLicense(any(), eq(VALID_UNTIL));
    }

    @Test
    void returnsValidDriversSortedByCreatedAtAscending() {
        Driver third = driverAt(BASE_TIME.plusHours(3), false, false);
        Driver first = driverAt(BASE_TIME.plusHours(1), false, false);
        Driver second = driverAt(BASE_TIME.plusHours(2), false, false);
        when(repository.findCandidates(null)).thenReturn(List.of(third, first, second));
        when(documentValidityPort.findDriversWithValidLicense(any(), eq(VALID_UNTIL)))
                .thenReturn(Set.of(third.getId(), first.getId(), second.getId()));

        ValidDriversResult result = service.listValid(new Query(null, VALID_UNTIL));

        assertThat(result.content()).containsExactly(first, second, third);
    }

    @Test
    void excludesMedicalHoldStatusImmediatelyWithoutCallingPortOrRepository() {
        ValidDriversResult result = service.listValid(new Query(DriverStatus.MEDICAL_HOLD_INCIDENT, VALID_UNTIL));

        assertThat(result.content()).isEmpty();
        assertThat(result.total()).isZero();
        assertThat(result.truncated()).isFalse();
        verifyNoInteractions(repository, documentValidityPort);
    }

    @Test
    void excludesMedicalHoldDriversAndDriversWithoutValidLicense() {
        Driver activeWithLicense = driverAt(BASE_TIME.plusHours(1), false, false);
        Driver activeWithoutLicense = driverAt(BASE_TIME.plusHours(2), false, false);
        Driver medicalHold = driverAt(BASE_TIME.plusHours(3), false, true);
        when(repository.findCandidates(null))
                .thenReturn(List.of(activeWithLicense, activeWithoutLicense, medicalHold));
        when(documentValidityPort.findDriversWithValidLicense(any(), eq(VALID_UNTIL)))
                .thenReturn(Set.of(activeWithLicense.getId()));

        ValidDriversResult result = service.listValid(new Query(null, VALID_UNTIL));

        assertThat(result.content()).containsExactly(activeWithLicense);
        assertThat(result.total()).isEqualTo(1);
        verify(documentValidityPort).findDriversWithValidLicense(
                eq(List.of(activeWithLicense.getId(), activeWithoutLicense.getId())), eq(VALID_UNTIL));
    }

    @Test
    void ignoresExtraIdsReturnedByPortThatWereNotInCandidates() {
        Driver candidate = driverAt(BASE_TIME, false, false);
        UUID unknownId = UUID.randomUUID();
        when(repository.findCandidates(DriverStatus.ACTIVE)).thenReturn(List.of(candidate));
        when(documentValidityPort.findDriversWithValidLicense(any(), eq(VALID_UNTIL)))
                .thenReturn(Stream.of(candidate.getId(), unknownId).collect(Collectors.toSet()));

        ValidDriversResult result = service.listValid(new Query(DriverStatus.ACTIVE, VALID_UNTIL));

        assertThat(result.content()).containsExactly(candidate);
        assertThat(result.total()).isEqualTo(1);
        assertThat(result.truncated()).isFalse();
    }

    private static List<Driver> buildDrivers(int count, boolean documentBlock, boolean medicalBlock) {
        List<Driver> drivers = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            drivers.add(driverAt(BASE_TIME.plusSeconds(i), documentBlock, medicalBlock));
        }
        return drivers;
    }

    private static Driver driverAt(OffsetDateTime createdAt, boolean documentBlock, boolean medicalBlock) {
        return Driver.restore(UUID.randomUUID(), "1144056789", "Carlos Pérez", "carlos@correo.com",
                "3001234567", documentBlock, medicalBlock, createdAt, createdAt);
    }
}
