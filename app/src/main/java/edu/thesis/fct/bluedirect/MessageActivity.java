package edu.thesis.fct.bluedirect;

import edu.thesis.fct.bluedirect.router.AllEncompasingP2PClient;
import edu.thesis.fct.bluedirect.router.MeshNetworkManager;
import edu.thesis.fct.bluedirect.router.Packet;
import edu.thesis.fct.bluedirect.router.Sender;
import edu.thesis.fct.bluedirect.wifi.WiFiDirectBroadcastReceiver;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * Activity for the group chat view
 * @author Peter Henderson
 *
 */
public class MessageActivity extends Activity {
	public static AllEncompasingP2PClient RECIPIENT = null;

	private static TextView messageView;

    private static final int SELECT_PICTURE = 1;

	/**
	 * Add appropriate listeners on creation
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.message);

		messageView = (TextView) findViewById(R.id.message_view);

		final Button buttonText = (Button) findViewById(R.id.btn_send);
		final Button buttonFile = (Button) findViewById(R.id.btn_file);
		final EditText message = (EditText) findViewById(R.id.edit_message);

		this.setTitle("Group Chat");

		buttonText.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				String msgStr = message.getText().toString();
				addMessage("This phone", msgStr);
				message.setText("");

				// Send to other clients as a group chat message
				for (AllEncompasingP2PClient c : MeshNetworkManager.routingTable.values()) {
					if (c.getMac().equals(MeshNetworkManager.getSelf().getMac()))
						continue;
					Sender.queuePacket(new Packet(Packet.TYPE.QUERY, msgStr.getBytes(), c.getMac(),
							WiFiDirectBroadcastReceiver.MAC));
				}

			}
		});

        buttonFile.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setType("image/*");
                intent.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(Intent.createChooser(intent,
                        "Select Picture"), SELECT_PICTURE);
            }
        });


	}

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            if (requestCode == SELECT_PICTURE) {
                Uri selectedImageUri = data.getData();

                byte [] file = UriToBytes(selectedImageUri);
                // Send to other clients as a group chat message
                for (AllEncompasingP2PClient c : MeshNetworkManager.routingTable.values()) {
                    if (c.getMac().equals(MeshNetworkManager.getSelf().getMac()))
                        continue;
                    Sender.queuePacket(new Packet(Packet.TYPE.FILE, file, c.getMac(),
                            WiFiDirectBroadcastReceiver.MAC));
                }

            }
        }
    }

    private byte[] UriToBytes(Uri uri){
        try {
            InputStream iStream = getContentResolver().openInputStream(uri);
            Cursor returnCursor =
                    getContentResolver().query(uri, null, null, null, null);
            int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            returnCursor.moveToFirst();

            byte[] inputData = getBytes(iStream, returnCursor.getString(nameIndex));
            return inputData;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    static byte[] getBytes(InputStream inputStream, String name) throws IOException {
        ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(byteBuffer);

        int bufferSize = 1024;
        byte[] buffer = new byte[bufferSize];

        dos.writeUTF(name);

        int len = 0;
        while ((len = inputStream.read(buffer)) != -1) {
            dos.write(buffer, 0, len);
        }
        dos.flush();
        return byteBuffer.toByteArray();
    }

    private static byte[] intToBytes( final int i ) {
        ByteBuffer bb = ByteBuffer.allocate(4);
        bb.putInt(i);
        return bb.array();
    }

	/**
	 * Add a message to the view
	 */
	public static void addMessage(String from, String text) {
		
		messageView.append(from + " searched " + text + "\n");
		final int scrollAmount = messageView.getLayout().getLineTop(messageView.getLineCount())
				- messageView.getHeight();
		// if there is no need to scroll, scrollAmount will be <=0
		if (scrollAmount > 0)
			messageView.scrollTo(0, scrollAmount);
		else
			messageView.scrollTo(0, 0);
	}

}
