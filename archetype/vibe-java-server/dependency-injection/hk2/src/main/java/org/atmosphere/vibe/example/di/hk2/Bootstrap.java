package org.atmosphere.vibe.example.di.hk2;

import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletRegistration;
import javax.servlet.annotation.WebListener;

import org.atmosphere.cpr.ApplicationConfig;
import org.atmosphere.vibe.Server;
import org.atmosphere.vibe.platform.bridge.atmosphere2.VibeAtmosphereServlet;
import org.atmosphere.vibe.transport.http.HttpTransportServer;
import org.atmosphere.vibe.transport.ws.WebSocketTransportServer;
import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.utilities.ServiceLocatorUtilities;

@WebListener
public class Bootstrap implements ServletContextListener {
    @Override
    public void contextInitialized(ServletContextEvent event) {
        ServiceLocator locator = ServiceLocatorUtilities.createAndPopulateServiceLocator();
        ServiceLocatorUtilities.enableImmediateScope(locator);
        Server server = locator.getService(Server.class);
        
        HttpTransportServer httpTransportServer = new HttpTransportServer().transportAction(server);
        WebSocketTransportServer wsTransportServer = new WebSocketTransportServer().transportAction(server);

        // Installs the server on Atmosphere platform
        ServletContext context = event.getServletContext();
        Servlet servlet = new VibeAtmosphereServlet().httpAction(httpTransportServer).wsAction(wsTransportServer);
        ServletRegistration.Dynamic reg = context.addServlet(VibeAtmosphereServlet.class.getName(), servlet);
        reg.setAsyncSupported(true);
        reg.setInitParameter(ApplicationConfig.DISABLE_ATMOSPHEREINTERCEPTOR, Boolean.TRUE.toString());
        reg.addMapping("/vibe");
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {}
}