package edu.thesis.fct.bluedirect;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.ChannelListener;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import edu.thesis.fct.bluedirect.bt.BTService;
import edu.thesis.fct.bluedirect.bt.BluetoothBroadcastReceiver;
import edu.thesis.fct.bluedirect.bt.BluetoothServer;
import edu.thesis.fct.bluedirect.config.Configuration;
import edu.thesis.fct.bluedirect.router.Packet;
import edu.thesis.fct.bluedirect.router.Sender;
import edu.thesis.fct.bluedirect.ui.DeviceDetailFragment;
import edu.thesis.fct.bluedirect.ui.DeviceListFragment;
import edu.thesis.fct.bluedirect.ui.PromptPasswordFragment;
import edu.thesis.fct.bluedirect.ui.DeviceListFragment.DeviceActionListener;
import edu.thesis.fct.bluedirect.wifi.WiFiDirectBroadcastReceiver;

/**
 * An activity that uses WiFi Direct APIs to discover and connect with available
 * devices. WiFi Direct APIs are asynchronous and rely on callback mechanism
 * using interfaces to notify the application of operation success or failure.
 * The application should also register a BroadcastReceiver for notification of
 * WiFi state related events.
 * 
 * Note: much of this is taken from the Wi-Fi P2P example 
 */
public class WiFiDirectActivity extends Activity implements ChannelListener, DeviceActionListener {

	public static final String TAG = "wifidirectdemo";
	private WifiP2pManager manager;
	private boolean isWifiP2pEnabled = false;
	private boolean retryChannel = false;

	private final IntentFilter intentFilter = new IntentFilter();
	private final IntentFilter wifiIntentFilter = new IntentFilter();
	private final IntentFilter btIntentFilter = new IntentFilter();
	private Channel channel;
	private BroadcastReceiver receiver = null;
	private BluetoothBroadcastReceiver btReceiver = null;
	private BluetoothServer btServer;

	public static BTService btService = null;

	WifiManager wifiManager;
	private boolean isWifiConnected;

	public boolean isVisible = true;

	WiFiDirectActivity context;

	/**
	 * @param isWifiP2pEnabled
	 *            the isWifiP2pEnabled to set
	 */
	public void setIsWifiP2pEnabled(boolean isWifiP2pEnabled) {
		this.isWifiP2pEnabled = isWifiP2pEnabled;
	}

	/**
	 * On create start running listeners and try Wi-Fi bridging if possible
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		context = this;

		// add necessary intent values to be matched.
		intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
		intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
		intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
		intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

		manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
		channel = manager.initialize(this, getMainLooper(), null);

		BluedirectAPI.setOnPacketReceivedListener(new onPacketReceivedListener() {
			@Override
			public void onPacketReceived(Packet p) {
				if (p.getType().equals(Packet.TYPE.QUERY)) {
					//If it's a message display the message and update the table if they're not there
					// for whatever reason
					final String message = p.getSenderMac() + " searched:\n" + new String(p.getData());
					final String msg = new String(p.getData());
					final String name = p.getSenderMac();

					//sendFiles(p.getSenderMac(), p.getBtSMac(), context);

					context.runOnUiThread(new Runnable() {

						@Override
						public void run() {
							if (context.isVisible) {
								Toast.makeText(context, message, Toast.LENGTH_LONG).show();
							} else {
								MessageActivity.addMessage(name, msg);
							}
						}
					});
				} else {
					new SavePhotoTask().execute(p.getData());
				}
			}
		});

		final Button button = (Button) findViewById(R.id.btn_switch);
		button.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Intent i = new Intent(getApplicationContext(), MessageActivity.class);
				startActivity(i);
			}
		});

	}

	private static void sendFiles(String rcvMac, String btRcvMac, Context context){
		File file = new File(Environment.getExternalStorageDirectory() + File.separator + "photo.jpg");
		Sender.queuePacket(new Packet(Packet.TYPE.FILE, fileToBytes(file), rcvMac, WiFiDirectBroadcastReceiver.MAC, btRcvMac, Configuration.getBluetoothSelfMac(context)));
	}

	class SavePhotoTask extends AsyncTask<byte[], String, String> {
		@Override
		protected String doInBackground(byte[]... params) {

			ByteArrayInputStream bis = new ByteArrayInputStream(params[0]);
			DataInputStream dis = new DataInputStream(bis);

			String name = "";
			byte [] data = null;
			try {
				name = dis.readUTF();

				byte [] b = new byte[1024];
				int len = 0;
				ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();

				while ((len = dis.read(b)) != -1) {
					byteBuffer.write(b, 0, len);
				}

				data = byteBuffer.toByteArray();
			} catch (IOException e) {
				e.printStackTrace();
			}

			File photo=new File(Environment.getExternalStorageDirectory(), name +"");

			if (photo.exists()) {
				photo.delete();
			}

			try {
				FileOutputStream fos=new FileOutputStream(photo.getPath());

				fos.write(data);
				fos.close();
			}
			catch (java.io.IOException e) {
				e.printStackTrace();
			}

			return photo.getAbsolutePath();
		}

		@Override
		protected void onPostExecute(String s){
			Intent intent = new Intent();
			intent.setAction(Intent.ACTION_VIEW);
			intent.setDataAndType(Uri.fromFile(new File(s)), "image/*");
			startActivity(intent);
		}

	}

	private static byte[] fileToBytes(File file) {
		FileInputStream fileInputStream = null;
		byte[] bFile = new byte[(int) file.length()];
		try
		{
			//convert file into array of bytes
			fileInputStream = new FileInputStream(file);
			fileInputStream.read(bFile);
			fileInputStream.close();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		return bFile;
	}

	/** register the BroadcastReceiver with the intent values to be matched */
	@Override
	public void onResume() {
		super.onResume();
		receiver = new WiFiDirectBroadcastReceiver(manager, channel, this);
		registerReceiver(receiver, intentFilter);
		this.isVisible = true;
	}

	@Override
	public void onPause() {
		super.onPause();
		unregisterReceiver(receiver);
		this.isVisible = false;
	}

	/**
	 * Remove all peers and clear all fields. This is called on
	 * BroadcastReceiver receiving a state change event.
	 */
	public void resetData() {
		DeviceListFragment fragmentList = (DeviceListFragment) getFragmentManager().findFragmentById(R.id.frag_list);
		DeviceDetailFragment fragmentDetails = (DeviceDetailFragment) getFragmentManager().findFragmentById(
				R.id.frag_detail);
		if (fragmentList != null) {
			fragmentList.clearPeers();
		}
		if (fragmentDetails != null) {
			fragmentDetails.resetViews();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.action_items, menu);
		return true;
	}


	/**
	 * Peer discover and state transitions based on capabilities
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.atn_direct_enable:
			if (manager != null && channel != null) {

				// Since this is the system wireless settings activity, it's
				// not going to send us a result. We will be notified by
				// WiFiDeviceBroadcastReceiver instead.

				startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
			} else {
				Log.e(TAG, "channel or manager is null");
			}
			return true;

		case R.id.atn_direct_discover:
			if (!isWifiP2pEnabled) {
				// If p2p not enabled try to connect as a legacy device
				wifiManager.startScan();
				Toast.makeText(WiFiDirectActivity.this, R.string.p2p_off_warning, Toast.LENGTH_SHORT).show();
				return true;
			}
			final DeviceListFragment fragment = (DeviceListFragment) getFragmentManager().findFragmentById(
					R.id.frag_list);
			fragment.onInitiateDiscovery();
			manager.discoverPeers(channel, new WifiP2pManager.ActionListener() {

				@Override
				public void onSuccess() {
					Toast.makeText(WiFiDirectActivity.this, "Discovery Initiated", Toast.LENGTH_SHORT).show();
				}

				@Override
				public void onFailure(int reasonCode) {
					Toast.makeText(WiFiDirectActivity.this, "Discovery Failed : " + reasonCode, Toast.LENGTH_SHORT)
							.show();
				}
			});
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void showDetails(WifiP2pDevice device) {
		DeviceDetailFragment fragment = (DeviceDetailFragment) getFragmentManager().findFragmentById(R.id.frag_detail);
		fragment.showDetails(device);
	}
	
	/**
	 * Try to connect through a callback to a given device
	 */
	@Override
	public void connect(WifiP2pConfig config) {
		manager.connect(channel, config, new ActionListener() {

			@Override
			public void onSuccess() {
				// WiFiDirectBroadcastReceiver will notify us. Ignore for now.
			}

			@Override
			public void onFailure(int reason) {
				Toast.makeText(WiFiDirectActivity.this, "Connect failed. Retry.", Toast.LENGTH_SHORT).show();
			}
		});
	}

	@Override
	public void disconnect() {
		// TODO: again here it should also include the other wifi hotspot thing
		final DeviceDetailFragment fragment = (DeviceDetailFragment) getFragmentManager().findFragmentById(
				R.id.frag_detail);
		fragment.resetViews();
		manager.removeGroup(channel, new ActionListener() {

			@Override
			public void onFailure(int reasonCode) {
				Log.d(TAG, "Disconnect failed. Reason :" + reasonCode);

			}

			@Override
			public void onSuccess() {
				fragment.getView().setVisibility(View.GONE);
			}

		});
	}

	@Override
	public void onChannelDisconnected() {
		// we will try once more
		if (manager != null && !retryChannel) {
			Toast.makeText(this, "Channel lost. Trying again", Toast.LENGTH_LONG).show();
			resetData();
			retryChannel = true;
			manager.initialize(this, getMainLooper(), this);
		} else {
			Toast.makeText(this, "Severe! Channel is probably lost premanently. Try Disable/Re-Enable P2P.",
					Toast.LENGTH_LONG).show();
		}
	}

	@Override
	public void cancelDisconnect() {

		/*
		 * A cancel abort request by user. Disconnect i.e. removeGroup if
		 * already connected. Else, request WifiP2pManager to abort the ongoing
		 * request
		 */
		if (manager != null) {
			final DeviceListFragment fragment = (DeviceListFragment) getFragmentManager().findFragmentById(
					R.id.frag_list);
			if (fragment.getDevice() == null || fragment.getDevice().status == WifiP2pDevice.CONNECTED) {
				disconnect();
			} else if (fragment.getDevice().status == WifiP2pDevice.AVAILABLE
					|| fragment.getDevice().status == WifiP2pDevice.INVITED) {

				manager.cancelConnect(channel, new ActionListener() {

					@Override
					public void onSuccess() {
						Toast.makeText(WiFiDirectActivity.this, "Aborting connection", Toast.LENGTH_SHORT).show();
					}

					@Override
					public void onFailure(int reasonCode) {
						Toast.makeText(WiFiDirectActivity.this,
								"Connect abort request failed. Reason Code: " + reasonCode, Toast.LENGTH_SHORT).show();
					}
				});
			}
		}

	}

	public void displayConnectDialog(String ssid) {

		PromptPasswordFragment ppf = new PromptPasswordFragment(this, ssid);
		ppf.show(this.getFragmentManager(), ppf.getTag());

	}
}
