package br.com.saudeConecta.application.notificacao;

import br.com.saudeConecta.domain.mensageria.Mensageria;
import br.com.saudeConecta.domain.mensageria.TipoMensagem;
import br.com.saudeConecta.domain.organizacao.Organizacao;
import br.com.saudeConecta.infrastructure.persistence.repository.MensageriaRepository;
import br.com.saudeConecta.infrastructure.persistence.repository.OrganizacaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmailMensageriaService - o que fica gravado do e-mail enviado")
class EmailMensageriaServiceTest {

    private static final String HTML_COM_LINK =
            "<html><a href=\"https://docclin.com.br/#/redefinir-senha?token=SEGREDO123\">Criar nova senha</a></html>";

    @Mock private MensageriaRepository mensageriaRepository;
    @Mock private OrganizacaoRepository organizacaoRepository;
    @InjectMocks private EmailMensageriaService service;

    @BeforeEach
    void setUp() {
        Organizacao organizacao = new Organizacao();
        organizacao.setId(4L);
        when(organizacaoRepository.findById(4L)).thenReturn(Optional.of(organizacao));
    }

    private Mensageria gravado() {
        ArgumentCaptor<Mensageria> captor = ArgumentCaptor.forClass(Mensageria.class);
        verify(mensageriaRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("Sucesso de e-mail de redefinição: o link com token não é gravado")
    void sucessoRecuperacaoOmiteCorpo() {
        service.registrarSucesso(new RegistroEnvioEmail(4L, null, "a@b.com", "Ana", "Assunto",
                HTML_COM_LINK, TipoMensagem.EMAIL_RECUPERACAO_SENHA), 1);

        assertThat(gravado().getCorpoMensagem())
                .isEqualTo(EmailMensageriaService.CORPO_OMITIDO_RECUPERACAO)
                .doesNotContain("SEGREDO123");
    }

    @Test
    @DisplayName("Falha de e-mail de redefinição: o link com token também não é gravado")
    void falhaRecuperacaoOmiteCorpo() {
        service.registrarFalha(new RegistroEnvioEmail(4L, null, "a@b.com", "Ana", "Assunto",
                HTML_COM_LINK, TipoMensagem.EMAIL_RECUPERACAO_SENHA), "smtp fora", 3);

        assertThat(gravado().getCorpoMensagem()).doesNotContain("SEGREDO123");
    }

    @Test
    @DisplayName("Outros tipos continuam gravando o HTML (o reenvio depende dele)")
    void outrosTiposGravamCorpo() {
        service.registrarSucesso(new RegistroEnvioEmail(4L, 9L, "a@b.com", "Ana", "Assunto",
                "<html>chamado</html>", TipoMensagem.EMAIL_CHAMADO_SUPORTE), 1);

        assertThat(gravado().getCorpoMensagem()).isEqualTo("<html>chamado</html>");
    }
}
