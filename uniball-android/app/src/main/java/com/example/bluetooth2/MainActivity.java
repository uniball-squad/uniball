/**
 * Uniball app v0.1
 * @author Uniball team
 *
 * based on
 * http://english.cxem.net/arduino/arduino5.php
 * by Koltykov A.V.
 */

package com.example.bluetooth2;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.UUID;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String TAG = "uniball.app";

    private Button mStartButton, mStopButton;
    private TextView mStatusView;
    private TextView mSuccessText;
    private ImageView mSuccessImage;
    private ImageView mWaitingImage;

    private Handler mHandler;

    final int RECIEVE_MESSAGE = 1;		// Status  for Handler
    private BluetoothAdapter mBluetoothAdapter = null;
    private BluetoothSocket mBluetoothSocket = null;
    private StringBuilder mMessageBuilder = new StringBuilder();

    private ConnectedThread mConnectedThread;

    // SPP UUID service
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // MAC address of Bluetooth module (you must edit this line)
    private static final String MY_ADDRESS = "98:D3:32:20:46:CC";


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        mStartButton = (Button) findViewById(R.id.start_button);
        mStopButton = (Button) findViewById(R.id.stop_button);
        mStatusView = (TextView) findViewById(R.id.status_text);
        mSuccessText = (TextView) findViewById(R.id.success_text);
        mSuccessImage = (ImageView) findViewById(R.id.success_image);
        mWaitingImage = (ImageView) findViewById(R.id.waiting_image);

        mStatusView.setMovementMethod(new ScrollingMovementMethod());

        /**
         * Handle Bluetooth messages
         */
        mHandler = new Handler() {

            public void handleMessage(android.os.Message msg) {
                switch (msg.what) {
                    case RECIEVE_MESSAGE:													// if receive massage
                        byte[] readBuf = (byte[]) msg.obj;
                        String strIncom = new String(readBuf, 0, msg.arg1);					// create string from bytes array
                        mMessageBuilder.append(strIncom);												// append string
                        int endOfLineIndex = mMessageBuilder.indexOf("\r\n");							// determine the end-of-line
                        if (endOfLineIndex > 0) { 											// if end-of-line,
                            String sbprint = mMessageBuilder.substring(0, endOfLineIndex);				// extract string
                            mMessageBuilder.delete(0, mMessageBuilder.length());										// and clear
                            mStatusView.setText(mStatusView.getText() + sbprint); 	        // update TextView
                            Log.i(TAG, sbprint);

                            mStopButton.setEnabled(true);
                            mStartButton.setEnabled(true);

                            if (sbprint.contains("You moved!!")) {
                                mSuccessText.setVisibility(View.VISIBLE);
                                mSuccessImage.setVisibility(View.VISIBLE);
                                mWaitingImage.setVisibility(View.INVISIBLE);
                            }
                        }

                        break;
                }
            };
        };

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();		// get Bluetooth adapter
        checkBTState();

        mStartButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                mConnectedThread.write("123");
                Toast.makeText(getBaseContext(), "Game started", Toast.LENGTH_SHORT).show();

                // hide success
                mSuccessText.setVisibility(View.INVISIBLE);
                mSuccessImage.setVisibility(View.INVISIBLE);
                mWaitingImage.setVisibility(View.VISIBLE);
            }
        });

        mStopButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                mConnectedThread.write("stop");
                Toast.makeText(getBaseContext(), "Game stopped", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {
        if(Build.VERSION.SDK_INT >= 10){
            try {
                final Method  m = device.getClass().getMethod("createInsecureRfcommSocketToServiceRecord", new Class[] { UUID.class });
                return (BluetoothSocket) m.invoke(device, MY_UUID);
            } catch (Exception e) {
                Log.e(TAG, "Could not create Insecure RFComm Connection",e);
                throw new RuntimeException(e);
            }
        }
        return  device.createRfcommSocketToServiceRecord(MY_UUID);
    }

    @Override
    public void onResume() {
        super.onResume();

        Log.d(TAG, "...onResume - try connect...");

        // Set up a pointer to the remote node using it's address.
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(MY_ADDRESS);

        // Two things are needed to make a connection:
        //   A MAC address, which we got above.
        //   A Service ID or UUID.  In this case we are using the
        //     UUID for SPP.

        try {
            mBluetoothSocket = createBluetoothSocket(device);
        } catch (IOException e) {
            errorExit("Fatal Error", "In onResume() and socket create failed: " + e.getMessage() + ".");
        }

        // Discovery is resource intensive.  Make sure it isn't going on
        // when you attempt to connect and pass your message.
        mBluetoothAdapter.cancelDiscovery();

        // Establish the connection.  This will block until it connects.
        Log.d(TAG, "...Connecting...");
        try {
            mBluetoothSocket.connect();
            Log.d(TAG, "....Connection ok...");
        } catch (IOException e) {
            try {
                mBluetoothSocket.close();
            } catch (IOException e2) {
                errorExit("Fatal Error", "In onResume() and unable to close socket during connection failure" + e2.getMessage() + ".");
            }
        }

        // Create a data stream so we can talk to server.
        Log.d(TAG, "...Create Socket...");

        mConnectedThread = new ConnectedThread(mBluetoothSocket);
        mConnectedThread.start();
    }

    @Override
    public void onPause() {
        super.onPause();

        Log.d(TAG, "...In onPause()...");

        try {
            mBluetoothSocket.close();
        } catch (IOException e2) {
            errorExit("Fatal Error", "In onPause() and failed to close socket." + e2.getMessage() + ".");
        }
    }

    private void checkBTState() {
        // Check for Bluetooth support and then check to make sure it is turned on
        // Emulator doesn't support Bluetooth and will return null
        if (mBluetoothAdapter == null) {
            errorExit("Fatal Error", "Bluetooth not support");
        } else {
            if (mBluetoothAdapter.isEnabled()) {
                Log.d(TAG, "...Bluetooth ON...");
            } else {
                // Prompt user to turn on Bluetooth
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, 1);
            }
        }
    }

    private void errorExit(String title, String message) {
        Toast.makeText(getBaseContext(), title + " - " + message, Toast.LENGTH_LONG).show();
        finish();
    }

    private class ConnectedThread extends Thread {
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, e.getLocalizedMessage());
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[256];  // buffer store for the stream
            int bytes; // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);		// Get number of bytes and message in "buffer"
                    mHandler.obtainMessage(RECIEVE_MESSAGE, bytes, -1, buffer).sendToTarget();		// Send to message queue Handler
                } catch (IOException e) {
                    break;
                }
            }
        }

        /* Call this from the main activity to send data to the remote device */
        public void write(String message) {
            Log.d(TAG, "...Data to send: " + message + "...");
            byte[] msgBuffer = message.getBytes();
            try {
                mmOutStream.write(msgBuffer);
            } catch (IOException e) {
                Log.d(TAG, "...Error data send: " + e.getMessage() + "...");
                Toast.makeText(getBaseContext(), "Could not send command", Toast.LENGTH_LONG).show();
            }
        }
    }
}