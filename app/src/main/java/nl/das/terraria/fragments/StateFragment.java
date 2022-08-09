package nl.das.terraria.fragments;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import nl.das.terraria.BTService;
import nl.das.terraria.R;
import nl.das.terraria.TerrariaApp;
import nl.das.terraria.Utils;
import nl.das.terraria.dialogs.WaitSpinner;
import nl.das.terraria.json.Device;
import nl.das.terraria.json.Sensor;
import nl.das.terraria.json.Sensors;
import nl.das.terraria.json.State;
import nl.das.terraria.json.States;

public class StateFragment extends Fragment {

    private int tabnr;
    private boolean trace = false;
    private String curIPAddress;
    private Sensors sensors;
    private WaitSpinner wait;
    private boolean bound;
    private final ArrayList<Integer> supportedMessages = new ArrayList<>();
    private Messenger svc;

    private LinearLayout deviceLayout;
    private TextView tvwDateTime;
    private TextView tvwTTemp;
    private TextView tvwRHum;
    private TextView tvwRTemp;

    public StateFragment() {
        supportedMessages.add(BTService.CMD_GET_SENSORS);
        supportedMessages.add(BTService.CMD_GET_STATE);
    }

    public static StateFragment newInstance(int tabnr) {
        Log.i("TerrariaBT", "StateFragment.newInstance() start");
        StateFragment fragment = new StateFragment();
        Bundle args = new Bundle();
        args.putInt("tabnr", tabnr);
        fragment.setArguments(args);
        Log.i("TerrariaBT", "StateFragment.newInstance() end");
        return fragment;
    }
    /**
     * Service connection that connects to the BTService.
     */
    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the object we can use to
            // interact with the service.  We are communicating with the
            // service using a Messenger, so here we get a client-side
            // representation of that from the raw IBinder object.
            svc = new Messenger(service);
            bound = true;
            // We want to monitor the service for as long as we are
            // connected to it.
            try {
                Message msg = Message.obtain(null, BTService.MSG_REGISTER_CLIENT);
                Bundle bdl = new Bundle();
                bdl.putIntegerArrayList("commands", supportedMessages);
                msg.setData(bdl);
                msg.replyTo = mMessenger;
                svc.send(msg);
                wait.start();
                getSensors();
                getState();
             } catch (RemoteException e) {
                // In this case the service has crashed before we could even
                // do anything with it; we can count on soon being
                // disconnected (and then reconnected if it can be restarted)
                // so there is no need to do anything here.
            }
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            svc = null;
            bound = false;
        }
    };

    /**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    final Messenger mMessenger = new Messenger(new IncomingHandler());

    /**
     * Handler of incoming messages from service.
     */
    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            Log.i("TerrariaBT","StateFragment: handleMessage() for message " + msg.what );
            switch (msg.what) {
                case BTService.CMD_GET_SENSORS:
                    Log.i("TerrariaBT", "StateFragment: " + msg.obj.toString());
                    sensors = new Gson().fromJson(msg.obj.toString(), Sensors.class);
                    updateSensors();
                    break;
                case BTService.CMD_GET_STATE:
                    Log.i("TerrariaBT", "StateFragment: " + msg.obj.toString());
                    States states = new Gson().fromJson(msg.obj.toString(), States.class);
                    trace = states.getTrace().equalsIgnoreCase("on");
                    updateState(states.getStates());
                    wait.dismiss();
                    break;
                default:
            }
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i("TerrariaBT", "StateFragment: onCreate() start");
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            tabnr = getArguments().getInt("tabnr");
        }
        Log.i("TerrariaBT", "StateFragment: onCreate() end. Tabnr=" + tabnr);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.i("TerrariaBT", "StateFragment: onCreateView() start");
        View view = inflater.inflate(R.layout.fragment_state, container, false);
        deviceLayout = view.findViewById(R.id.trm_lay_device_state);
        for (Device d :  TerrariaApp.configs[tabnr - 1].getDevices()) {
            View v = inflater.inflate(R.layout.fragment_device_state, container, false);
            SwitchCompat sw = v.findViewById(R.id.trm_switchDevice);
            String devname = d.getDevice();
            int r = getResources().getIdentifier(devname, "string", "nl.das.terrariaBT");
            sw.setText(getResources().getString(r));
            if (devname.equalsIgnoreCase("uvlight")) {
                v.findViewById(R.id.trm_lay_uv).setVisibility(View.VISIBLE);
            } else {
                v.findViewById(R.id.trm_lay_uv).setVisibility(View.GONE);
            }
            deviceLayout.addView(v);
        }
        Log.i("TerrariaBT", "StateFragment: onCreateView() end");
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Log.i("TerrariaBT", "StateFragment: onViewCreated() start");
        super.onViewCreated(view, savedInstanceState);
        wait = new WaitSpinner(requireActivity());
        bound = false;
        Intent intent = new Intent(getContext(), BTService.class);
        if(!getContext().bindService(intent, connection, 0)) {
            Log.e("TerrariaBT","TerrariaApp: Could not bind to BTService");
        }

        Button btn = view.findViewById(R.id.trm_refreshButton);
        btn.setOnClickListener(v -> {
            Log.i("TerrariaBT", "StateFragment: refresh State");
            wait.start();
            getSensors();
            getState();
        });

        Button btnClock = view.findViewById(R.id.trm_st_btnClock);
        btnClock.setOnClickListener(v -> {
            Log.i("TerrariaBT", "StateFragment: set Clock");
            // Create an instance of the dialog fragment and show it
            ClockDialogFragment dlgClock = ClockDialogFragment.newInstance();
            FragmentManager fm = requireActivity().getSupportFragmentManager();
            // SETS the target fragment for use later when sending results
            fm.setFragmentResultListener("time", this, (requestKey, result) -> result.getString("clockValue"));
            dlgClock.show(fm, "ClockDialogFragment");
        });
        // walk through all device views
        int vix = 0;
        for (Device d :  TerrariaApp.configs[tabnr - 1].getDevices()) {
            View v = deviceLayout.getChildAt(vix);
            SwitchCompat swManual = v.findViewById(R.id.trm_swManualDevice);
            TextView state = v.findViewById(R.id.trm_tvwDeviceState);
            swManual.setOnClickListener(cv -> {
                SwitchCompat sc = (SwitchCompat) cv;
                if (sc.isChecked()) {
                    switchManual(d.getDevice(), true);
                } else {
                    switchManual(d.getDevice(), false);
                    state.setText("");
                }
            });
            SwitchCompat swDevice = v.findViewById(R.id.trm_switchDevice);
            swDevice.setOnClickListener(cv -> {
                SwitchCompat sc = (SwitchCompat) cv;
                if (sc.isChecked()) {
                    switchDevice(d.getDevice(), true);
                } else {
                    switchDevice(d.getDevice(), false);
                    state.setText("");
                }
            });
            if (d.isLifecycle()) {
                TextView tvwHours = v.findViewById(R.id.trm_tvwHours_lcc);
                Button btnReset = v.findViewById(R.id.trm_btnReset);
                btnReset.setOnClickListener(cv -> {
                    Log.i("TerrariaBT", "StateFragment: Reset lifecycle counter for device '" + d.getDevice() + "'");
                    // Create an instance of the dialog fragment and show it
                    ResetHoursDialogFragment dlgReset = ResetHoursDialogFragment.newInstance(d.getDevice());
                    FragmentManager fm = requireActivity().getSupportFragmentManager();
                    // SETS the target fragment for use later when sending results
                    fm.setFragmentResultListener("reset", this, (requestKey, result) -> {
                        tvwHours.setText(result.getInt("hours"));
                        onResetHoursSave(d.getDevice(), result.getInt("hours"));
                    });
                    dlgReset.show(fm, "ResetHoursDialogFragment");
                });
            }
            vix++;
        }

        tvwDateTime = view.findViewById(R.id.trm_st_tvwDateTime);
        tvwTTemp = view.findViewById(R.id.trm_st_tvwTtemp);
        tvwRHum = view.findViewById(R.id.trm_st_tvwRhum);
        tvwRTemp = view.findViewById(R.id.trm_st_tvwRtemp);

        Log.i("TerrariaBT", "StateFragment: onViewCreated() end");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            Message msg = Message.obtain(null, BTService.MSG_UNREGISTER_CLIENT);
            msg.replyTo = mMessenger;
            svc.send(msg);
        } catch (RemoteException e) { }
        getContext().unbindService(connection);
        bound = false;
        Log.i("TerrariaBT", "StateFragment: onDestroy() end");
    }


    private void switchDevice(String device, boolean yes) {
//        final WaitSpinner wait = new WaitSpinner(requireActivity());
//        wait.start();
//        String url = "http://" + curIPAddress + "/device/" + device + (yes ? "/on" : "/off");
//        Log.i("TerrariaBT","Execute PUT request " + url);
//        // Switch device off.
//        StringRequest jsonArrayRequest = new StringRequest(Request.Method.PUT, url,
//                response1 -> {
//                    Log.i("TerrariaBT", "Switch device " + device + (yes ? " on" : " off"));
//                    wait.dismiss();
//                },
//                error -> {
//                    wait.dismiss();
//                    Log.i("TerrariaBT","Error " + error.getMessage());
//                    new NotificationDialog(getContext(), "Error", "Kontakt met Terrarium Control Unit verloren.").show();
//                }
//        );
//        // Add the request to the RequestQueue.
//        RequestQueueSingleton.getInstance(getContext()).add(jsonArrayRequest);
    }

    private void switchManual(String device, boolean yes) {
//        final WaitSpinner wait = new WaitSpinner(requireActivity());
//        wait.start();
//        String url = "http://" + curIPAddress + "/device/" + device + (yes ? "/manual" : "/auto");
//        Log.i("TerrariaBT","Execute PUT request " + url);
//        // Switch device off.
//        StringRequest jsonArrayRequest = new StringRequest(Request.Method.PUT, url,
//                response1 -> {
//                    Log.i("TerrariaBT", "Switch device " + device + (yes ? " to manual" : " to auto"));
//                    wait.dismiss();
//                },
//                error -> {
//                    wait.dismiss();
//                    Log.i("TerrariaBT","Error " + error.getMessage());
//                    new NotificationDialog(getContext(), "Error", "Kontakt met Terrarium Control Unit verloren.").show();
//                }
//        );
//        // Add the request to the RequestQueue.
//        RequestQueueSingleton.getInstance(getContext()).add(jsonArrayRequest);
    }

    private void getSensors() {
        if (TerrariaApp.MOCK[tabnr - 1]) {
            Log.i("TerrariaBT", "StateFragment: Retrieved mocked sensor readings");
            Gson gson = new Gson();
            try {
                String response = new BufferedReader(
                    new InputStreamReader(getResources().getAssets().open("sensors_" + TerrariaApp.configs[tabnr - 1].getMockPostfix() + ".json")))
                    .lines().collect(Collectors.joining("\n"));
                sensors = gson.fromJson(response, Sensors.class);
                updateSensors();
            } catch (JsonSyntaxException | IOException e) {
                Utils.showMessage(getContext(), TerrariaApp.appView, "StateFragment: Sensors response contains errors:\n" + e.getMessage());
            }

        } else {
            if (svc != null) {
                Log.i("TerrariaBT", "StateFragment: getSensors() from server");
                try {
                    Message msg = Message.obtain(null, BTService.CMD_GET_SENSORS);
                    msg.replyTo = mMessenger;
                    svc.send(msg);
                } catch (RemoteException e) {
                    // There is nothing special we need to do if the service has crashed.
                }
            } else {
                Log.i("TerrariaBT", "StateFragment: BTService is not ready yet");
            }
        }
    }

    private void updateSensors() {
        Log.i("TerrariaBT","StateFragment: updateSensors() start");
        tvwDateTime.setText(sensors.getClock());
        for (Sensor sensor: sensors.getSensors()) {
            if (sensor.getLocation().equalsIgnoreCase("room")) {
                tvwRTemp.setText(getString(R.string.temperature, sensor.getTemperature()));
                tvwRHum.setText(getString(R.string.humidity, sensor.getHumidity()));
            } else if (sensor.getLocation().equalsIgnoreCase("terrarium")) {
                tvwTTemp.setText(getString(R.string.temperature, sensor.getTemperature()));
            }
        }
        Log.i("TerrariaBT","StateFragment: updateSensors() end");
    }

    private void getState() {
        // Request state.
        if (TerrariaApp.MOCK[tabnr - 1]) {
            Log.i("TerrariaBT", "StateFragment: Retrieved mocked state readings");
            Gson gson = new Gson();
            try {
                String response = new BufferedReader(
                        new InputStreamReader(getResources().getAssets().open("state_" + TerrariaApp.configs[tabnr - 1].getMockPostfix() + ".json")))
                        .lines().collect(Collectors.joining("\n"));
                List<State> states = gson.fromJson(response, new TypeToken<List<State>>() {}.getType());
                updateState(states);
                wait.dismiss();
            } catch (JsonSyntaxException | IOException e) {
                Utils.showMessage(getContext(), TerrariaApp.appView, "StateFragment: State response contains errors:\n" + e.getMessage());
            }
        } else {
            if (svc != null) {
                Log.i("TerrariaBT", "StateFragment: getState() from server");
                try {
                    Message msg = Message.obtain(null, BTService.CMD_GET_STATE);
                    msg.replyTo = mMessenger;
                    svc.send(msg);
                } catch (RemoteException e) {
                    // There is nothing special we need to do if the service has crashed.
                }
            } else {
                Log.i("TerrariaBT", "StateFragment: BTService is not ready yet");
            }
        }
    }

    private void updateState(List<State> states) {
        Log.i("TerrariaBT","StateFragment: updateState() start");
        int vix = 0;
        for (Device d :  TerrariaApp.configs[tabnr - 1].getDevices()) {
            for (State s : states) {
                if (d.getDevice().equalsIgnoreCase(s.getDevice())) {
                    View v = deviceLayout.getChildAt(vix);
                    SwitchCompat swManual = v.findViewById(R.id.trm_swManualDevice);
                    SwitchCompat swDevice = v.findViewById(R.id.trm_switchDevice);
                    TextView state = v.findViewById(R.id.trm_tvwDeviceState);
                    swManual.setChecked(s.getManual().equalsIgnoreCase("yes"));
                    swDevice.setChecked(s.getState().equalsIgnoreCase("on"));
                    state.setText(translateEndTime(s.getEndTime()));
                    if (d.isLifecycle()) {
                        TextView h = v.findViewById(R.id.trm_tvwHours_lcc);
                        h.setText(getString(R.string.hoursOn, s.getHoursOn()));
                    }
                }
            }
            vix++;
        }
        Log.i("TerrariaBT","StateFragment: updateState() end");
    }

    public void onResetHoursSave(String device, int hoursOn) {
//        final WaitSpinner wait = new WaitSpinner(requireActivity());
//        wait.start();
//        String url = "http://" + curIPAddress + "/counter/" + device  + "/" + hoursOn;
//        Log.i("TerrariaBT","Execute POST request " + url);
//        // Set counter.
//        StringRequest jsonObjectRequest = new StringRequest(Request.Method.POST, url,
//                response -> {
//                    Log.i("TerrariaBT", "Set lifecycle counter of " + device + " to " + hoursOn);
//                    wait.dismiss();
//                },
//                error -> {
//                    wait.dismiss();
//                    Log.i("Terrarium","Error " + error.getMessage());
//                    new NotificationDialog(getContext(), "Error", "Kontakt met Terrarium Control Unit verloren.").show();
//                }
//        );
//        // Add the request to the RequestQueue.
//        RequestQueueSingleton.getInstance(getContext()).add(jsonObjectRequest);
    }

    private String translateEndTime(String endTime) {
        if (endTime == null) {
            return "";
        } else {
            if (endTime.equalsIgnoreCase("no endtime")) {
                return "geen eindtijd";
            } else if (endTime.equalsIgnoreCase("until ideal temperature is reached")) {
                return "tot ideale temperatuur bereikt is";
            } else if (endTime.equalsIgnoreCase("until ideal humidity is reached")) {
                return "tot ideale vochtigheidsgraad bereikt is";
            } else {
                return endTime;
            }
        }
    }

    public void record(boolean start) {
//        final WaitSpinner wait = new WaitSpinner(requireActivity());
//        wait.start();
//        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
//        String url = "http://" + curIPAddress + "/trace/" + (start ? "on" : "off");
//        Log.i("TerrariaBT","Execute POST request " + url);
//        VoidRequest voidRequest = new VoidRequest(Request.Method.POST, url, null,
//                (Response.Listener<Void>) response1 -> {
//                    Log.i("TerrariaBT", "Recording " + (start ? "started..." : "stopped"));
//                    wait.dismiss();
//                },
//                (Response.ErrorListener) error -> {
//                    wait.dismiss();
//                    Log.i("TerrariaBT","Error " + error.getMessage());
//                    new NotificationDialog(getContext(), "Error", "Kontakt met Terrarium Control Unit verloren.").show();
//                }
//        );
//        // Add the request to the RequestQueue.
//        RequestQueueSingleton.getInstance(getContext()).add(voidRequest);
    }

    public void viewRecording(String type) { // type = "state" or "temperature"
//        final WaitSpinner wait = new WaitSpinner(requireActivity());
//        wait.start();
//        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
//        String url = "http://" + curIPAddress + "/history/" + type;
//        Log.i("TerrariaBT","Execute GET request " + url);
//        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
//                (Response.Listener<String>) response1 -> {
//                    Log.i("TerrariaBT", "Get the recording of " + type);
//                    wait.dismiss();
//                },
//                (Response.ErrorListener) error -> {
//                    wait.dismiss();
//                    Log.i("TerrariaBT","Error " + error.getMessage());
//                    new NotificationDialog(getContext(), "Error", "Kontakt met Terrarium Control Unit verloren.").show();
//                }
//        );
//        // Add the request to the RequestQueue.
//        RequestQueueSingleton.getInstance(getContext()).add(stringRequest);
    }
}