package example.services;

import com.google.protobuf.Empty;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import service.*;

/**
 * Implementation of a gRPC service for managing a coffee pot. This class extends
 * CoffeePotGrpc.CoffeePotImplBase and provides concrete implementations for brewing
 * coffee, retrieving a cup of coffee, and checking the current coffee pot status.
 * <p>
 * This class maintains the state of the coffee pot, including whether it's currently
 * brewing, the available number of cups, and the time remaining for the brewing process.
 * It is designed to handle concurrent requests in a synchronized manner to ensure thread-safety.
 */
public class CoffeePotImpl extends CoffeePotGrpc.CoffeePotImplBase {

    private static final int MAX_CUPS = 5;
    private static final long BREW_TIME_MS = 30_000;
    private static final Logger logger = LoggerFactory.getLogger(CoffeePotImpl.class);
    private boolean brewing = false;
    private long brewStartTime = 0;
    private int cupCount = 0;

    /**
     * Starts the coffee brewing process. If brewing is already in progress or coffee is available in the pot,
     * the brewing process will not be initiated, and an appropriate response will be sent.
     *
     * @param request          the incoming request, typically empty, indicating the client wants to start brewing
     * @param responseObserver the observer used to send responses back to the client
     */
    @Override
    public synchronized void brew(Empty request, StreamObserver<BrewResponse> responseObserver) {
        try {
            if (brewing) {
                responseObserver.onNext(failedResp("Already brewing coffee!"));
                return;
            }

            if (cupCount > 0) {
                responseObserver.onNext(failedResp("Pot still has coffee (" + cupCount + " cups left)."));
                return;
            }
            // start brewing
            brewing = true;
            brewStartTime = System.currentTimeMillis();
            new Thread(() -> {
                try {
                    Thread.sleep(BREW_TIME_MS);
                    synchronized (CoffeePotImpl.this) {
                        brewing = false;
                        cupCount = MAX_CUPS;
                    }
                } catch (InterruptedException ie) {
                    // restore state on interruption
                    synchronized (CoffeePotImpl.this) {
                        brewing = false;
                        cupCount = 0;
                    }
                    // log error
                    System.err.println("Brewing thread interrupted: " + ie.getMessage());
                }
            }).start();

            responseObserver.onNext(BrewResponse.newBuilder()
                    .setIsSuccess(true)
                    .setMessage("Brewing started. Ready in 30s.")
                    .build());
        } catch (Exception e) {
            responseObserver.onNext(
                    BrewResponse.newBuilder()
                            .setIsSuccess(false)
                            .setError("Internal error: " + e.getMessage())
                            .build());
        } finally {
            responseObserver.onCompleted();
        }
    }

    /**
     * Handles a request to retrieve a cup of coffee. The method checks the brewing status
     * and the available coffee count to determine whether a cup can be served.
     *
     * @param request          An empty request object indicating a request to get a cup of coffee.
     * @param responseObserver A StreamObserver used to send the response back to the client.
     *                         The response includes success status, an error message if any,
     *                         or the remaining coffee count on success.
     */
    @Override
    public synchronized void getCup(Empty request, StreamObserver<GetCupResponse> responseObserver) {
        try {
            if (brewing) {
                long elapsed = System.currentTimeMillis() - brewStartTime;
                long remaining = Math.max(0, BREW_TIME_MS - elapsed);
                int secs = (int) (remaining / 1000);
                int mins = secs / 60;
                secs %= 60;
                responseObserver.onNext(GetCupResponse.newBuilder()
                        .setIsSuccess(false)
                        .setError("Brewing in progress: " + mins + "m" + secs + "s left.")
                        .build());
                return;
            }

            if (cupCount <= 0) {
                responseObserver.onNext(failedGetCup("No coffee left."));
            } else {
                cupCount--;
                responseObserver.onNext(GetCupResponse.newBuilder()
                        .setIsSuccess(true)
                        .setMessage("Enjoy your cup! (" + cupCount + " left)")
                        .build());
            }
        } catch (Exception e) {
            responseObserver.onNext(
                    GetCupResponse.newBuilder()
                            .setIsSuccess(false)
                            .setError("Internal error: " + e.getMessage())
                            .build());
        } finally {
            responseObserver.onCompleted();
        }
    }

    /**
     * Provides the current status of the coffee brewing process, including time remaining for brewing,
     * number of available cups, and the current state message.
     *
     * @param request          an empty request object, as no input parameters are required for this method
     * @param responseObserver a stream observer for sending back the BrewStatusResponse containing the status of the coffee pot
     */
    @Override
    public synchronized void brewStatus(Empty request,
                                        StreamObserver<BrewStatusResponse> responseObserver) {
        try {
            BrewStatus.Builder status = BrewStatus.newBuilder();
            if (brewing) {
                long elapsed = System.currentTimeMillis() - brewStartTime;
                long remaining = Math.max(0, BREW_TIME_MS - elapsed);
                int secs = (int) (remaining / 1000);
                int mins = secs / 60;
                secs %= 60;
                status.setMinutes(mins)
                        .setSeconds(secs)
                        .setMessage("Brewing in progress...");
            } else if (cupCount > 0) {
                status.setMinutes(0)
                        .setSeconds(0)
                        .setMessage("Ready: " + cupCount + " cups available.");
            } else {
                status.setMinutes(0)
                        .setSeconds(0)
                        .setMessage("Idle: no coffee.");
            }
            responseObserver.onNext(
                    BrewStatusResponse.newBuilder()
                            .setStatus(status)
                            .build());
        } catch (Exception e) {
            // gRPC status error
            responseObserver.onError(new StatusRuntimeException(
                    Status.INTERNAL.withDescription(e.getMessage())));
            return;
        }
        responseObserver.onCompleted();
    }

    /**
     * Creates a failed BrewResponse with a specified error message.
     *
     * @param err the error message to be included in the response
     * @return a BrewResponse object indicating failure and containing the provided error message
     */
    // Helpers
    private BrewResponse failedResp(String err) {
        return BrewResponse.newBuilder()
                .setIsSuccess(false)
                .setError(err)
                .build();
    }

    /**
     * Constructs a failed GetCupResponse object with the provided error message.
     *
     * @param err the error message describing the failure
     * @return a GetCupResponse object with isSuccess set to false and the error message set
     */
    private GetCupResponse failedGetCup(String err) {
        return GetCupResponse.newBuilder()
                .setIsSuccess(false)
                .setError(err)
                .build();
    }
}