import java.io.*;
import java.net.*;
import java.net.DatagramSocket;
import java.io.*;
import java.net.*;

public class FServerSAW {
    private static boolean canSend = true;
    private static boolean isLastPacket = false;
    private static final int PACKET_SIZE = 512;
    private static DatagramSocket socket;
    private static DatagramPacket frameToSend;
    private static boolean isFileName = false;
    private static int[][] forget = new int[4][2];

    public static void main(String args[]) {
        FServerSAW fserver = new FServerSAW();
        if (args.length == 0) {
            System.out.println("Port number required");
            return;
        }
        int port = Integer.parseInt(args[0]);                           //getting port of server being used
        //getting Consignments that the server forgets to send
        for (int i = 0; i < 4; i++)
            forget[i][0] = -1;
        for (int i = 0; i < args.length - 1; i++) {
            forget[i][0] = Integer.parseInt(args[i + 1].substring(args[i + 1].lastIndexOf('T') + 1));
            forget[i][1] = 0;
        }
        try {
            socket = new DatagramSocket(port);
            while (true) {
                DatagramPacket recievedPacket = new DatagramPacket(new byte[100], 100);
                socket.receive(recievedPacket);                             //recieve the request with the file name sent by client
                String fileName = fserver.printData(recievedPacket).trim();
                if (!isFileName)
                    continue;

                int seqNo = 0;
                canSend = true;
                isLastPacket = false;
                isFileName = false;
                //getting Client's IP and Port
                int clientPort = recievedPacket.getPort();
                InetAddress clientIpAddress = recievedPacket.getAddress();
                System.out.println("Received request for " + fileName + " from " + clientIpAddress + " port " + port+"\n");

                //extracting file and converting into bytes array
                int counter = 0;                                            // to know the number of packets read
                while (true) {

                    if (canSend) {
                        byte[] dataToSend = fserver.getData(counter, fileName);
                        if (dataToSend == null)
                            break;
                        counter++;
                        frameToSend = fserver.makeFrame(seqNo, dataToSend, clientPort, clientIpAddress);
                        fserver.sendFrame(counter, frameToSend);
                        seqNo = (seqNo + 1);
                        canSend = false;


                    }
                    socket.setSoTimeout(30);
                    
                        int ackNo = fserver.receiveFrame(counter);
                        if (ackNo == seqNo) {
                            System.out.println("Received ACK "+ackNo+"\n");

                            canSend = true;
                        } else {
                            System.out.println("Received ACK " + ackNo + " Expected " + seqNo);
                        }
                        if (isLastPacket) {
                            canSend = true;
                            socket.setSoTimeout(0);
                            break;
                        }
                     
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private void sendFrame(int seqNo, DatagramPacket reply) throws IOException {

        //Random random = new Random();
        //System.out.println("Sending Sequence No. : " + seqNo);
        int flag = 0;
        for (int i = 0; i < 4; i++) {
            if (forget[i][0] != -1 && forget[i][0] == seqNo && forget[i][1] == 0) {
                flag = 1;
                forget[i][1] = 1;
            }
        }
        if (flag == 0) {
            socket.send(reply);
            System.out.println("Sent CONSIGNMENT" + (seqNo - 1) + "\n");
        } else {
            System.out.println("Forgot CONSIGNMENT" + (seqNo - 1) + "\n");
        }

    }


    private DatagramPacket makeFrame(int seq, byte[] buf, int clientPort, InetAddress clientHost) {
        String prefix = "RDT " + seq + " ";
        byte[] packetBuffer = new byte[2048];
        byte[] prefixInBytes = prefix.getBytes();
        System.arraycopy(prefixInBytes, 0, packetBuffer, 0, prefixInBytes.length);
        System.arraycopy(buf, 0, packetBuffer, prefixInBytes.length, buf.length);
        String suffix = "";
        if (isLastPacket) {
            suffix = " END";

        }
        suffix += " \r\n";
        byte[] suffixInBytes = suffix.getBytes();
        System.arraycopy(suffixInBytes, 0, packetBuffer, buf.length + prefixInBytes.length, suffixInBytes.length);
        return new DatagramPacket(packetBuffer, packetBuffer.length, clientHost, clientPort);
    }

    private byte[] getData(int count, String file)  {
        byte[] data = new byte[512];
        int x;
        try {
            
            FileInputStream input = new FileInputStream(file);
            input.skip(PACKET_SIZE * count);
            if (input.available() > 0) {
                x = input.read(data);
                if (x < 512) {
                    isLastPacket = true;
                }
                return data;
            } else {
                isLastPacket = true;
                return null;
            }
        } catch (IOException e){
            e.printStackTrace();
        }
        return data;
    }

    private int receiveFrame(int counter)  {
        DatagramPacket receive_ack = new DatagramPacket(new byte[512], 512);
        try{
            socket.receive(receive_ack);
        }
        catch(SocketTimeoutException s){
            //System.out.println("Packet#"+counter+" time out "+System.currentTimeMillis());
            timeOut(counter);
        }
        catch(IOException e){
            e.printStackTrace();
        }
        
        int ackData = 0;
        byte[] acknowledge = receive_ack.getData();
        String data = new String(acknowledge);

        //System.out.println("Acknowledgement Received: "+ data);

        try {
            String[] info = data.split(" ");
            if (info[0].trim().equalsIgnoreCase("ACK"))
                ackData = Integer.parseInt(info[1].trim());
        } catch (Exception e) {
            System.out.println("Packet Corrupted!");
            e.printStackTrace();
        }

        return ackData;
    }

    private void timeOut(int cnt) {
        try {
            socket.setSoTimeout(30);
            sendFrame(cnt, frameToSend);
        } 
        catch (IOException e) {
            e.printStackTrace();
        }
        canSend = false;
    }

    private String printData(DatagramPacket request)  {
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(request.getData())));
            String str = br.readLine();
            System.out.println(str);
            String firstWord = str.substring(0, 7);
            if (firstWord.equalsIgnoreCase("REQUEST")) {
                isFileName = true;
            }
            str = str.substring(7).trim();
            return str;
        }
        catch (IOException e){
            e.printStackTrace();
        }
        return "";
    }
}
