package br.com.saudeConecta.config.security;

import br.com.saudeConecta.application.assinatura.AcessoAssinaturaService;
import br.com.saudeConecta.domain.organizacao.Organizacao;
import br.com.saudeConecta.domain.usuario.StatusUsuario;
import br.com.saudeConecta.domain.usuario.TipoUsuarioNovo;
import br.com.saudeConecta.domain.usuario.Usuario;
import br.com.saudeConecta.infrastructure.persistence.repository.UsuarioRepository;
import com.auth0.jwt.exceptions.TokenExpiredException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FiltroAcesso - autenticação por JWT, status do usuário e assinatura")
class FiltroAcessoTest {

    private static final String TOKEN = "token-jwt";
    private static final String LOGIN = "12345678900";
    private static final Long ORG_ID = 7L;

    @Mock private TokenService tokenService;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private AcessoAssinaturaService acessoAssinaturaService;

    @InjectMocks
    private FiltroAcesso filtroAcesso;

    private MockHttpServletResponse response;
    private MockFilterChain chain;

    @BeforeEach
    void setUp() {
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
    }

    @AfterEach
    void tearDown() {
        br.com.saudeConecta.config.tenant.TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest requisicao(String metodo, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(metodo, uri);
        request.setRequestURI(uri);
        request.addHeader("Authorization", "Bearer " + TOKEN);
        return request;
    }

    private Usuario usuario(TipoUsuarioNovo tipo, StatusUsuario status, Long orgId) {
        Organizacao org = null;
        if (orgId != null) {
            org = Organizacao.builder().nome("Clinica").build();
            org.setId(orgId);
        }
        Usuario u = Usuario.builder().login(LOGIN).senha("x").tipoUsuarioNovo(tipo)
                .status(status).organizacao(org).build();
        u.setId(1L);
        return u;
    }

    @Test
    @DisplayName("4.1 - usuário do token apagado: 401 com JSON, sem autenticar e sem seguir a cadeia")
    void deveResponder401QuandoUsuarioDoTokenNaoExiste() throws Exception {
        when(tokenService.getSubject(TOKEN)).thenReturn(LOGIN);
        when(usuarioRepository.findByLogin(LOGIN)).thenReturn(null);

        filtroAcesso.doFilter(requisicao("GET", "/consultas"), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString()).contains("\"error\"");
        assertThat(chain.getRequest()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("5.4 - token adulterado (assinatura inválida, não expirado) responde 401, não 500")
    void deveResponder401QuandoTokenAdulterado() throws Exception {
        TokenService tokenReal = new TokenService();
        org.springframework.test.util.ReflectionTestUtils.setField(tokenReal, "secret", "segredo-do-servidor");
        TokenService tokenDeOutroServidor = new TokenService();
        org.springframework.test.util.ReflectionTestUtils.setField(tokenDeOutroServidor, "secret", "segredo-forjado");
        String tokenForjado = tokenDeOutroServidor.gerarToken(usuario(TipoUsuarioNovo.GESTOR, StatusUsuario.ATIVO, ORG_ID));
        org.springframework.test.util.ReflectionTestUtils.setField(filtroAcesso, "tokenService", tokenReal);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/consultas");
        request.addHeader("Authorization", "Bearer " + tokenForjado);

        filtroAcesso.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString())
                .contains("\"error\"").contains("\"message\"").contains("Token invalido ou expirado");
        assertThat(chain.getRequest()).isNull();
        verifyNoInteractions(usuarioRepository);
    }

    @Test
    @DisplayName("Token expirado continua respondendo 401 no formato JSON")
    void deveResponder401QuandoTokenExpirado() throws Exception {
        when(tokenService.getSubject(TOKEN)).thenThrow(new TokenExpiredException("expirado", Instant.now()));

        filtroAcesso.doFilter(requisicao("GET", "/consultas"), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Token invalido ou expirado");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("4.2 - usuário bloqueado (INATIVO) com token ainda válido: 401")
    void deveResponder401QuandoUsuarioInativo() throws Exception {
        when(tokenService.getSubject(TOKEN)).thenReturn(LOGIN);
        when(usuarioRepository.findByLogin(LOGIN))
                .thenReturn(usuario(TipoUsuarioNovo.CLINICO, StatusUsuario.INATIVO, ORG_ID));

        filtroAcesso.doFilter(requisicao("GET", "/consultas"), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"error\"");
        assertThat(chain.getRequest()).isNull();
        verifyNoInteractions(acessoAssinaturaService);
    }

    @Test
    @DisplayName("4.2 - assinatura sem acesso (suspensa/cancelada): 403 com mensagem clara")
    void deveResponder403QuandoAssinaturaSemAcesso() throws Exception {
        when(tokenService.getSubject(TOKEN)).thenReturn(LOGIN);
        when(usuarioRepository.findByLogin(LOGIN))
                .thenReturn(usuario(TipoUsuarioNovo.GESTOR, StatusUsuario.ATIVO, ORG_ID));
        when(acessoAssinaturaService.organizacaoPermiteAcesso(ORG_ID)).thenReturn(false);

        filtroAcesso.doFilter(requisicao("GET", "/consultas"), response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("\"message\"").contains("plano");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("Usuário ativo com assinatura que permite acesso: autentica e segue a cadeia")
    void deveAutenticarQuandoUsuarioAtivoEAssinaturaOk() throws Exception {
        when(tokenService.getSubject(TOKEN)).thenReturn(LOGIN);
        when(usuarioRepository.findByLogin(LOGIN))
                .thenReturn(usuario(TipoUsuarioNovo.CLINICO, StatusUsuario.ATIVO, ORG_ID));
        when(acessoAssinaturaService.organizacaoPermiteAcesso(ORG_ID)).thenReturn(true);

        filtroAcesso.doFilter(requisicao("GET", "/consultas"), response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/assinaturas/minha-assinatura",
            "/assinaturas/minha-assinatura/gerar-link-pagamento",
            "/assinaturas/minha-assinatura/historico",
            "/assinaturas/limites",
            "/cobrancas/minhas-cobrancas",
            "/cobrancas/pendente-atual"
    })
    @DisplayName("4.2 - clínica suspensa ainda acessa as telas de assinatura/pagamento")
    void devePermitirRotasDePagamentoComAssinaturaSuspensa(String uri) throws Exception {
        when(tokenService.getSubject(TOKEN)).thenReturn(LOGIN);
        when(usuarioRepository.findByLogin(LOGIN))
                .thenReturn(usuario(TipoUsuarioNovo.GESTOR, StatusUsuario.ATIVO, ORG_ID));

        filtroAcesso.doFilter(requisicao("GET", uri), response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
        verify(acessoAssinaturaService, never()).organizacaoPermiteAcesso(anyLong());
    }

    @Test
    @DisplayName("ROOT não depende de assinatura")
    void rootNaoDependeDeAssinatura() throws Exception {
        when(tokenService.getSubject(TOKEN)).thenReturn(LOGIN);
        when(usuarioRepository.findByLogin(LOGIN))
                .thenReturn(usuario(TipoUsuarioNovo.ROOT, StatusUsuario.ATIVO, null));

        filtroAcesso.doFilter(requisicao("GET", "/consultas"), response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
        verifyNoInteractions(acessoAssinaturaService);
    }

    private Usuario usuarioNoPrimeiroAcesso() {
        Usuario u = usuario(TipoUsuarioNovo.CLINICO, StatusUsuario.ATIVO, ORG_ID);
        u.setDeveTrocarSenha(true);
        return u;
    }

    @Test
    @DisplayName("Primeiro acesso: qualquer rota que não seja a troca de senha responde 403 TROCA_SENHA_OBRIGATORIA")
    void primeiroAcessoBloqueiaOutrasRotas() throws Exception {
        when(tokenService.getSubject(TOKEN)).thenReturn(LOGIN);
        when(usuarioRepository.findByLogin(LOGIN)).thenReturn(usuarioNoPrimeiroAcesso());
        when(tokenService.exigeTrocaSenha(TOKEN)).thenReturn(true);

        filtroAcesso.doFilter(requisicao("GET", "/consultas"), response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("TROCA_SENHA_OBRIGATORIA");
        assertThat(chain.getRequest()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("Primeiro acesso: a troca da própria senha passa, mesmo com a clínica suspensa")
    void primeiroAcessoLiberaTrocaDaPropriaSenha() throws Exception {
        when(tokenService.getSubject(TOKEN)).thenReturn(LOGIN);
        when(usuarioRepository.findByLogin(LOGIN)).thenReturn(usuarioNoPrimeiroAcesso());
        when(tokenService.exigeTrocaSenha(TOKEN)).thenReturn(true);

        filtroAcesso.doFilter(requisicao("PUT", "/usuario/trocarSenharUsuariobyOrg/1"), response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
        // Avisa o service de que é o dono trocando no próprio login (é isso que encerra o primeiro acesso)
        assertThat(br.com.saudeConecta.config.tenant.TenantContext.isTrocaSenhaPendente()).isTrue();
        verifyNoInteractions(acessoAssinaturaService);
    }

    @Test
    @DisplayName("Já trocou a senha mas ainda usa o token antigo (com a marca): acesso normal, sem consultar o token")
    void tokenAntigoDepoisDaTrocaNaoBloqueia() throws Exception {
        when(tokenService.getSubject(TOKEN)).thenReturn(LOGIN);
        when(usuarioRepository.findByLogin(LOGIN))
                .thenReturn(usuario(TipoUsuarioNovo.CLINICO, StatusUsuario.ATIVO, ORG_ID));
        when(acessoAssinaturaService.organizacaoPermiteAcesso(ORG_ID)).thenReturn(true);

        filtroAcesso.doFilter(requisicao("GET", "/consultas"), response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
        verify(tokenService, never()).exigeTrocaSenha(TOKEN);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "PUT /usuario/trocarSenharUsuariobyOrg/2",
            "PUT /usuario/trocarSenharUsuariobyOrg/11",
            "GET /usuario/trocarSenharUsuariobyOrg/1",
            "PUT /usuario/trocarSenharUsuariobyOrg/1/extra"
    })
    @DisplayName("Primeiro acesso: senha de outro usuário, outro verbo ou rota parecida continuam bloqueados")
    void primeiroAcessoNaoLiberaRotasParecidas(String metodoERota) throws Exception {
        String[] partes = metodoERota.split(" ");
        when(tokenService.getSubject(TOKEN)).thenReturn(LOGIN);
        when(usuarioRepository.findByLogin(LOGIN)).thenReturn(usuarioNoPrimeiroAcesso());
        when(tokenService.exigeTrocaSenha(TOKEN)).thenReturn(true);

        filtroAcesso.doFilter(requisicao(partes[0], partes[1]), response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("Primeiro acesso + token da senha master (sem a marca): acesso normal, sem exigir troca")
    void tokenDaSenhaMasterNaoExigeTroca() throws Exception {
        when(tokenService.getSubject(TOKEN)).thenReturn(LOGIN);
        when(usuarioRepository.findByLogin(LOGIN)).thenReturn(usuarioNoPrimeiroAcesso());
        when(tokenService.exigeTrocaSenha(TOKEN)).thenReturn(false);
        when(acessoAssinaturaService.organizacaoPermiteAcesso(ORG_ID)).thenReturn(true);

        filtroAcesso.doFilter(requisicao("GET", "/consultas"), response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
        assertThat(br.com.saudeConecta.config.tenant.TenantContext.isTrocaSenhaPendente()).isFalse();
    }

    @Test
    @DisplayName("Sem header Authorization: apenas segue a cadeia (Spring Security decide)")
    void semTokenSegueACadeia() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/cep/01310100");

        filtroAcesso.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        verifyNoInteractions(tokenService, usuarioRepository, acessoAssinaturaService);
    }
}
