package edu.thesis.fct.bluedirect.router;

import java.util.concurrent.ConcurrentLinkedQueue;

import edu.thesis.fct.bluedirect.WiFiDirectActivity;
import edu.thesis.fct.bluedirect.bt.BluetoothBroadcastReceiver;
import edu.thesis.fct.bluedirect.config.Configuration;
import edu.thesis.fct.bluedirect.router.tcp.TcpSender;

/**
 * Responsible for sending all packets that appear in the queue
 *
 */
public class Sender implements Runnable {

	/**
	 * Queue for packets to send
	 */
	private static ConcurrentLinkedQueue<Packet> ccl;

	/**
	 * Constructor
	 */
	public Sender() {
		if (ccl == null)
			ccl = new ConcurrentLinkedQueue<Packet>();
	}

	/**
	 * Enqueue a packet to send
	 * @param p
	 * @return
	 */
	public static boolean queuePacket(Packet p) {
		if (ccl == null)
			ccl = new ConcurrentLinkedQueue<Packet>();
		return ccl.add(p);
	}

	@Override
	public void run() {
		TcpSender packetSender = new TcpSender();

		while (true) {
			//Sleep to give up CPU cycles
			while (ccl.isEmpty()) {
				try {
					Thread.sleep(2000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}

			Packet p = ccl.remove();
			IPBundle bundle = MeshNetworkManager.getIPForClient(p.getMac());

			if (bundle.getMethod().equals(Packet.METHOD.WD)){
				packetSender.sendPacket(bundle.getAddress(), Configuration.RECEIVE_PORT, p);
			} else {
				WiFiDirectActivity.btService.write(p.serialize());
			}

		}
	}

}
