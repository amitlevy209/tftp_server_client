package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.BlockingConnectionHandler;
import bgu.spl.net.srv.ConnectionHandler;
import bgu.spl.net.srv.Connections;

import java.io.FileOutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.IOException;


class holder {
static ConcurrentHashMap<Integer,Boolean> ids_login = new ConcurrentHashMap<>();
static ConcurrentHashMap<String,Boolean> userNames_login = new ConcurrentHashMap<>();

static Object connectionLock = new Object();

}


public class TftpProtocol implements BidiMessagingProtocol<byte[]>  {

private int connectionId;
private Connections<byte[]> connections;
private boolean shouldTerminate;

private Queue<byte[]> packetToSend = new LinkedList<>(); // check again

private Queue<byte[]> recivedPacket = new LinkedList<>();

int packetsRECIVED = 0;

int packetsLEFT = 0;

String username;
static Object lockFiles = new Object();

@Override
public void start(int connectionId, Connections<byte[]> connections) {
// TODO implement this
this.shouldTerminate= false;
this.connectionId=connectionId;
this.connections=connections;
}

@Override
public void process(byte[] message) {

// TODO implement this
String MsgType = defineOpCode(message);

if (MsgType=="LOGRQ") {

//check if the name is valid
byte[] usernamebyte = Arrays.copyOfRange(message, 2,message.length-1); // to skip the last byte
this.username = byteToString(usernamebyte);
boolean usernameTaken = usernameTaken(username);

if (usernameTaken){
    byte[] response = ERRORpacketFactory("Invalid username - Username is already taken.", (short) 7);
    connections.send(connectionId, response);
    return;
}

// check if the user is already connected
boolean alreadyConnected = alredyConnected(connectionId);

if (alreadyConnected) {
    byte[] response = ERRORpacketFactory("User already logged in-Login username already connected", (short) 7);
    connections.send(connectionId, response);
}

else {
    holder.ids_login.put(connectionId,true);
    holder.userNames_login.put(username, true);
    short j = (short) 0;
    byte[] indicator ={((byte)(j>>8)), ((byte)(j &0xff))};
    byte[] response= ACKpacketFactory(indicator);
    connections.send(connectionId, response);
}

}

// only if the user is connected handle the message
if (alredyConnected(connectionId)){

if (MsgType == "DELRQ") {
byte[] filename = Arrays.copyOfRange(message,2, message.length - 1);
String fileName = byteToString(filename);
String directoryPath = "Flies";
String filePathString = directoryPath + File.separator + fileName;

// Create a File object with the specified file path
File file = new File(filePathString);

// Check if the file exists
    if (!file.exists()) {
        byte[] errorResponse = ERRORpacketFactory("File not found- RRQ DELRQ of non-existing file", (short) 1);
        connections.send(connectionId, errorResponse);

    } else {
        //delete massage
        boolean delete = file.delete();
        short j = (short) 0;
        byte[] indicator = {((byte) (j >> 8)), ((byte) (j & 0xff))};
        byte[] response = ACKpacketFactory(indicator);
        connections.send(connectionId, response);

        //BCAST- send update to all users
        byte[] bcast = BCASTpacketFactory(filename, "Deleted");
        for (Integer user : holder.ids_login.keySet()) {
            connections.send(user, bcast);
        }
    }
// }
}

if (MsgType == "RRQ") {
byte[] filename = Arrays.copyOfRange(message,2, message.length - 1);
String fileName = byteToString(filename);
String directoryPath = "Flies";
String filePathString = directoryPath + File.separator + fileName;
System.out.println("here");
// Create a File object with the specified file path
File file = new File(filePathString);

            // Check if the file exists
            if (file.exists()) {
                Path filePath = Paths.get(filePathString);

                try {
                    byte[] fileBytes = Files.readAllBytes(filePath);

                    // take care in the case that the data is exactly 512
                    if(fileBytes.length == 512){
                        short j = (short) (1);
                        byte[] blockNum1 = {((byte) (j >> 8)), ((byte) (j & 0xff))};
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
                        packetsLEFT--;
                        connections.send(connectionId, packetToSend.remove());
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
                            short j = (short) (i+1);
                            byte[] blockNum = {((byte) (j >> 8)), ((byte) (j & 0xff))};
                            byte[] response = DATApacketFactory(dataTosend, blockNum);
                            System.out.println(response.length);

                            packetToSend.add(response);
                        }

                        // add to the queue and send the first packet
                        packetsLEFT--;
                        connections.send(connectionId, packetToSend.remove());
                    } else {
                        short j = (short) 1;
                        byte[] blockNum = {((byte) (j >> 8)), ((byte) (j & 0xff))};
                        byte[] response = DATApacketFactory(fileBytes, blockNum);
                        connections.send(connectionId, response);
                    }
                } catch (IOException e) {
                }

            // file isn't excites
            } else {
                byte[] response = ERRORpacketFactory("File not found- RRQ DELRQ of non- existing file.", (short) 1);
                connections.send(connectionId, response);
            }
        //}
    }

    
if (MsgType == "ACK"){
byte[] blockNumByts = Arrays.copyOfRange(message,2,4);
short blockNum = (short) (((short) blockNumByts[0]) << 8 | (short) (blockNumByts[1]));

// if the ack block num isn't 0 - need to send the rest of the packets from rrq
if (blockNum != (short) 0){
        if(packetsLEFT > 0){ //i have more packets to send
       packetsLEFT--;
       connections.send(connectionId, packetToSend.remove()); // send the next packet

    }
}
}


if (MsgType == "WRQ") {
byte[] filename = Arrays.copyOfRange(message,2, message.length - 1);
String fileName = byteToString(filename);
String directoryPath = "Flies";
String filePathString = directoryPath + File.separator + fileName;

// Create a File object with the specified file path
File file = new File(filePathString);

    // Check if the file exists
    if (file.exists()) {
        byte[] response = ERRORpacketFactory("File already exists – File name exists on WRQ.", (short) 5);
        connections.send(connectionId, response);
    } else {
        recivedPacket.add(filename);
        short j = (short) 0;
        byte[] indicator = {((byte) (j >> 8)), ((byte) (j & 0xff))};
        byte[] response = ACKpacketFactory(indicator);
        connections.send(connectionId, response);

    }
}

if(MsgType == "DATA"){
//TODO
byte[] blockNumByts = Arrays.copyOfRange(message,4,6);
short blockNum = (short) (((short) blockNumByts[0]) << 8 | (short) (blockNumByts[1]));

byte[] blockSizeByts = Arrays.copyOfRange(message,2,4);
short blockSize = (short) (((short) blockSizeByts[0]) << 8 | (short) (blockSizeByts[1]));

    if (blockSize >= (short) 512){
        byte[] data = Arrays.copyOfRange(message,6,message.length );
        recivedPacket.add(data);
        packetsRECIVED++;
        // send ACK
        byte[] response = ACKpacketFactory(blockNumByts);
        connections.send(connectionId, response);
    }

    // the end of the msg- upload file

    else{

        //    int size = (packetsRECIVED * 512) + blockSize; // the size of the file
            byte[] data = Arrays.copyOfRange(message, 6, message.length);
            recivedPacket.add(data);
            packetsRECIVED = 0; // initialize it again to 0

        // send ACK
        byte[] response = ACKpacketFactory(blockNumByts);
        connections.send(connectionId, response);

            // the first packet is the file name
            byte[] fileNameByte = recivedPacket.remove();
            String directoryPath = "Flies";
            String fileName = byteToString(fileNameByte);
            String filePath = directoryPath + File.separator + fileName;

            //decide the size
        int size=0;
        for (byte[] packet : recivedPacket) {
            size= size+ packet.length;
        }

            // create an array of bytes of the full data
            byte[] dataToUpload = new byte[size];
            int i = 0;
            for (byte[] packet : recivedPacket) {
               // if (i < 512) {
                    System.arraycopy(packet, 0, dataToUpload, i, packet.length);
                    i += packet.length;
               // }
            }
            recivedPacket.clear();

            try (FileOutputStream fileData = new FileOutputStream(filePath)) {
                    // Write the byte array to the file
                    fileData.write(dataToUpload);
                    System.out.println("Data uploaded successfully."); // to check

                    //send bcast- update all users
                    byte[] bcast = BCASTpacketFactory(fileNameByte, "Upload");
                    for (Integer user : holder.ids_login.keySet()) {
                        connections.send(user, bcast);
                    }

            } catch (IOException e) {
                    System.out.println("An error occurred."); // to check
                    e.printStackTrace();
                }
            }
        }

    //}


// change this method
if (MsgType == "DIRQ") {

File directory = new File("Flies"); // Path to the directory containing the files

// Get the list of files in the directory
File[] files = directory.listFiles();
if (files != null) {
    // Create a StringBuilder to store the file names with null byte separators
    StringBuilder stringBuilder = new StringBuilder();

    // Append each file name to the StringBuilder with a null byte separator
    for (File file : files) {
        stringBuilder.append(file.getName()).append('\0');
    }

    // Convert the StringBuilder to a byte array
    byte[] namesArray = stringBuilder.toString().getBytes(StandardCharsets.UTF_8);

    //split the data to 512 packets and send as a data packet

    // take care in the case that the data is exactly 512
    if(namesArray.length == 512){
        short j = (short) (1);
        byte[] blockNum1 = {((byte) (j >> 8)), ((byte) (j & 0xff))};
        byte[] response1 = DATApacketFactory(namesArray, blockNum1);
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
        packetsLEFT--;
        connections.send(connectionId, packetToSend.remove());
    }

    //if the file is bigger than 512 bytes
    else if (namesArray.length > 512) {
        int modlueNumOfBlock = namesArray.length % 512;

        if (modlueNumOfBlock == 0) {
            packetsLEFT = namesArray.length / 512;
        } else {
            packetsLEFT = (namesArray.length / 512) + 1;
        }

        for (int i = 0; i < packetsLEFT; i++) {
            int size;
            if (modlueNumOfBlock != 0 && i == packetsLEFT - 1) {
                size = modlueNumOfBlock;
            } else {
                size = 512;
            }
            // create the data packet
            byte[] dataTosend = Arrays.copyOfRange(namesArray, i * 512, (i * 512) + size);
            short j = (short) (i+1);
            byte[] blockNum = {((byte) (j >> 8)), ((byte) (j & 0xff))};
            byte[] response = DATApacketFactory(dataTosend, blockNum);
            System.out.println(response.length);

            packetToSend.add(response);
        }

        // add to the queue and send the first packet
        packetsLEFT--;
        connections.send(connectionId, packetToSend.remove());

    // the data is less than 512
    } else {
        short j = (short) 1;
        byte[] blockNum = {((byte) (j >> 8)), ((byte) (j & 0xff))};
        byte[] response = DATApacketFactory(namesArray, blockNum);
        connections.send(connectionId, response);
    }
}
}

if (MsgType== "DISC"){
boolean isLogedIn = alredyConnected(connectionId);

if(!isLogedIn){
    byte [] response= ERRORpacketFactory("User not logged in – Any opcode received before Login completes", (short)6);
}
else {

    short p = (short) 0;
    byte[] indecator ={((byte)(p>>8)), ((byte)(p &0xff))};
    byte [] response= ACKpacketFactory(indecator); //removed from log in
    connections.send(connectionId,response);

    //disconnect the user
    this.connections.disconnect(connectionId);
    holder.ids_login.remove(connectionId);
    holder.userNames_login.remove(username);
    shouldTerminate = true;

}


if (MsgType == "Illegal TFTP operation - Unknown Opcode") {
    byte[] response = ERRORpacketFactory("Illegal TFTP operation - Unknown Opcode", (short) 4);
    connections.send(connectionId, response);
}
}
}
// the user hasn't been connected yet
else {
byte[] response = ERRORpacketFactory("User not logged in - Any opcode received before login completes", (short) 6);
connections.send(connectionId, response);
}
}

@Override
public boolean shouldTerminate() {
// TODO implement this
return shouldTerminate;
}


//////////////////////////////////////factory packets functions//////////////////////////////////////////////

public byte[] ACKpacketFactory (byte [] indicator){
byte [] response= new byte[4];
short opcode= 4;
response[0]= ((byte)(opcode>>8));
response[1]= ((byte)(opcode &0xff));
response[2]= indicator[0];
response[3]= indicator[1];

return response;
}

public byte[] DATApacketFactory (byte [] dataFile, byte[] blockNumber){
byte [] response= new byte[6+ dataFile.length];
short opcode= 3;
response[0]= ((byte)(opcode>>8));
response[1]= ((byte)(opcode &0xff));

int packetSize= dataFile.length;

response[2]= (byte)((packetSize>>8)&0xFF);
response[3]= (byte)(packetSize &0xff);


response[4]= blockNumber[0];
response[5]= blockNumber[1];

int index= 6;
for (int i=0; i<dataFile.length; i=i+1){
response [index]= dataFile[i];
index=index+1;
}

return response;
}

private boolean alredyConnected (int connectionId){
return holder.ids_login.containsKey(connectionId);
}

private boolean usernameTaken (String username){
return holder.userNames_login.containsKey(username);
}

private String byteToString (byte[] bytes) {

// Convert the byte array to a string using a specific charset
return new String(bytes, StandardCharsets.UTF_8);
}

public byte[] ERRORpacketFactory (String errorMsg, short errorCode){
//translate string
byte[] error=  errorMsg.getBytes(StandardCharsets.UTF_8);

byte [] response= new byte[5+ error.length];

short opcode= 5;
response[0]= ((byte)(opcode>>8));
response[1]= ((byte)(opcode &0xff));


response[2]= (byte)((errorCode>>8));
response[3]= (byte)(errorCode &0xff);

int index= 4;
for (int i=0; i<error.length; i=i+1){
response [index]= error[i];
index=index+1;
}
response[response.length-1]= 0;
return response;
}

public byte[] BCASTpacketFactory (byte [] fileName, String msgType){
int size = 2 + 2 + fileName.length;
byte [] response= new byte[size];
short opcode= 9;
response[0]= ((byte)(opcode>>8));
response[1]= ((byte)(opcode &0xff));

// add the added or deleted byte
if (msgType ==  "Upload"){
response[2]= 1;
}

else {
response[2]=0;
}

// add the file name bytes
int j = 3;
for (int i=0; i< fileName.length; i++){ // check the condition
    response[j]= fileName[i];
    j++;
}

// add the blocking byte
response[size - 1]= (byte) 0;

return response;
}

private String defineOpCode (byte[] msg){
// converting 2 byte array to a short
String MsgType= "Illegal TFTP operation - Unknown Opcode";
byte[] opCodeByte = Arrays.copyOfRange(msg, 0, 2);
short opCode = ( short ) ((( short ) opCodeByte [0]) << 8 | ( short ) ( opCodeByte [1]) );
if (opCode ==  1) {
MsgType = "RRQ";
}
if (opCode== 2) {
MsgType = "WRQ";
}
if (opCode == 3) {
MsgType = "DATA";
}
if (opCode == 4) {
MsgType = "ACK";
}
if (opCode== 5) {
MsgType = "ERROR";
}
if (opCode == 6) {
MsgType = "DIRQ";
}
if (opCode == 7) {
MsgType = "LOGRQ";
}
if (opCode == 8) {
MsgType = "DELRQ";
}
if (opCode == 9) {
MsgType = "BCAST";
}
if (opCode == 10) {
MsgType = "DISC";
}
return MsgType;
}


}