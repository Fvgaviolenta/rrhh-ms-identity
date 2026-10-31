package com.rrhh.identity.util;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RutEmpresaTest {

    @Test
    void normalizaFormatosValidos() {
        assertEquals(Optional.of("76086428-5"), RutEmpresa.normalizar("76.086.428-5"));
        assertEquals(Optional.of("76086428-5"), RutEmpresa.normalizar("760864285"));
        assertEquals(Optional.of("76086428-5"), RutEmpresa.normalizar(" 76086428-5 "));
        assertEquals(Optional.of("11111111-1"), RutEmpresa.normalizar("11.111.111-1"));
        assertEquals(Optional.of("12345670-K"), RutEmpresa.normalizar("12.345.670-k"));
    }

    @Test
    void rechazaDvIncorrectoYBasura() {
        assertEquals(Optional.empty(), RutEmpresa.normalizar("76.123.456-7"));
        assertEquals(Optional.empty(), RutEmpresa.normalizar(""));
        assertEquals(Optional.empty(), RutEmpresa.normalizar(null));
        assertEquals(Optional.empty(), RutEmpresa.normalizar("abc"));
        assertEquals(Optional.empty(), RutEmpresa.normalizar("1"));
    }
}
