package model;

/**
 * Response class is used to send the response back to the client.
 */
public class Response {
    private int statusCode;
    private String message;

    public Response(int statusCode, String message) {
        this.statusCode = statusCode;
        this.message = message;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getMessage() {
        return message;
    }
}
