package example.grpcclient;

import example.services.CoffeePotImpl;
import example.services.SortImpl;
import example.services.VigenereImpl;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerMethodDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Server that manages startup/shutdown of the `Node`.
 */
public class Node {
    private static final Logger logger = LoggerFactory.getLogger(Node.class);

    static private Server server;
    int port;

    ServerSocket serv = null;
    InputStream in = null;
    OutputStream out = null;
    Socket clientSocket = null;

    net.Network network = null;

    Node(int port) {
        this.port = port;
        this.network = new net.proto.Network();
    }

    /**
     * Main launches the server from the command line.
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length != 6) {
            System.out.println("Expected arguments: <regAddr(string)> <regPort(int)> <nodeAddr(string)> <nodePort(int)> <name(String)> <registerOn(bool)>");
            logger.error("Expected arguments: <regAddr(string)> <regPort(int)> <nodeAddr(string)> <nodePort(int)> <name(String)> <registerOn(bool)>");
            System.exit(1);
        }
        int regPort = 9003;
        int nodePort = 9099;
        try {
            regPort = Integer.parseInt(args[1]);
            nodePort = Integer.parseInt(args[3]);
        } catch (NumberFormatException nfe) {
            System.out.println("[Port] must be an integer");
            logger.error("[Port] must be an integer");
            System.exit(2);
        }
        final Node server = new Node(nodePort);
        logger.info("Starting Node on port {}", nodePort);
        System.out.println(args[0]);
        System.out.println(args[1]);
        System.out.println(args[2]);
        System.out.println(args[3]);
        System.out.println(args[4]);
        System.out.println(args[5]);
        logger.info("reg address: {}, reg port {}, node address: {}, node port {}, name: {}, registerOn: {}", args[0], regPort, args[2], nodePort, args[4], args[5]);

        // Comment the next 2 lines for your local client server development (Activity 2 task 1, you need this part again for Task 2)
        if (args[5].equals("true")) { // since I am too lazy to convert it to bool
            Register regThread = new Register(args[0], regPort, args[2], nodePort, args[4]);
            logger.info("Registering on port {}", regPort);
            regThread.start();
        }
        server.start();
        server.blockUntilShutdown();
    }

    private void start() throws IOException {
        /* The port on which the server should run */
        // Here we are adding the different services that a client can call
        ArrayList<String> services = new ArrayList<>();
        try {
            server = ServerBuilder.forPort(port)
                    .addService(new EchoImpl())
                    .addService(new JokeImpl())
                    .addService(new CoffeePotImpl())
                    .addService(new SortImpl())
                    .addService(new VigenereImpl())
                    .addService(new RegistryAnswerImpl(services)).build().start();
        } catch (IOException e) {
            logger.error("Error starting server on port {}", port, e);
            System.exit(1);
        }

        for (var service : server.getServices()) {
            // returns the services that are available from this node
            for (ServerMethodDefinition<?, ?> method : service.getMethods()) {
                services.add(method.getMethodDescriptor().getFullMethodName());
                System.out.println(method.getMethodDescriptor().getFullMethodName());
                logger.info("Service: {}", method.getMethodDescriptor().getFullMethodName());
            }
        }

        System.out.println("Server running ...");
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                // Use stderr here since the logger may have been reset by its JVM shutdown
                // hook.
                System.err.println("*** shutting down gRPC server since JVM is shutting down");
                try {
                    Node.this.stop();
                } catch (InterruptedException e) {
                    e.printStackTrace(System.err);
                    logger.error("Error while shutting down server", e);
                }
                System.err.println("*** server shut down");
            }
        });
    }

    private void stop() throws InterruptedException {
        if (server != null) {
            logger.info("Shutting down gRPC server since JVM is shutting down");
            server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    /**
     * Await termination on the main thread since the grpc library uses daemon
     * threads.
     */
    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            logger.info("Waiting for gRPC server to terminate");
            server.awaitTermination();
        }
    }

}