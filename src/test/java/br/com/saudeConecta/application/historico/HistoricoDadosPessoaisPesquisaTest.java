package br.com.saudeConecta.application.historico;

import br.com.saudeConecta.application.historico.dto.HistoricoAlteracaoResponse;
import br.com.saudeConecta.config.tenant.TenantAccessDeniedException;
import br.com.saudeConecta.config.tenant.TenantHelper;
import br.com.saudeConecta.domain.historicodadospessoais.EntidadeTipo;
import br.com.saudeConecta.domain.historicodadospessoais.HistoricoDadosPessoais;
import br.com.saudeConecta.domain.organizacao.Organizacao;
import br.com.saudeConecta.domain.usuario.Usuario;
import br.com.saudeConecta.infrastructure.persistence.repository.HistoricoDadosPessoaisRepository;
import br.com.saudeConecta.infrastructure.persistence.repository.OrganizacaoRepository;
import br.com.saudeConecta.infrastructure.persistence.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("HistoricoDadosPessoaisService.pesquisar - auditoria do SUPER_ADMIN")
class HistoricoDadosPessoaisPesquisaTest {

    @Mock private HistoricoDadosPessoaisRepository historicoRepository;
    @Mock private OrganizacaoRepository organizacaoRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private TenantHelper tenantHelper;
    @Mock private br.com.saudeConecta.infrastructure.persistence.repository.AdminOrganizacaoRepository adminOrganizacaoRepository;
    @Mock private br.com.saudeConecta.infrastructure.persistence.repository.ProfissionalRepository profissionalRepository;
    @Mock private br.com.saudeConecta.infrastructure.persistence.repository.SecretariaRepository secretariaRepository;

    @InjectMocks
    private HistoricoDadosPessoaisService service;

    private static HistoricoDadosPessoais registro() {
        Organizacao org = Organizacao.builder().nome("Clínica Vida").build();
        org.setId(7L);
        Usuario usuario = Usuario.builder().login("28617486000120").senha("x")
                .tipoUsuarioNovo(br.com.saudeConecta.domain.usuario.TipoUsuarioNovo.GESTOR).build();
        usuario.setId(3L);
        return HistoricoDadosPessoais.builder()
                .id(10L).organizacao(org).usuario(usuario)
                .entidade(EntidadeTipo.SECRETARIA).idEntidade(4L)
                .campo("telefone").valorAnterior("1100").valorNovo("1199")
                .criadoEm(LocalDateTime.of(2026, 11, 18, 10, 0))
                .build();
    }

    @Test
    @DisplayName("Quem não é SUPER_ADMIN não consulta a auditoria")
    void negaQuemNaoESuperAdmin() {
        when(tenantHelper.isSuperAdmin()).thenReturn(false);

        assertThatThrownBy(() -> service.pesquisar(null, null, null, null, null, PageRequest.of(0, 20)))
                .isInstanceOf(TenantAccessDeniedException.class);
        verifyNoInteractions(historicoRepository);
    }

    @Test
    @DisplayName("Converte para a resposta com clínica, nome, perfil e login de quem alterou")
    void converteParaResposta() {
        when(tenantHelper.isSuperAdmin()).thenReturn(true);
        when(adminOrganizacaoRepository.buscarNomesPorUsuarioIds(any()))
                .thenReturn(List.<Object[]>of(new Object[]{3L, "Bianca Nascimento"}));
        when(historicoRepository.pesquisar(any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(registro())));

        Page<HistoricoAlteracaoResponse> pagina = service.pesquisar(null, null, null, null, null, PageRequest.of(0, 20));

        HistoricoAlteracaoResponse linha = pagina.getContent().get(0);
        assertThat(linha.organizacaoNome()).isEqualTo("Clínica Vida");
        assertThat(linha.usuarioLogin()).isEqualTo("28617486000120");
        assertThat(linha.usuarioNome()).isEqualTo("Bianca Nascimento");
        assertThat(linha.usuarioPerfil()).isEqualTo("Administrador");
        assertThat(linha.campo()).isEqualTo("telefone");
        assertThat(linha.valorAnterior()).isEqualTo("1100");
        assertThat(linha.valorNovo()).isEqualTo("1199");
    }

    @Test
    @DisplayName("Quem não tem cadastro de perfil (ex.: ROOT) fica sem nome, mas com perfil")
    void semCadastroFicaSemNome() {
        when(tenantHelper.isSuperAdmin()).thenReturn(true);
        when(historicoRepository.pesquisar(any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(registro())));

        HistoricoAlteracaoResponse linha = service.pesquisar(null, null, null, null, null, PageRequest.of(0, 20))
                .getContent().get(0);

        assertThat(linha.usuarioNome()).isNull();
        assertThat(linha.usuarioPerfil()).isEqualTo("Administrador");
    }

    @Test
    @DisplayName("Página vazia não consulta nomes")
    void paginaVaziaNaoConsultaNomes() {
        when(tenantHelper.isSuperAdmin()).thenReturn(true);
        when(historicoRepository.pesquisar(any(), any(), any(), any(), any(), any())).thenReturn(Page.empty());

        service.pesquisar(null, null, null, null, null, PageRequest.of(0, 20));

        verifyNoInteractions(adminOrganizacaoRepository, profissionalRepository, secretariaRepository);
    }

    @Test
    @DisplayName("Período: 'até' inclui o dia inteiro e campo vazio vira sem filtro")
    void periodoIncluiODiaInteiro() {
        when(tenantHelper.isSuperAdmin()).thenReturn(true);
        when(historicoRepository.pesquisar(any(), any(), any(), any(), any(), any())).thenReturn(Page.empty());

        service.pesquisar(7L, EntidadeTipo.ADMIN, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 18), "  ",
                PageRequest.of(0, 20));

        verify(historicoRepository).pesquisar(eq(7L), eq(EntidadeTipo.ADMIN),
                eq(LocalDateTime.of(2026, 11, 1, 0, 0)), eq(LocalDateTime.of(2026, 11, 19, 0, 0)),
                isNull(), any());
    }

    @Test
    @DisplayName("Data final antes da inicial é recusada")
    void recusaPeriodoInvertido() {
        when(tenantHelper.isSuperAdmin()).thenReturn(true);

        assertThatThrownBy(() -> service.pesquisar(null, null, LocalDate.of(2026, 11, 18), LocalDate.of(2026, 11, 1),
                null, PageRequest.of(0, 20)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(historicoRepository);
    }

    @Test
    @DisplayName("Página grande é limitada e a ordem é sempre da mais recente para a mais antiga")
    void limitaTamanhoEOrdena() {
        when(tenantHelper.isSuperAdmin()).thenReturn(true);
        when(historicoRepository.pesquisar(any(), any(), any(), any(), any(), any())).thenReturn(Page.empty());

        service.pesquisar(null, null, null, null, null, PageRequest.of(2, 5000, Sort.by("campo")));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(historicoRepository).pesquisar(any(), any(), any(), any(), any(), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(HistoricoDadosPessoaisService.TAMANHO_MAXIMO_PAGINA);
        assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(captor.getValue().getSort().getOrderFor("criadoEm")).isNotNull();
        assertThat(captor.getValue().getSort().getOrderFor("criadoEm").isDescending()).isTrue();
        assertThat(captor.getValue().getSort().getOrderFor("campo")).isNull();
    }
}
