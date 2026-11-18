package br.com.saudeConecta.application.plataforma.dto;

import br.com.saudeConecta.domain.planos.StatusAssinatura;
import br.com.saudeConecta.domain.planos.TipoPlano;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Item da lista "precisa de atenção". Para {@code PENDENCIA_MANUAL}: {@code organizacaoId},
 * {@code plano}, {@code status} e {@code valorMensal} vêm nulos, {@code nome} é a clínica do evento
 * (ou "Clínica não identificada"), {@code detalhe} é o motivo legível e {@code dataVencimento} é o dia
 * em que o evento chegou.
 *
 * @param organizacaoId  id da clínica (nulo em PENDENCIA_MANUAL)
 * @param nome           nome da clínica
 * @param plano          plano base
 * @param status         status da assinatura
 * @param motivo         PENDENCIA_MANUAL, SUSPENSA, INADIMPLENTE ou TRIAL_VENCENDO
 * @param dataVencimento vencimento da assinatura
 * @param valorMensal    valor mensal da assinatura
 * @param detalhe        texto curto extra (motivo da pendência); nulo nos demais motivos
 */
public record AtencaoResponse(Long organizacaoId, String nome, TipoPlano plano, StatusAssinatura status,
                              String motivo, LocalDate dataVencimento, BigDecimal valorMensal,
                              String detalhe) {
}
