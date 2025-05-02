package example.services;

import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;
import service.*;

public class CoffeePotImpl extends CoffeePotGrpc.CoffeePotImplBase {

    private static final int MAX_CUPS = 5;
    private static final long BREW_TIME_MS = 30_000;

    private boolean brewing = false;
    private long brewStartTime = 0; // epoch millis
    private int cupCount = 0;

    @Override
    public synchronized void brew(Empty request, StreamObserver<BrewResponse> responseObserver) {
        if (brewing) {
            responseObserver.onNext(BrewResponse.newBuilder()
                    .setIsSuccess(false)
                    .setError("❌ Already brewing coffee!")
                    .build());
            responseObserver.onCompleted();
            return;
        }

        if (cupCount > 0) {
            responseObserver.onNext(BrewResponse.newBuilder()
                    .setIsSuccess(false)
                    .setError("❌ Pot still has coffee (" + cupCount + " cups left). Brew denied.")
                    .build());
            responseObserver.onCompleted();
            return;
        }

        brewing = true;
        brewStartTime = System.currentTimeMillis();

        // Async completion of brewing
        new Thread(() -> {
            try {
                Thread.sleep(BREW_TIME_MS);
                synchronized (this) {
                    brewing = false;
                    cupCount = MAX_CUPS;
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();

        responseObserver.onNext(BrewResponse.newBuilder()
                .setIsSuccess(true)
                .setMessage("✅ Brewing started. Will be ready in 30 seconds.")
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public synchronized void getCup(Empty request, StreamObserver<GetCupResponse> responseObserver) {
        if (brewing) {
            long timeLeft = BREW_TIME_MS - (System.currentTimeMillis() - brewStartTime);
            int seconds = (int) (timeLeft / 1000);
            int minutes = seconds / 60;
            seconds = seconds % 60;

            responseObserver.onNext(GetCupResponse.newBuilder()
                    .setIsSuccess(false)
                    .setError("⏳ Brewing in progress. " + minutes + "m " + seconds + "s left.")
                    .build());
            responseObserver.onCompleted();
            return;
        }

        if (cupCount <= 0) {
            responseObserver.onNext(GetCupResponse.newBuilder()
                    .setIsSuccess(false)
                    .setError("☕ No coffee left. Please brew again.")
                    .build());
        } else {
            cupCount--;
            responseObserver.onNext(GetCupResponse.newBuilder()
                    .setIsSuccess(true)
                    .setMessage("☕ Enjoy your cup! " + cupCount + " cups remaining.")
                    .build());
        }
        responseObserver.onCompleted();
    }

    @Override
    public synchronized void brewStatus(Empty request,
                                        StreamObserver<BrewStatusResponse> responseObserver) {
        // 1) Builder for the nested status message (top-level class BrewStatus)
        BrewStatus.Builder statusBuilder = BrewStatus.newBuilder();

        if (brewing) {
            long timeLeft = BREW_TIME_MS - (System.currentTimeMillis() - brewStartTime);
            if (timeLeft < 0) timeLeft = 0;
            int totalSeconds = (int) (timeLeft / 1000);
            int minutes = totalSeconds / 60;
            int seconds = totalSeconds % 60;

            statusBuilder
                    .setMinutes(minutes)
                    .setSeconds(seconds)
                    .setMessage("⏳ Brewing in progress. Please wait.");
        } else if (cupCount > 0) {
            statusBuilder
                    .setMinutes(0)
                    .setSeconds(0)
                    .setMessage("✅ Pot is brewed. " + cupCount + " cups available.");
        } else {
            statusBuilder
                    .setMinutes(0)
                    .setSeconds(0)
                    .setMessage("❄️ Idle. No coffee. Please start brewing.");
        }

        // 2) Build the outer response, setting the nested status
        BrewStatusResponse response = BrewStatusResponse.newBuilder()
                .setStatus(statusBuilder.build())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

}
