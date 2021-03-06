package org.atmosphere.vibe.example.clsutering.redis2;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Map;

import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletRegistration;
import javax.servlet.annotation.WebListener;

import org.atmosphere.cpr.ApplicationConfig;
import org.atmosphere.vibe.ClusteredServer;
import org.atmosphere.vibe.ServerSocket;
import org.atmosphere.vibe.platform.action.Action;
import org.atmosphere.vibe.platform.bridge.atmosphere2.VibeAtmosphereServlet;
import org.atmosphere.vibe.transport.http.HttpTransportServer;
import org.atmosphere.vibe.transport.ws.WebSocketTransportServer;

import redis.clients.jedis.BinaryJedisPubSub;
import redis.clients.jedis.Jedis;

@WebListener
public class Bootstrap implements ServletContextListener {
    @Override
    public void contextInitialized(ServletContextEvent event) {
        final ClusteredServer server = new ClusteredServer();
        // Receives a message
        new Thread(new Runnable() {
            @Override
            public void run() {
                @SuppressWarnings("resource")
                Jedis jedis = new Jedis("localhost");
                jedis.subscribe(new BinaryJedisPubSub() {
                    @Override
                    public void onUnsubscribe(byte[] channel, int subscribedChannels) {}
                    
                    @Override
                    public void onSubscribe(byte[] channel, int subscribedChannels) {}
                    
                    @Override
                    public void onPUnsubscribe(byte[] pattern, int subscribedChannels) {}
                    
                    @Override
                    public void onPSubscribe(byte[] pattern, int subscribedChannels) {}
                    
                    @Override
                    public void onPMessage(byte[] pattern, byte[] channel, byte[] message) {}
                    
                    @SuppressWarnings("unchecked")
                    @Override
                    public void onMessage(byte[] channel, byte[] message) {
                        ByteArrayInputStream bais = new ByteArrayInputStream(message);
                        Map<String, Object> body = null;
                        try (ObjectInputStream in = new ObjectInputStream(bais)) {
                            body = (Map<String, Object>) in.readObject();
                        } catch (ClassNotFoundException e) {
                            throw new RuntimeException(e);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        System.out.println("receiving a message: " + body);
                        server.messageAction().on(body);
                    }
                }, "vibe".getBytes());
            }
        })
        .start();
        // Publishes a message
        server.publishAction(new Action<Map<String, Object>>() {
            @Override
            public void on(Map<String, Object> message) {
                System.out.println("publishing a message: " + message);
                ByteArrayOutputStream baos = new ByteArrayOutputStream(512);
                try (ObjectOutputStream out = new ObjectOutputStream(baos)) {
                    out.writeObject(message);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                @SuppressWarnings("resource")
                Jedis jedis = new Jedis("localhost");
                jedis.publish("vibe".getBytes(), baos.toByteArray());
            }
        });
        
        server.socketAction(new Action<ServerSocket>() {
            @Override
            public void on(final ServerSocket socket) {
                System.out.println("on socket: " + socket.uri());
                socket.on("echo", new Action<Object>() {
                    @Override
                    public void on(Object data) {
                        System.out.println("on echo event: " + data);
                        socket.send("echo", data);
                    }
                });
                socket.on("chat", new Action<Object>() {
                    @Override
                    public void on(Object data) {
                        System.out.println("on chat event: " + data);
                        server.all().send("chat", data);
                    }
                });
            }
        });

        HttpTransportServer httpTransportServer = new HttpTransportServer().transportAction(server);
        WebSocketTransportServer wsTransportServer = new WebSocketTransportServer().transportAction(server);
        
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
