package bgu.spl.net.impl.tftp;

import java.io.*;
import java.net.Socket;

public class TftpClient {
    // TODO: implement the main logic of the client, when using a thread per client the main logic goes here

    public static String cuurentAction = "NONE";


    public static String currentFileName = "NONE";

    public static Thread keyboard;


    public static void main(String[] args) {
        if (args.length == 0) {
            args = new String[]{"localhost", "7777"};
        }

        // Check for correct usage
        if (args.length != 2) {
            System.out.println("Usage: java TftpClient <server address> <port>");
            return;
        }

        String serverAddress = args[0];
        int port = Integer.parseInt(args[1]);

        try (Socket socket = new Socket(serverAddress, port)) {
            // Get input and output streams for the socket
            // Declare input and output variables outside the try block
            BufferedInputStream input = new BufferedInputStream(socket.getInputStream());
            BufferedOutputStream output = new BufferedOutputStream(socket.getOutputStream());

            Thread ThiskeyboardThread = new Thread(new KeyboardThread(output, input));
            ThiskeyboardThread.start();

            TftpClient.keyboard= ThiskeyboardThread;

            Thread ThislisteningThread = new Thread(new listeningThread(input, output));
            ThislisteningThread.start();

            ThiskeyboardThread.join();

            ThislisteningThread.join();

        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
