package bgu.spl.net.impl.tftp;

//import jdk.internal.util.xml.impl.Input;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class KeyboardThread implements Runnable {
    private OutputStream OutPut;

    private InputStream InPut;

    protected static boolean connected;


    public KeyboardThread(BufferedOutputStream output , BufferedInputStream input) {
        this.OutPut = output;
        this.InPut= input;
        this.connected= true;
    }

    public void run() {
        while (connected) {
            Scanner scanner = new Scanner(System.in);
            System.out.print("Enter command: ");
            String inputFromClient = scanner.nextLine(); // Read input from the keyboard
            byte[] message = convertClientString(inputFromClient);
            if (message != null || message.length > 0) {
                try {

                    OutPut.write(message);// write to server through socket
                    OutPut.flush();
                   synchronized (TftpClient.keyboard) {
                        TftpClient.keyboard.wait();
                    }
                } catch (Exception e) {
                }
            }
        }
    }

public byte [] convertClientString (String userInput) {
          String definder="";

          boolean finish = false;
          for (int i = 0; i < userInput.length() && !(finish); i = i + 1) {

              if (userInput.charAt(i) == ' ') {
                  finish = true;
              } else {
                  definder = definder + userInput.charAt(i);
              }
          }

          if (definder.equals("RRQ")){

             TftpClient.cuurentAction= "RRQ";


              String fileName= userInput.substring(4); // "rrq fillenameEXAMP"

              TftpClient.currentFileName= fileName;

              byte [] fileNameByte= fileName.getBytes(StandardCharsets.UTF_8);
              byte [] toSent= new byte[3+ fileNameByte.length];

              short opCode = 1;
              toSent[0] = (byte) (opCode >> 8);
              toSent[1] = (byte) (opCode & 0xff);
                int index=2;
              for (byte ThisByte: fileNameByte){
                  toSent[index]= ThisByte;
                  index=index+1;
              }
              toSent[toSent.length-1]=0;

              return toSent;
          }
          if (definder.equals("WRQ")){

              TftpClient.cuurentAction= "WRQ";

              String fileName= userInput.substring(4); // "wrq fillenameEXAMP"
              TftpClient.currentFileName= fileName;

              byte [] fileNameByte= fileName.getBytes(StandardCharsets.UTF_8);
              byte [] toSent= new byte[3+ fileNameByte.length];

              short opCode = 2;
              toSent[0] = (byte) (opCode >> 8);
              toSent[1]= ((byte) (opCode & 0xff));
              int index=2;
              for (byte ThisByte: fileNameByte){
                  toSent[index]= ThisByte;
                  index=index+1;
              }
              toSent[toSent.length-1]=0;

              return toSent;
          }

          if (definder.equals("ACK")){

              byte [] toSent= new byte[4];
              int blockNum = (userInput.charAt(4)); //example- ACK 3
              short block= (short) blockNum;
              short opCode= 3;
              toSent[0] = (byte) (opCode >> 8);
              toSent[1]= ((byte) (opCode & 0xff));
              toSent[0] = (byte) (block >> 8);
              toSent[3]= ((byte) (block & 0xff));

              return  toSent;
          }

              if (definder.equals("DIRQ")){

                  TftpClient.cuurentAction="DIRQ";

                  byte [] toSent= new byte[2];
                  short opCode = 6;
                  toSent[0] = (byte) (opCode >> 8);
                  toSent[1] = (byte) (opCode & 0xff);
                  return toSent;
              }

              if (definder.equals("LOGRQ")){

                  String UserName= userInput.substring(6); // "LOGRQ USERenameEXAMP"
                  byte [] UserNameByte= UserName.getBytes(StandardCharsets.UTF_8);
                  byte [] toSent= new byte[3+ UserNameByte.length];

                  short opCode = 7;
                  toSent[0] = (byte) (opCode >> 8);
                  toSent[1]= ((byte) (opCode & 0xff));
                  int index=2;
                  for (byte ThisByte: UserNameByte){
                      toSent[index]= ThisByte;
                      index=index+1;
                  }
                 return toSent;

              }

              if (definder.equals("DELRQ")){

                  TftpClient.cuurentAction="DELRQ";

                  String fileName= userInput.substring(6); // "DELRQ fillenameEXAMP"

                  byte [] fileNameByte= fileName.getBytes(StandardCharsets.UTF_8);
                  byte [] toSent= new byte[3+ fileNameByte.length];

                  short opCode = 8;
                  toSent[0] = (byte) (opCode >> 8);
                  toSent[1]= ((byte) (opCode & 0xff));
                  int index=2;
                  for (byte ThisByte: fileNameByte){
                      toSent[index]= ThisByte;
                      index=index+1;
                  }
                  toSent[toSent.length-1]=0;

                  return toSent;

              }

              if (definder.equals("DISC")){

                  TftpClient.cuurentAction= "DISC";
                  byte [] toSent= new byte[2];
                  short opCode= 10;
                  toSent[0] = (byte) (opCode >> 8);
                  toSent[1]= ((byte) (opCode & 0xff));
                  return  toSent;

          }
              return null;
              // notice ERROR (4) and BCAST (9) did not take into accounts as commands from clients.
      }
}

