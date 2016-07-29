package edu.thesis.fct.bluedirect;

import edu.thesis.fct.bluedirect.router.Packet;

/**
 * Created by abs on 10-07-2016.
 */
public interface onPacketReceivedListener {
    public abstract void onPacketReceived(Packet p);
}
