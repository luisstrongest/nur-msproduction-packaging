package com.nurticenter.produccion.infrastructure.event;

import com.nurticenter.produccion.domain.event.OrdenGenerada;
import com.nurticenter.produccion.domain.event.PaqueteListo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * TEST UNITARIO CON MOCKS - SpringEventPublisher.
 *
 * El bus de eventos de Spring (ApplicationEventPublisher) se sustituye por un
 * mock: aqui solo interesa que el adaptador publique lo que recibe, en orden,
 * y que no haga nada cuando no hay eventos.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SpringEventPublisher (unit test con mocks)")
class SpringEventPublisherTest {

    @Mock
    private ApplicationEventPublisher springPublisher;

    @InjectMocks
    private SpringEventPublisher publisher;

    private final OrdenGenerada ordenGenerada =
            new OrdenGenerada("orden-1", LocalDate.of(2026, 7, 15), 2, LocalDateTime.now());

    private final PaqueteListo paqueteListo =
            new PaqueteListo("paquete-1", "PAC-1001", "PKG-ABC12345", LocalDateTime.now());

    @Test
    @DisplayName("Publica cada evento de dominio en el bus de Spring, respetando el orden")
    void publicaCadaEventoEnOrden() {
        publisher.publicar(List.of(ordenGenerada, paqueteListo));

        InOrder enOrden = inOrder(springPublisher);
        enOrden.verify(springPublisher).publishEvent(ordenGenerada);
        enOrden.verify(springPublisher).publishEvent(paqueteListo);
    }

    @Test
    @DisplayName("Una lista vacia no genera ninguna publicacion")
    void listaVaciaNoPublicaNada() {
        publisher.publicar(List.of());

        verifyNoInteractions(springPublisher);
    }

    @Test
    @DisplayName("Una lista nula es tolerada (guardia defensiva)")
    void listaNulaNoPublicaNada() {
        publisher.publicar(null);

        verifyNoInteractions(springPublisher);
    }

    @Test
    @DisplayName("Un evento que no implementa DomainEvent tambien se publica")
    void tambienPublicaEventosQueNoSonDeDominio() {
        Object eventoGenerico = new Object();

        publisher.publicar(Arrays.asList(eventoGenerico));

        verify(springPublisher).publishEvent(eventoGenerico);
    }
}
