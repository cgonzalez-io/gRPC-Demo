package example.grpcclient;

import com.google.protobuf.Empty;
import example.scenarios.AutoTestRunner;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Client with interactive mode and auto-run mode (-Dauto=1) for CLI or Gradle.
 * Robust error handling added to prevent crashes on invalid input or RPC failures.
 */
public class Client {
    private static final Logger logger = LoggerFactory.getLogger(Client.class);
    private static final String INVALID_CHAR_REGEX = "[^a-zA-Z0-9,&]";
    private final EchoGrpc.EchoBlockingStub echoStub;
    private final JokeGrpc.JokeBlockingStub jokeStub;
    private final RegistryGrpc.RegistryBlockingStub registryStub;
    private final CoffeePotGrpc.CoffeePotBlockingStub coffeeStub;
    private final SortGrpc.SortBlockingStub sortStub;
    private final VigenereGrpc.VigenereBlockingStub vigenereStub;
    private final char separator = '&';

    /**
     * Constructs a Client instance that initializes the stubs required for communication with various services.
     *
     * @param serviceChannel  the gRPC channel used to communicate with the services like Echo, Joke, Coffee, and Sort
     * @param registryChannel the gRPC channel used to communicate with the Registry service
     */
    public Client(ManagedChannel serviceChannel, ManagedChannel registryChannel) {
        this.echoStub = EchoGrpc.newBlockingStub(serviceChannel);
        this.jokeStub = JokeGrpc.newBlockingStub(serviceChannel);
        this.registryStub = RegistryGrpc.newBlockingStub(registryChannel);
        this.coffeeStub = CoffeePotGrpc.newBlockingStub(serviceChannel);
        this.sortStub = SortGrpc.newBlockingStub(serviceChannel);
        this.vigenereStub = VigenereGrpc.newBlockingStub(serviceChannel);
    }

    /**
     * The main method serves as the entry point for the application. It initializes gRPC channels, parses command-line
     * arguments, and manages the interactive or automated workflows based on the input parameters.
     *
     * @param args the command-line arguments, which must include:
     *             args[0] - the hostname or IP address of the primary server
     *             args[1] - the port number of the primary server
     *             args[2] - the hostname or IP address of the registry server
     *             args[3] - the port number of the registry server
     *             args[4] - the initial message or user input for the workflow
     *             args[5] - a boolean ("true" or "false") indicating whether the registry-based flow is enabled
     *             optional property auto -Pauto= - the auto-run flag (1 for auto-run, 0 for manual)
     */
    public static void main(String[] args) {
        if (args.length != 6) {
            System.err.println("Usage: <host> <port> <regHost> <regPort> <message> <regOn>");
            logger.error("Usage: <host> <port> <regHost> <regPort> <message> <regOn>");
            logger.info("Passed arguments: {}", Arrays.toString(args));
            return;
        }

        String host = args[0];
        int port = 9099;
        String regHost = args[2];
        int regPort = 9003;
        String message = args[4];
        boolean regOn;
        try {
            port = Integer.parseInt(args[1]);
            regPort = Integer.parseInt(args[3]);
            regOn = Boolean.parseBoolean(args[5]);
        } catch (NumberFormatException e) {
            System.err.println("Invalid numeric argument: " + e.getMessage());
            return;
        }

        ManagedChannel svcCh = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext().build();
        ManagedChannel regCh = ManagedChannelBuilder.forAddress(regHost, regPort)
                .usePlaintext().build();

        try {
            Client client = new Client(svcCh, regCh);
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            String autoProp = System.getProperty("auto");
            // Wrap all flows in top-level try to catch unexpected exceptions
            try {
                if ("1".equals(autoProp)) {
                    try {
                        new AutoTestRunner(client).runAll();
                    } catch (Exception ae) {
                        System.err.println("Auto-run failed: " + ae.getMessage());
                    }
                } else if (regOn) {
                    try {
                        client.dynamicFlow(reader, message);
                    } catch (IOException | StatusRuntimeException e) {
                        System.err.println("Interactive (registry) flow error: " + e.getMessage());
                    }
                } else {
                    try {
                        client.staticMenu(reader, message);
                    } catch (IOException | StatusRuntimeException e) {
                        System.err.println("Interactive (local) menu error: " + e.getMessage());
                    }
                }
            } catch (Exception e) {
                System.err.println("Unexpected client error: " + e.getMessage());
            }
        } finally {
            shutdownChannel(svcCh);
            shutdownChannel(regCh);
        }
    }

    // --- RPC call helpers ---

    /**
     * Shuts down the specified gRPC channel immediately and awaits termination
     * for a specified duration.
     * <p>
     * This method terminates the channel immediately using `shutdownNow()`
     * and waits for its termination up to 5 seconds. It ensures that the
     * thread is not left in an interrupted state if `InterruptedException` occurs
     * during the await termination process.
     *
     * @param channel the {@code ManagedChannel} to be shut down and terminated.
     *                This channel must be non-null, and it represents an active
     *                gRPC channel that needs to be properly closed to release
     *                resources.
     */
    private static void shutdownChannel(ManagedChannel channel) {
        try {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Calls the Echo service to send a message and receive an echoed response.
     * If a channel is provided, it creates a new EchoBlockingStub for the communication.
     * If no channel is provided, it uses an existing stub.
     *
     * @param msg the message to be sent to the Echo service
     * @param ch  the ManagedChannel used to establish the connection to the Echo service;
     *            if null, the existing echoStub is used
     */
    public void callEcho(String msg, ManagedChannel ch) {
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

    /**
     * Triggers the brew operation on the CoffeePot service.
     *
     * @param ch the gRPC communication channel through which the request is made. If null, the default coffeeStub will be used.
     */
    public void callBrew(ManagedChannel ch) {
        try {
            CoffeePotGrpc.CoffeePotBlockingStub stub = (ch != null)
                    ? CoffeePotGrpc.newBlockingStub(ch)
                    : coffeeStub;
            BrewResponse resp = stub.brew(Empty.getDefaultInstance());
            System.out.println(resp.getIsSuccess() ? resp.getMessage() : "Error: " + resp.getError());
        } catch (StatusRuntimeException e) {
            System.err.println("Brew RPC failed: " + e.getStatus());
        }
    }

    /**
     * Makes a gRPC call to retrieve a cup of coffee from the CoffeePot service.
     * If a valid ManagedChannel is provided, it uses that channel to create a stub; otherwise, it uses a preconfigured stub.
     * Logs the success message or the error depending on the response status.
     * Handles any runtime exceptions that may occur during the RPC call.
     *
     * @param ch the gRPC ManagedChannel used to communicate with the CoffeePot service. If null, a preconfigured stub is used.
     */
    public void callGetCup(ManagedChannel ch) {
        try {
            CoffeePotGrpc.CoffeePotBlockingStub stub = (ch != null)
                    ? CoffeePotGrpc.newBlockingStub(ch)
                    : coffeeStub;
            GetCupResponse resp = stub.getCup(Empty.getDefaultInstance());
            System.out.println(resp.getIsSuccess() ? resp.getMessage() : "Error: " + resp.getError());
        } catch (StatusRuntimeException e) {
            System.err.println("GetCup RPC failed: " + e.getStatus());
        }
    }

    /**
     * Fetches and logs the brewing status from the CoffeePot service.
     *
     * @param ch The gRPC ManagedChannel used for communication with the CoffeePot service.
     *           If this parameter is null, a default CoffeePotBlockingStub is used.
     */
    public void callBrewStatus(ManagedChannel ch) {
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

    /**
     * Invokes the Joke service to retrieve and display a list of jokes.
     *
     * @param n  the number of jokes to retrieve from the service
     * @param ch the gRPC managed channel to use for the request; if null, a default stub will be used
     */
    public void callJokeValue(int n, ManagedChannel ch) {
        try {
            JokeRes resp = (ch != null ? JokeGrpc.newBlockingStub(ch) : jokeStub)
                    .getJoke(JokeReq.newBuilder().setNumber(n).build());
            resp.getJokeList().forEach(j -> System.out.println("- " + j));
        } catch (StatusRuntimeException e) {
            System.err.println("Joke RPC failed: " + e.getStatus());
        }
    }

    // --- Interactive flows ---

    /**
     * Calls the sort service to sort a list of integers using the specified algorithm.
     * This method interacts with the remote Sort service via gRPC.
     *
     * @param data the list of integers to be sorted
     * @param algo the sorting algorithm to be applied (e.g., MERGE, QUICK, INTERN)
     * @param ch   the managed gRPC channel used to communicate with the Sort service;
     *             if null, a default stub will be used
     */
    public void callSortList(List<Integer> data, Algo algo, ManagedChannel ch) {
        try {
            SortResponse resp = (ch != null ? SortGrpc.newBlockingStub(ch) : sortStub)
                    .sort(SortRequest.newBuilder().addAllData(data).setAlgo(algo).build());
            System.out.println(resp.getIsSuccess() ? "Sorted: " + resp.getDataList() : "Error: " + resp.getError());
        } catch (StatusRuntimeException e) {
            System.err.println("Sort RPC failed: " + e.getStatus());
        }
    }

    /**
     * Encodes the provided plaintext using the Vigenere cipher through a gRPC call.
     * <p>
     * This method prompts the user for plaintext and a key, then sends these values
     * to a remote gRPC service for encoding. The result is displayed to the user, or
     * an error message is printed if the operation fails.
     *
     * @param reader a BufferedReader used to read user input for plaintext and key
     * @param ch     a ManagedChannel for gRPC communication. If null, a default stub is used
     * @throws IOException if an input/output error occurs during user input reading
     */
    // call methods for Vigenere
    public void callEncode(BufferedReader reader, ManagedChannel ch) throws IOException {
        System.out.print("Enter plaintext: ");
        String plain = reader.readLine();
        if (plain == null || plain.isEmpty()) {
            System.out.println("Plaintext cannot be empty.");
            return;
        }
        if (!plain.chars().allMatch(Character::isLetter)) {
            System.out.println("Plaintext must only contain letters.");
            return;
        }
        printProcessedInput(plain, false);
        System.out.print("Enter key: ");
        String key = reader.readLine();
        if (key == null || key.isEmpty()) {
            System.out.println("Key cannot be empty.");
            return;
        }
        if (!key.chars().allMatch(Character::isLetter)) {
            System.out.println("Key must only contain letters.");
            return;
        }
        printProcessedInput(key, false);
        //readback request to user via console
        printProcessedInput(plain + separator + key, true);
        try {
            VigenereGrpc.VigenereBlockingStub stub = (ch != null)
                    ? VigenereGrpc.newBlockingStub(ch)
                    : vigenereStub;
            EncodeResponse resp = stub.encode(
                    EncodeRequest.newBuilder()
                            .setPlaintext(plain)
                            .setKey(key)
                            .build());
            if (!resp.getError()) {
                System.out.println("Ciphertext: " + resp.getCiphertext());
            } else {
                System.err.println("Encode error: " + resp.getErrorMsg());
            }
        } catch (StatusRuntimeException e) {
            System.err.println("Encode RPC failed: " + e.getStatus());
        }
    }

    /**
     * Invokes the decode operation on the Vigenère cipher service using the provided ciphertext and key.
     * The ciphertext and key are read as input from the user. The method communicates with the service
     * through the specified gRPC channel to decode the ciphertext into plaintext.
     *
     * @param reader the BufferedReader used to read user input for the ciphertext and key
     * @param ch     the ManagedChannel used for gRPC communication with the decoding service;
     *               if null, a default stub is used
     * @throws IOException if an I/O error occurs while reading input from the BufferedReader
     */
    public void callDecode(BufferedReader reader, ManagedChannel ch) throws IOException {
        System.out.print("Enter ciphertext: ");
        String cipher = reader.readLine();
        if (cipher == null || cipher.isEmpty()) {
            System.out.println("Ciphertext cannot be empty.");
            return;
        }
        if (!cipher.chars().allMatch(Character::isLetter)) {
            System.out.println("Ciphertext must only contain letters.");
            return;
        }
        printProcessedInput(cipher, false);
        System.out.print("Enter key: ");
        String key = reader.readLine();
        if (key == null || key.isEmpty()) {
            System.out.println("Key cannot be empty.");
            return;
        }
        if (!key.chars().allMatch(Character::isLetter)) {
            System.out.println("Key must only contain letters.");
            return;
        }
        printProcessedInput(key, false);

        //readback request to user via console
        printProcessedInput(cipher + separator + key, true);
        try {
            VigenereGrpc.VigenereBlockingStub stub = (ch != null)
                    ? VigenereGrpc.newBlockingStub(ch)
                    : vigenereStub;
            DecodeResponse resp = stub.decode(
                    DecodeRequest.newBuilder()
                            .setCiphertext(cipher)
                            .setKey(key)
                            .build());
            if (!resp.getError()) {
                System.out.println("Plaintext: " + resp.getPlaintext());
            } else {
                System.err.println("Decode error: " + resp.getErrorMsg());
            }
        } catch (StatusRuntimeException e) {
            System.err.println("Decode RPC failed: " + e.getStatus());
        }

    }

    /**
     * Retrieves and prints the Vigenere cipher operation history from a remote service.
     *
     * @param reader the BufferedReader used for potential user input (not utilized in this method)
     * @param ch     the ManagedChannel for communication with the remote service; if null, a default stub is used
     */
    public void callHistory(BufferedReader reader, ManagedChannel ch) {
        try {
            VigenereGrpc.VigenereBlockingStub stub = (ch != null)
                    ? VigenereGrpc.newBlockingStub(ch)
                    : vigenereStub;
            HistoryResponse resp = stub.history(HistoryRequest.getDefaultInstance());
            System.out.println("Vigenere history:");
            for (String op : resp.getOperationsList()) {
                System.out.println("• " + op);
            }
        } catch (StatusRuntimeException e) {
            System.err.println("History RPC failed: " + e.getStatus());
        }
    }

    /**
     * Handles a dynamic flow of service selection, invocation, and execution
     * for a client interacting with a registry of services.
     *
     * @param reader         a BufferedReader used to read user inputs, such as service selection
     * @param initialMessage an initial message to be passed to the invoked service for processing
     * @throws IOException if there is an error reading from the given BufferedReader or during service invocation
     */
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
            int sel = parseInt(reader.readLine(), -1).orElse(-1) - 1;
            if (sel < 0 || sel >= services.size()) {
                System.out.println("Invalid selection.");
                return;
            }
            String fullService = services.get(sel);

            SingleServerRes srvRes = registryStub.findServer(
                    FindServerReq.newBuilder().setServiceName(fullService).build());
            Connection conn = srvRes.getConnection();

            ManagedChannel dynCh = ManagedChannelBuilder.forAddress(conn.getUri(), conn.getPort())
                    .usePlaintext().build();
            try {
                invokeByMethod(fullService, initialMessage, reader, dynCh);
            } finally {
                shutdownChannel(dynCh);
            }
        } catch (StatusRuntimeException e) {
            System.err.println("Registry RPC failed: " + e.getStatus());
            logger.error("Registry RPC failed: {}", e.getStatus());
        } catch (IOException e) {
            System.err.println("I/O error: " + e.getMessage());
            logger.error("I/O error: {}", e.getMessage());
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            logger.error("Unexpected error: {}", e.getMessage());
        }
    }

    /**
     * Displays a static menu with predefined options and executes the corresponding service based on the user's choice.
     *
     * @param reader         a {@code BufferedReader} instance used to read the user's input
     * @param initialMessage the initial message to be processed or passed to certain service calls
     * @throws IOException if there is an error reading input from the {@code BufferedReader}
     */
    private void staticMenu(BufferedReader reader, String initialMessage) throws IOException {
        System.out.println("Select service to call:");
        System.out.println("1) Echo");
        System.out.println("2) Joke");
        System.out.println("3) Brew Coffee");
        System.out.println("4) Get Cup");
        System.out.println("5) Brew Status");
        System.out.println("6) Sort");
        System.out.println("7) Vigenere Encode");
        System.out.println("8) Vigenere Decode");
        System.out.println("9) Vigenere History");
        System.out.print("Enter choice: ");
        String line = reader.readLine();
        int choice = parseInt(line, -1).orElse(-1);
        if (choice < 1 || choice > 9) {
            System.out.println("Invalid choice: " + line);
            return;
        }
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
            case 7:
                callEncode(reader, null);
                break;
            case 8:
                callDecode(reader, null);
                break;
            case 9:
                callHistory(reader, null);
                break;
        }
    }

    /**
     * Invokes a gRPC method based on the provided full service name and performs
     * the corresponding action.
     *
     * @param fullService the full service name in the format "service/method"
     * @param msg         a message string used for the "parrot" method
     * @param reader      a BufferedReader object for reading user input in certain methods
     * @param ch          a ManagedChannel instance used to communicate with the gRPC service
     * @throws IOException if an I/O error occurs during method execution
     */
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
            case "encode":
                callEncode(reader, ch);
                break;
            case "decode":
                callDecode(reader, ch);
                break;
            case "history":
                callHistory(reader, ch);
                break;
            default:
                System.out.println("Unknown method: " + method);
        }
    }

    /**
     * Prompts the user for the number of jokes to request and invokes the method to fetch jokes.
     *
     * @param reader A BufferedReader for reading user input.
     * @param ch     A ManagedChannel for gRPC communication.
     * @throws IOException If an I/O error occurs during input reading.
     */
    private void callJoke(BufferedReader reader, ManagedChannel ch) throws IOException {
        System.out.print("How many jokes? ");
        int n = parseInt(reader.readLine(), 0).orElse(0);
        if (n < 0) {
            System.out.println("Invalid number of jokes: " + n);
            return;
        }
        printProcessedInput(String.valueOf(n), false);
        callJokeValue(n, ch);
    }

    /**
     * Attempts to parse the given string into an {@code Integer}. If the input is null
     * or cannot be parsed into a valid integer, an empty {@code Optional} is returned.
     * Logs errors for invalid or null inputs.
     *
     * @param s the input string to be parsed; may be null or contain non-numeric characters
     * @return an {@code Optional} containing the parsed integer if successful, or an empty {@code Optional} if parsing fails
     */
    private Optional<Integer> parseInt(String s, int defaultValue) {
        if (s == null) {
            logger.error("Input is null");
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(s.trim()));
        } catch (NumberFormatException e) {
            logger.error("Invalid input: {}", s);
            return defaultValue == 0 ? Optional.empty() : Optional.of(defaultValue);
        }
    }

    /**
     * Reads a list of integers and a sorting algorithm choice from the user, then performs a sorting operation.
     *
     * @param reader The {@link BufferedReader} used to read input from the user.
     * @param ch     The {@link ManagedChannel} used for communication with the remote service.
     * @throws IOException If an I/O error occurs while reading user input.
     */
    // Updated callSort method that handles nulls and uses Optional from parseInt
    private void callSort(BufferedReader reader, ManagedChannel ch) throws IOException {
        System.out.print("Enter numbers (comma-separated): ");
        String numbersLine = reader.readLine();
        if (numbersLine == null) {
            System.out.println("No input provided for numbers.");
            return;
        }
        List<Integer> data = Arrays.stream(numbersLine.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> parseInt(s, 0))  // Use parseInt to handle nulls
                .flatMap(Optional::stream)  // Java 9+: converts Optional<Integer> to Stream<Integer>
                .collect(Collectors.toList());

        printProcessedInput(data.toString(), false);

        System.out.print("Choose algo (0=MERGE,1=QUICK,2=INTERN): ");
        String algoLine = reader.readLine();
        printProcessedInput(algoLine, false);
        if (algoLine == null) {
            System.out.println("No input provided for algorithm selection.");
            return;
        }
        Optional<Integer> optionalIdx = parseInt(algoLine, 0);
        if (optionalIdx.isEmpty()) {
            System.out.println("Invalid algorithm selection input.");
            return;
        }
        int idx = optionalIdx.get();
        Algo algo = Algo.forNumber(idx);
        if (algo == null) {
            System.out.println("Invalid algorithm selection: " + idx);
            return;
        }
        printProcessedInput(algo.toString() + separator + data, true);
        callSortList(data, algo, ch);
    }

    private void printProcessedInput(String input, boolean isCombined) {
        String cleanedInput = input.replaceAll(INVALID_CHAR_REGEX, "");
        String messagePrefix = isCombined ? "Your request is: " : "Your input is: ";
        String output = isCombined ? formatCombinedInput(cleanedInput) : cleanedInput;
        System.out.println(messagePrefix + output);
    }

    private String formatCombinedInput(String input) {
        return Arrays.stream(input.split(String.valueOf(separator)))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .collect(Collectors.joining(" - "));
    }
}


