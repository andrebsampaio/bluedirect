package edu.thesis.fct.bluedirect.bt;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import edu.thesis.fct.bluedirect.router.AllEncompasingP2PClient;
import edu.thesis.fct.bluedirect.router.Bridge;
import edu.thesis.fct.bluedirect.router.MeshNetworkManager;
import edu.thesis.fct.bluedirect.router.Packet;

public class BluetoothServer implements Runnable {

    final static String TAG = "BT SERVER SERVICE";
    BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    private static final UUID UUID_KEY = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");
    /**
     * Flag if the receiver has been running to prevent overzealous thread spawning
     */
    public static boolean running = false;

    public static boolean bridgeEstablished = false;

    public static boolean establishingBridge = false;

    private final BluetoothServerSocket mServerSocket;

    private ConcurrentLinkedQueue<Packet> packetQueue;

    private static Bridge currentBridge;

    private static BluetoothSocket socket;

    Context context;

    public BluetoothServer(Context context, ConcurrentLinkedQueue<Packet> packetQueue){
        BluetoothServer.running = true;
        BluetoothServerSocket temp = null;
        currentBridge = null;
        this.context = context;
        this.packetQueue = packetQueue;
        try {
            temp = mBluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord("Hyrax", UUID_KEY);
        } catch (IOException e) {
            e.printStackTrace();
        }
        mServerSocket = temp;
    }

    public static void setCurrentBridge(Bridge bridge){
        currentBridge = bridge;
        bridgeEstablished = true;
    }

    public void run() {
        System.out.println("SERVER STARTED");
        while (true) {
            try {
                socket = mServerSocket.accept();
                if (socket != null) {
                    final String address = socket.getRemoteDevice().getAddress();
                    if (currentBridge == null){
                        establishingBridge = true;
                        ConnectedThread connected = new ConnectedThread(socket, null);
                        connected.start();
                        try {
                            connected.join();
                            socket.close();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    } else if (currentBridge.getBTMac().equals(address)){
                        ConnectedThread connected = new ConnectedThread(socket, currentBridge);
                        connected.start();
                    } else {
                        socket.close();
                    }

                }
            } catch (IOException e) {
                break;
            }
        }
    }



    private class ConnectedThread extends Thread {
            BluetoothSocket mSocket;
            DataInputStream input;
            DataOutputStream output;
            Bridge bridge;

            public ConnectedThread(BluetoothSocket socket, Bridge currentBridge) {
                mSocket = socket;
                this.bridge = currentBridge;
                DataInputStream tmpIn = null;
                DataOutputStream tmpOut = null;
                try {
                    tmpIn = new DataInputStream(mSocket.getInputStream());
                    tmpOut = new DataOutputStream(mSocket.getOutputStream());
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                input = tmpIn;
                output = tmpOut;
            }

            public void run(){
                try {
                    if (bridge == null){
                        String GID = input.readUTF();
                        System.out.println(GID);
                        output.writeBoolean(true);
                        if (GID.equals(MeshNetworkManager.getSelf().getGroupID())){
                            output.writeBoolean(true);
                            output.writeUTF(MeshNetworkManager.getSelf().getGroupID());
                            AllEncompasingP2PClient self = MeshNetworkManager.getSelf();
                            bridge = new Bridge(GID, mSocket.getRemoteDevice().getAddress());
                            self.setBridge(bridge);
                            self.setLastUpdate(AllEncompasingP2PClient.getUnixCurrentTime());
                            MeshNetworkManager.setSelf(self);
                            setCurrentBridge(bridge);
                            byte [] table = MeshNetworkManager.serializeRoutingTable();
                            output.writeInt(table.length);
                            output.write(table);
                            int tableSize = input.readInt();
                            byte [] rcvTable = new byte[tableSize];
                            input.readFully(rcvTable);
                            MeshNetworkManager.deserializeRoutingTableAndAdd(rcvTable);
                            output.writeBoolean(true);
                        } else {
                            throw new SameGroupException();
                        }
                    } else {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();

                        byte[] buf = new byte[1024];
                        while (true) {
                            int n = input.read(buf);
                            if (n < 0)
                                break;
                            baos.write(buf, 0, n);
                        }

                        byte trimmedBytes[] = baos.toByteArray();
                        Packet p = Packet.deserialize(trimmedBytes);
                        p.setSenderIP(p.getSenderIP());
                        packetQueue.add(p);
                        output.writeBoolean(true);
                        //
                        // mSocket.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (SameGroupException e) {
                    try {
                        if (mSocket != null)
                            mSocket.close();
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                    e.printStackTrace();
                }
            }
        }
}
