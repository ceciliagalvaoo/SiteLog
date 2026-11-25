/**
 * The REST API ({@code /api/v1/**}): one sub-package per aggregate ({@code site}, {@code dailylog})
 * plus the shared pieces ({@code common} paging, {@code error} ProblemDetail mapping,
 * {@code security} 401/403 handlers and the CSRF token endpoint, {@code docs} OpenAPI).
 *
 * <p>Same layering as the screens: a controller only maps JSON request records to the service
 * commands and the service views to JSON response records. Business rules, transactions and
 * authorization ({@code @PreAuthorize}) stay in the services, so the screen and the API cannot
 * disagree. Entities never leave the service layer.
 *
 * <p>A separate top-level package (and not {@code site.api}, {@code dailylog.api}) so that the
 * {@code @RestControllerAdvice} can be limited to it with one {@code basePackages} value, and
 * the HTTP adapter of the whole API is in one place for review.
 */
package hr.algebra.sitelog.api;
