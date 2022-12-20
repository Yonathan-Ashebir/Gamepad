package com.inteljConsole.automation;

import java.awt.AWTException;
import java.awt.Robot;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Scanner;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class SlaveBot {
    public static int DEFAULT_PORT_NO = 3333;
    private int portNo = DEFAULT_PORT_NO;
    private boolean started = false;
    private boolean shouldStop = false;
    private Robot myBot = new Robot();
    private Consumer onConnect = null;

    private int mouseX = 0;
    private int mouseY = 0;

    public static void main(String[] args) throws AWTException, IOException {
        int portNo = DEFAULT_PORT_NO;
        if (args.length == 1)
            portNo = Integer.parseInt(args[0]);
        else if (args.length != 0) throw new IllegalArgumentException("Unnecessary args supplied");

        SlaveBot bot = new SlaveBot(portNo);
        bot.setOnConnect(new Consumer<Socket>() {
            @Override
            public void accept(Socket o) {
                System.out.println("  Accepted client connection from " + o.getRemoteSocketAddress().toString().substring(1));
            }
        });
        System.out.print("Waiting at addresses");
        Enumeration enums = NetworkInterface.getNetworkInterfaces().asIterator().next().getInetAddresses();
        boolean isFirst = true;
        while (enums.hasMoreElements()) {
            if (isFirst) {
                isFirst = false;
            } else System.out.print(",");
            System.out.print(" " + enums.nextElement().toString().substring(1) + ":" + portNo);

        }
        System.out.println(" :");
        bot.start();
    }

    SlaveBot() throws AWTException {
    }

    SlaveBot(int portNo) throws AWTException {
        this.portNo = portNo;
    }

    public synchronized void start() throws IOException {
        if (started) return;
        started = true;
        ServerSocket serverSocket = new ServerSocket(portNo);
        new Thread(() -> runServer(serverSocket)).start();
    }

    public void stop() {
        shouldStop = true;
    }

    private void runServer(ServerSocket serverSocket) {
        boolean firstTime = true;
        main:
        while (!shouldStop) {
            try {
                if (!firstTime)
                    System.out.println("  Client disconnected, waiting to reconnect ...");//warn: for unshared library
                else firstTime = false;
                Socket client = serverSocket.accept();
                if (onConnect != null) onConnect.accept(client);
                InputStream in = client.getInputStream();
                OutputStream out = client.getOutputStream();
                Scanner s = new Scanner(in, "UTF-8");
                try {
                    String data = s.useDelimiter("\\r\\n\\r\\n").next();
                    Matcher get = Pattern.compile("^GET").matcher(data);
                    if (get.find()) {
                        Matcher match = Pattern.compile("Sec-WebSocket-Key: (.*)").matcher(data);
                        match.find();
                        byte[] response = ("HTTP/1.1 101 Switching Protocols\r\n"
                                + "Connection: Upgrade\r\n"
                                + "Upgrade: websocket\r\n"
                                + "Sec-WebSocket-Accept: "
                                + Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((match.group(1) + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes("UTF-8")))
                                + "\r\n\r\n").getBytes("UTF-8");
//                        System.out.println("Response:" + new String(response));

                        out.write(response, 0, response.length);
                        int read = client.getInputStream().read();
                        int state = 0;

                        byte[] buf = null;
                        int bufInd = 0;

                        int[] enc = new int[4];
                        int encInd = 0;

                        int[] lenBuf = null;
                        int lenInd = 0;

//                        long lastSendTime = -1;
                        while (read != -1) {
                            if (shouldStop) break;
                            switch (state) {
                                case 0:
                                    state = 1;
                                    break;
                                case 1: {
                                    int diff = read - 128;
                                    if (diff <= 125) {
                                        buf = new byte[diff];
                                        state = 3;
                                        encInd = 0;
                                    } else if (diff == 126) {
                                        state = 2;
                                        lenBuf = new int[2];
                                        lenInd = 0;

                                    } else if (diff == 127) {
                                        state = 2;
                                        lenBuf = new int[4];
                                        lenInd = 0;

                                    } else throw new IllegalArgumentException("Bad message length");
                                    break;
                                }
                                case 2: {
                                    lenBuf[lenInd] = read;
                                    lenInd++;
                                    if (lenInd >= lenBuf.length) {
                                        int len = 0;
                                        for (int ind = 0; ind < lenBuf.length; ind++) {
                                            len |= lenBuf[ind] << ((lenBuf.length - 1 - ind) * 8);
                                        }
                                        buf = new byte[len];
                                        encInd = 0;
                                        state = 3;
                                    }
                                    break;
                                }
                                case 3: {
                                    enc[encInd] = read;
                                    encInd++;
                                    if (encInd >= enc.length) {
                                        if (buf.length == 0) {
                                            state = 0;
                                            //handle empty signal
                                            client.close();
                                            continue main;

                                        } else {
                                            state = 4;
                                            bufInd = 0;
                                        }
                                    }

                                    break;

                                }
                                case 4: {

                                    buf[bufInd] = (byte) (read ^ enc[bufInd & 0x3]);
                                    bufInd++;
                                    if (bufInd >= buf.length) {
                                        state = 0;

                                        //handle result from buf
//                                        handleSignal(buf);
//                                        if (lastSendTime == -1)//info: keep for debugging
//                                            lastSendTime = System.currentTimeMillis();
//                                        else {
//                                            long cTime = System.currentTimeMillis();
//                                            System.out.println("   Resent in " + (cTime - lastSendTime)+" local time millis: "+new Date().getTime());
//                                            lastSendTime = cTime;
//
//                                        }
                                        System.out.println("  - Action code: " + buf[0]);


//                                        String message = new String(buf);
//                                        System.out.print("   Message codes: ");
//                                        for(char c : message.toCharArray()){
//                                            System.out.print((int)c+" ");
//                                        }
//                                        System.out.println();
                                    }
                                    break;
                                }
                                default:
                                    throw new IllegalStateException("Invalid state");
                            }

                            read = client.getInputStream().read();
                        }
                    }
                } finally {
                    s.close();
                    client.close();
                }
            } catch (Exception ignore) {
                ignore.printStackTrace();
            }
        }
        shouldStop = started = false;

    }

    private void handleSignal(byte[] signal) {
        switch (signal[0]) {
            case (byte) 1:
                if (signal.length != 2) throw new IllegalArgumentException("Bad key to click");
                int i = toIntByBits(signal[1]);
                myBot.keyPress(i);
                myBot.keyRelease(i);
                break;
            case (byte) 2:
                if (signal.length != 2) throw new IllegalArgumentException("Bad key to press");
                myBot.keyPress(toIntByBits(signal[1]));
                break;
            case (byte) 3:
                if (signal.length != 2) throw new IllegalArgumentException("Bad key to release");
                myBot.keyRelease(toIntByBits(signal[1]));
                break;
            case (byte) 4:
                if (signal.length < 3)
                    throw new IllegalArgumentException("Bad location to move mouse to");
                String data = new String(signal, 1, signal.length - 1);
                int commaInd = data.indexOf(",");
                int x = Integer.parseInt(data.substring(0, commaInd));
                int y = Integer.parseInt(data.substring(commaInd + 1));
                myBot.mouseMove(x, y);
                mouseX = x;
                mouseY = y;
                break;
            case (byte) 5:
                if (signal.length < 3)
                    throw new IllegalArgumentException("Bad amount to move mouse by");

                data = new String(signal, 1, signal.length - 1);
                commaInd = data.indexOf(",");
                int dx = Integer.parseInt(data.substring(0, commaInd));
                int dy = Integer.parseInt(data.substring(commaInd + 1));
                myBot.mouseMove(mouseX += dx, mouseY += dy);
                break;
            case (byte) 6:
                if (signal.length != 1)
                    new IllegalArgumentException("Bad left click signal");
                myBot.mousePress(16);
                myBot.mouseRelease(16);
                break;
            case (byte) 7:
                if (signal.length != 1)
                    new IllegalArgumentException("Bad left mouse press");
                myBot.mousePress(16);
                break;
            case (byte) 8:
                if (signal.length != 1)
                    new IllegalArgumentException("Bad left mouse release");
                myBot.mouseRelease(16);
                break;
            case (byte) 9:
                if (signal.length != 1)
                    new IllegalArgumentException("Bad right click signal");
                myBot.mousePress(4);
                myBot.mouseRelease(4);
                break;
            case (byte) 10:
                if (signal.length != 1)
                    new IllegalArgumentException("Bad right mouse press");
                myBot.mousePress(4);
                break;
            case (byte) 11:
                if (signal.length != 1)
                    new IllegalArgumentException("Bad right mouse release");
                myBot.mouseRelease(4);
                break;

            default:
                throw new IllegalArgumentException("Unknow acton code: " + signal[0]);
        }
    }

    public Consumer getOnConnect() {
        return onConnect;
    }

    public void setOnConnect(Consumer onConnect) {
        this.onConnect = onConnect;
    }

    private static int toIntByBits(byte a) {//byte -> int conversion by bits: not default
        return a < 0 ? (256+a) : (int) a;
    }
}

//todo: completely tras