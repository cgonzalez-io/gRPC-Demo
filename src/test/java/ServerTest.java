import com.google.protobuf.Empty;
import example.grpcclient.Client;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.junit.Test;
import static org.junit.Assert.*;
import org.json.JSONObject;
import service.*;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;


public class ServerTest {

    ManagedChannel channel;
    private EchoGrpc.EchoBlockingStub blockingStub;
    private JokeGrpc.JokeBlockingStub blockingStub2;


    @org.junit.Before
    public void setUp() throws Exception {
        // assuming default port and localhost for our testing, make sure Node runs on this port
        channel = ManagedChannelBuilder.forTarget("localhost:8000").usePlaintext().build();

        blockingStub = EchoGrpc.newBlockingStub(channel);
        blockingStub2 = JokeGrpc.newBlockingStub(channel);
    }

    @org.junit.After
    public void close() throws Exception {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);

    }


    @Test
    public void parrot() {
        // success case
        ClientRequest request = ClientRequest.newBuilder().setMessage("test").build();
        ServerResponse response = blockingStub.parrot(request);
        assertTrue(response.getIsSuccess());
        assertEquals("test", response.getMessage());

        // error cases
        request = ClientRequest.newBuilder().build();
        response = blockingStub.parrot(request);
        assertFalse(response.getIsSuccess());
        assertEquals("No message provided", response.getError());

        request = ClientRequest.newBuilder().setMessage("").build();
        response = blockingStub.parrot(request);
        assertFalse(response.getIsSuccess());
        assertEquals("No message provided", response.getError());
    }

    // For this test the server needs to be started fresh AND the list of jokes needs to be the initial list
    @Test
    public void joke() {
        // getting first joke
        JokeReq request = JokeReq.newBuilder().setNumber(1).build();
        JokeRes response = blockingStub2.getJoke(request);
        assertEquals(1, response.getJokeCount());
        assertEquals("Did you hear the rumor about butter? Well, I'm not going to spread it!", response.getJoke(0));

        // getting next 2 jokes
        request = JokeReq.newBuilder().setNumber(2).build();
        response = blockingStub2.getJoke(request);
        assertEquals(2, response.getJokeCount());
        assertEquals("What do you call someone with no body and no nose? Nobody knows.", response.getJoke(0));
        assertEquals("I don't trust stairs. They're always up to something.", response.getJoke(1));

        // getting 2 more but only one more on server
        request = JokeReq.newBuilder().setNumber(2).build();
        response = blockingStub2.getJoke(request);
        assertEquals(2, response.getJokeCount());
        assertEquals("How do you get a squirrel to like you? Act like a nut.", response.getJoke(0));
        assertEquals("I am out of jokes...", response.getJoke(1));

        // trying to get more jokes but out of jokes
        request = JokeReq.newBuilder().setNumber(2).build();
        response = blockingStub2.getJoke(request);
        assertEquals(1, response.getJokeCount());
        assertEquals("I am out of jokes...", response.getJoke(0));

        // trying to add joke without joke field
        JokeSetReq req2 = JokeSetReq.newBuilder().build();
        JokeSetRes res2 = blockingStub2.setJoke(req2);
        assertFalse(res2.getOk());

        // trying to add empty joke
        req2 = JokeSetReq.newBuilder().setJoke("").build();
        res2 = blockingStub2.setJoke(req2);
        assertFalse(res2.getOk());

        // adding a new joke (well word)
        req2 = JokeSetReq.newBuilder().setJoke("whoop").build();
        res2 = blockingStub2.setJoke(req2);
        assertTrue(res2.getOk());

        // should have the new "joke" now and return it
        request = JokeReq.newBuilder().setNumber(1).build();
        response = blockingStub2.getJoke(request);
        assertEquals(1, response.getJokeCount());
        assertEquals("whoop", response.getJoke(0));
    }

}