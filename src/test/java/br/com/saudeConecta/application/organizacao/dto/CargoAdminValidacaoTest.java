package br.com.saudeConecta.application.organizacao.dto;

import br.com.saudeConecta.domain.admin.CargoAdmin;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O cargo do administrador só aceita os valores de {@link CargoAdmin#VALORES}.
 * Valida pelo DTO de atualização, onde o cargo é opcional (nulo passa).
 */
class CargoAdminValidacaoTest {

    private static ValidatorFactory fabrica;
    private static Validator validator;

    @BeforeAll
    static void criarValidator() {
        fabrica = Validation.buildDefaultValidatorFactory();
        validator = fabrica.getValidator();
    }

    @AfterAll
    static void fecharFabrica() {
        fabrica.close();
    }

    private static Set<String> camposComErro(String cargo) {
        AtualizarAdminOrgCompletoRequest req = new AtualizarAdminOrgCompletoRequest(
                null, cargo, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        return validator.validate(req).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    @Test
    void todosOsCargosDaListaSaoAceitos() {
        CargoAdmin.VALORES.forEach(cargo -> assertThat(camposComErro(cargo)).doesNotContain("cargo"));
    }

    @Test
    void cargoNuloEAceitoNaAtualizacao() {
        assertThat(camposComErro(null)).doesNotContain("cargo");
    }

    @ParameterizedTest
    @ValueSource(strings = {"GERENTE", "DIRETOR_ADMINISTRATIVO", "Gestor", "Administrador", "Diretor", "", "Dono"})
    void valoresAntigosOuSoltosSaoRecusados(String cargo) {
        assertThat(camposComErro(cargo)).contains("cargo");
    }

    @Test
    void regexCobreExatamenteALista() {
        assertThat(CargoAdmin.VALORES).allMatch(c -> c.matches(CargoAdmin.REGEX));
        assertThat(CargoAdmin.VALORES).hasSize(5);
    }
}
