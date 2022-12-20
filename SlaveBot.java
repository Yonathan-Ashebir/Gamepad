import java.awt.Robot;
import java.net.ServerSocket;
import java.rmi.ServerError;

class SlaveBot {
    private int portNo = 3333;

    public static void main(String[] args) {

    }

    SlaveBot() {
    }

    SlaveBot(int portNo) {
        this.portNo = portNo;
    }

    public synchronized void start() {
    }

    public void stop() {
    }

    private void runServer(ServerSocket serverSocket) {

    }

}