package nl.das.terraria;

import android.annotation.SuppressLint;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import nl.das.terraria.json.Command;
import nl.das.terraria.json.Response;

public class BTService extends Service {
    public static final int MSG_REGISTER_CLIENT = 1;
    public static final int MSG_UNREGISTER_CLIENT = 2;
    public static final int CMD_GET_PROPERTIES = 3;
    public static final int CMD_GET_SENSORS = 4;
    public static final int CMD_GET_STATE = 5;
    public static final int CMD_GET_TIMERS = 6;

    enum Commands {
        getProperties(CMD_GET_PROPERTIES),
        getSensors(CMD_GET_SENSORS),
        getState(CMD_GET_STATE),
        getTimers(CMD_GET_TIMERS);

        private int value;
        private Commands(int value) {
            this.value = value;
        }
    }
    // Bluetooth stuff
    private List<BluetoothDevice> pairedDevices = new ArrayList<>();
    private BluetoothDevice connectedDevice;
    private String curUuid;
    private BluetoothSocket mmSocket;
    private static ConnectedThread connectedThread;
    /** Keeps track of all current registered clients. */
    private static Map<Integer, Set<Messenger>> clients = new HashMap<>();

    @SuppressLint("MissingPermission")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        /*
        - Get the Bluetooth device and its service UUID
        - create a socket connected to the service
        - create a thread that monitors the incoming bytes (response from server)
         */
        curUuid = intent.getStringExtra("UUID");
        connectedDevice = intent.getParcelableExtra("Device");
        Log.i("TerrariaBT","BTService: Start: device=" + connectedDevice.getName() + " uuid=" + curUuid);
        try {
            // Get a BluetoothSocket to connect with the given BluetoothDevice.
            // the app's UUID string, also used in the server code.
            mmSocket = connectedDevice.createInsecureRfcommSocketToServiceRecord(UUID.fromString(curUuid));

            BluetoothManager bluetoothManager = getSystemService(BluetoothManager.class);
            BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
            bluetoothAdapter.cancelDiscovery();
            // Connect to the remote device through the socket. This call blocks
            // until it succeeds or throws an exception.
            Log.i("TerrariaBT","BTService: Connecting to socket....");
            mmSocket.connect();
            if (mmSocket.isConnected()) {
                Log.i("TerrariaBT","BTService: Connected. Start listening thread... ");
                connectedThread = new ConnectedThread(mmSocket);
                connectedThread.start();
            }
        } catch (IOException connectException) {
            // Unable to connect; close the socket and return.
            Log.e("TerrariaBT", "Could not connect to socket", connectException);
            try {
                if (mmSocket != null) {
                    mmSocket.close();
                }
            } catch (IOException closeException) {
                Log.e("TerrariaBT", "Could not close the client socket", closeException);
            }
        }
        return START_NOT_STICKY;
    }

    /**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    Messenger mMessenger;

    /**
     * When binding to the service, we return an interface to our messenger
     * for sending messages to the service.
     */
    @Override
    public IBinder onBind(Intent intent) {
        mMessenger = new Messenger(new IncomingHandler(this));
        return mMessenger.getBinder();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i("TerrariaBT","BTService: Stopping.....");
        connectedThread.cancel();
        connectedThread.interrupt();
        try {
            if (mmSocket != null) {
                mmSocket.close();
            }
        } catch (IOException closeException) {
            Log.e("TerrariaBT", "Could not close the client socket", closeException);
        }
        while (connectedThread.isAlive()) {}
    }

    private void sendResponse(int cmd, JsonObject obj) {
        Log.i("TerrariaBT","BTService: sendResponse() for command " + cmd);
        // Go through all Messengers that are regitered for the given command
        // and when found sent it the message.
        for (Messenger m : clients.get(cmd)) {
            try {
                Message res = Message.obtain(null, cmd);
                res.obj = new Gson().toJson(obj);
                m.send(res);
            } catch (RemoteException e) {
                // The client is dead.  Remove it from the list;
                // we are going through the list from back to front
                // so this is safe to do inside the loop.
                for (int c : clients.keySet()) {
                    clients.get(c).remove(m);
                }
            }
        }
    }

    /**
     * Handler of incoming messages from clients.
     */
    static class IncomingHandler extends Handler {
        private Context applicationContext;

        IncomingHandler(Context context) {
            applicationContext = context.getApplicationContext();
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            Log.i("TerrariaBT","BTService: Received message " + msg.what);
            switch (msg.what) {
                case MSG_REGISTER_CLIENT:
                    Set<Integer> cmds = new HashSet(msg.getData().getIntegerArrayList("commands"));
                    register(msg.replyTo, cmds);
                    break;
                case MSG_UNREGISTER_CLIENT:
                    for (int c : clients.keySet()) {
                        clients.get(c).remove(msg.replyTo);
                    }
                    break;
                case CMD_GET_PROPERTIES:
                    connectedThread.write(new Gson().toJson(new Command(Commands.getProperties.name(), null)));
                    break;
                case CMD_GET_SENSORS:
                    connectedThread.write(new Gson().toJson(new Command(Commands.getSensors.name(), null)));
                    break;
                case CMD_GET_STATE:
                    connectedThread.write(new Gson().toJson(new Command(Commands.getState.name(), null)));
                    break;
                default:
            }
        }
    }

    private static void register(Messenger msgr, Set<Integer> cmds) {
        Log.i("TerrariaBT","BTService: Register client for commands " + cmds);
        for (Integer cmd : cmds) {
            Set<Messenger> m = clients.get(cmd);
            if (m == null) clients.put(cmd, new HashSet<>());
            clients.get(cmd).add(msgr);
        }
    }

    /*
     * The thread that will communicate over the socket opened by the ConnectThread
     */
    private class ConnectedThread extends Thread {

        private final BluetoothSocket mmSocket;
        private InputStream mmInStream;
        private OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket mmSocket) throws IOException {
            this.mmSocket = mmSocket;
            if (!mmSocket.isConnected()) {
                Log.e("TerrariaBT", "BTService: Socket is not connected.");
            }
        }

        @Override
        public void run() {
            // Keep listening to the InputStream until an exception occurs.
            try {
                mmInStream = mmSocket.getInputStream();
                // Read from the InputStream.
                int b;
                StringBuilder sb = new StringBuilder();
                while ((b = mmInStream.read()) != -1) {
                    if (b == 0x03) { //ETX character
                        String message = sb.toString();
                        Log.i("TerrariaBT","BTService: Received a message of size  " + message.length());
                        Response res = new Gson().fromJson(message, Response.class);
                        sendResponse(Commands.valueOf(res.getCommand()).value, res.getResponse());
                        sb = new StringBuilder();
                    } else {
                        sb.append((char) b);
                    }
                }
            } catch (IOException e) {
                Log.e("TerrariaBT", "BTService: Input stream IO error: " + e.getMessage());
            }
        }

        // Call this from the main activity to send data to the remote device.
        public void write(String msg) {
            try {
                mmOutStream = mmSocket.getOutputStream();
                mmOutStream.write(msg.getBytes());
                mmOutStream.write(0x03);
            } catch (IOException e) {
            }
        }

        // Call this method from the main activity to shut down the connection.
        public void cancel() {
            try {
                if (mmInStream != null && mmOutStream != null) {
                    mmInStream.close();
                    mmOutStream.close();
                }
            } catch (IOException e) { }
        }
    }

}