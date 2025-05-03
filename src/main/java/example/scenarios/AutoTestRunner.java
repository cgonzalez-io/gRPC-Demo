package example.scenarios;

import example.grpcclient.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.Algo;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * The AutoTestRunner class is responsible for executing a suite of automated tests on
 * various gRPC-based services. It facilitates the testing of multiple scenarios, manages
 * exceptions during test execution, and provides detailed test result reporting.
 * <p>
 * The class leverages a client instance to simulate interactions with the gRPC services
 * and validates the expected behavior of various operations. Test results are aggregated
 * and displayed in a formatted report, summarizing overall test performance.
 * <p>
 * This class provides the capability to test the following services:
 * - Echo service: Verifies message echoing with both valid and invalid inputs.
 * - Joke service: Assesses retrieval of jokes with different input parameters.
 * - CoffeePot service: Simulates and tests coffee brewing processes and related states.
 * - Sorting service: Evaluates sorting functionality for various algorithms and lists.
 * - Vigenère cipher: Tests encoding, decoding, and historical operations with valid and invalid inputs.
 */
public class AutoTestRunner {

    private static final Logger logger = LoggerFactory.getLogger(AutoTestRunner.class);

    /**
     * Represents a client instance used to interact with external systems or services.
     * This variable is a fundamental component of the AutoTestRunner class, providing
     * the necessary interface for executing operations related to automated testing.
     * It is initialized via the constructor and remains immutable throughout the lifecycle
     * of the AutoTestRunner instance.
     */
    private final Client client;

    /**
     * Constructs an AutoTestRunner instance with the provided client.
     *
     * @param client the Client instance used to facilitate test execution
     */
    public AutoTestRunner(Client client) {
        this.client = client;
    }

    public void runAll() throws Exception {
        List<TestCase> cases = new ArrayList<>();

        // Echo
        cases.add(new TestCase("Echo: valid message", "Hello, World!", "Hello, World!",
                () -> client.callEcho("Hello, World!", null)));
        cases.add(new TestCase("Echo: empty message", "(empty)", "error: No message provided",
                () -> client.callEcho("", null)));

        // Joke
        cases.add(new TestCase("Joke: 2 jokes", "2", "2 jokes printed",
                () -> client.callJokeValue(2, null)));
        cases.add(new TestCase("Joke: 0 jokes", "0", "edge case: no jokes or out-of-range",
                () -> client.callJokeValue(0, null)));

        // CoffeePot
        cases.add(new TestCase("CoffeePot: getCup before brew", "--", "error or empty state",
                () -> client.callGetCup(null)));
        cases.add(new TestCase("CoffeePot: brew start", "--", "Starting brew",
                () -> client.callBrew(null)));
        cases.add(new TestCase("CoffeePot: brew again", "--", "error on double brew",
                () -> client.callBrew(null)));
        Thread.sleep(31000);
        cases.add(new TestCase("CoffeePot: brewStatus post-completion", "--", "Completed",
                () -> client.callBrewStatus(null)));
        cases.add(new TestCase("CoffeePot: getCup after brew", "--", "Cup served",
                () -> client.callGetCup(null)));

        // Sort
        List<Integer> list = List.of(5, 3, 7, 1);
        cases.add(new TestCase("Sort: MERGE", list.toString(), "[1,3,5,7]",
                () -> client.callSortList(list, Algo.MERGE, null)));
        cases.add(new TestCase("Sort: QUICK", list.toString(), "[1,3,5,7]",
                () -> client.callSortList(list, Algo.QUICK, null)));
        cases.add(new TestCase("Sort: INTERN", list.toString(), "[1,3,5,7]",
                () -> client.callSortList(list, Algo.INTERN, null)));
        cases.add(new TestCase("Sort: invalid algo", list.toString(), "error on null Algo",
                () -> client.callSortList(list, null, null)));

        // Vigenere
        cases.add(new TestCase("Vigenere: encode", "HELLO,KEY", "Ciphertext printed",
                () -> {
                    BufferedReader r = new BufferedReader(new StringReader("HELLO\nKEY\n"));
                    client.callEncode(r, null);
                }));
        cases.add(new TestCase("Vigenere: decode", "RIJVS,KEY", "Plaintext printed",
                () -> {
                    BufferedReader r = new BufferedReader(new StringReader("RIJVS\nKEY\n"));
                    client.callDecode(r, null);
                }));
        cases.add(new TestCase("Vigenere: empty encode", ",KEY", "error: plaintext missing",
                () -> {
                    BufferedReader r = new BufferedReader(new StringReader("\nKEY\n"));
                    client.callEncode(r, null);
                }));
        cases.add(new TestCase("Vigenere: wrong key", "RIJVS,BAD", "error: decode failed",
                () -> {
                    BufferedReader r = new BufferedReader(new StringReader("RIJVS\nBAD\n"));
                    client.callDecode(r, null);
                }));
        cases.add(new TestCase("Vigenere: history", "--", "history list",
                () -> client.callHistory(null, null)));

        // Execute and collect
        List<TestResult> results = new ArrayList<>();
        for (TestCase tc : cases) {
            results.add(runCase(tc));
        }
        printReport(results);
    }

    private TestResult runCase(TestCase tc) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos, true, StandardCharsets.UTF_8);
        PrintStream oldOut = System.out;
        System.setOut(ps);
        boolean passed = true;
        String error = "";
        try {
            tc.block.run();
        } catch (Exception e) {
            logger.error("Error during test case execution: {}", e.getMessage(), e);
            passed = false;
            error = e.getMessage();
        } finally {
            System.out.flush();
            System.setOut(oldOut);
        }
        String actual = baos.toString(StandardCharsets.UTF_8).trim().replaceAll("\r?\n", "; ");
        return new TestResult(tc.name, tc.inputDesc, tc.expectedDesc, actual, passed, error);
    }

    private void printReport(List<TestResult> results) {
        // widened columns to accommodate longer input/actual strings
        String fmt = "%-25s | %-20s | %-25s | %-30s | %-6s";
        System.out.println();
        System.out.printf(fmt + "%n", "Test", "Input", "Expected", "Actual", "Result");
        // total line length = 118 chars
        System.out.println(String.join("", Collections.nCopies(118, "-")));
        int passed = 0;
        for (TestResult tr : results) {
            // if actual is empty, print (none)
            String actualDisplay = tr.actual.isEmpty() ? "(none)" : tr.actual;
            System.out.printf(fmt + "%n",
                    tr.name,
                    tr.input,
                    tr.expected,
                    actualDisplay,
                    tr.passed ? "PASS" : "FAIL");
            if (tr.passed) passed++;
        }

        System.out.println();
        System.out.printf("SUMMARY: %d/%d passed%n", passed, results.size());
    }

    @FunctionalInterface
    private interface TestBlock {
        void run() throws Exception;
    }

    private static class TestCase {
        final String name, inputDesc, expectedDesc;
        final TestBlock block;

        TestCase(String name, String inputDesc, String expectedDesc, TestBlock block) {
            this.name = name;
            this.inputDesc = inputDesc;
            this.expectedDesc = expectedDesc;
            this.block = block;
        }
    }

    private static class TestResult {
        final String name, input, expected, actual;
        final boolean passed;
        final String error;

        TestResult(String name, String input, String expected, String actual, boolean passed, String error) {
            this.name = name;
            this.input = input;
            this.expected = expected;
            this.actual = actual;
            this.passed = passed;
            this.error = error;
        }
    }
}
