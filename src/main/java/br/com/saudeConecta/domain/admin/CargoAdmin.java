package br.com.saudeConecta.domain.admin;

import java.util.List;

/**
 * Lista única de cargos do administrador da clínica.
 *
 * <p>O cargo é guardado como texto (o rótulo exibido), não como código, porque a coluna já era
 * texto livre e aparece direto nas telas e e-mails. Antes cada tela tinha a sua lista
 * (GERENTE, DIRETOR_ADMINISTRATIVO, "Gestor"...) e o valor salvo por uma não aparecia na outra.
 * O front repete esta lista em {@code shared/constants/cargos-admin.ts}; as duas mudam juntas.</p>
 */
public final class CargoAdmin {

    public static final String RESPONSAVEL = "Responsável";
    public static final String DIRETOR = "Diretor(a)";
    public static final String GERENTE = "Gerente";
    public static final String ADMINISTRADOR = "Administrador(a)";
    public static final String COORDENADOR = "Coordenador(a)";

    /** Cargos aceitos, na ordem em que aparecem nas telas. */
    public static final List<String> VALORES = List.of(RESPONSAVEL, DIRETOR, GERENTE, ADMINISTRADOR, COORDENADOR);

    /**
     * Expressão para {@code @Pattern} nos DTOs (anotação exige constante de compilação).
     * Nulo passa no {@code @Pattern}: quem exige o campo usa também {@code @NotBlank}.
     */
    public static final String REGEX = "^(Responsável|Diretor\\(a\\)|Gerente|Administrador\\(a\\)|Coordenador\\(a\\))$";

    /** Mensagem de erro de validação, já com as opções. */
    public static final String MENSAGEM_INVALIDO =
            "Cargo inválido. Use: Responsável, Diretor(a), Gerente, Administrador(a) ou Coordenador(a)";

    private CargoAdmin() {
    }
}
