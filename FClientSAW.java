import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.io.File;

public class FClientSAW {
    private static int forget[][] = new int[4][2];
    private static boolean lastPacket = false;
    private static int preSeqNo = -1;
    private static String outputFileName = "";
    private static int counter = 0;

    public static void main(String[] args){
        /*if(args.length != 3){
            System.out.println("Required arguments: Server port");
            return;
        }*/

        for (int i = 0; i < 4; i++)
            forget[i][0] = -1;
        for (int i = 0; i < args.length - 3; i++) {
            forget[i][0] = Integer.parseInt(args[i + 3].substring(args[i + 3].lastIndexOf('T') + 1));
            forget[i][1] = 0;
        }
        int port = Integer.parseInt(args[1]);
        InetAddress server = null;
        FClientSAW fclient = new FClientSAW();
        DatagramSocket socket = null;
        try {
            server = InetAddress.getByName(args[0]);
            socket = new DatagramSocket();
        }
        catch (IOException e){
            e.printStackTrace();
        }
        String file = args[2];
        File f = new File(file);
		
        fclient.setOutputFileName("new" + file);
        file = "REQUEST" + file + "\r\n";
        byte[] finame = file.getBytes();
        DatagramPacket fileRequest = new DatagramPacket(finame, finame.length, server, port);
        try {
            assert socket != null;
            socket.send(fileRequest);
        }
        catch (IOException e){
            e.printStackTrace();
        }
        int sequence_number;
        while (true) try {
            DatagramPacket response = new DatagramPacket(new byte[2048], 2048);
            socket.receive(response);
            sequence_number = fclient.printData(response);
            int flag = 0;
            for (int i = 0; i < 4; i++) {
                if (forget[i][0] != -1 && forget[i][0] == sequence_number && forget[i][1] == 0) {
                    flag = 1;
                    forget[i][1] = 1;
                }
            }
            if (flag == 0) {
                sequence_number = (sequence_number + 1);
                String str = "ACK " + sequence_number + " \r\n";
                byte[] buf = str.getBytes();
                DatagramPacket acknowledgement = new DatagramPacket(buf, buf.length, server, port);
                System.out.println("Sent ACK " + (sequence_number) + "\n");
                socket.send(acknowledgement);
                if (lastPacket) {
                    System.out.println("END");
                    break;
                }
            } else
                System.out.println("Forgot ACK" + (sequence_number + 1) + "\n");

        } catch (IOException e) {
            e.printStackTrace();
        }
			
    }

    private String getOutputFileName() {
        return outputFileName;
    }

    private void setOutputFileName(String name) {
        outputFileName = name;
    }

    private int printData(DatagramPacket request) {
        byte[] dataPacket = (request.getData());
        byte[][] information = parseData(dataPacket);
        byte[] str = trim(information[1]);


        int nextSeqNo = Integer.parseInt(new String(str));
        if ((preSeqNo + 1) == nextSeqNo) {
            byte[] buf;
            buf = (information[2]);

            if (information[3][0] == 69) {
                buf = trim(buf);
                lastPacket = true;
            }
            try {
                FileOutputStream fos = new FileOutputStream(getOutputFileName(), true);
                fos.write(buf);
            }
            catch (IOException e){
                e.printStackTrace();
            }
            counter++;
            System.out.println("Received CONSIGNMENT" + (counter - 1)+"\n");
            preSeqNo = nextSeqNo;
        } else {
            System.out.println("Received CONSIGNMENT" + (counter - 1) + "\t received \t duplicate - discarding"+"\n");
            nextSeqNo = preSeqNo;
        }
        return nextSeqNo;
    }

    private byte[] trim(byte[] bytes) {
        int i = bytes.length - 1;
        while (i >= 0 && bytes[i] == 0) {
            --i;
        }

        return Arrays.copyOf(bytes, i + 1);
    }

    private byte[][] parseData(byte data[]) {
        int i, len, len1, len2, len3;
        int lastIndex1 = 0;
        byte[][] info = new byte[4][512];
        byte[] data1, data2, data3;
        len = data.length;
        for (i = 0; i < len; i++) {
            if (data[i] == 32)
                break;
            info[0][i] = data[i];
        }
        data1 = new byte[len - i - 1];
        System.arraycopy(data, i + 1, data1, 0, len - i - 1);
        len1 = data1.length;
        for (i = 0; i < len1; i++) {
            if (data1[i] == 32)
                break;
            info[1][i] = data1[i];
        }
        data2 = new byte[len1 - i - 1];
        System.arraycopy(data1, i + 1, data2, 0, len1 - i - 1);
        len2 = data2.length;
        for (i = len2 - 1; i > 0; i--) {
            if (data2[i] == 10 && data2[i - 1] == 13) {
                lastIndex1 = i - 1;                           // lastIndex1 holds last \n pos
                break;
            }
        }
        data3 = new byte[lastIndex1 - 1];
        System.arraycopy(data2, 0, data3, 0, lastIndex1 - 1);
        len3 = data3.length;
        if (data3[len3 - 1] == 68 && data3[len3 - 2] == 78 && data3[len3 - 3] == 69) {
            info[3][0] = 69;                              // E
            info[3][1] = 78;                              // N
            info[3][2] = 68;                              // D
            System.arraycopy(data3, 0, info[2], 0, data3.length - 4);
        } else {
            //info[3][0]=(byte)'!';
            System.arraycopy(data3, 0, info[2], 0, data3.length);
        }
        return info;
    }
}
