package edu.yu.cs.com3800.stage5;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ClientForDemo {
    static String validSrc = "public class SimpleClass {\n" +
            "    // Constructor that takes no arguments\n" +
            "    public SimpleClass() {\n" +
            "    }\n" +
            "\n" +
            "    // Public method named run, takes no arguments, and returns a String\n" +
            "    public String run() {\n" +
            "        return \"Hello, World!\";\n" +
            "    }\n" +
            "}";

    public static void main(String[] args) throws URISyntaxException {
        int httpServerPort = Integer.parseInt(args[0]);
        int round = Integer.parseInt(args[1]);
        HttpClient client = HttpClient.newHttpClient();
        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        URI uri = new URI("http://localhost:" + httpServerPort + "/compileandrun");
        if(round == 0){
            for (int i = 0; i < 9; i++) {
                int j = i;
                Runnable task = () -> {
                    try {
                        String copy = validSrc;
                        copy = copy.replace("World", "World " + j);
                        HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create(uri.toString()))
                                .header("Content-Type", "text/x-java-source")
                                .POST(HttpRequest.BodyPublishers.ofString(copy))
                                .build();

                        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        System.out.println(response.body());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                };
                executor.submit(task);
            }
        } else if (round == 1) {
            for (int i = 0; i < 9; i++) {
                int j = i;
                Runnable task = () -> {
                    try {
                        String copy = validSrc;
                        copy = copy.replace("World", "World! " + j);
                        HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create(uri.toString()))
                                .header("Content-Type", "text/x-java-source")
                                .POST(HttpRequest.BodyPublishers.ofString(copy))
                                .build();

                        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        System.out.println(response.body());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                };
                executor.submit(task);
            }
        }else{
            try {
                String copy = validSrc;
                copy = copy.replace("World", "World! " + "I wonder if this will work");
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(uri.toString()))
                        .header("Content-Type", "text/x-java-source")
                        .POST(HttpRequest.BodyPublishers.ofString(copy))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                System.out.println(response.body());
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
