package nl.das.terraria;

import android.annotation.SuppressLint;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
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
    public static final int CMD_SET_DEVICE_ON = 6;
    public static final int CMD_SET_DEVICE_ON_FOR = 7;
    public static final int CMD_SET_DEVICE_OFF = 8;
    public static final int CMD_SET_DEVICE_MANUAL_ON = 9;
    public static final int CMD_SET_DEVICE_MANUAL_OFF = 10;
    public static final int CMD_SET_LIFECYCLE_COUNTER = 11;
    public static final int CMD_GET_TIMERS = 12;
    public static final int CMD_SET_TIMERS = 13;
    public static final int CMD_GET_RULESET = 14;
    public static final int CMD_SET_RULESET = 15;
    public static final int CMD_GET_SPRAYERRULE = 16;
    public static final int CMD_SET_SPRAYERRULE = 17;
    public static final int CMD_GET_TEMP_FILES = 18;
    public static final int CMD_GET_STATE_FILES = 19;
    public static final int CMD_GET_TEMP_FILE = 20;
    public static final int CMD_GET_STATE_FILE = 21;
    public static final int MSG_DISCONNECTED = 22;

    enum Commands {
        getProperties(CMD_GET_PROPERTIES),
        getSensors(CMD_GET_SENSORS),
        getState(CMD_GET_STATE),
        setDeviceOn(CMD_SET_DEVICE_ON),
        setDeviceOnFor(CMD_SET_DEVICE_ON_FOR),
        setDeviceOff(CMD_SET_DEVICE_OFF),
        setDeviceManualOn(CMD_SET_DEVICE_MANUAL_ON),
        setDeviceManualOff(CMD_SET_DEVICE_MANUAL_OFF),
        setLifecycleCounter(CMD_SET_LIFECYCLE_COUNTER),
        getTimersForDevice(CMD_GET_TIMERS),
        replaceTimers(CMD_SET_TIMERS),
        getRuleset(CMD_GET_RULESET),
        saveRuleset(CMD_SET_RULESET),
        getSprayerRule(CMD_GET_SPRAYERRULE),
        setSprayerRule(CMD_SET_SPRAYERRULE),
        getTempTracefiles(CMD_GET_TEMP_FILES),
        getStateTracefiles(CMD_GET_STATE_FILES),
        getTemperatureFile(CMD_GET_TEMP_FILE),
        getStateFile(CMD_GET_STATE_FILE);

        private final int value;
        Commands(int value) {
            this.value = value;
        }
    }
    // Bluetooth stuff
    private static boolean connected;
    private BluetoothSocket mmSocket;
    private static ConnectedThread connectedThread;
    /** Keeps track of all current registered clients. */
    private static final Map<Integer, Set<Messenger>> clients = new HashMap<>();

    @SuppressLint("MissingPermission")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        /*
        - Get the Bluetooth device and its service UUID
        - create a socket connected to the service
        - create a thread that monitors the incoming bytes (response from server)
         */
        String curUuid = intent.getStringExtra("UUID");
        BluetoothDevice connectedDevice = intent.getParcelableExtra("Device");
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
            connected = true;
        } catch (IOException connectException) {
            // Unable to connect; close the socket and return.
            Log.e("TerrariaBT", "BTService: Could not connect to socket");
            try {
                if (mmSocket != null) {
                    mmSocket.close();
                }
            } catch (IOException closeException) {
                Log.e("TerrariaBT", "BTService: Could not close the client socket", closeException);
            }
            connected = false;
        }
        return START_STICKY;
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
        mMessenger = new Messenger(new IncomingHandler());
        return mMessenger.getBinder();
    }

    @Override
    public void onDestroy() {
        Log.i("TerrariaBT","BTService: Stopping.....");
        if (connectedThread != null && connectedThread.isAlive()) {
            connectedThread.cancel();
            connectedThread.interrupt();
            try {
                if (mmSocket != null) {
                    mmSocket.close();
                }
            } catch (IOException closeException) {
                Log.e("TerrariaBT", "Could not close the client socket", closeException);
            }
            while (connectedThread.isAlive()) {
            }
        }
        super.onDestroy();
    }

    private void sendResponse(int cmd, JsonObject obj) {
        Log.i("TerrariaBT","BTService: sendResponse() for command " + cmd);
        // Go through all Messengers that are regitered for the given command
        // and when found sent it the message.
        for (Messenger m : Objects.requireNonNull(clients.get(cmd))) {
            try {
                Message res = Message.obtain(null, cmd);
                if (obj != null) {
                    res.obj = new Gson().toJson(obj);
                }
                m.send(res);
            } catch (RemoteException e) {
                // The client is dead.  Remove it from the list;
                // we are going through the list from back to front
                // so this is safe to do inside the loop.
                for (int c : clients.keySet()) {
                    Objects.requireNonNull(clients.get(c)).remove(m);
                }
            }
        }
    }

    /**
     * Handler of incoming messages from clients.
     */
    @SuppressLint("HandlerLeak")
    class IncomingHandler extends Handler {

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            Log.i("TerrariaBT","BTService: Received message " + msg.what);
            switch (msg.what) {
                case MSG_REGISTER_CLIENT: {
                    Set<Integer> cmds = new HashSet<>(msg.getData().getIntegerArrayList("commands"));
                    register(msg.replyTo, cmds);
                    break;
                }
                case MSG_UNREGISTER_CLIENT: {
                    for (int c : clients.keySet()) {
                        Objects.requireNonNull(clients.get(c)).remove(msg.replyTo);
                    }
                    break;
                }
                case CMD_GET_PROPERTIES: {
                    if (!connected) {
                        sendResponse(MSG_DISCONNECTED, null);
                    } else {
                        connectedThread.write(new Gson().toJson(new Command(Commands.getProperties.name(), null)));
                    }
                    break;
                }
                case CMD_GET_SENSORS: {
                    if (!connected) {
                        sendResponse(MSG_DISCONNECTED, null);
                    } else {
                        connectedThread.write(new Gson().toJson(new Command(Commands.getSensors.name(), null)));
                    }
                    break;
                }
                case CMD_GET_STATE: {
                    if (!connected) {
                        sendResponse(MSG_DISCONNECTED, null);
                    } else {
                        connectedThread.write(new Gson().toJson(new Command(Commands.getState.name(), null)));
                    }
                    break;
                }
                case CMD_SET_DEVICE_ON: {
                    if (!connected) {
                        sendResponse(MSG_DISCONNECTED, null);
                    } else {
                        JsonObject data = new JsonObject();
                        data.addProperty("device", msg.obj.toString());
                        connectedThread.write(new Gson().toJson(new Command(Commands.setDeviceOn.name(), data)));
                    }
                    break;
                }
                case CMD_SET_DEVICE_ON_FOR: {
                    if (!connected) {
                        sendResponse(MSG_DISCONNECTED, null);
                    } else {
                        JsonObject data = new JsonObject();
                        data.addProperty("device", msg.obj.toString());
                        data.addProperty("period", msg.arg1);
                        connectedThread.write(new Gson().toJson(new Command(Commands.setDeviceOnFor.name(), data)));
                    }
                    break;
                }
                case CMD_SET_DEVICE_OFF: {
                    if (!connected) {
                        sendResponse(MSG_DISCONNECTED, null);
                    } else {
                        JsonObject data = new JsonObject();
                        data.addProperty("device", msg.obj.toString());
                        connectedThread.write(new Gson().toJson(new Command(Commands.setDeviceOff.name(), data)));
                    }
                    break;
                }
                case CMD_SET_DEVICE_MANUAL_ON: {
                    if (!connected) {
                        sendResponse(MSG_DISCONNECTED, null);
                    } else {
                        JsonObject data = new JsonObject();
                        data.addProperty("device", msg.obj.toString());
                        connectedThread.write(new Gson().toJson(new Command(Commands.setDeviceManualOn.name(), data)));
                    }
                    break;
                }
                case CMD_SET_DEVICE_MANUAL_OFF: {
                    if (!connected) {
                        sendResponse(MSG_DISCONNECTED, null);
                    } else {
                        JsonObject data = new JsonObject();
                        data.addProperty("device", msg.obj.toString());
                        connectedThread.write(new Gson().toJson(new Command(Commands.setDeviceManualOff.name(), data)));
                    }
                    break;
                }
                case CMD_SET_LIFECYCLE_COUNTER: {
                    if (!connected) {
                        sendResponse(MSG_DISCONNECTED, null);
                    } else {
                        JsonObject data = new JsonObject();
                        data.addProperty("device", msg.obj.toString());
                        data.addProperty("hours", msg.arg1);
                        connectedThread.write(new Gson().toJson(new Command(Commands.setLifecycleCounter.name(), data)));
                    }
                    break;
                }
                case CMD_GET_TIMERS: {
                    if (!connected) {
                        sendResponse(MSG_DISCONNECTED, null);
                    } else {
                        JsonObject data = new JsonObject();
                        data.addProperty("device", msg.obj.toString());
                        connectedThread.write(new Gson().toJson(new Command(Commands.getTimersForDevice.name(), data)));
                    }
                    break;
                }
                case CMD_SET_TIMERS: {
                    if (!connected) {
                        sendResponse(MSG_DISCONNECTED, null);
                    } else {
                        JsonObject data = new JsonObject();
                        data.addProperty("timers", msg.obj.toString());
                        connectedThread.write(new Gson().toJson(new Command(Commands.replaceTimers.name(), data)));
                    }
                    break;
                }
                case CMD_GET_RULESET: {
                    if (!connected) {
                        sendResponse(MSG_DISCONNECTED, null);
                    } else {
                        JsonObject data = new JsonObject();
                        data.addProperty("rulesetnr", msg.arg1);
                        connectedThread.write(new Gson().toJson(new Command(Commands.getRuleset.name(), data)));
                    }
                    break;
                }
                case CMD_SET_RULESET: {
                    if (!connected) {
                        sendResponse(MSG_DISCONNECTED, null);
                    } else {
                        JsonObject data = new JsonObject();
                        data.addProperty("rulesetnr", msg.arg1);
                        data.addProperty("ruleset", msg.obj.toString());
                        connectedThread.write(new Gson().toJson(new Command(Commands.saveRuleset.name(), data)));
                    }
                    break;
                }
                case CMD_GET_SPRAYERRULE: {
                    if (!connected) {
                        sendResponse(MSG_DISCONNECTED, null);
                    } else {
                        connectedThread.write(new Gson().toJson(new Command(Commands.getSprayerRule.name(), null)));
                    }
                    break;
                }
                case CMD_SET_SPRAYERRULE: {
                    if (!connected) {
                        sendResponse(MSG_DISCONNECTED, null);
                    } else {
                        JsonObject data = JsonParser.parseString(msg.obj.toString()).getAsJsonObject();
                        connectedThread.write(new Gson().toJson(new Command(Commands.setSprayerRule.name(), data)));
                    }
                    break;
                }
                case CMD_GET_TEMP_FILES: {
                    if (!connected) {
                        sendResponse(MSG_DISCONNECTED, null);
                    } else {
                        connectedThread.write(new Gson().toJson(new Command(Commands.getTempTracefiles.name(), null)));
                    }
                    break;
                }
                case CMD_GET_TEMP_FILE: {
                    if (!connected) {
                        sendResponse(MSG_DISCONNECTED, null);
                    } else {
                        JsonObject data = new JsonObject();
                        data.addProperty("fname", msg.obj.toString());
                        connectedThread.write(new Gson().toJson(new Command(Commands.getTemperatureFile.name(), data)));
                    }
                    break;
                }
                case CMD_GET_STATE_FILES: {
                    if (!connected) {
                        sendResponse(MSG_DISCONNECTED, null);
                    } else {
                        connectedThread.write(new Gson().toJson(new Command(Commands.getStateTracefiles.name(), null)));
                    }
                    break;
                }
                case CMD_GET_STATE_FILE: {
                    if (!connected) {
                        sendResponse(MSG_DISCONNECTED, null);
                    } else {
                        JsonObject data = new JsonObject();
                        data.addProperty("fname", msg.obj.toString());
                        connectedThread.write(new Gson().toJson(new Command(Commands.getStateFile.name(), data)));
                    }
                    break;
                }
                default:
                    Log.e("TerrariaBT", "BTService: Unimplemented command " + msg.what);
            }
        }
    }

    private static void register(Messenger msgr, Set<Integer> cmds) {
        Log.i("TerrariaBT","BTService: Register client for commands " + cmds);
        for (Integer cmd : cmds) {
            clients.computeIfAbsent(cmd, k -> new HashSet<>());
            Objects.requireNonNull(clients.get(cmd)).add(msgr);
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
//                        Log.i("TerrariaBT", message);
                        Response res = new Gson().fromJson(message, Response.class);
                        sendResponse(Commands.valueOf(res.getCommand()).value, res.getResponse());
                        sb = new StringBuilder();
                    } else {
                        sb.append((char) b);
                    }
                }
            } catch (IOException e) {
                Log.e("TerrariaBT", "BTService: Input stream IO error: " + e.getMessage());
//                sendResponse(MSG_DISCONNECTED, null);
            }
        }

        // Call this from the main activity to send data to the remote device.
        public void write(String msg) {
            try {
                mmOutStream = mmSocket.getOutputStream();
                mmOutStream.write(msg.getBytes());
                mmOutStream.write(0x03);
            } catch (IOException e) {
                Log.e("TerrariaBT", "BTService: Write IO error: " + e.getMessage());
            }
        }

        // Call this method from the main activity to shut down the connection.
        public void cancel() {
            try {
                if (mmInStream != null && mmOutStream != null) {
                    mmInStream.close();
                    mmOutStream.close();
                }
            } catch (IOException e) {
                Log.e("TerrariaBT", "BTService: Cancel IO error: " + e.getMessage());
            }
        }
    }

}