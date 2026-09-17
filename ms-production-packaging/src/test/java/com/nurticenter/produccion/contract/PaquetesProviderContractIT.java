package com.nurticenter.produccion.contract;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import au.com.dius.pact.provider.spring.junit5.PactVerificationSpringProvider;
import com.nurticenter.produccion.domain.model.paquete.CodigoIdentificacion;
import com.nurticenter.produccion.domain.model.paquete.DireccionImpresa;
import com.nurticenter.produccion.domain.model.paquete.EstadoPaquete;
import com.nurticenter.produccion.domain.model.paquete.Etiqueta;
import com.nurticenter.produccion.domain.model.paquete.PacienteId;
import com.nurticenter.produccion.domain.model.paquete.PaqueteEtiquetado;
import com.nurticenter.produccion.domain.model.paquete.PaqueteId;
import com.nurticenter.produccion.domain.repository.PaqueteEtiquetadoRepository;
import com.nurticenter.produccion.infrastructure.persistence.paquete.SpringDataPaqueteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * PRUEBAS DE CONTRATO - lado PROVEEDOR.
 *
 * Proveedor  : ms-production-packaging (BC4)
 * Consumidor : ms-logistic-delivery (BC5), cuyo pacto vive en
 *              src/test/resources/pacts/ms-logistic-delivery-ms-production-packaging.json
 *
 * Aqui no se escribe ninguna expectativa: Pact lee el pacto, levanta esta
 * aplicacion en un puerto real, ejecuta CADA interaccion del contrato contra ella
 * y compara la respuesta con lo que el consumidor declaro necesitar. Un @TestTemplate
 * se convierte en tantos tests como interacciones tenga el pacto.
 *
 * Lo unico que aporta el proveedor son los @State: el estado previo que cada
 * interaccion presupone. El texto del @State debe coincidir LETRA A LETRA con el
 * `given(...)` del consumidor; si no coincide, Pact falla la interaccion en vez de
 * pasarla por alto, que es justo lo que se quiere.
 *
 * Se ejecuta con el perfil 'it' (H2 en memoria) para que no haga falta Docker:
 *
 *   ./mvnw -Pcontract test-compile failsafe:integration-test failsafe:verify
 *
 * o, desde la raiz del repositorio, el gate completo:  tools\validate-contracts.ps1
 */
@Provider("ms-production-packaging")
@PactFolder("src/test/resources/pacts")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
class PaquetesProviderContractIT {

    // Los mismos ids que fija el consumidor en su pacto. Si el consumidor los
    // cambia, el pacto trae otro @State y estos metodos dejan de casar: el fallo
    // aparece aqui, en el proveedor, que es donde tiene que aparecer.
    private static final String PAQUETE_LISTO_ID = "7f3e0f9c-4c2a-4f0a-9d21-1a2b3c4d5e6f";
    private static final String ORDEN_ID         = "3c1e5b77-9a48-4d5e-8b1c-2f9d0e7a6b54";

    @LocalServerPort
    private int puerto;

    /** Puerto de dominio: se siembra igual que lo haria la aplicacion. */
    @Autowired
    private PaqueteEtiquetadoRepository paquetes;

    /** Solo para vaciar la tabla entre interacciones (el puerto no expone borrado). */
    @Autowired
    private SpringDataPaqueteRepository jpaPaquetes;

    /**
     * Se ejecuta antes de cada interaccion y ANTES del @State correspondiente
     * (Pact invoca los cambios de estado en beforeTestExecution). Deja la base
     * vacia: sin esto, los paquetes sembrados para una interaccion se colarian en
     * la lista que devuelve la siguiente y el pacto pasaria por casualidad.
     */
    @BeforeEach
    void prepararEntorno(PactVerificationContext context) {
        jpaPaquetes.deleteAll();
        context.setTarget(new HttpTestTarget("localhost", puerto));
    }

    @TestTemplate
    @ExtendWith(PactVerificationSpringProvider.class)
    void verificarInteraccionDelContrato(PactVerificationContext context) {
        context.verifyInteraction();
    }

    // =================================================================================
    // Estados previos exigidos por el pacto
    // =================================================================================

    @State("existen 2 paquetes LISTOS pendientes de transferencia a BC5")
    void dosPaquetesListos() {
        paquetes.guardar(paqueteListo(UUID.randomUUID().toString(), "PAC-1001",
                UUID.randomUUID().toString(), "Maria Perez"));
        paquetes.guardar(paqueteListo(UUID.randomUUID().toString(), "PAC-1002",
                UUID.randomUUID().toString(), "Juan Gutierrez"));
    }

    @State("existe el paquete LISTO con id " + PAQUETE_LISTO_ID)
    void unPaqueteListoConIdConocido() {
        paquetes.guardar(paqueteListo(PAQUETE_LISTO_ID, "PAC-1001", ORDEN_ID, "Maria Perez"));
    }

    @State("no existe ningun paquete con id 00000000-0000-0000-0000-000000000000")
    void ningunPaqueteConEseId() {
        // La base ya quedo vacia en @BeforeEach. El estado se declara igualmente:
        // documenta la precondicion y falla si alguien la rompe mas adelante.
    }

    @State("la orden " + ORDEN_ID + " tiene 1 paquete ENVASADO sin etiquetar")
    void unPaqueteEnvasadoSinEtiqueta() {
        paquetes.guardar(PaqueteEtiquetado.reconstituir(
                PaqueteId.de(UUID.randomUUID().toString()),
                PacienteId.de("PAC-2002"),
                ORDEN_ID,
                EstadoPaquete.ENVASADO,
                null));
    }

    // =================================================================================
    // Helpers de siembra
    // =================================================================================

    /**
     * Reconstruye un paquete ya LISTO y etiquetado.
     *
     * El codigo de identificacion se genera con la MISMA regla de dominio que usa
     * la aplicacion ({@link CodigoIdentificacion#generar}), no a mano: el consumidor
     * exige el formato PKG-XXXXXXXX y el proveedor tiene que demostrar que lo cumple
     * produciendolo como lo produce en real.
     */
    private PaqueteEtiquetado paqueteListo(String paqueteId, String pacienteId,
                                           String ordenId, String nombrePaciente) {
        PaqueteId id = PaqueteId.de(paqueteId);
        Etiqueta etiqueta = Etiqueta.reconstituir(
                nombrePaciente,
                new DireccionImpresa("Av. Mutualista", "450", "Edificio azul, piso 2", "Santa Cruz"),
                CodigoIdentificacion.generar(id),
                LocalDateTime.of(2026, 9, 17, 8, 15, 30, 123_000_000));

        return PaqueteEtiquetado.reconstituir(
                id, PacienteId.de(pacienteId), ordenId, EstadoPaquete.LISTO, etiqueta);
    }
}
