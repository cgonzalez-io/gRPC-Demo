package example.services;

import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.*;

import java.util.ArrayList;
import java.util.List;


/**
 * The VigenereImpl class extends VigenereGrpc.VigenereImplBase and implements
 * the RPC methods for encoding and decoding text using the Vigenère cipher.
 * It provides functionality to encrypt and decrypt messages based on a given key,
 * while ensuring input validation and error handling.
 */
public class VigenereImpl extends VigenereGrpc.VigenereImplBase {

    private static final Logger logger = LoggerFactory.getLogger(VigenereImpl.class);
    private final List<String> history = new ArrayList<>();

    /**
     * Encodes a given plaintext string with the Vigenère cipher using the provided key.
     * The method validates the inputs and encodes the plaintext based on the key.
     * Errors during validation or processing are returned in the response.
     *
     * @param req the request object containing the plaintext and key for the encoding operation
     * @param obs the observer to send the encoding response back to the client
     */
    @Override
    public void encode(EncodeRequest req, StreamObserver<EncodeResponse> obs) {
        try {
            String plain = req.getPlaintext();
            String key = req.getKey();
            // Validate inputs
            if (plain == null || plain.isEmpty()) {
                throw new IllegalArgumentException("Plaintext cannot be empty.");
            }
            if (key == null || key.isEmpty()) {
                throw new IllegalArgumentException("Key cannot be empty.");
            }
            if (!key.chars().allMatch(Character::isLetter)) {
                throw new IllegalArgumentException("Key must only contain letters.");
            }

            StringBuilder cipher = new StringBuilder();
            int m = key.length();
            for (int i = 0, j = 0; i < plain.length(); i++) {
                char c = plain.charAt(i);
                if (Character.isLetter(c)) {
                    char base = Character.isUpperCase(c) ? 'A' : 'a';
                    int pi = c - base;
                    char k = key.charAt(j % m);
                    char kb = Character.isUpperCase(k) ? 'A' : 'a';
                    int ki = k - kb;
                    char enc = (char) ((pi + ki) % 26 + base);
                    cipher.append(enc);
                    j++;
                } else {
                    cipher.append(c);
                }
            }
            history.add("E:" + req.getPlaintext() + "->" + cipher);
            obs.onNext(EncodeResponse.newBuilder()
                    .setCiphertext(cipher.toString())
                    .setError(false)
                    .build());
        } catch (IllegalArgumentException iae) {
            obs.onNext(EncodeResponse.newBuilder()
                    .setError(true)
                    .setErrorMsg(iae.getMessage())
                    .build());
        } catch (Exception e) {
            obs.onNext(EncodeResponse.newBuilder()
                    .setError(true)
                    .setErrorMsg("Internal error during encoding: " + e.getMessage())
                    .build());
        } finally {
            obs.onCompleted();
        }
    }

    /**
     * Decodes a given ciphertext using the Vigenère cipher algorithm and a specified key.
     * Produces a plaintext result or provides error information if decoding is unsuccessful.
     *
     * @param req the DecodeRequest containing the ciphertext to decode and the key to use for decoding
     * @param obs the StreamObserver through which the method responds with a DecodeResponse
     */
    @Override
    public void decode(DecodeRequest req, StreamObserver<DecodeResponse> obs) {
        try {
            String cipher = req.getCiphertext();
            String key = req.getKey();
            // Validate inputs
            if (cipher == null || cipher.isEmpty()) {
                throw new IllegalArgumentException("Ciphertext cannot be empty.");
            }
            if (key == null || key.isEmpty()) {
                throw new IllegalArgumentException("Key cannot be empty.");
            }
            if (!key.chars().allMatch(Character::isLetter)) {
                throw new IllegalArgumentException("Key must only contain letters.");
            }

            StringBuilder plain = new StringBuilder();
            int m = key.length();
            for (int i = 0, j = 0; i < cipher.length(); i++) {
                char c = cipher.charAt(i);
                if (Character.isLetter(c)) {
                    char base = Character.isUpperCase(c) ? 'A' : 'a';
                    int ci = c - base;
                    char k = key.charAt(j % m);
                    char kb = Character.isUpperCase(k) ? 'A' : 'a';
                    int ki = k - kb;
                    char dec = (char) ((ci - ki + 26) % 26 + base);
                    plain.append(dec);
                    j++;
                } else {
                    plain.append(c);
                }
            }
            history.add("D:" + req.getCiphertext() + "->" + plain);
            obs.onNext(DecodeResponse.newBuilder()
                    .setPlaintext(plain.toString())
                    .setError(false)
                    .build());
        } catch (IllegalArgumentException iae) {
            obs.onNext(DecodeResponse.newBuilder()
                    .setError(true)
                    .setErrorMsg(iae.getMessage())
                    .build());
        } catch (Exception e) {
            obs.onNext(DecodeResponse.newBuilder()
                    .setError(true)
                    .setErrorMsg("Internal error during decoding: " + e.getMessage())
                    .build());
        } finally {
            obs.onCompleted();
        }
    }

    /**
     * Retrieves the history of completed operations and sends it back to the client.
     *
     * @param req the HistoryRequest object sent by the client to request the operation history
     * @param obs the StreamObserver used to send the HistoryResponse containing the list of operations back to the client
     */
    @Override
    public void history(HistoryRequest req, StreamObserver<HistoryResponse> obs) {
        HistoryResponse resp = HistoryResponse.newBuilder()
                .addAllOperations(history)
                .build();
        obs.onNext(resp);
        obs.onCompleted();
    }
}
