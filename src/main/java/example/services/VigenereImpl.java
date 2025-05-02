package example.services;

import io.grpc.stub.StreamObserver;
import service.*;


/**
 * The VigenereImpl class extends VigenereGrpc.VigenereImplBase and implements
 * the RPC methods for encoding and decoding text using the Vigenère cipher.
 * It provides functionality to encrypt and decrypt messages based on a given key,
 * while ensuring input validation and error handling.
 */
public class VigenereImpl extends VigenereGrpc.VigenereImplBase {

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
}
