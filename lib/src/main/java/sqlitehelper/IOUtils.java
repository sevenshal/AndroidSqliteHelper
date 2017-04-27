package sqlitehelper;

import java.io.Closeable;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Created by sevenshal on 2017/4/26.
 */

public class IOUtils {

    public static void close(ServerSocket socket) {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public static void close(Socket socket) {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }


    public static void close(Closeable socket) {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
}
