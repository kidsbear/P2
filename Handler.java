import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Handler extends Thread {

    private boolean connectMode = false;
    private String serverAddress;
    private int serverPort;
    private SocketChannel clientSock;
    
    private DateFormat dateFormat = new SimpleDateFormat("dd MMM HH:mm:ss");
    private Date date = new Date();

    Handler(SocketChannel clientSock) {
        if (clientSock != null) {
            this.clientSock = clientSock;
        } else {
            this.interrupt();
        }
    }

    private String handleHeader(InputStream input) throws IOException {
        int nxt = input.read();
        int eoh = 0;
        int size = 1024;
        byte[] buf = new byte[size];
        int idx = 0;

        while (nxt != -1) {
            buf[idx++] = (byte) nxt;
            if (idx >= size) {
                size <<= 1;
                byte[] tmp = new byte[size];
                System.arraycopy(buf, 0, tmp, 0, idx);
                buf = tmp;
            }
            switch (eoh) {
                case 0:
                    eoh += nxt == (int) '\n' ? 1 : 0;
                    break;
                case 1:
                    eoh += nxt == (int) '\r' ? 1 : -1;
                    break;
                case 2:
                    eoh += nxt == (int) '\n' ? 1 : -1;
            }
            if (eoh == 3)
                break;
            nxt = input.read();
        }

        BufferedReader bfReader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(buf)));

        String initialLine = bfReader.readLine();
	initialLine = initialLine.replaceAll("HTTP/1.1", "HTTP/1.0");
        if (initialLine == null)
            throw new IllegalArgumentException();
        if (initialLine.toLowerCase().startsWith("connect")) {
            this.connectMode = true;
        }
        
        StringBuilder clientMessage = new StringBuilder(initialLine + "\r\n");
        String newLine = bfReader.readLine();

        while (newLine != null && newLine.length() != 0) {
            newLine = newLine.trim();
            String[] splitContent = newLine.split(" ");
            if (splitContent[0].toLowerCase().equals("host:")) {
                String[] temp = splitContent[1].split(":");
                this.serverAddress = temp[0];
                if (temp.length == 2) {
                    this.serverPort = Integer.parseInt(temp[1]);
                } else {
                    this.serverPort = getPort(initialLine);
                }
                System.out.println(dateFormat.format(date) + " - >>> " + initialLine);
            } else if (!this.connectMode && newLine.toLowerCase().startsWith("connection:")) {
                newLine = "Connection: close";
            } else if (!this.connectMode && newLine.toLowerCase().startsWith("proxy-connection:")) {
                newLine = "Proxy-connection: close";
            }
            clientMessage.append(newLine).append("\r\n");
            newLine = bfReader.readLine();
        }
        clientMessage.append("\r\n");
        return clientMessage.toString();
    }

    public void run() {
        try {
            String header = handleHeader(clientSock.socket().getInputStream());
            if (this.serverAddress == null) {
                clientSock.socket().close();
                return;
            }
            if (connectMode) {
                connect();
            } else {
                nonConnect(header);
            }
        } catch (Exception e) {
            System.err.println("Error when handle client input: " + e.getMessage());
        }

    }

    // Get port number from first line
    private int getPort(String line) {
        int res;
        if (line.toLowerCase().contains("https://")) {
            res = 443;
        } else {
            res = 80;
        }

        String[] seg = line.split(" ")[1].split(":");
        if (seg.length >= 3 && seg[2].charAt(0) <= '9' && seg[2].charAt(0) >= '0') {
            res = Integer.parseInt(seg[2].replaceAll("[\\D].*", ""));
        }
        return res;
    }

    private void connect() throws IOException {
        Socket serverSock;

        try {
            serverSock = new Socket(this.serverAddress, this.serverPort);
        } catch (Exception e) {
            String errorMessage = "HTTP/1.0 502 Bad Gateway\r\n\r\n";
            this.clientSock.write(ByteBuffer.wrap(errorMessage.getBytes()));
            this.clientSock.socket().close();
            return;
        }

        InputStream fromServer = serverSock.getInputStream();
        OutputStream toServer = serverSock.getOutputStream();
        InputStream fromClient = clientSock.socket().getInputStream();
        OutputStream toClient = clientSock.socket().getOutputStream();

        String okMessage = "HTTP/1.1 200 OK\r\n\r\n";
        this.clientSock.write(ByteBuffer.wrap(okMessage.getBytes()));

        byte[] buf = new byte[1024];
        int cnt = 10;
        while (cnt > 0) {
            try {
                serverSock.setSoTimeout(10);
                int read = fromServer.read(buf);
                if (read == -1)
                    break;
                toClient.write(buf, 0, read);
                cnt = 10;
            } catch (Exception e) {
                cnt--;
            }

            try {
                clientSock.socket().setSoTimeout(10);
                int read = fromClient.read(buf);
                if (read == -1)
                    break;
                toServer.write(buf, 0, read);
                cnt = 10;
            } catch (Exception e) {
                cnt--;
            }
        }
        clientSock.socket().close();
        serverSock.close();
    }

    private void nonConnect(String clientHeader) throws IOException {
        Socket serverSock = new Socket(this.serverAddress, this.serverPort);
        OutputStream toServer = serverSock.getOutputStream();

        toServer.write(clientHeader.getBytes());
        byte[] buf = new byte[1024];
        int timeout = 100;
        try {
            clientSock.socket().setSoTimeout(timeout);
            int read = clientSock.socket().getInputStream().read(buf);
            while (read != -1) {
                toServer.write(buf, 0, read);
                read = clientSock.socket().getInputStream().read(buf);
            }
        } catch (java.net.SocketTimeoutException ignored) {
        }

        String serverHeader = handleHeader(serverSock.getInputStream());
        OutputStream toClient = this.clientSock.socket().getOutputStream();

        toClient.write(serverHeader.getBytes());

        try {
            serverSock.setSoTimeout(timeout);
            int read = serverSock.getInputStream().read(buf);
            while (read != -1) {
                toClient.write(buf, 0, read);
                read = serverSock.getInputStream().read(buf);
            }
        } catch (java.net.SocketTimeoutException e) {
            serverSock.close();
        }

        clientSock.socket().close();
    }
}
