package hr.algebra.sitelog.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * Turns on {@code @Aspect} beans ({@code DomainActionAspect}). Spring Boot 4 only
 * auto-configures this with its AspectJ starter; {@code aspectjweaver} already comes with
 * Spring Data JPA, so enabling it here needs no extra dependency.
 *
 * <p>{@code proxyTargetClass = true}: the services are classes without interfaces, so the
 * proxy is a subclass (CGLIB), the same kind method security and transactions already use.
 * The aspect, {@code @PreAuthorize} and {@code @Transactional} share that one proxy; their
 * {@code @Order} decides who runs first.
 */
@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class AuditConfig {

}
