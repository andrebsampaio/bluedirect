package edu.thesis.fct.bluedirect.bt;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Environment;
import android.util.Log;


import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import edu.thesis.fct.bluedirect.router.AllEncompasingP2PClient;
import edu.thesis.fct.bluedirect.router.Bridge;
import edu.thesis.fct.bluedirect.router.MeshNetworkManager;
import edu.thesis.fct.bluedirect.router.Packet;
import edu.thesis.fct.bluedirect.router.Receiver;

/**
 * Created by abs on 18-07-2016.
 */
public class BTSender {

    BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    private static final UUID UUID_KEY = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");
    static boolean establishingBridge = false;


    public boolean sendPacket(String mac, Object data, boolean isPacket) {
        SendThread sender = new SendThread(mac,data,isPacket);
        Thread thread = new Thread(sender);
        thread.start();
        try {
            thread.join();
            return sender.isSent();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return false;
    }

    private class SendThread implements Runnable {
        private volatile boolean sent = false;
        private final BluetoothSocket bluetoothSocket;
        private Object data;
        private boolean isPacket;

        public SendThread(String mac, Object data, boolean isPacket)
        {
            BluetoothSocket temp = null;
            this.data = data;
            this.isPacket = isPacket;
            try {
                temp = mBluetoothAdapter.getRemoteDevice(mac).createRfcommSocketToServiceRecord(UUID_KEY);
            } catch (IOException e) {
                e.printStackTrace();
            }
            bluetoothSocket = temp;
        }

        public boolean isSent(){
            return sent;
        }

        @Override
        public void run() {
            int retries = 3;
            establishingBridge = true;
            if(mBluetoothAdapter.isDiscovering())
            {
                mBluetoothAdapter.cancelDiscovery();
            }

                while (retries > 0 && !bluetoothSocket.isConnected()){

                    try{
                        bluetoothSocket.connect();
                    }catch (IOException connectException) {
                        connectException.printStackTrace();
                        if (retries == 0){
                            try {
                                bluetoothSocket.close();
                                establishingBridge = false;
                            } catch (IOException closeException) {
                                closeException.printStackTrace();
                            }
                        } else {
                            try {
                                Thread.currentThread().sleep(2000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            retries--;
                            System.out.println("Retrying connect to " + bluetoothSocket.getRemoteDevice().getName());
                        }

                    }

                }
                System.out.println("Connected to " + bluetoothSocket.getRemoteDevice().getName());
                ConnectedThread connected = new ConnectedThread(bluetoothSocket, data, isPacket);
                Thread thread = new Thread(connected);
                thread.start();

                Log.d("BT DEBUG", "connected to:" + bluetoothSocket.getRemoteDevice().getName());
            }
    }

    private class ConnectedThread implements Runnable {
        BluetoothSocket mSocket;
        DataOutputStream output;
        DataInputStream input;
        boolean isPacket;
        Object data;

        private volatile boolean sent = false;

        public boolean isSent(){
            return sent;
        }

        public ConnectedThread(BluetoothSocket socket, Object data, boolean isPacket) {
            mSocket = socket;
            this.data = data;
            this.isPacket = isPacket;
            DataOutputStream tmpOut = null;
            DataInputStream tmpIn = null;
            try {
                tmpOut = new DataOutputStream(mSocket.getOutputStream());
                tmpIn = new DataInputStream(mSocket.getInputStream());
            } catch (IOException e) {
                e.printStackTrace();
            }
            output = tmpOut;
            input = tmpIn;

        }

        public void run(){
            if (mSocket == null) return;
            try {
                if (isPacket){
                    output.write(((Packet)data).serialize());
                } else {
                    output.writeUTF((String)data);
                }
                boolean ACK = input.readBoolean();
                if (ACK){
                    sent = true;
                    if (!isPacket){
                        boolean established = input.readBoolean();
                        if (established){
                            String otherGID = input.readUTF();
                            Bridge bridge = new Bridge(otherGID,mSocket.getRemoteDevice().getAddress());
                            AllEncompasingP2PClient self = MeshNetworkManager.getSelf();
                            self.setBridge(bridge);
                            MeshNetworkManager.setSelf(self);
                            BluetoothServer.setCurrentBridge(bridge);
                            int tableSize = input.readInt();
                            byte [] rcvTable = new byte[tableSize];
                            input.readFully(rcvTable);
                            MeshNetworkManager.deserializeRoutingTableAndAdd(rcvTable);
                            byte [] table = MeshNetworkManager.serializeRoutingTable();
                            output.writeInt(table.length);
                            output.write(table);
                        } else {
                            establishingBridge = false;
                        }

                    }
                    //mSocket.close();
                }


            } catch (Exception e) {
                establishingBridge = false;
                e.printStackTrace();
            }
        }
    }
}
