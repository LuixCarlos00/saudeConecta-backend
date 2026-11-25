package br.com.saudeConecta.config.tenant;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TenantContext {
    
    private static final ThreadLocal<Long> CURRENT_TENANT = new ThreadLocal<>();
    private static final ThreadLocal<Long> CURRENT_USER = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> TROCA_SENHA_PENDENTE = new ThreadLocal<>();
    
    private TenantContext() {
    }
    
    public static void setCurrentTenant(Long organizacaoId) {
        log.debug("tenant.contexto definir organizacaoId={}", organizacaoId);
        CURRENT_TENANT.set(organizacaoId);
    }
    
    public static Long getCurrentTenant() {
        return CURRENT_TENANT.get();
    }
    
    public static Long getCurrentTenantOrThrow() {
        Long tenantId = CURRENT_TENANT.get();
        if (tenantId == null) {
            throw new TenantNotDefinedException("Tenant não definido no contexto atual");
        }
        return tenantId;
    }
    
    public static void setCurrentUser(Long userId) {
        log.debug("tenant.contexto definir usuarioId={}", userId);
        CURRENT_USER.set(userId);
    }
    
    public static Long getCurrentUser() {
        return CURRENT_USER.get();
    }
    
    /**
     * Marca que a requisição atual é de um primeiro acesso com a troca de senha pendente.
     * Preenchido pelo FiltroAcesso (banco + token); a sessão da senha master nunca marca.
     *
     * @param pendente true quando o dono da conta ainda precisa trocar a senha recebida por e-mail
     */
    public static void setTrocaSenhaPendente(boolean pendente) {
        TROCA_SENHA_PENDENTE.set(pendente);
    }

    /**
     * @return true quando a requisição atual é de um primeiro acesso com troca de senha pendente
     */
    public static boolean isTrocaSenhaPendente() {
        return Boolean.TRUE.equals(TROCA_SENHA_PENDENTE.get());
    }

    public static void clear() {
        log.debug("tenant.contexto limpar");
        CURRENT_TENANT.remove();
        CURRENT_USER.remove();
        TROCA_SENHA_PENDENTE.remove();
    }
    
    public static boolean hasTenant() {
        return CURRENT_TENANT.get() != null;
    }
}
