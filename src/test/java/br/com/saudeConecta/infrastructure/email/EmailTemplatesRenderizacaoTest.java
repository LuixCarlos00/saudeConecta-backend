package br.com.saudeConecta.infrastructure.email;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.exceptions.TemplateInputException;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Renderiza os templates de e-mail com o Thymeleaf real (mesmo prefixo/sufixo do Spring Boot)
 * para garantir que todos usam o estilo único de {@code fragments/email-base.html}.
 */
class EmailTemplatesRenderizacaoTest {

    private static SpringTemplateEngine engine;

    @BeforeAll
    static void configurar() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
    }

    private static Map<String, Object> variaveisCompletas() {
        Map<String, Object> v = new HashMap<>();
        v.put("nome", "Maria");
        v.put("login", "12345678900");
        v.put("senha", "s3nh@");
        v.put("tipoUsuario", "Administrador");
        v.put("nomeOrganizacao", "Clínica Teste");
        v.put("link", "https://docclin.com.br/#/redefinir-senha?token=abc");
        v.put("validadeMinutos", 30);
        v.put("protocolo", "#SC-0001");
        v.put("titulo", "Erro ao salvar");
        v.put("categoria", "Problema no sistema");
        v.put("prioridade", "Alta");
        v.put("status", "Em análise");
        v.put("quantidadeAnexos", 2);
        v.put("corpo", "Descrição");
        v.put("prazoDias", 3);
        v.put("previsaoAtendimento", "21/11/2026");
        v.put("statusAnterior", "Em análise");
        v.put("statusAtual", "Em andamento");
        v.put("observacao", "Estamos verificando.");
        v.put("dataFimTrial", "30/11/2026");
        v.put("plano", "Profissional");
        v.put("valorMensal", "R$ 99,90");
        v.put("initPoint", "https://mercadopago.com/checkout");
        v.put("payerEmail", "maria@exemplo.com");
        return v;
    }

    private static String renderizar(String template, Map<String, Object> variaveis) {
        return engine.process(template, new Context(Locale.forLanguageTag("pt-BR"), variaveis));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "email-credenciais",
            "email-recuperacao-senha",
            "email-chamado-suporte",
            "email-chamado-status",
            "email-ativacao-assinatura"
    })
    void deveRenderizarComEstiloUnicoERodapePadrao(String template) throws Exception {
        String html = renderizar(template, variaveisCompletas());

        // Fragmento de estilo e rodapé foram incluídos
        assertThat(html).contains("<style>").contains(".marca").contains("#1d4ed8");
        assertThat(html).contains("© DocClin - Todos os direitos reservados");
        assertThat(html).contains("Olá, <span>Maria</span>!");

        // Nada da paleta antiga: gradiente, amarelo de aviso, fundo escuro e cor por perfil
        assertThat(html)
                .doesNotContain("linear-gradient")
                .doesNotContain("#fff3cd")
                .doesNotContain("#ffc107")
                .doesNotContain("#1a1a2e");

        salvarPrevia(template, html);
    }

    @Test
    void credenciaisDeveExibirLoginESenhaSemCorPorPerfil() {
        String html = renderizar("email-credenciais", variaveisCompletas());

        assertThat(html).contains("12345678900").contains("s3nh@").contains("Clínica Teste");
        assertThat(html).doesNotContain("style=\"background-color");
    }

    @Test
    void chamadoStatusDeveOmitirMensagemQuandoObservacaoVazia() {
        Map<String, Object> v = variaveisCompletas();
        v.put("observacao", "");

        String html = renderizar("email-chamado-status", v);

        assertThat(html).doesNotContain("Mensagem da equipe de suporte");
    }

    @Test
    void credenciaisDeveOmitirOrganizacaoQuandoNula() {
        Map<String, Object> v = variaveisCompletas();
        v.put("nomeOrganizacao", null);

        String html = renderizar("email-credenciais", v);

        assertThat(html).doesNotContain("Organização:");
    }

    @Test
    void deveFalharQuandoTemplateNaoExiste() {
        assertThatThrownBy(() -> renderizar("email-inexistente", variaveisCompletas()))
                .isInstanceOf(TemplateInputException.class);
    }

    /** Grava o HTML em target/ para conferência visual no navegador (não afeta o resultado do teste). */
    private static void salvarPrevia(String template, String html) throws Exception {
        Path pasta = Path.of("target", "previa-emails");
        Files.createDirectories(pasta);
        Files.writeString(pasta.resolve(template + ".html"), html);
    }
}
