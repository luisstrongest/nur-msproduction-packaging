package com.nurticenter.produccion.domain.shared;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TEST UNITARIO - Value Object Porcion.
 *
 * Java puro: sin Spring, sin base de datos y sin mocks. El VO no tiene
 * colaboradores, asi que se verifica su estado directamente.
 */
@DisplayName("Porcion (Value Object)")
class PorcionTest {

    @Nested
    @DisplayName("Invariantes de construccion")
    class Invariantes {

        @ParameterizedTest(name = "cantidad = {0} -> rechazada")
        @ValueSource(doubles = {0.0, -1.0, -250.5})
        void rechazaCantidadNoPositiva(double cantidad) {
            assertThatThrownBy(() -> new Porcion(cantidad, UnidadMedida.GRAMOS, "Sopa"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("positiva");
        }

        @Test
        void rechazaUnidadNula() {
            assertThatThrownBy(() -> new Porcion(250, null, "Sopa"))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void rechazaDescripcionNula() {
            assertThatThrownBy(() -> new Porcion(250, UnidadMedida.GRAMOS, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void aceptaUnaPorcionValida() {
            Porcion porcion = new Porcion(250, UnidadMedida.GRAMOS, "Porcion individual");

            assertThat(porcion.cantidad()).isEqualTo(250);
            assertThat(porcion.unidad()).isEqualTo(UnidadMedida.GRAMOS);
            assertThat(porcion.descripcion()).isEqualTo("Porcion individual");
        }
    }

    @Nested
    @DisplayName("multiplicar() - calculo que usa la ACL")
    class Multiplicar {

        @Test
        void devuelveUnaNuevaInstanciaSinMutarElOriginal() {
            Porcion individual = new Porcion(250, UnidadMedida.GRAMOS, "Sopa");

            Porcion total = individual.multiplicar(12);

            assertThat(total.cantidad()).isEqualTo(3000);
            assertThat(individual.cantidad()).isEqualTo(250);   // el VO es inmutable
            assertThat(total).isNotSameAs(individual);
        }

        @Test
        void conservaUnidadYDescripcion() {
            Porcion total = new Porcion(200, UnidadMedida.MILILITROS, "Jugo").multiplicar(3);

            assertThat(total.unidad()).isEqualTo(UnidadMedida.MILILITROS);
            assertThat(total.descripcion()).isEqualTo("Jugo");
        }

        @ParameterizedTest(name = "numeroPacientes = {0} -> rechazado")
        @ValueSource(ints = {0, -1, -10})
        void rechazaNumeroDePacientesNoPositivo(int pacientes) {
            Porcion porcion = new Porcion(250, UnidadMedida.GRAMOS, "Sopa");

            assertThatThrownBy(() -> porcion.multiplicar(pacientes))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("mayor a cero");
        }
    }

    @Nested
    @DisplayName("formatoCorto()")
    class Formato {

        @ParameterizedTest(name = "{0} {1} -> {2}")
        @CsvSource({
                "250, GRAMOS,     250 g",
                "200, MILILITROS, 200 ml",
                "1,   UNIDADES,   1 u"
        })
        void formateaCantidadYSimbolo(double cantidad, UnidadMedida unidad, String esperado) {
            Porcion porcion = new Porcion(cantidad, unidad, "irrelevante");

            assertThat(porcion.formatoCorto()).isEqualTo(esperado);
        }
    }

    @Test
    @DisplayName("Dos porciones con los mismos valores son iguales (semantica de Value Object)")
    void igualdadPorValor() {
        Porcion a = new Porcion(250, UnidadMedida.GRAMOS, "Sopa");
        Porcion b = new Porcion(250, UnidadMedida.GRAMOS, "Sopa");

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }
}
