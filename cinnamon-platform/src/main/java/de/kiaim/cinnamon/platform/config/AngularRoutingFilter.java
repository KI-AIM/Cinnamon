package de.kiaim.cinnamon.platform.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AngularRoutingFilter implements Filter {

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
    HttpServletRequest httpServletRequest = ((HttpServletRequest) request);
    String requestURI = httpServletRequest.getRequestURI();

    if (shouldDispatch(requestURI)) {
        request.getRequestDispatcher("/").forward(request, response);
    } else {
        chain.doFilter(request, response);
    }
  }

  @Override
  public void init(FilterConfig filterConfig) {}

  @Override
  public void destroy() {}

  private boolean shouldDispatch(String requestURI) {
    // Exclude/Include URLs here
    // Do not forward api calls, root requests, and requested resources
    return !(requestURI.startsWith("/api") || requestURI.startsWith("/actuator") || requestURI.equals("/") || requestURI.contains("."));
  }

}
