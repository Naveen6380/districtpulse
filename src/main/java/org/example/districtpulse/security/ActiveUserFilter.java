package org.example.districtpulse.security;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.core.context.SecurityContextHolder;
import java.io.IOException;
public class ActiveUserFilter extends OncePerRequestFilter {
    private final UserRepository users;
    public ActiveUserFilter(UserRepository users){this.users=users;}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth!=null && auth.isAuthenticated()) {
            var user=users.findById(auth.getName()).orElse(null);
            Object claim=auth instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken jwt?jwt.getToken().getClaim("version"):null;
            Long version=claim instanceof Number number?number.longValue():null;
            if(user==null || !user.isEnabled() || version==null || version.longValue()!=user.getTokenVersion() || auth.getAuthorities().stream().noneMatch(a->a.getAuthority().equals("ROLE_"+user.getRole()))) {
                response.sendError(401); return;
            }
        }
        chain.doFilter(request,response);
    }
}
