import java.io.IOException;
import java.net.*;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Proxy {
    private final static int defaultPort = 12345;

    public static void main(String[] args) {
        int port;
        if (args.length != 1) {
            port = defaultPort;
        } else {
            try {
                port = Integer.parseInt(args[0]);
            } catch (Exception e) {
                System.out.println("Invalid parameter");
                return;
            }
        }

        ServerSocketChannel server;
        try {
            server = ServerSocketChannel.open();
            server.bind(new InetSocketAddress(port));
        } catch (IOException e) {
            System.out.println("Failed to create server");
            return;
        }
        
        DateFormat dateFormat = new SimpleDateFormat("dd MMM HH:mm:ss");
        Date date = new Date();
        System.out.println(dateFormat.format(date) + " - Proxy listening on localhost:" + port);

        while (true) {
            try {
                SocketChannel clientSock = server.accept();
                new Handler(clientSock).start();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (Exception e2) {
                break;
            }

        }

    }
}
