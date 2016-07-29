package edu.thesis.fct.bluedirect;

import android.content.Context;

import edu.thesis.fct.bluedirect.config.Configuration;
import edu.thesis.fct.bluedirect.router.AllEncompasingP2PClient;
import edu.thesis.fct.bluedirect.router.MeshNetworkManager;
import edu.thesis.fct.bluedirect.router.Packet;
import edu.thesis.fct.bluedirect.router.Receiver;
import edu.thesis.fct.bluedirect.router.Sender;
import edu.thesis.fct.bluedirect.wifi.WiFiDirectBroadcastReceiver;

/**
 * Created by abs on 10-07-2016.
 */
public class BluedirectAPI {

    public static void broadcastQuery(String msg, Context activity){
        // Send to other clients as a group chat message
        for (AllEncompasingP2PClient c : MeshNetworkManager.routingTable.values()) {
            if (c.getMac().equals(MeshNetworkManager.getSelf().getMac()))
                continue;
            Sender.queuePacket(new Packet(Packet.TYPE.QUERY, msg.getBytes(), c.getMac(),
                    WiFiDirectBroadcastReceiver.MAC, c.getBtmac(), Configuration.getBluetoothSelfMac(activity)));
        }
    }

    public static void broadcastFile(byte[] file, Context activity){
        for (AllEncompasingP2PClient c : MeshNetworkManager.routingTable.values()) {
            if (c.getMac().equals(MeshNetworkManager.getSelf().getMac()))
                continue;
            Sender.queuePacket(new Packet(Packet.TYPE.FILE, file, c.getMac(),
                    WiFiDirectBroadcastReceiver.MAC, c.getBtmac(), Configuration.getBluetoothSelfMac(activity)));
        }
    }

    public static void addOnPacketReceivedListener(onPacketReceivedListener listener){
        Receiver.addListener(listener);
    }





}
