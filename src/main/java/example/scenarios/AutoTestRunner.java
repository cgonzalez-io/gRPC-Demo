package example.scenarios;

import example.grpcclient.Client;
import service.Algo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.List;

/**
 * Contains all hard-coded test scenarios for the Client's auto-run mode.
 */
public class AutoTestRunner {

    private final Client client;

    public AutoTestRunner(Client client) {
        this.client = client;
    }

    /**
     * Execute all predefined test scenarios (both successful and error cases).
     */
    public void runAll() throws IOException {
        System.out.println("=== AUTO TESTS START ===");

        // Echo tests
        System.out.println("[Echo] Valid");
        client.callEcho("Hello, World!", null);
        System.out.println("[Echo] Empty -> expecting error");
        client.callEcho("", null);

        // Joke tests
        System.out.println("[Joke] 2 jokes");
        client.callJokeValue(2, null);
        System.out.println("[Joke] 0 jokes -> expecting empty list or error");
        client.callJokeValue(0, null);

        // CoffeePot tests
        System.out.println("[CoffeePot] getCup before brew -> expecting error");
        client.callGetCup(null);
        System.out.println("[CoffeePot] start brew");
        client.callBrew(null);
        System.out.println("[CoffeePot] start brew again -> expecting error");
        client.callBrew(null);
        sleepMs(31000);
        System.out.println("[CoffeePot] brewStatus after brew completion");
        client.callBrewStatus(null);
        System.out.println("[CoffeePot] getCup after brew");
        client.callGetCup(null);

        // Sort tests
        List<Integer> list = Arrays.asList(5, 3, 7, 1);
        System.out.println("[Sort] MERGE on " + list);
        client.callSortList(list, Algo.MERGE, null);
        System.out.println("[Sort] QUICK on " + list);
        client.callSortList(list, Algo.QUICK, null);
        System.out.println("[Sort] INTERN on " + list);
        client.callSortList(list, Algo.INTERN, null);
        System.out.println("[Sort] invalid algo -> expecting error");
        client.callSortList(list, Algo.forNumber(99), null);

        // --- Vigenère auto-tests ---
        System.out.println("-- Vigenère: encode HELLO/KEY --");
        BufferedReader enc1 = new BufferedReader(new StringReader("HELLO\nKEY\n"));
        client.callEncode(enc1, null);

        System.out.println("-- Vigenère: decode RIJVS/KEY --");
        BufferedReader dec1 = new BufferedReader(new StringReader("RIJVS\nKEY\n"));
        client.callDecode(dec1, null);

        System.out.println("-- Vigenère: encode empty/plain error --");
        BufferedReader enc2 = new BufferedReader(new StringReader("\nKEY\n"));
        client.callEncode(enc2, null);

        System.out.println("-- Vigenère: decode wrong key error --");
        BufferedReader dec2 = new BufferedReader(new StringReader("RIJVS\nBAD\n"));
        client.callDecode(dec2, null);

        System.out.println("-- Vigenère: history --");
        // history() doesn’t need a reader, so just pass a dummy one or null:
        client.callHistory(null, null);

        System.out.println("=== AUTO TESTS END ===");
    }

    private void sleepMs(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
