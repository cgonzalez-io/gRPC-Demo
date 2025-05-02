package example.services;

import com.google.protobuf.Empty;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import service.*;

public class CoffeePotImpl extends CoffeePotGrpc.CoffeePotImplBase {

    private static final int MAX_CUPS = 5;
    private static final long BREW_TIME_MS = 30_000;

    private boolean brewing = false;
    private long brewStartTime = 0;
    private int cupCount = 0;

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

    // Helpers
    private BrewResponse failedResp(String err) {
        return BrewResponse.newBuilder()
                .setIsSuccess(false)
                .setError(err)
                .build();
    }

    private GetCupResponse failedGetCup(String err) {
        return GetCupResponse.newBuilder()
                .setIsSuccess(false)
                .setError(err)
                .build();
    }
}