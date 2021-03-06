package org.atmosphere.vibe.example.clustering.amqp1;

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

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.MessageProperties;

@WebListener
public class Bootstrap implements ServletContextListener {
    @Override
    public void contextInitialized(ServletContextEvent event) {
        final ClusteredServer server = new ClusteredServer();
        try {
            ConnectionFactory connectionFactory = new ConnectionFactory();
            Connection connection = connectionFactory.newConnection();
            final Channel channel = connection.createChannel();
            channel.exchangeDeclare("vibe", "topic");
            String queue = channel.queueDeclare().getQueue();
            channel.queueBind(queue, "vibe", "server");
            // Receives a message
            channel.basicConsume(queue, new DefaultConsumer(channel) {
                @SuppressWarnings("unchecked")
                @Override
                public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties props, byte[] body) throws IOException {
                    if (envelope.getRoutingKey().equals("server")) {
                        ByteArrayInputStream bais = new ByteArrayInputStream(body);
                        Map<String, Object> message = null;
                        try (ObjectInputStream in = new ObjectInputStream(bais)) {
                            message = (Map<String, Object>) in.readObject();
                        } catch (ClassNotFoundException e) {
                            throw new RuntimeException(e);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        System.out.println("receiving a message: " + message);
                        server.messageAction().on(message);
                        channel.basicAck(envelope.getDeliveryTag(), false);
                    }
                }
            });
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
                    try {
                        channel.basicPublish("vibe", "server", MessageProperties.BASIC, baos.toByteArray());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

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
