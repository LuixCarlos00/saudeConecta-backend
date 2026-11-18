package br.com.saudeConecta.presentation.controller.historico;

import br.com.saudeConecta.application.historico.HistoricoDadosPessoaisService;
import br.com.saudeConecta.application.historico.dto.HistoricoAlteracaoResponse;
import br.com.saudeConecta.domain.historicodadospessoais.EntidadeTipo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import br.com.saudeConecta.domain.historicodadospessoais.HistoricoDadosPessoais;
import jdk.jfr.Description;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/historico-dados-pessoais")
@RequiredArgsConstructor
@Slf4j
@Description("Endpoints para consulta de histórico de alterações de dados pessoais. Exclusivo para SUPER_ADMIN.")
public class HistoricoDadosPessoaisController {

    private final HistoricoDadosPessoaisService historicoDadosPessoaisService;

    /**
     * Tela de auditoria do SUPER_ADMIN: alterações de dados cadastrais de todas as clínicas, paginadas,
     * mais recentes primeiro. Todos os filtros são opcionais.
     *
     * @param organizacaoId clínica
     * @param entidade      tipo de cadastro (PACIENTE, PROFISSIONAL, ADMIN, SECRETARIA, ORGANIZACAO, ENDERECO)
     * @param de            primeiro dia (yyyy-MM-dd), inclusive
     * @param ate           último dia (yyyy-MM-dd), inclusive
     * @param campo         parte do nome do campo
     * @param pageable      página (tamanho máximo 100)
     * @return página de alterações
     */
    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Description("Auditoria paginada de alterações de dados cadastrais. Exclusivo para SUPER_ADMIN.")
    public ResponseEntity<Page<HistoricoAlteracaoResponse>> pesquisar(
            @RequestParam(required = false) Long organizacaoId,
            @RequestParam(required = false) EntidadeTipo entidade,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            @RequestParam(required = false) String campo,
            @PageableDefault(size = 20) Pageable pageable) {
        try {
            return ResponseEntity.ok(historicoDadosPessoaisService.pesquisar(organizacaoId, entidade, de, ate, campo, pageable));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Busca os 10 registros mais recentes de histórico globalmente.
     * Apenas SUPER_ADMIN pode acessar este endpoint.
     *
     * @return lista dos 10 registros mais recentes
     */
    @GetMapping("/recentes")
    @Transactional(readOnly = true)
    @Description("Busca os 10 registros mais recentes. Exclusivo para SUPER_ADMIN.")
    public ResponseEntity<List<HistoricoDadosPessoais>> buscarHistoricoRecentes() {
        try {
            List<HistoricoDadosPessoais> historico = historicoDadosPessoaisService.buscarHistoricoRecentes();
            return ResponseEntity.ok(historico);
        } catch (IllegalStateException e) {
            log.warn("historico.buscar negado motivo=ACESSO_NEGADO escopo=RECENTES");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    /**
     * Busca histórico de alterações por usuário.
     * Apenas SUPER_ADMIN pode acessar este endpoint.
     *
     * @param idUsuario ID do usuário
     * @return lista de histórico ordenado por data (mais recente primeiro)
     */
    @GetMapping("/usuario/{idUsuario}")
    @Transactional(readOnly = true)
    @Description("Busca histórico de alterações por usuário. Exclusivo para SUPER_ADMIN.")
    public ResponseEntity<List<HistoricoDadosPessoais>> buscarHistoricoPorUsuario(@PathVariable Long idUsuario) {
        try {
            List<HistoricoDadosPessoais> historico = historicoDadosPessoaisService.buscarHistoricoByUsuario(idUsuario);
            return ResponseEntity.ok(historico);
        } catch (IllegalStateException e) {
            log.warn("historico.buscar negado motivo=ACESSO_NEGADO escopo=USUARIO usuarioId={}", idUsuario);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    /**
     * Busca histórico de alterações por tipo de entidade e ID da entidade.
     * Apenas SUPER_ADMIN pode acessar este endpoint.
     *
     * @param entidade   tipo da entidade (PACIENTE, PROFISSIONAL, ADMIN, SECRETARIA, ORGANIZACAO, ENDERECO)
     * @param idEntidade ID do registro da entidade
     * @return lista de histórico ordenado por data (mais recente primeiro)
     */
    @GetMapping("/entidade/{entidade}/{idEntidade}")
    @Transactional(readOnly = true)
    @Description("Busca histórico de alterações por entidade e ID. Exclusivo para SUPER_ADMIN.")
    public ResponseEntity<List<HistoricoDadosPessoais>> buscarHistoricoPorEntidade(
            @PathVariable String entidade,
            @PathVariable Long idEntidade) {
        try {
            List<HistoricoDadosPessoais> historico = historicoDadosPessoaisService.buscarHistoricoByEntidade(entidade, idEntidade);
            return ResponseEntity.ok(historico);
        } catch (IllegalStateException e) {
            log.warn("historico.buscar negado motivo=ACESSO_NEGADO escopo=ENTIDADE entidade={} entidadeId={}", entidade, idEntidade);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }
}
