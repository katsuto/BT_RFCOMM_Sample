package jp.aicl.bt_rfcomm_sample;

import android.os.Bundle;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    static final String TAG = "BT_RFCOMM";

    private TextView txtStatus;
    private TextView txtReceivedData;
    private TextView txtSendData;
    private EditText etxtAddress;
    private EditText etxtLength;
    private EditText etxtData;
    private TextView etxtTarget;
    public final static char CHAR_CR = (char) 0x0D;

    private BluetoothAdapter bluetoothAdapter;
    private BTClientThread btClientThread;
    private static final UUID BT_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
    private static final String BT_DEVICE = "raspberrypi";
    private static final int MSG_BT = 0;
    private static final int MSG_RECVDATA = 1;

    final Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            String s;
            switch (msg.what) {
                case MSG_BT:
                    s = (String) msg.obj;
                    if (s != null) {
                        txtStatus.setText(s);
                    }
                    break;
                case MSG_RECVDATA:
                    s = (String) msg.obj;
                    if (s != null) {
                        txtReceivedData.setText(s);
                    }
                    break;
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        txtStatus = (TextView) findViewById(R.id.textViewStatus);
        txtReceivedData = (TextView) findViewById(R.id.textViewReceivedData);
        txtSendData = (TextView) findViewById(R.id.textViewSendData);
        etxtAddress = (EditText) findViewById(R.id.editTextAddress);
        etxtLength = (EditText) findViewById(R.id.editTextLength);
        etxtData = (EditText) findViewById(R.id.editTextData);
        etxtTarget = (EditText) findViewById(R.id.editTextTarget);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Log.d(TAG, "This device doesn't support bluetooth");
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putString("txtStatus", txtStatus.getText().toString());
        outState.putString("txtReceivedData", txtReceivedData.getText().toString());
        outState.putString("txtSendData", txtSendData.getText().toString());
        outState.putString("etxtAddress", etxtAddress.getText().toString());
        outState.putString("etxtLength", etxtLength.getText().toString());
        outState.putString("etxtData", etxtData.getText().toString());
        outState.putString("etxtTarget", etxtTarget.getText().toString());
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        setContentView(R.layout.activity_main);

        txtStatus.setText(savedInstanceState.getString("txtStatus"));
        txtReceivedData.setText(savedInstanceState.getString("txtReceivedData"));
        txtSendData.setText(savedInstanceState.getString("txtSendData"));
        etxtAddress.setText(savedInstanceState.getString("etxtAddress"));
        etxtLength.setText(savedInstanceState.getString("etxtLength"));
        etxtData.setText(savedInstanceState.getString("etxtData"));
        etxtTarget.setText(savedInstanceState.getString("etxtTarget"));
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (btClientThread != null) {
            btClientThread.interrupt();
            btClientThread = null;
        }
    }

    public void buttonDefault_onClick(View view) {
        Log.d("Button clicked", "Default");

        etxtAddress.setText("005010");
        etxtLength.setText("0002");
        etxtData.setText("ABCDABCD");
    }

    public void buttonSend_onClick(View view) {
        Log.d("Button clicked", "Send");
        txtStatus.setText("SEND START");
        String cmd = "S" + etxtAddress.getText().toString() + etxtLength.getText().toString() + etxtData.getText().toString();
        txtSendData.setText(cmd);
        btClientThread = new BTClientThread();
        btClientThread.start();
    }

    public void buttonRead_onClick(View view) {
        Log.d("Button clicked", "Read");
        txtStatus.setText("READ START");
        String cmd = "R" + etxtAddress.getText().toString() + etxtLength.getText().toString();
        txtSendData.setText(cmd);
        btClientThread = new BTClientThread();
        btClientThread.start();
    }

    public class BTClientThread extends Thread {
        InputStream inputStream;
        OutputStream outputStream;
        BluetoothSocket bluetoothSocket;

        public void run() {
            byte[] receivedBuff = new byte[1024];

            BluetoothDevice bluetoothDevice = null;
            Set<BluetoothDevice> devices = bluetoothAdapter.getBondedDevices();
            handler.obtainMessage(MSG_BT, "BT TARGET SERCHING");

            for (BluetoothDevice device : devices) {
                Log.d(TAG, "BondedDevices:" + device.getName().toString());
                if (device.getName().equals(etxtTarget.getText().toString().trim())) {
                    bluetoothDevice = device;
                    break;
                }
            }

            if (bluetoothDevice == null) {
                handler.obtainMessage(MSG_BT, "BT CANNOT FIND TARGET");
                Log.d(TAG, "No device found.");
                return;
            }

            try {
                bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(BT_UUID);

                try {
                    bluetoothSocket.connect();

                    handler.obtainMessage(MSG_BT, "CONNECTED " + bluetoothDevice.getName()).sendToTarget();

                    inputStream = bluetoothSocket.getInputStream();
                    outputStream = bluetoothSocket.getOutputStream();

                    String senddata = txtSendData.getText().toString() + CHAR_CR;
                    outputStream.write(senddata.getBytes());
                    handler.obtainMessage(MSG_BT, "SENT DATA " + bluetoothDevice.getName()).sendToTarget();
                    Thread.sleep(5000);
                    int receivedBytes = inputStream.read(receivedBuff);
                    byte[] buffer = new byte[receivedBytes];
                    System.arraycopy(receivedBuff, 0, buffer, 0, receivedBytes);
                    String s = new String(buffer, StandardCharsets.UTF_8);
                    handler.obtainMessage(MSG_BT, "RECEIVED DATA " + bluetoothDevice.getName()).sendToTarget();

                    handler.obtainMessage(MSG_RECVDATA, s).sendToTarget();

                } catch (IOException e) {
                    Log.d(TAG, e.getMessage());
                }

                handler.obtainMessage(MSG_BT, "DISCONNECTED").sendToTarget();

            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (bluetoothSocket != null) {
                    try {
                        bluetoothSocket.close();
                    } catch (IOException e) {
                    }
                    bluetoothSocket = null;
                }
            }

            handler.obtainMessage(MSG_BT, "DISCONNECTED - Exit thread").sendToTarget();
        }
    }
}