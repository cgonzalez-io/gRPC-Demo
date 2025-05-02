package example.grpcclient;

import com.google.protobuf.Empty;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import service.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class Client {
    private final EchoGrpc.EchoBlockingStub echoStub;
    private final JokeGrpc.JokeBlockingStub jokeStub;
    private final RegistryGrpc.RegistryBlockingStub registryStub;
    private final CoffeePotGrpc.CoffeePotBlockingStub coffeeStub;
    private final SortGrpc.SortBlockingStub sortStub;

    public Client(ManagedChannel serviceChannel, ManagedChannel registryChannel) {
        this.echoStub = EchoGrpc.newBlockingStub(serviceChannel);
        this.jokeStub = JokeGrpc.newBlockingStub(serviceChannel);
        this.registryStub = RegistryGrpc.newBlockingStub(registryChannel);
        this.coffeeStub = CoffeePotGrpc.newBlockingStub(serviceChannel);
        this.sortStub = SortGrpc.newBlockingStub(serviceChannel);
    }

    public static void main(String[] args) {
        if (args.length != 6) {
            System.err.println("Usage: <host> <port> <regHost> <regPort> <message> <regOn>");
            return;
        }

        String host = args[0];
        int port;
        String initialMessage = args[4];
        boolean regOn;
        int regPort;
        try {
            port = Integer.parseInt(args[1]);
            regPort = Integer.parseInt(args[3]);
            regOn = Boolean.parseBoolean(args[5]);
        } catch (NumberFormatException e) {
            System.err.println("Error parsing numeric argument: " + e.getMessage());
            return;
        }

        ManagedChannel serviceChannel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext().build();
        ManagedChannel registryChannel = ManagedChannelBuilder.forAddress(args[2], regPort)
                .usePlaintext().build();

        try {
            Client client = new Client(serviceChannel, registryChannel);
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

            if (regOn) {
                client.dynamicFlow(reader, initialMessage);
            } else {
                client.staticMenu(reader, initialMessage);
            }
        } catch (IOException e) {
            System.err.println("I/O error: " + e.getMessage());
        } finally {
            shutdownChannel(serviceChannel);
            shutdownChannel(registryChannel);
        }
    }

    private static void shutdownChannel(ManagedChannel channel) {
        try {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void dynamicFlow(BufferedReader reader, String initialMessage) throws IOException {
        try {
            ServicesListRes servicesRes = registryStub.getServices(GetServicesReq.newBuilder().build());
            List<String> services = servicesRes.getServicesList();
            if (services.isEmpty()) {
                System.out.println("No services registered.");
                return;
            }
            System.out.println("Available services:");
            for (int i = 0; i < services.size(); i++) {
                System.out.printf("%d) %s%n", i + 1, services.get(i));
            }
            System.out.print("Select a service by number: ");
            int sel = parseInt(reader.readLine(), -1) - 1;
            if (sel < 0 || sel >= services.size()) {
                System.out.println("Invalid selection.");
                return;
            }
            String fullService = services.get(sel);

            SingleServerRes srvRes = registryStub.findServer(
                    FindServerReq.newBuilder().setServiceName(fullService).build());
            Connection conn = srvRes.getConnection();

            ManagedChannel dynChannel = ManagedChannelBuilder.forAddress(conn.getUri(), conn.getPort())
                    .usePlaintext().build();
            try {
                invokeByMethod(fullService, initialMessage, reader, dynChannel);
            } finally {
                shutdownChannel(dynChannel);
            }
        } catch (StatusRuntimeException e) {
            System.err.println("Registry RPC failed: " + e.getStatus());
        }
    }

    private void staticMenu(BufferedReader reader, String initialMessage) throws IOException {
        System.out.println("Select service to call:");
        System.out.println("1) Echo");
        System.out.println("2) Joke");
        System.out.println("3) Brew Coffee");
        System.out.println("4) Get Cup");
        System.out.println("5) Brew Status");
        System.out.println("6) Sort");
        System.out.print("Enter choice: ");
        int choice = parseInt(reader.readLine(), -1);

        switch (choice) {
            case 1:
                callEcho(initialMessage, null);
                break;
            case 2:
                callJoke(reader, null);
                break;
            case 3:
                callBrew(null);
                break;
            case 4:
                callGetCup(null);
                break;
            case 5:
                callBrewStatus(null);
                break;
            case 6:
                callSort(reader, null);
                break;
            default:
                System.out.println("Invalid choice.");
        }
    }

    private void invokeByMethod(String fullService, String msg, BufferedReader reader, ManagedChannel ch) throws IOException {
        String method = fullService.substring(fullService.lastIndexOf('/') + 1);
        switch (method) {
            case "parrot":
                callEcho(msg, ch);
                break;
            case "getJoke":
                callJoke(reader, ch);
                break;
            case "brew":
                callBrew(ch);
                break;
            case "getCup":
                callGetCup(ch);
                break;
            case "brewStatus":
                callBrewStatus(ch);
                break;
            case "sort":
                callSort(reader, ch);
                break;
            default:
                System.out.println("Unknown method: " + method);
        }
    }

    private int parseInt(String s, int defaultVal) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    private void callEcho(String msg, ManagedChannel ch) {
        try {
            EchoGrpc.EchoBlockingStub stub = (ch != null)
                    ? EchoGrpc.newBlockingStub(ch)
                    : echoStub;
            ServerResponse resp = stub.parrot(ClientRequest.newBuilder().setMessage(msg).build());
            System.out.println("Echo: " + (resp.getIsSuccess() ? resp.getMessage() : resp.getError()));
        } catch (StatusRuntimeException e) {
            System.err.println("Echo RPC failed: " + e.getStatus());
        }
    }

    private void callJoke(BufferedReader reader, ManagedChannel ch) throws IOException {
        System.out.print("How many jokes? ");
        int n = parseInt(reader.readLine(), 0);
        try {
            JokeGrpc.JokeBlockingStub stub = (ch != null)
                    ? JokeGrpc.newBlockingStub(ch)
                    : jokeStub;
            JokeRes resp = stub.getJoke(JokeReq.newBuilder().setNumber(n).build());
            resp.getJokeList().forEach(j -> System.out.println("- " + j));
        } catch (StatusRuntimeException e) {
            System.err.println("Joke RPC failed: " + e.getStatus());
        }
    }

    private void callBrew(ManagedChannel ch) {
        try {
            CoffeePotGrpc.CoffeePotBlockingStub stub = (ch != null)
                    ? CoffeePotGrpc.newBlockingStub(ch)
                    : coffeeStub;
            BrewResponse resp = stub.brew(Empty.getDefaultInstance());
            if (resp.getIsSuccess()) System.out.println(resp.getMessage());
            else System.out.println("Error: " + resp.getError());
        } catch (StatusRuntimeException e) {
            System.err.println("Brew RPC failed: " + e.getStatus());
        }
    }

    private void callGetCup(ManagedChannel ch) {
        try {
            CoffeePotGrpc.CoffeePotBlockingStub stub = (ch != null)
                    ? CoffeePotGrpc.newBlockingStub(ch)
                    : coffeeStub;
            GetCupResponse resp = stub.getCup(Empty.getDefaultInstance());
            if (resp.getIsSuccess()) System.out.println(resp.getMessage());
            else System.out.println("Error: " + resp.getError());
        } catch (StatusRuntimeException e) {
            System.err.println("GetCup RPC failed: " + e.getStatus());
        }
    }

    private void callBrewStatus(ManagedChannel ch) {
        try {
            CoffeePotGrpc.CoffeePotBlockingStub stub = (ch != null)
                    ? CoffeePotGrpc.newBlockingStub(ch)
                    : coffeeStub;
            BrewStatusResponse resp = stub.brewStatus(Empty.getDefaultInstance());
            BrewStatus status = resp.getStatus();
            System.out.printf("Status: %s (%dm %ds)%n",
                    status.getMessage(), status.getMinutes(), status.getSeconds());
        } catch (StatusRuntimeException e) {
            System.err.println("Status RPC failed: " + e.getStatus());
        }
    }

    private void callSort(BufferedReader reader, ManagedChannel ch) throws IOException {
        System.out.print("Enter numbers (comma-separated): ");
        List<Integer> data = Arrays.stream(reader.readLine().split(","))
                .map(str -> parseInt(str, Integer.MIN_VALUE))
                .filter(val -> val != Integer.MIN_VALUE)
                .collect(Collectors.toList());
        System.out.print("Choose algo (0=MERGE,1=QUICK,2=INTERN): ");
        int idx = parseInt(reader.readLine(), 2);
        Algo algo = Algo.forNumber(idx);
        try {
            SortGrpc.SortBlockingStub stub = (ch != null)
                    ? SortGrpc.newBlockingStub(ch)
                    : sortStub;
            SortResponse resp = stub.sort(
                    SortRequest.newBuilder().setAlgo(algo).addAllData(data).build()
            );
            if (resp.getIsSuccess()) System.out.println("Sorted: " + resp.getDataList());
            else System.out.println("Error: " + resp.getError());
        } catch (StatusRuntimeException e) {
            System.err.println("Sort RPC failed: " + e.getStatus());
        }
    }
}


