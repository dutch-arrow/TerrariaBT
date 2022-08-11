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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;

import com.google.gson.Gson;

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
import nl.das.terraria.json.Action;
import nl.das.terraria.json.Device;
import nl.das.terraria.json.Error;
import nl.das.terraria.json.Ruleset;

public class RulesetFragment extends Fragment {

    private int tabnr;
    private final ArrayList<Integer> supportedMessages = new ArrayList<>();
    private Messenger svc;
    private InputMethodManager imm;
    private Ruleset ruleset;
    private int currentRsNr;
    private int currentRlNr;

    private final int[] lastPos = new int[4];
    private final boolean[] userSelect = new boolean[4];
    private final String[] devSpinner = new String[10];

    // Ruleset
    private Button btnSave;
    private Button btnRefresh;
    private SwitchCompat swActive;
    private EditText edtFrom;
    private EditText edtTo;
    private EditText edtIdeal;
    // Rule
    private static final int NR_OF_ACTIONS = 2;
    private EditText edtValueBelow;
    private EditText edtValueAbove;
    private List<String> spn_items;
    private final Spinner[] spnDevice = new Spinner[4];
    private final RadioGroup[] rbgPeriod = new RadioGroup[4];
    private final RadioButton[] rbnActionIdeal = new RadioButton[4];
    private final RadioButton[] rbnActionPeriod = new RadioButton[4];
    private final EditText[] edtActionPeriod = new EditText[4];
    private final TextView[] tvwSeconds = new TextView[4];
    private WaitSpinner wait;

    public RulesetFragment() {
        supportedMessages.add(BTService.CMD_GET_RULESET);
        supportedMessages.add(BTService.CMD_SET_RULESET);
    }

    public static RulesetFragment newInstance(int tabnr, int rulesetNr) {
        RulesetFragment fragment = new RulesetFragment();
        Bundle args = new Bundle();
        args.putInt("tabnr", tabnr);
        args.putInt("rulesetnr", rulesetNr);
        fragment.setArguments(args);
        return fragment;
    }
    /**
     * Service connection that connects to the BTService.
     */
    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the object we can use to
            // interact with the service.  We are communicating with the
            // service using a Messenger, so here we get a client-side
            // representation of that from the raw IBinder object.
            svc = new Messenger(service);
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
                getRuleset();
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
            Log.i("TerrariaBT","RulesetFragment: handleMessage() for message " + msg.what );
            switch (msg.what) {
                case BTService.CMD_GET_RULESET:
                    Log.i("TerrariaBT", "RulesetFragment: " + msg.obj.toString());
                    ruleset = new Gson().fromJson(msg.obj.toString(), Ruleset.class);
                    updateRuleset();
                    currentRlNr = 0;
                    updateRule();
                    currentRlNr = 1;
                    updateRule();
                    wait.dismiss();
                    break;
                case BTService.CMD_SET_RULESET:
                    if (msg.obj != null && msg.obj.toString().length() > 0) {
                        String errmsg;
                        Error err = new Gson().fromJson(msg.obj.toString(), Error.class);
                        if (err != null) {
                            errmsg = err.getError();
                        } else {
                            errmsg = msg.obj.toString();
                        }
                        Utils.showMessage(requireContext(), requireView(), errmsg);
                    } else {
                        Log.i("TerrariaBT", "RulesetFragment: No response");
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
            currentRsNr = getArguments().getInt("rulesetnr");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.ruleset_frg, container, false);
        imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);

        btnSave = view.findViewById(R.id.rs_btnSave);
        btnSave.setOnClickListener(v -> {
            btnSave.requestFocusFromTouch();
            imm.hideSoftInputFromWindow(btnSave.getWindowToken(), 0);
            saveRuleset();
            btnSave.setEnabled(false);
        });
        btnRefresh = view.findViewById(R.id.rs_btnRefresh);
        btnRefresh.setOnClickListener(v -> {
            imm.hideSoftInputFromWindow(btnRefresh.getWindowToken(), 0);
            getRuleset();
            btnSave.setEnabled(false);
        });
        swActive = view.findViewById(R.id.rs_swActive);
        swActive.setOnClickListener(v -> {
            if (swActive.isChecked()) {
                ruleset.setActive("yes");
            } else {
                ruleset.setActive("no");
            }
            btnSave.setEnabled(true);
        });
        edtFrom = view.findViewById(R.id.rs_edtFrom);
        edtFrom.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                imm.hideSoftInputFromWindow(edtFrom.getWindowToken(), 0);
                if (checkTime(edtFrom)) {
                    String value = String.valueOf(edtFrom.getText()).trim();
                    ruleset.setFrom(value);
                    edtTo.requestFocus();
                    btnSave.setEnabled(true);
                }
            }
            return false;
        });
        edtFrom.setOnFocusChangeListener(((v, hasFocus) -> {
            if (!hasFocus) {
                if (checkTime(edtFrom)) {
                    String value = String.valueOf(edtFrom.getText()).trim();
                    ruleset.setFrom(value);
                    btnSave.setEnabled(true);
                }
            }
        }));
        edtTo = view.findViewById(R.id.rs_edtTo);
        edtTo.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                imm.hideSoftInputFromWindow(edtTo.getWindowToken(), 0);
                if (checkTime(edtTo)) {
                    String value = String.valueOf(edtTo.getText()).trim();
                    ruleset.setTo(value);
                    edtIdeal.requestFocus();
                    btnSave.setEnabled(true);
                }
            }
            return false;
        });
        edtTo.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                if (checkTime(edtTo)) {
                    String value = String.valueOf(edtTo.getText()).trim();
                    ruleset.setTo(value);
                    btnSave.setEnabled(true);
                }
            }
        });
        edtIdeal = view.findViewById(R.id.rs_edtIdeal);
        edtIdeal.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                imm.hideSoftInputFromWindow(edtIdeal.getWindowToken(), 0);
                String value = String.valueOf(edtIdeal.getText()).trim();
                if (checkInteger(edtIdeal, value, 15, 40)) {
                    int intval = value.trim().length() == 0 ? 0 : Integer.parseInt(value);
                    ruleset.setTempIdeal(intval);
                    btnSave.setEnabled(true);
                }
            }
            return false;
        });
        edtIdeal.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                String value = String.valueOf(edtIdeal.getText()).trim();
                if (checkInteger(edtIdeal, value, 15, 40)) {
                    int intval = value.trim().length() == 0 ? 0 : Integer.parseInt(value);
                    ruleset.setTempIdeal(intval);
                    btnSave.setEnabled(true);
                }
            }
        });

        edtValueBelow = view.findViewById(R.id.rs_edtValueBelow);
        edtValueBelow.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                imm.hideSoftInputFromWindow(edtValueBelow.getWindowToken(), 0);
                String value = String.valueOf(edtValueBelow.getText()).trim();
                if (checkInteger(edtValueBelow, value, 15, 40)) {
                    int intval = value.trim().length() == 0 ? 0 : Integer.parseInt(value);
                    ruleset.getRules().get(0).setValue(-intval);
                    btnSave.setEnabled(true);
                }
            }
            return false;
        });
        edtValueBelow.setOnFocusChangeListener(((v, hasFocus) -> {
            if (!hasFocus) {
                String value = String.valueOf(edtValueBelow.getText()).trim();
                if (checkInteger(edtValueBelow, value, 15, 40)) {
                    int intval = value.trim().length() == 0 ? 0 : Integer.parseInt(value);
                    ruleset.getRules().get(0).setValue(-intval);
                    btnSave.setEnabled(true);
                }
            }
        }));

        edtValueAbove = view.findViewById(R.id.rs_edtValueAbove);
        edtValueAbove.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                imm.hideSoftInputFromWindow(edtValueAbove.getWindowToken(), 0);
                String value = String.valueOf(edtValueAbove.getText()).trim();
                if (checkInteger(edtValueAbove, value, 15, 40)) {
                    int intval = value.trim().length() == 0 ? 0 : Integer.parseInt(value);
                    ruleset.getRules().get(1).setValue(intval);
                    btnSave.setEnabled(true);
                }
            }
            return false;
        });
        edtValueAbove.setOnFocusChangeListener(((v, hasFocus) -> {
            if (!hasFocus) {
                String value = String.valueOf(edtValueAbove.getText()).trim();
                if (checkInteger(edtValueAbove, value, 15, 40)) {
                    int intval = value.trim().length() == 0 ? 0 : Integer.parseInt(value);
                    ruleset.getRules().get(1).setValue(intval);
                    btnSave.setEnabled(true);
                }
            }
        }));

        for (int a = 0; a < (NR_OF_ACTIONS * 2); a++) {
            int fa = a;
            int resId = getResources().getIdentifier("rs_spnDevice_" + (a + 1), "id", requireContext().getPackageName());
            spnDevice[a] = view.findViewById(resId);
            resId = getResources().getIdentifier("rs_rbgPeriod_" + (a + 1), "id", requireContext().getPackageName());
            rbgPeriod[a] = view.findViewById(resId);
            resId = getResources().getIdentifier("rs_rbIdeal_" + (a + 1), "id", requireContext().getPackageName());
            rbnActionIdeal[a] = view.findViewById(resId);
            resId = getResources().getIdentifier("rs_rbPeriod_" + (a + 1), "id", requireContext().getPackageName());
            rbnActionPeriod[a] = view.findViewById(resId);
            resId = getResources().getIdentifier("rs_edtPeriod_" + (a + 1), "id", requireContext().getPackageName());
            edtActionPeriod[a] = view.findViewById(resId);
            resId = getResources().getIdentifier("rs_lblSeconds_" + (a + 1), "id", requireContext().getPackageName());
            tvwSeconds[a] = view.findViewById(resId);

            spnDevice[a].setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    int rlnr = fa / 2; // 0->0, 1->0, 2->1, 3->1
                    int anr = fa % 2; // 0->0, 1->1, 2->0, 3->1
                    if (lastPos[fa] != position) {
                        if (position == 0) {
                            rbgPeriod[fa].setEnabled(false);
                            rbnActionIdeal[fa].setEnabled(false);
                            rbnActionIdeal[fa].setChecked(false);
                            edtActionPeriod[fa].setText("");
                            ruleset.getRules().get(rlnr).getActions().get(anr).setOnPeriod(0);
                            ruleset.getRules().get(rlnr).getActions().get(anr).setDevice("no device");
                            rbnActionPeriod[fa].setEnabled(false);
                            rbnActionPeriod[fa].setChecked(false);
                            edtActionPeriod[fa].setEnabled(false);
                            tvwSeconds[fa].setTextColor(getResources().getColor(R.color.disabled, null));
                            if (userSelect[fa]) {
                                btnSave.setEnabled(true);
                            }
                        } else if (position > 0) {
                            if (userSelect[fa]) {
                                btnSave.setEnabled(true);
                            }
                            rbgPeriod[fa].setEnabled(true);
                            rbnActionIdeal[fa].setEnabled(true);
                            rbnActionPeriod[fa].setEnabled(true);
                            if (rbnActionPeriod[fa].isChecked()) {
                                edtActionPeriod[fa].setEnabled(true);
                            }
                            tvwSeconds[fa].setTextColor(getResources().getColor(R.color.black, null));
                            String dev = devSpinner[position - 1];
                            ruleset.getRules().get(rlnr).getActions().get(anr).setDevice(dev);
                        }
                        lastPos[fa] = position;
                        userSelect[fa] = true;
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });

            rbnActionIdeal[a].setOnClickListener(v -> {
                int rlnr = fa / 2; // 0->0, 1->0, 2->1, 3->1
                int anr = fa % 2; // 0->0, 1->1, 2->0, 3->1
                ruleset.getRules().get(rlnr).getActions().get(anr).setOnPeriod(-2);
                edtActionPeriod[fa].setText("");
                edtActionPeriod[fa].setEnabled(false);
                rbnActionPeriod[fa].setChecked(false);
                rbnActionIdeal[fa].setChecked(true);
                btnSave.setEnabled(true);
            });

            rbnActionPeriod[a].setOnClickListener(v -> {
                edtActionPeriod[fa].setEnabled(true);
                rbnActionIdeal[fa].setChecked(false);
                btnSave.setEnabled(true);
            });

            edtActionPeriod[a].setOnEditorActionListener((v, actionId, event) -> {
                int rlnr = fa / 2; // 0->0, 1->0, 2->1, 3->1
                int anr = fa % 2; // 0->0, 1->1, 2->0, 3->1
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    imm.hideSoftInputFromWindow(edtActionPeriod[fa].getWindowToken(), 0);
                    String value = String.valueOf(edtActionPeriod[fa].getText()).trim();
                    if (checkInteger(edtActionPeriod[fa], value, 0, 3600)) {
                        if (value.trim().length() > 0) {
                            int intval = value.trim().length() == 0 ? 0 : Integer.parseInt(value);
                            ruleset.getRules().get(rlnr).getActions().get(anr).setOnPeriod(intval);
                        }
                        spnDevice[fa].requestFocus();
                        btnSave.setEnabled(true);
                    }
                }
                return false;
            });
            edtActionPeriod[a].setOnFocusChangeListener(((v, hasFocus) -> {
                if (!hasFocus) {
                    int rlnr = fa / 2; // 0->0, 1->0, 2->1, 3->1
                    int anr = fa % 2; // 0->0, 1->1, 2->0, 3->1
                    String value = String.valueOf(edtActionPeriod[fa].getText()).trim();
                    if (checkInteger(edtActionPeriod[fa], value, 0, 3600)) {
                        if (value.trim().length() > 0) {
                            int intval = value.trim().length() == 0 ? 0 : Integer.parseInt(value);
                            ruleset.getRules().get(rlnr).getActions().get(anr).setOnPeriod(intval);
                        }
                        btnSave.setEnabled(true);
                    }
                }
            }));
        }
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        wait = new WaitSpinner(requireActivity());
        // Bind to BTService
        Intent intent = new Intent(requireContext(), BTService.class);
        if(!requireContext().bindService(intent, connection, 0)) {
            Log.e("TerrariaBT","RulesetFragment: Could not bind to BTService");
        }

        spn_items = new ArrayList<>();
        spn_items.add("");
        int i = 0;
        for (Device d :  TerrariaApp.configs[tabnr - 1].getDevices()) {
            if (!d.getDevice().startsWith("light") && !d.getDevice().equalsIgnoreCase("uvlight")) {
                int r = getResources().getIdentifier(d.getDevice(), "string", "nl.das.terrariaBT");
                devSpinner[i] = d.getDevice();
                spn_items.add(getResources().getString(r));
                i++;
            }
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), R.layout.spinner_list, R.id.rs_spnItem, spn_items.toArray(new String[0]));
        for (int a = 0; a < (NR_OF_ACTIONS * 2); a++) {
            spnDevice[a].setAdapter(adapter);
        }
        getRuleset();
    }

    @Override
    public void onDestroy() {
        if (svc != null) {
            super.onDestroy();
            try {
                Message msg = Message.obtain(null, BTService.MSG_UNREGISTER_CLIENT);
                msg.replyTo = mMessenger;
                svc.send(msg);
            } catch (RemoteException ignored) {
            }
            requireContext().unbindService(connection);
            Log.i("TerrariaBT", "RulesetFragment: onDestroy() end");
        } else {
            Log.i("TerrariaBT", "RulesetFragment: why is onDestroy() called?");
        }
    }

    private void getRuleset() {
        if (TerrariaApp.MOCK[tabnr - 1]) {
            Log.i("TerrariaBT", "RulesetFragment: getRuleset() from file (mock)");
            try {
                Gson gson = new Gson();
                String response = new BufferedReader(
                        new InputStreamReader(getResources().getAssets().open("ruleset" + currentRsNr + "_" + TerrariaApp.configs[tabnr - 1].getMockPostfix() + ".json")))
                        .lines().collect(Collectors.joining("\n"));
                ruleset = gson.fromJson(response, Ruleset.class);
                updateRuleset();
                currentRlNr = 0;
                updateRule();
                currentRlNr = 1;
                updateRule();
                wait.dismiss();
            } catch (IOException e) {
                wait.dismiss();
            }
        } else {
            if (svc != null) {
                Log.i("TerrariaBT", "RulesetFragment: getRuleset() from server");
                try {
                    Message msg = Message.obtain(null, BTService.CMD_GET_RULESET);
                    msg.replyTo = mMessenger;
                    msg.arg1 = currentRsNr;
                    svc.send(msg);
                } catch (RemoteException e) {
                    // There is nothing special we need to do if the service has crashed.
                }
            } else {
                Log.i("TerrariaBT", "RulesetFragment: BTService is not ready yet");
            }
        }
    }

    private void updateRuleset() {
        swActive.setChecked(ruleset.getActive().equalsIgnoreCase("yes"));
        String from, to;
        if (ruleset.getFrom().equalsIgnoreCase("00:00")) {
            from = "";
        } else {
            from = ruleset.getFrom().replace(':', '.');
        }
        edtFrom.setText(from);
        if (ruleset.getTo().equalsIgnoreCase("00:00")) {
            to = "";
        } else {
            to = ruleset.getTo().replace(':', '.');
        }
        edtTo.setText(to);
        edtIdeal.setText(String.valueOf(ruleset.getTempIdeal()));
    }

    private void updateRule() {
        int value = ruleset.getRules().get(currentRlNr).getValue();
        if (value < 0) {
            edtValueBelow.setText(String.valueOf(-value));
        } else if (value > 0) {
            edtValueAbove.setText(String.valueOf(value));
        }
        for (int i = 0; i < NR_OF_ACTIONS; i++) {
            Action a = ruleset.getRules().get(currentRlNr).getActions().get(i);
            int nr = i + (currentRlNr * 2);
            int ix = 0;
            if (!a.getDevice().equalsIgnoreCase("no device")) {
                String dev = a.getDevice();
                if (a.getDevice().endsWith("_1") || a.getDevice().endsWith("_2")) {
                    dev = a.getDevice().substring(0, a.getDevice().length() - 2);
                }
                int r = getResources().getIdentifier(dev, "string", "nl.das.terrariaBT");
                ix = spn_items.indexOf(getResources().getString(r));
            }
            spnDevice[nr].setSelection(ix);
            if (a.getOnPeriod() < 0) {
                rbnActionIdeal[nr].setChecked(true);
                rbnActionPeriod[nr].setChecked(false);
                edtActionPeriod[nr].setEnabled(false);
                tvwSeconds[nr].setTextColor(getResources().getColor(R.color.disabled, null));
            } else if (a.getOnPeriod() > 0) {
                rbnActionPeriod[nr].setChecked(true);
                rbnActionIdeal[nr].setChecked(false);
                edtActionPeriod[nr].setText(String.valueOf(a.getOnPeriod()));
                edtActionPeriod[nr].setEnabled(true);
                tvwSeconds[nr].setTextColor(getResources().getColor(R.color.black, null));
            } else {
                rbnActionIdeal[nr].setChecked(false);
                rbnActionIdeal[nr].setEnabled(false);
                rbnActionPeriod[nr].setChecked(false);
                rbnActionPeriod[nr].setEnabled(false);
                edtActionPeriod[nr].setText("");
                edtActionPeriod[nr].setEnabled(false);
                tvwSeconds[nr].setTextColor(getResources().getColor(R.color.disabled, null));
            }
        }
    }

    private void saveRuleset() {
        ruleset.setTerrarium(1);
        if (svc != null) {
            try {
                Log.i("TerrariaBT", "RulesetFragment: saveTimers()");
                Message msg = Message.obtain(null, BTService.CMD_SET_RULESET);
                msg.replyTo = mMessenger;
                msg.arg1 = currentRsNr;
                msg.obj = new Gson().toJson(ruleset);
                svc.send(msg);
            } catch (RemoteException e) {
                // There is nothing special we need to do if the service has crashed.
            }
        } else {
            Log.i("TerrariaBT", "RulesetFragment: BTService is not ready yet");
        }
    }

    private boolean checkTime(EditText field) {
        String value = String.valueOf(field.getText()).trim();
        if (value.length() != 0) {
            String[] parts = value.split("\\.");
            if (parts.length == 2) {
                try {
                    int hr = Integer.parseInt(parts[0].trim());
                    if (hr < 0 || hr > 23) {
                        field.setError("Uuropgave moet tussen 0 en 23 zijn");
                    }
                } catch (NumberFormatException e) {
                    field.setError("Uuropgave is geen getal");
                }
                try {
                    int min = Integer.parseInt(parts[1].trim());
                    if (min < 0 || min > 59) {
                        field.setError("Minutenopgave moet tussen 0 en 59 zijn");
                    }
                } catch (NumberFormatException e) {
                    field.setError("Minutenopgave is geen getal");
                }
            } else {
                field.setError("Tijdopgave is niet juist. Formaat: hh.mm");
            }
        }
        return field.getError() == null;
    }

    private boolean checkInteger(EditText field, String value, int minValue, int maxValue) {
        if (value.trim().length() > 0) {
            try {
                int rv = Integer.parseInt(value);
                if (rv < minValue || rv > maxValue) {
                    field.setError("Waarde moet tussen " + minValue + " en " + maxValue + " zijn.");
                }
            } catch (NumberFormatException e) {
                field.setError("Waarde moet tussen " + minValue + " en " + maxValue + " zijn.");
            }
        }
        return field.getError() == null;
    }

}