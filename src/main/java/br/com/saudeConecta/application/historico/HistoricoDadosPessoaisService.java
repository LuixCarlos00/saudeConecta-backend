package br.com.saudeConecta.application.historico;

 import br.com.saudeConecta.application.historico.dto.HistoricoAlteracaoResponse;
import br.com.saudeConecta.domain.historicodadospessoais.EntidadeTipo;
 import br.com.saudeConecta.domain.historicodadospessoais.HistoricoDadosPessoais;
 import br.com.saudeConecta.domain.organizacao.Organizacao;
 import br.com.saudeConecta.domain.usuario.Usuario;
 import br.com.saudeConecta.config.tenant.TenantAccessDeniedException;
 import br.com.saudeConecta.config.tenant.TenantHelper;
import br.com.saudeConecta.infrastructure.persistence.repository.HistoricoDadosPessoaisRepository;
 import br.com.saudeConecta.infrastructure.persistence.repository.AdminOrganizacaoRepository;
 import br.com.saudeConecta.infrastructure.persistence.repository.OrganizacaoRepository;
 import br.com.saudeConecta.infrastructure.persistence.repository.ProfissionalRepository;
 import br.com.saudeConecta.infrastructure.persistence.repository.SecretariaRepository;
 import br.com.saudeConecta.infrastructure.persistence.repository.UsuarioRepository;
 import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
 import java.lang.reflect.Field;
 import java.lang.reflect.Modifier;
 import java.math.BigDecimal;
import java.time.LocalDate;
   import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class HistoricoDadosPessoaisService {

    private final HistoricoDadosPessoaisRepository historicoRepository;
    private final OrganizacaoRepository organizacaoRepository;
    private final UsuarioRepository usuarioRepository;
    private final TenantHelper tenantHelper;
    private final AdminOrganizacaoRepository adminOrganizacaoRepository;
    private final ProfissionalRepository profissionalRepository;
    private final SecretariaRepository secretariaRepository;

    private static final Set<String> CAMPOS_IGNORADOS = Set.of(
            "serialVersionUID",
            "organizacao",
            "usuario",
            "endereco",           // endereço é tratado separadamente
            "especialidades",     // coleções não comparadas aqui
            "tipoProfissional",   // relação lazy
            "assinaturas",        // coleção lazy da Organizacao
            "status",             // status da Organizacao não é editável pelo Admin
            "logoUrl",            // não editável na tela de dados pessoais
            "createdAt",
            "updatedAt",
            "criadoEm",
            // campos ID de cada entidade
            "id", "paciCodigo", "endCodigo"
    );

    // ==========================================
    // Consultas
    // ==========================================

    /**
     * Busca histórico de alterações por tipo de entidade e ID da entidade.
     * Apenas SUPER_ADMIN pode acessar este método.
     *
     * @param entidade   tipo da entidade (enum EntidadeTipo como String)
     * @param idEntidade ID do registro da entidade
     * @return lista de histórico ordenado por data (mais recente primeiro)
     */
    @Transactional(readOnly = true)
    public List<HistoricoDadosPessoais> buscarHistoricoByEntidade(String entidade, Long idEntidade) {
        // Checagem explícita do papel: ausência de organização no token não basta.
        if (!tenantHelper.isSuperAdmin()) {
            throw new TenantAccessDeniedException("Acesso negado: apenas SUPER_ADMIN pode acessar histórico por entidade");
        }
        log.debug("historico.buscar escopo=ENTIDADE entidade={} entidadeId={}", entidade, idEntidade);
        return historicoRepository.findByEntidadeAndIdEntidadeOrderByCriadoEmDesc(entidade, idEntidade);
    }

    /**
     * Busca histórico de alterações por usuário.
     * Apenas SUPER_ADMIN pode acessar este método.
     *
     * @param idUsuario ID do usuário
     * @return lista de histórico ordenado por data (mais recente primeiro)
     */
    @Transactional(readOnly = true)
    public List<HistoricoDadosPessoais> buscarHistoricoByUsuario(Long idUsuario) {
        // Checagem explícita do papel: ausência de organização no token não basta.
        if (!tenantHelper.isSuperAdmin()) {
            throw new TenantAccessDeniedException("Acesso negado: apenas SUPER_ADMIN pode acessar histórico por usuário");
        }
        log.debug("historico.buscar escopo=USUARIO usuarioId={}", idUsuario);
        return historicoRepository.findByUsuario_IdOrderByCriadoEmDesc(idUsuario);
    }

    /**
     * Busca os 10 registros mais recentes de histórico globalmente.
     * Apenas SUPER_ADMIN pode acessar este método.
     *
     * @return lista dos 10 registros mais recentes
     */
    @Transactional(readOnly = true)
    public List<HistoricoDadosPessoais> buscarHistoricoRecentes() {
        // Checagem explícita do papel: ausência de organização no token não basta.
        if (!tenantHelper.isSuperAdmin()) {
            throw new TenantAccessDeniedException("Acesso negado: apenas SUPER_ADMIN pode acessar histórico");
        }
        log.debug("historico.buscar escopo=RECENTES");
        return historicoRepository.findTop10ByOrderByCriadoEmDesc();
    }

    /** Maior página aceita na auditoria: a tela pede 20; o teto evita varrer a tabela inteira de uma vez. */
    static final int TAMANHO_MAXIMO_PAGINA = 100;

    /**
     * Auditoria do SUPER_ADMIN: alterações de dados cadastrais de todas as clínicas, mais recentes primeiro.
     *
     * @param organizacaoId clínica (null = todas)
     * @param entidade      tipo de cadastro (null = todos)
     * @param de            primeiro dia do período, inclusive (null = sem início)
     * @param ate           último dia do período, inclusive (null = sem fim)
     * @param campo         parte do nome do campo (null ou vazio = todos)
     * @param pageable      página e tamanho; a ordenação é sempre por data decrescente
     * @return página de alterações
     */
    @Transactional(readOnly = true)
    public Page<HistoricoAlteracaoResponse> pesquisar(Long organizacaoId, EntidadeTipo entidade,
                                                      LocalDate de, LocalDate ate, String campo, Pageable pageable) {
        if (!tenantHelper.isSuperAdmin()) {
            throw new TenantAccessDeniedException("Acesso negado: apenas SUPER_ADMIN pode acessar a auditoria");
        }
        if (de != null && ate != null && ate.isBefore(de)) {
            throw new IllegalArgumentException("A data final não pode ser anterior à data inicial");
        }
        int tamanho = Math.min(Math.max(pageable.getPageSize(), 1), TAMANHO_MAXIMO_PAGINA);
        Pageable pagina = PageRequest.of(pageable.getPageNumber(), tamanho, Sort.by(Sort.Direction.DESC, "criadoEm"));
        String filtroCampo = campo == null || campo.isBlank() ? null : campo.trim();
        log.debug("historico.pesquisar organizacaoId={} entidade={} de={} ate={} pagina={}",
                organizacaoId, entidade, de, ate, pagina.getPageNumber());
        Page<HistoricoDadosPessoais> registros = historicoRepository.pesquisar(
                organizacaoId,
                entidade,
                de != null ? de.atStartOfDay() : null,
                // "até" inclui o dia inteiro: compara com o início do dia seguinte
                ate != null ? ate.plusDays(1).atStartOfDay() : null,
                filtroCampo,
                pagina);
        Map<Long, String> nomes = nomesDosAutores(registros.getContent());
        return registros.map(h -> HistoricoAlteracaoResponse.de(h,
                h.getUsuario() != null ? nomes.get(h.getUsuario().getId()) : null));
    }

    /**
     * Nome de quem fez cada alteração da página. O nome mora no cadastro do perfil (admin,
     * profissional ou secretária), não no usuário: busca os três em lote, uma consulta por perfil.
     *
     * @param registros linhas da página
     * @return usuarioId → nome (sem entrada quando o usuário não tem cadastro, ex.: ROOT)
     */
    private Map<Long, String> nomesDosAutores(List<HistoricoDadosPessoais> registros) {
        Set<Long> ids = new HashSet<>();
        registros.forEach(h -> { if (h.getUsuario() != null) { ids.add(h.getUsuario().getId()); } });
        Map<Long, String> nomes = new HashMap<>();
        if (ids.isEmpty()) {
            return nomes;
        }
        for (List<Object[]> pares : List.of(
                adminOrganizacaoRepository.buscarNomesPorUsuarioIds(ids),
                profissionalRepository.buscarNomesPorUsuarioIds(ids),
                secretariaRepository.buscarNomesPorUsuarioIds(ids))) {
            for (Object[] par : pares) {
                nomes.putIfAbsent((Long) par[0], (String) par[1]);
            }
        }
        return nomes;
    }

   //=======================================

    /**
     * Compara dois objetos (antes e depois da atualização) via reflection,
     * detecta quais campos mudaram e persiste uma linha por campo alterado.
     *
     * Suporta: Paciente, Profissional, Secretaria, AdminOrganizacao
     *
     * @param entidade     tipo da entidade (enum EntidadeTipo)
     * @param idEntidade   ID do registro que foi alterado
     * @param idUsuario    ID do usuário que realizou a alteração
     * @param antes        objeto com estado ANTES da atualização
     * @param depois       objeto com estado DEPOIS da atualização (já salvo)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarAlteracoesDeObjeto(EntidadeTipo entidade, Long idEntidade,
                                            Long idUsuario, Object antes, Object depois) {
        if (antes == null || depois == null) {
            log.warn("historico.registrar ignorado motivo=OBJETO_NULO entidade={} entidadeId={}", entidade, idEntidade);
            return;
        }

        if (!antes.getClass().equals(depois.getClass())) {
            throw new IllegalArgumentException("Os objetos 'antes' e 'depois' devem ser do mesmo tipo");
        }

        Long orgId = tenantHelper.getCurrentTenantId();

        Organizacao organizacao = organizacaoRepository.findById(orgId)
                .orElseThrow(() -> new IllegalStateException("Organização não encontrada no contexto"));

        Usuario usuario = usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> new IllegalStateException("Usuário não encontrado: " + idUsuario));

        List<HistoricoDadosPessoais> registros = new ArrayList<>();

        // Coleta todos os campos da classe e superclasses
        List<Field> campos = getAllFieldsFromClass(antes.getClass());

        for (Field campo : campos) {
            if (CAMPOS_IGNORADOS.contains(campo.getName()) || Modifier.isStatic(campo.getModifiers())) continue;

            campo.setAccessible(true);

            try {
                Object valorAntes  = campo.get(antes);
                Object valorDepois = campo.get(depois);

                // Sem mudança — ignora (com tratamento especial para BigDecimal)
                if (!saoIguais(valorAntes, valorDepois)) {
                    log.debug("historico.registrar campo alterado entidade={} entidadeId={} campo={}",
                            entidade, idEntidade, campo.getName());
                    registros.add(montarRegistro(organizacao, usuario, entidade, idEntidade,
                            campo.getName(), valorAntes, valorDepois));
                }

            } catch (IllegalAccessException e) {
                log.warn("historico.registrar falha motivo=CAMPO_INACESSIVEL campo={} causa={}", campo.getName(), e.getClass().getSimpleName());
            }
        }

        if (registros.isEmpty()) {
            log.debug("historico.registrar sem alteracao entidade={} entidadeId={}", entidade, idEntidade);
            return;
        }

        historicoRepository.saveAll(registros);
        log.debug("historico.registrar ok campos={} entidade={} entidadeId={}", registros.size(), entidade, idEntidade);
    }

    private HistoricoDadosPessoais montarRegistro(Organizacao organizacao, Usuario usuario, EntidadeTipo entidade,
                                                  Long idEntidade, String campo, Object valorAntes, Object valorDepois) {
        return HistoricoDadosPessoais.builder()
                .organizacao(organizacao)
                .usuario(usuario)
                .entidade(entidade)
                .idEntidade(idEntidade)
                .campo(campo)
                .valorAnterior(valorAntes != null ? valorAntes.toString() : null)
                .valorNovo(valorDepois != null ? valorDepois.toString() : null)
                .build();
    }

    /**
     * Compara dois valores com tratamento especial para BigDecimal (ignora scale).
     * BigDecimal.equals(250.00, 250) retorna false; compareTo retorna 0.
     */
    private boolean saoIguais(Object a, Object b) {
        if (Objects.equals(a, b)) return true;
        if (a instanceof BigDecimal bigA && b instanceof BigDecimal bigB) {
            return bigA.compareTo(bigB) == 0;
        }
        return false;
    }

    public static List<Field> getAllFieldsFromClass(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            fields.addAll(Arrays.asList(current.getDeclaredFields()));
            current = current.getSuperclass();
        }
        return fields;
    }

}