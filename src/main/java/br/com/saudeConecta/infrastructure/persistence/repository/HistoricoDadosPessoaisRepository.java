package br.com.saudeConecta.infrastructure.persistence.repository;

import br.com.saudeConecta.domain.historicodadospessoais.EntidadeTipo;
import br.com.saudeConecta.domain.historicodadospessoais.HistoricoDadosPessoais;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface HistoricoDadosPessoaisRepository extends JpaRepository<HistoricoDadosPessoais, Long> {

    // Métodos para SUPER_ADMIN (sem filtro de organização)
    List<HistoricoDadosPessoais> findByUsuario_IdOrderByCriadoEmDesc(Long usuarioId);

    List<HistoricoDadosPessoais> findByEntidadeAndIdEntidadeOrderByCriadoEmDesc(
            String entidade, Long idEntidade);

    // Busca os 10 registros mais recentes globalmente (para SUPER_ADMIN)
    List<HistoricoDadosPessoais> findTop10ByOrderByCriadoEmDesc();

    /**
     * Auditoria do SUPER_ADMIN: alterações de todas as clínicas, com filtros opcionais (nulo = sem filtro).
     * Traz organização e usuário no mesmo select para a tela não disparar um select por linha.
     */
    @Query(value = """
            SELECT h FROM HistoricoDadosPessoais h
              JOIN FETCH h.organizacao o
              JOIN FETCH h.usuario u
             WHERE (:organizacaoId IS NULL OR o.id = :organizacaoId)
               AND (:entidade IS NULL OR h.entidade = :entidade)
               AND (:de IS NULL OR h.criadoEm >= :de)
               AND (:ate IS NULL OR h.criadoEm < :ate)
               AND (:campo IS NULL OR LOWER(h.campo) LIKE LOWER(CONCAT('%', :campo, '%')))
            """,
            countQuery = """
            SELECT COUNT(h) FROM HistoricoDadosPessoais h
             WHERE (:organizacaoId IS NULL OR h.organizacao.id = :organizacaoId)
               AND (:entidade IS NULL OR h.entidade = :entidade)
               AND (:de IS NULL OR h.criadoEm >= :de)
               AND (:ate IS NULL OR h.criadoEm < :ate)
               AND (:campo IS NULL OR LOWER(h.campo) LIKE LOWER(CONCAT('%', :campo, '%')))
            """)
    Page<HistoricoDadosPessoais> pesquisar(@Param("organizacaoId") Long organizacaoId,
                                           @Param("entidade") EntidadeTipo entidade,
                                           @Param("de") LocalDateTime de,
                                           @Param("ate") LocalDateTime ate,
                                           @Param("campo") String campo,
                                           Pageable pageable);
}
