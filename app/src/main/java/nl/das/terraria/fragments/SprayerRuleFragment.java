package nl.das.terraria.fragments;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.stream.Collectors;

import nl.das.terraria.BTService;
import nl.das.terraria.R;
import nl.das.terraria.TerrariaApp;
import nl.das.terraria.Utils;
import nl.das.terraria.dialogs.WaitSpinner;
import nl.das.terraria.json.Action;
import nl.das.terraria.json.Error;
import nl.das.terraria.json.Ruleset;
import nl.das.terraria.json.SprayerRule;

public class SprayerRuleFragment extends Fragment {
    // Drying rule
    private Button btnSaveDR;
    private EditText edtDelay;
    private EditText edtFanInPeriod;
    private EditText edtFanOutPeriod;

    private InputMethodManager imm;
    private WaitSpinner wait;

    private String curIPAddress;
    private SprayerRule dryingRule;
    private int tabnr;
    private boolean bound;
    private final ArrayList<Integer> supportedMessages = new ArrayList<>();
    private Messenger svc;

    public SprayerRuleFragment() {
        supportedMessages.add(BTService.CMD_GET_SPRAYERRULE);
        supportedMessages.add(BTService.CMD_SET_SPRAYERRULE);
    }

    public static SprayerRuleFragment newInstance(int tabnr) {
        SprayerRuleFragment fragment = new SprayerRuleFragment();
        Bundle args = new Bundle();
        args.putInt("tabnr", tabnr);
        fragment.setArguments(args);
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
                getDryingRule();
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
            Log.i("TerrariaBT","SprayerRuleFragment: handleMessage() for message " + msg.what );
            switch (msg.what) {
                case BTService.CMD_GET_SPRAYERRULE:
                    Log.i("TerrariaBT", "SprayerRuleFragment: " + msg.obj.toString());
                    dryingRule = new Gson().fromJson(msg.obj.toString(), SprayerRule.class);
                    updateDryingRule();
                    wait.dismiss();
                    break;
                case BTService.CMD_SET_SPRAYERRULE:
                    if (msg.obj != null && msg.obj.toString() != null && msg.obj.toString().length() > 0) {
                        String errmsg = "";
                        Error err = new Gson().fromJson(msg.obj.toString(), Error.class);
                        if (err != null) {
                            errmsg = err.getError();
                        } else {
                            errmsg = msg.obj.toString();
                        }
                        Utils.showMessage(getContext(), requireView(), errmsg);
                    } else {
                        Log.i("TerrariaBT", "SprayerRuleFragment: No response");
                    }
                    wait.dismiss();
                    break;
                default:
            }
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            tabnr = getArguments().getInt("tabnr");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.drying_rule_frg, parent, false).getRootView();
        imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);

        btnSaveDR = view.findViewById(R.id.dr_btnSave);
        btnSaveDR.setEnabled(false);
        btnSaveDR.setOnClickListener(v -> {
            btnSaveDR.requestFocusFromTouch();
            saveDryingRule();
            btnSaveDR.setEnabled(false);
        });
        Button btnRefreshDR = view.findViewById(R.id.dr_btnRefresh);
        btnRefreshDR.setEnabled(true);
        btnRefreshDR.setOnClickListener(v -> {
            getDryingRule();
            btnSaveDR.setEnabled(false);
        });

        edtDelay = view.findViewById(R.id.dr_edtDelay);
        edtDelay.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                String value = String.valueOf(edtDelay.getText()).trim();
                if (checkInteger(edtDelay, value)) {
                    dryingRule.setDelay(Integer.parseInt(value));
                    edtFanInPeriod.requestFocus();
                    btnSaveDR.setEnabled(true);
                    imm.hideSoftInputFromWindow(edtDelay.getWindowToken(), 0);
                }
            }
            return false;
        });
        edtDelay.setOnFocusChangeListener(((v, hasFocus) -> {
            if (!hasFocus) {
                String value = String.valueOf(edtDelay.getText()).trim();
                if (checkInteger(edtDelay, value)) {
                    dryingRule.setDelay(Integer.parseInt(value));
                    btnSaveDR.setEnabled(true);
                }
            }
        }));

        edtFanInPeriod = view.findViewById(R.id.dr_edtOnPeriodIn);
        edtFanInPeriod.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                String value = String.valueOf(edtFanInPeriod.getText()).trim();
                if (checkInteger(edtFanInPeriod, value)) {
                    dryingRule.getActions().get(0).setDevice("fan_in");
                    dryingRule.getActions().get(0).setOnPeriod(Integer.parseInt(value) * 60);
                    edtFanOutPeriod.requestFocus();
                    btnSaveDR.setEnabled(true);
                    imm.hideSoftInputFromWindow(edtFanInPeriod.getWindowToken(), 0);
                }
            }
            return false;
        });
        edtFanInPeriod.setOnFocusChangeListener(((v, hasFocus) -> {
            if (!hasFocus) {
                String value = String.valueOf(edtFanInPeriod.getText()).trim();
                if (checkInteger(edtFanInPeriod, value)) {
                    dryingRule.getActions().get(0).setDevice("fan_in");
                    dryingRule.getActions().get(0).setOnPeriod(Integer.parseInt(value) * 60);
                    btnSaveDR.setEnabled(true);
                }
            }
        }));


        edtFanOutPeriod = view.findViewById(R.id.dr_edtOnPeriodOut);
        edtFanOutPeriod.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                String value = String.valueOf(edtFanOutPeriod.getText()).trim();
                if (checkInteger(edtFanOutPeriod, value)) {
                    dryingRule.getActions().get(1).setDevice("fan_out");
                    dryingRule.getActions().get(1).setOnPeriod(Integer.parseInt(value) * 60);
                    btnSaveDR.setEnabled(true);
                    imm.hideSoftInputFromWindow(edtFanOutPeriod.getWindowToken(), 0);
                }
            }
            return false;
        });
        edtFanOutPeriod.setOnFocusChangeListener(((v, hasFocus) -> {
            if (!hasFocus) {
                String value = String.valueOf(edtFanOutPeriod.getText()).trim();
                if (checkInteger(edtFanOutPeriod, value)) {
                    dryingRule.getActions().get(1).setDevice("fan_out");
                    dryingRule.getActions().get(1).setOnPeriod(Integer.parseInt(value) * 60);
                    btnSaveDR.setEnabled(true);
                }
            }
        }));
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        wait = new WaitSpinner(requireActivity());
        // Bind to BTService
        bound = false;
        Intent intent = new Intent(getContext(), BTService.class);
        if(!getContext().bindService(intent, connection, 0)) {
            Log.e("TerrariaBT","SprayerRuleFragment: Could not bind to BTService");
        }
    }

    @Override
    public void onDestroy() {
        if (svc != null) {
            super.onDestroy();
            try {
                Message msg = Message.obtain(null, BTService.MSG_UNREGISTER_CLIENT);
                msg.replyTo = mMessenger;
                svc.send(msg);
            } catch (RemoteException e) {
            }
            getContext().unbindService(connection);
            bound = false;
            Log.i("TerrariaBT", "RulesetFragment: onDestroy() end");
        } else {
            Log.i("TerrariaBT", "RulesetFragment: why is onDestroy() called?");
        }
    }

    private void getDryingRule() {
        if (TerrariaApp.MOCK[tabnr - 1]) {
            Log.i("TerrariaBT", "SprayerRuleFragment: getDryingRule() from file (mock)");
            try {
                Gson gson = new Gson();
                String response = new BufferedReader(
                        new InputStreamReader(getResources().getAssets().open("sprayer_rule_" + TerrariaApp.configs[tabnr - 1].getMockPostfix() + ".json")))
                        .lines().collect(Collectors.joining("\n"));
                dryingRule = gson.fromJson(response.toString(), SprayerRule.class);
                updateDryingRule();
                wait.dismiss();
            } catch (IOException e) {
                wait.dismiss();
            }
        } else {
            if (svc != null) {
                Log.i("TerrariaBT", "SprayerRuleFragment: getDryingRule() from server");
                try {
                    Message msg = Message.obtain(null, BTService.CMD_GET_SPRAYERRULE);
                    msg.replyTo = mMessenger;
                    svc.send(msg);
                } catch (RemoteException e) {
                    // There is nothing special we need to do if the service has crashed.
                }
            } else {
                Log.i("TerrariaBT", "SprayerRuleFragment: BTService is not ready yet");
            }
        }
    }

    private void updateDryingRule() {
        int value = dryingRule.getDelay();
        edtDelay.setText(value + "");
        for (int i = 0; i < 4; i++) {
            Action a = dryingRule.getActions().get(i);
            if (a.getDevice().equalsIgnoreCase("fan_in")) {
                edtFanInPeriod.setText(a.getOnPeriod() / 60 + "");
            } else if (a.getDevice().equalsIgnoreCase("fan_out")) {
                edtFanOutPeriod.setText(a.getOnPeriod() / 60 + "");
            }
        }
    }

    private void saveDryingRule() {
        dryingRule.getActions().get(2).setDevice("no device");
        dryingRule.getActions().get(2).setOnPeriod(0);
        dryingRule.getActions().get(3).setDevice("no device");
        dryingRule.getActions().get(3).setOnPeriod(0);
        if (svc != null) {
            try {
                Log.i("TerrariaBT", "SprayerRuleFragment: saveTimers()");
                Message msg = Message.obtain(null, BTService.CMD_SET_SPRAYERRULE);
                msg.replyTo = mMessenger;
                msg.obj = new Gson().toJson(dryingRule);
                svc.send(msg);
            } catch (RemoteException e) {
                // There is nothing special we need to do if the service has crashed.
            }
        } else {
            Log.i("TerrariaBT", "SprayerRuleFragment: BTService is not ready yet");
        }
    }

    private boolean checkInteger(EditText field, String value) {
        if (value.trim().length() > 0) {
            try {
                int rv = Integer.parseInt(value);
                if (rv < 0 || rv > 60) {
                    field.setError("Waarde moet tussen " + 0 + " en " + 60 + " zijn.");
                }
            } catch (NumberFormatException e) {
                field.setError("Waarde moet tussen " + 0 + " en " + 60 + " zijn.");
            }
        }
        return field.getError() == null;
    }
}
