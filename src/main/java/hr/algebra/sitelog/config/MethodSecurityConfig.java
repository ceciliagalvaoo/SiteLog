package hr.algebra.sitelog.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Turns on {@code @PreAuthorize} on the services: a call that the rule refuses throws
 * {@code AccessDeniedException} before the method body runs (403 on screens and in the API).
 *
 * <p>Rules used across the services (roles from the domain model):
 * <ul>
 * <li>reading: any signed-in user ({@code isAuthenticated()} on the service class);</li>
 * <li>sites, crews, users: {@code hasRole('ADMIN')};</li>
 * <li>daily logs, work entries, crew rows, sign-off: {@code hasRole('SITE_MANAGER')} and the
 * site's own manager, checked by the {@code siteAccess} bean
 * ({@code @siteAccess.canManageSite(#siteId)} / {@code @siteAccess.canManageLog(#dailyLogId)}).</li>
 * </ul>
 * A separate class (not {@code SecurityConfig}) so the {@code @WebMvcTest} slices, which import
 * {@code SecurityConfig} and mock the services, stay free of method-security proxies.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class MethodSecurityConfig {

}
