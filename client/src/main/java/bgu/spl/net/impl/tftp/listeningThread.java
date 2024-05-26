package bgu.spl.net.impl.tftp;

import javax.sound.midi.MidiSystem;
import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class listeningThread implements Runnable {


    private InputStream InPut;
    private OutputStream OutPut;


    private Queue<byte[]> packetToSend = new LinkedList<>(); // check again

    private Queue<byte[]> recivedPacket = new LinkedList<>();

    private Queue<byte[]> recivedDIRQ = new LinkedList<>(); // check again

    int packetsDIRQ = 0;
    int packetsRECIVED = 0;

    int packetsLEFT = 0;

    public boolean connected;

    public listeningThread(BufferedInputStream input, BufferedOutputStream output) {

        this.InPut = input;
        this.OutPut = output;
        connected=true;
        String MsgType;

    }

    public void run() {
        TftpEncoderDecoder decoder = new TftpEncoderDecoder();
        try { //just for automatic closing
            int read;

            while (connected && (read = InPut.read()) >= 0) {
                byte[] nextMessage = decoder.decodeNextByte((byte) read);
                if (nextMessage != null) {
                        dealWithMsg(nextMessage);
                }
                }

                } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void dealWithMsg(byte[] message) throws IOException {

        byte [] toDefine= {message[0], message[1]};
        String MsgType= defineMsg(toDefine);

        //////////////first case, received an ack-
        // can be as a confirmation or as a sign to start sending data

        if (MsgType=="ACK") { //means ack

            short blockNum = (short) (((short) message[2]) << 8 | (short) (message[3])); // taking blocknum out from reciving packet
            System.out.println("ACK" + blockNum);
            synchronized (TftpClient.keyboard) {
                TftpClient.keyboard.notifyAll();
            }

            //first case= ack after log because client logged in
            //this case is not waiting for extra data accordignly

            // second case= ack after disc because server removes client from logged in
            if(blockNum == (short) 0 && TftpClient.cuurentAction == "DISC"){
                connected= false;
                KeyboardThread.connected= false;
                synchronized (TftpClient.keyboard) {
                    TftpClient.keyboard.notifyAll();
                }

                return;
            }

            //third case, server sents ack after WWR so want to recieve sents ack
            if (blockNum ==0 && TftpClient.cuurentAction == "WRQ") {
                //need to start sending data for server
                sentDataToServer(TftpClient.currentFileName);

            }
            if ((blockNum != 0) && TftpClient.cuurentAction == "WRQ") {
                if (packetsLEFT > 0) { //i have more packets to send
                    packetsLEFT--;
                    OutPut.write(packetToSend.remove());
                    OutPut.flush();

                    synchronized (TftpClient.keyboard) {
                        TftpClient.keyboard.notifyAll();
                    }

                    return;
                }
            }
            return;
        }
        //////////second case, received data which can be afer rrq/ disq
        if (MsgType=="DATA") { //means data

            short packetSize = (short) (((short) message[4]) << 8 | (short) (message[5])); // taking blocknum out from reciving packet
            System.out.println("DATA packet received, block number: " + packetSize);

            ////////first case, recived a data to be saved in filles after asking RRQ
            if (TftpClient.cuurentAction == "RRQ") {
                //need to save this data to filles

                saveDataToClientFilles(message, TftpClient.currentFileName);
                return;
            }

            //second case ack after dirq
            if (Objects.equals(TftpClient.cuurentAction, "DIRQ")) {
                //sent confirmation ack back to server for reciving this datat package
                saveAndPrintDirq(message);
                synchronized(TftpClient.keyboard) {
                    TftpClient.keyboard.notifyAll();
                }
                return;
            }
        }

        if (Objects.equals(MsgType, "ERROR")){// means error {
            short errorType = (short) (((short) message[2]) << 8 | (short) (message[3])); // taking errorcode out from reciving packet

        byte[] error = new byte[message.length - 4];
        for (int i = 0; i < message.length - 4; i = i + 1) {
            error[i] = message[i + 4];
        }
        String errorInString = new String(error, StandardCharsets.UTF_8);
        System.out.println("ERROR received: Code " + errorType + ", Message: " + errorInString);

            synchronized (TftpClient.keyboard) {
                TftpClient.keyboard.notifyAll();
            }

        return;

    }
         if (MsgType=="BCAST") {

             TftpClient.cuurentAction= "NONE";

            short deletedOrAdded = (short) (message[2]); // taking errorcode out from reciving packet

            String status = ""; //just for default, will get value- "added"/"deleted"
            if (deletedOrAdded == 0) {
                status = "deleted";
            } else if (deletedOrAdded == 1) {
                status = "added";
            }


            byte[] filleName = new byte[message.length - 3];
            for (int i = 0; i < message.length - 3; i = i + 1) {
                filleName[i] = message[i + 3];

            }
            String filleNameInString = new String(filleName, StandardCharsets.UTF_8);

            System.out.println("BCAST received: File " + filleNameInString + " was " + status);
             synchronized (TftpClient.keyboard) {
                 TftpClient.keyboard.notifyAll();
             }
            return;

        }
        synchronized (TftpClient.keyboard) {
            TftpClient.keyboard.notifyAll();
        }
         return;
    }

    public String defineMsg(byte[] defineOPC){
        short opCode = ByteBuffer.wrap(defineOPC).getShort();
        String MsgType = "Illegal TFTP operation - Unknown Opcode.";

        if (opCode == 1) {
            return "RRQ";
        }
        if (opCode == 2) {
            return "WRQ";
        }
        if (opCode == 3) {
            return "DATA";
        }
        if (opCode == 4) {
            return "ACK";
        }
        if (opCode == 5) {
            return "ERROR";
        }
        if (opCode == 6) {
            return "DIRQ";
        }
        if (opCode == 7) {
            return "LOGRQ";
        }
        if (opCode == 8) {
            return "DELRQ";
        }
        if (opCode == 9) {
            return "BCAST";
        }
        if (opCode == 10) {
            return "DISC";
        }

        return null;
    }

    public void sentDataToServer(String dataToServer) {
        String fileName = dataToServer;
        //finding the file to the server and start sending it

        String filePathString =  "." +File.separator + fileName;

        // Create a File object with the specified file path
        File file = new File(filePathString);

        Path filePath = Paths.get( filePathString);
        byte[] fileBytes=null;
        try {
            fileBytes=Files.readAllBytes(filePath);

            // take care in the case that the data is exactly 512
            if (fileBytes.length == 512) {
                short j = (short) (1);
                byte[] blockNum1 = {((byte) (j >> 8)), ((byte) (j & 0x00ff))};
                byte[] response1 = DATApacketFactory(fileBytes, blockNum1);
                System.out.println(response1.length);

                packetToSend.add(response1);
                packetsLEFT++;

                short i = (short) (2);
                byte[] blockNum2 = {((byte) (i >> 8)), ((byte) (i & 0xff))};
                byte[] emptyArray = new byte[0];
                byte[] response2 = DATApacketFactory(emptyArray, blockNum2);

                packetToSend.add(response2);
                packetsLEFT++;

                // remove from the queue and send the first packet

                OutPut.write(packetToSend.remove());
                OutPut.flush();
                packetsLEFT--;

                synchronized (TftpClient.keyboard) {
                    TftpClient.keyboard.notifyAll();
                }

            }

            //if the file is bigger than 512 bytes
            else if (fileBytes.length > 512) {
                int modlueNumOfBlock = fileBytes.length % 512;

                if (modlueNumOfBlock == 0) {
                    packetsLEFT = fileBytes.length / 512;
                } else {
                    packetsLEFT = (fileBytes.length / 512) + 1;
                }

                for (int i = 0; i < packetsLEFT; i++) {
                    int size;
                    if (modlueNumOfBlock != 0 && i == packetsLEFT - 1) {
                        size = modlueNumOfBlock;
                    } else {
                        size = 512;
                    }
                    // create the data packet
                    byte[] dataTosend = Arrays.copyOfRange(fileBytes, i * 512, (i * 512) + size);
                    short j = (short) (i + 1);
                    byte[] blockNum = {((byte) (j >> 8)), ((byte) (j & 0xff))};
                    byte[] response = DATApacketFactory(dataTosend, blockNum);
                    System.out.println(response.length);

                    packetToSend.add(response);
                }

                // add to the queue and send the first packet
                packetsLEFT--;
                OutPut.write(packetToSend.remove());
                OutPut.flush();

                synchronized (TftpClient.keyboard) {
                    TftpClient.keyboard.notifyAll();
                }

            } else {
                short j = (short) 1;
                byte[] blockNum = {((byte) (j >> 8)), ((byte) (j & 0xff))};
                byte[] response = DATApacketFactory(fileBytes, blockNum);
                OutPut.write(response);
                OutPut.flush();

                synchronized (TftpClient.keyboard) {
                    TftpClient.keyboard.notifyAll();
                }
            }
        } catch (IOException e) {
        }

    }


    public byte[] DATApacketFactory(byte[] dataFile, byte[] blockNumber) {
        byte[] response = new byte[6 + dataFile.length];
        short opcode = 3;
        response[0] = ((byte) (opcode >> 8));
        response[1] = ((byte) (opcode & 0xff));

        int packetSize = dataFile.length;

        response[2] = (byte) ((packetSize >> 8) & 0xFF);
        response[3] = (byte) (packetSize & 0xff);


        response[4] = blockNumber[0];
        response[5] = blockNumber[1];

        int index = 6;
        for (int i = 0; i < dataFile.length; i = i + 1) {
            response[index] = dataFile[i];
            index = index + 1;
        }

        return response;

    }


    public byte[] ACKpacketFactory(byte[] indicator) {
        byte[] response = new byte[4];
        short opcode = 4;
        response[0] = ((byte) (opcode >> 8));
        response[1] = ((byte) (opcode & 0xff));
        response[2] = indicator[0];
        response[3] = indicator[1];

        return response;
    }

    public byte[] ERRORpacketFactory(String errorMsg, short errorCode) {
//translate string
        byte[] error = errorMsg.getBytes(StandardCharsets.UTF_8);

        byte[] response = new byte[5 + error.length];

        short opcode = 5;
        response[0] = ((byte) (opcode >> 8));
        response[1] = ((byte) (opcode & 0xff));


        response[2] = (byte) ((errorCode >> 8));
        response[3] = (byte) (errorCode & 0xff);

        int index = 4;
        for (int i = 0; i < error.length; i = i + 1) {
            response[index] = error[i];
            index = index + 1;
        }
        response[response.length - 1] = 0;
        return response;
    }

    public void saveDataToClientFilles(byte[] message, String fileName) throws IOException {

        byte[] blockNumByts = Arrays.copyOfRange(message, 4, 6);
        short blockNum = (short) (((short) blockNumByts[0]) << 8 | (short) (blockNumByts[1]));

        byte[] blockSizeByts = Arrays.copyOfRange(message, 2, 4);
        short blockSize = (short) (((short) blockSizeByts[0]) << 8 | (short) (blockSizeByts[1]));

        if (blockSize >= (short) 512) {
            byte[] data = Arrays.copyOfRange(message, 6, message.length);
            recivedPacket.add(data);
            packetsRECIVED++;
            // send ACK
            byte[] response = ACKpacketFactory(blockNumByts);
            OutPut.write(response);
            OutPut.flush();
            synchronized (TftpClient.keyboard) {
                TftpClient.keyboard.notifyAll();
            }
        }

        // the end of the msg- upload file
        else {
            byte[] data = Arrays.copyOfRange(message, 6, message.length);
            recivedPacket.add(data);

            int size=0;
            for (byte[] packet : recivedPacket) {
                size=size+ packet.length;
            }

            packetsRECIVED = 0; // initialize it again to 0

            // send ACK
            byte[] response = ACKpacketFactory(blockNumByts);
            OutPut.write(response);
            OutPut.flush();

            String filePath =  "." + File.separator + fileName;

            // create an array of bytes of the full data
            byte[] dataToUpload = new byte[size];
            int i = 0;
            for (byte[] packet : recivedPacket) {
                System.arraycopy(packet, 0, dataToUpload, i, packet.length);
                i += packet.length;
            }
            recivedPacket.clear();

            try (FileOutputStream fileData = new FileOutputStream(filePath)) {
                // Write the byte array to the file
                fileData.write(dataToUpload);
                System.out.println("Data uploaded successfully."); // to check
                synchronized (TftpClient.keyboard) {
                    TftpClient.keyboard.notifyAll();
                }
            }
        }
    }

    public void saveAndPrintDirq(byte[] message) throws IOException {

        byte[] blockNumByts = Arrays.copyOfRange(message, 4, 6);
        short blockNum = (short) (((short) blockNumByts[0]) << 8 | (short) (blockNumByts[1]));

        byte[] blockSizeByts = Arrays.copyOfRange(message, 2, 4);
        short blockSize = (short) (((short) blockSizeByts[0]) << 8 | (short) (blockSizeByts[1]));

        if (blockSize >= (short) 512) {
            byte[] data = Arrays.copyOfRange(message, 6, message.length);
            recivedDIRQ.add(data);
            packetsDIRQ++;
            // send ACK
            byte[] response = ACKpacketFactory(blockNumByts);
            OutPut.write(response);
            OutPut.flush();
        }

        // the end of the msg- print list
        else {

            byte[] data = Arrays.copyOfRange(message, 6, message.length);
            recivedDIRQ.add(data);
            packetsDIRQ = 0; // initialize it again to 0

            // send ACK
            byte[] response = ACKpacketFactory(blockNumByts);
            OutPut.write(response);
            OutPut.flush();

            // create an array of bytes of the full data
            int size=0;
            for (byte[] packet : recivedDIRQ) {
                size = size + packet.length;
            }

            byte[] dataToPrint = new byte[size];
            System.out.println(size);
            int i = 0;
            for (byte[] packet : recivedDIRQ) {
                System.arraycopy(packet, 0, dataToPrint, i, packet.length);
                i += packet.length;
            }
            recivedDIRQ.clear();


            String resultString = new String(dataToPrint, StandardCharsets.UTF_8);
            String[] lines = resultString.split("\0");

            // Print the result
            for (String line : lines) {
                System.out.println(line);
            }
            }
        }
    }





