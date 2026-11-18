package br.com.saudeConecta.application.historico.dto;

import br.com.saudeConecta.domain.historicodadospessoais.EntidadeTipo;
import br.com.saudeConecta.domain.historicodadospessoais.HistoricoDadosPessoais;
import br.com.saudeConecta.domain.usuario.TipoUsuarioNovo;
import br.com.saudeConecta.domain.usuario.Usuario;

import java.time.LocalDateTime;

/**
 * Uma linha da auditoria de dados cadastrais vista pelo SUPER_ADMIN: um campo alterado,
 * com o valor de antes e o de depois, quem alterou e em qual clínica.
 * Existe para não serializar a entidade JPA (ela traz Organizacao e Usuario lazy inteiros).
 *
 * @param id             id do registro de histórico
 * @param criadoEm       quando a alteração foi gravada
 * @param organizacaoId  clínica em que a alteração aconteceu
 * @param organizacaoNome nome da clínica
 * @param usuarioId      usuário que fez a alteração
 * @param usuarioLogin   login de quem alterou (CPF ou CNPJ)
 * @param usuarioNome    nome de quem alterou (cadastro de admin, profissional ou secretária), ou null
 * @param usuarioPerfil  perfil de quem alterou (Administrador, Profissional, Secretária, Super admin)
 * @param entidade       tipo do cadastro alterado
 * @param idEntidade     id do cadastro alterado
 * @param campo          nome do campo alterado
 * @param valorAnterior  valor antes da alteração
 * @param valorNovo      valor depois da alteração
 */
public record HistoricoAlteracaoResponse(
        Long id,
        LocalDateTime criadoEm,
        Long organizacaoId,
        String organizacaoNome,
        Long usuarioId,
        String usuarioLogin,
        String usuarioNome,
        String usuarioPerfil,
        EntidadeTipo entidade,
        Long idEntidade,
        String campo,
        String valorAnterior,
        String valorNovo
) {

    /**
     * Converte a entidade. A consulta que alimenta este método já traz organização e usuário (join fetch).
     *
     * @param h    registro de histórico
     * @param nome nome de quem alterou, já resolvido em lote pelo service (pode ser null)
     * @return linha para a tela
     */
    public static HistoricoAlteracaoResponse de(HistoricoDadosPessoais h, String nome) {
        Usuario u = h.getUsuario();
        return new HistoricoAlteracaoResponse(
                h.getId(),
                h.getCriadoEm(),
                h.getOrganizacao() != null ? h.getOrganizacao().getId() : null,
                h.getOrganizacao() != null ? h.getOrganizacao().getNome() : null,
                u != null ? u.getId() : null,
                u != null ? u.getLogin() : null,
                nome,
                u != null ? perfil(u.getTipoUsuarioNovo()) : null,
                h.getEntidade(),
                h.getIdEntidade(),
                h.getCampo(),
                h.getValorAnterior(),
                h.getValorNovo());
    }

    private static String perfil(TipoUsuarioNovo tipo) {
        if (tipo == null) { return null; }
        return switch (tipo) {
            case ROOT -> "Super admin";
            case GESTOR -> "Administrador";
            case CLINICO -> "Profissional";
            case ASSISTENTE -> "Secretária";
        };
    }
}
