package utils;

import com.sun.net.httpserver.HttpExchange;
import model.Response;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.stream.Collectors;

/**
 * HttpUtils class has utility methods to handle the HTTP requests.
 */
public class HttpUtils {
    /**
     * Method to get the request body from the HttpExchange object.
     * @param exchange - Incoming request
     * @return - Request body as a string
     */
    public static String getRequestBody(HttpExchange exchange){
        String requestBody;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody()))) {
            requestBody = reader.lines().collect(Collectors.joining());
        }catch (IOException e){
            System.out.println("Error parsing request body"+e);
            return null;
        }
        return requestBody;
    }

    /**
     * Method to make a GET request to the given server URL.
     * @param serverUrl - URL to make the GET request
     * @return - Response object with the status code and response message
     * @throws IOException
     */
    public static Response makeGetRequest(String serverUrl) throws IOException {
        URL url = new URL(serverUrl);

        // Open a connection on the URL
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Connection", "keep-alive"); // keep connection alive

         // Get status code
        int statusCode = connection.getResponseCode();
        InputStream inputStream;
        if (statusCode >= 400) {
            inputStream = connection.getErrorStream(); // Get error stream for 4xx and 5xx responses
        } else {
            inputStream = connection.getInputStream();
        }
        BufferedReader in = new BufferedReader(new InputStreamReader(inputStream));
       
        StringBuilder response = new StringBuilder();
        String inputLine;
        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }

        // close resources
        inputStream.close();
        in.close();

        // disconnect the connection
        connection.disconnect();

        return new Response(statusCode,response.toString());
    }

    /**
     * Method to make a POST request to the given server URL.
     * @param serverUrl - URL to make the POST request
     * @param requestBody - Request body to be sent
     * @return - Response object with the status code and response message
     * @throws IOException
     */
    public static Response makePostRequest(String serverUrl, String requestBody) throws IOException {
        URL url = new URL(serverUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Connection", "keep-alive"); // keep connection alive

        connection.setDoOutput(true);

        // Write the data to the connection
        OutputStream os = connection.getOutputStream();
        os.write(requestBody.getBytes());
        os.flush();
        os.close();

        // Get the response
        int statusCode = connection.getResponseCode();
        InputStream inputStream;
        if (statusCode >= 400) {
            inputStream = connection.getErrorStream(); // Get error stream for 4xx and 5xx responses
        } else {
            inputStream = connection.getInputStream();
        }
        BufferedReader in = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder response = new StringBuilder();
        String inputLine;
        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }

        // close resources
        inputStream.close();
        in.close();

        // disconnect the connection
        connection.disconnect();

        return new Response(statusCode,response.toString());
    }

    /**
     * Method to send the response back to the client.
     * @param exchange - HttpExchange object
     * @param response - Response object to be sent
     */
    public static void sendResponse(HttpExchange exchange, Response response)  {
        try{
            String message = response.getMessage();
            int statusCode = response.getStatusCode();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Connection", "keep-alive"); // Keep the connection alive
            exchange.sendResponseHeaders(statusCode, message.length());
            OutputStream os = exchange.getResponseBody();
            os.write(message.getBytes());
            os.close();
        }catch(IOException e){
            System.out.println("Error sending response!!"); // TO-DO: Retry sending response
            exchange.close();
        }
    }
}
