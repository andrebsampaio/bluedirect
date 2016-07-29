package edu.thesis.fct.bluedirect.bt;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.SystemClock;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import edu.thesis.fct.bluedirect.router.MeshNetworkManager;
import edu.thesis.fct.bluedirect.router.Packet;

/**
 *  A BroadcastReceiver that notifies of important bluetooth connection events.
 */
public class BluetoothBroadcastReceiver extends BroadcastReceiver {

    private static final UUID UUID_KEY = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");
    private BluetoothAdapter mBluetoothAdapter;
    private Queue<BluetoothDevice> foundDevices = new LinkedList<>();
    public static BTSender btSender = null;
    public BluetoothBroadcastReceiver(){
        super();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        btSender = new BTSender();

        if (BluetoothDevice.ACTION_FOUND.equals(action)) {
            for (BluetoothDevice d : foundDevices){
                if (d.getAddress().equals(device)) return;
            }
            foundDevices.add(device);
            System.out.println("FOUND " + device.getName() + " with " + device.getAddress());
        }
        else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
            //Device is now connected
        }
        else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
            System.out.println("BT discovery finished");
            new Thread()
            {
                public void run() {
                    while (!BluetoothServer.bridgeEstablished){
                        if (foundDevices.isEmpty()){
                            try {
                                this.sleep(2000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            mBluetoothAdapter.startDiscovery();
                            return;
                        }
                        while (MeshNetworkManager.routingTable.size() == 1){
                            try {
                                this.sleep(2000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        while (!foundDevices.isEmpty()){
                            BluetoothDevice deviceTmp = foundDevices.remove();
                            if (MeshNetworkManager.getSelf() != null && !BluetoothServer.establishingBridge && !btSender.establishingBridge){
                                btSender.sendPacket(deviceTmp.getAddress(),MeshNetworkManager.getSelf().getGroupID(),false);
                            }
                        }
                    }
                }
            }.start();

        }
        else if (BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED.equals(action)) {
            //Device is about to disconnect
        }
        else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
            //Device has disconnected
        }
    }
}
