package nl.das.terraria.fragments;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import nl.das.terraria.BTService;
import nl.das.terraria.R;
import nl.das.terraria.Utils;
import nl.das.terraria.dialogs.WaitSpinner;
import nl.das.terraria.json.Error;
import nl.das.terraria.json.FileContent;
import nl.das.terraria.json.Files;
import nl.das.terraria.json.SprayerRule;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link HistoryFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class HistoryFragment extends Fragment {

    private int curTabNr;
    private WaitSpinner wait;

    private boolean bound;
    private final ArrayList<Integer> supportedMessages = new ArrayList<>();
    private Messenger svc;
    private LineChart chart;
    private final List<ILineDataSet> dataSets = new ArrayList<>();
    private final LineData lineData = new LineData(dataSets);

    // map: <device, <time, on>>
    private final Map<String, Map<Integer, Boolean>> history_state = new HashMap<>();
    private final Map<Integer, Integer> history_temp = new HashMap<>();
    private static final SimpleDateFormat dtfmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
    private static final SimpleDateFormat tmfmt = new SimpleDateFormat("HH:mm", Locale.US);
    private long xstart;
    private int hmstart;
    private long xend;
    private final String[] devicesNl = {"lamp1", "lamp2", "lamp3", "lamp4", "uvlamp", "lamp6", "pomp", "nevel", "sproeier", "vent_in", "vent_uit", ""};
    private final String[] devicesEn = {"light1", "light2", "light3", "light4", "uvlight", "light6", "pump", "mist", "sprayer", "fan_in", "fan_out", ""};
    private final boolean[] devState = {false, false, false, false, false, false, false, false, false, false, false, false};
    private List<String> fileList = new ArrayList<>();

    public HistoryFragment() {
        supportedMessages.add(BTService.CMD_GET_TEMP_FILES);
        supportedMessages.add(BTService.CMD_GET_TEMP_FILE);
        supportedMessages.add(BTService.CMD_GET_STATE_FILES);
        supportedMessages.add(BTService.CMD_GET_STATE_FILE);
    }

    public static HistoryFragment newInstance(int tabnr) {
        HistoryFragment fragment = new HistoryFragment();
        Bundle args = new Bundle();
        args.putInt("tabnr", tabnr);
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
                getHistoryFiles();
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
            Log.i("TerrariaBT","HistoryFragment: handleMessage() for message " + msg.what );
            Log.i("TerrariaBT", "HistoryFragment: " + msg.obj.toString());
            switch (msg.what) {
                case BTService.CMD_GET_TEMP_FILES:
                    wait.dismiss();
                    break;
                case BTService.CMD_GET_TEMP_FILE:
                    try {
                        xend = 0;
                        /*
                            2021-08-01 05:00:00 r=21 t=21
                            2021-08-01 06:00:00 r=21 t=21
                            2021-08-01 06:45:00 r=21 t=21
                         */
                        String content = new Gson().fromJson(msg.obj.toString(), FileContent.class).getContent();
                        String[] lines = content.split("\n");
                        for (String line : lines) {
                            String[] parts = line.split(" ");
                            if (parts[2].equalsIgnoreCase("start")) {
                                xstart = Objects.requireNonNull(dtfmt.parse(parts[0] + " " + parts[1])).getTime() / 1000;
                                String[] tm = parts[1].split(":");
                                hmstart = Integer.parseInt(tm[0]) * 3600 + Integer.parseInt(tm[1]) * 60 + Integer.parseInt(tm[2]);
                            } else if (parts[2].equalsIgnoreCase("stop")) {
                                xend = Objects.requireNonNull(dtfmt.parse(parts[0] + " " + parts[1])).getTime() / 1000;
                            } else {
                                int tm = (int)((Objects.requireNonNull(dtfmt.parse(parts[0] + " " + parts[1])).getTime() / 1000) - xstart);
                                int terr = Integer.parseInt(parts[3].split("=")[1]);
                                history_temp.put(tm, terr);
                            }
                            if (xend == 0) {
                                xend = xstart + 24 * 60 * 60;
                            }
                        }
                        drawTerrTempLine(0xFFF43F1A);
                    } catch (ParseException e) {
                        Log.i("TerrariaBT","HistoryFragment: Parse error in parsing Temp trace file");
                        e.printStackTrace();
                        Utils.showMessage(requireContext(), getView(), e.getMessage());
                    }
                    wait.dismiss();
                    break;
                case BTService.CMD_GET_STATE_FILES:
                    Files files = new Gson().fromJson(msg.obj.toString(), Files.class);
                    for (String f : files.getFiles()) {
                        fileList.add(f.replaceAll("state_", ""));
                    }
                    fileList.sort(Collections.reverseOrder());
                    wait.dismiss();
                    break;
                case BTService.CMD_GET_STATE_FILE:
                    try {
                        xend = 0;
                        /*  0123456789012345678
                            2021-08-01 05:00:00 start
                            2021-08-01 06:00:00 mist 1 -1
                            2021-08-01 06:00:00 fan_in 0
                            2021-08-01 06:00:00 fan_out 0
                        */
                        String content = new Gson().fromJson(msg.obj.toString(), FileContent.class).getContent();
                        String[] lines = content.split("\n");
                        for (String line : lines) {
                            String[] parts = line.split(" ");
                            if (parts[2].equalsIgnoreCase("start")) {
                                xstart = Objects.requireNonNull(dtfmt.parse(parts[0] + " " + parts[1])).getTime() / 1000;
                                String[] tm = parts[1].split(":");
                                hmstart = Integer.parseInt(tm[0]) * 3600 + Integer.parseInt(tm[1]) * 60 + Integer.parseInt(tm[2]);
                            } else if (parts[2].equalsIgnoreCase("stop")) {
                                xend = Objects.requireNonNull(dtfmt.parse(parts[0] + " " + parts[1])).getTime() / 1000;
                            } else {
                                int tm = (int) ((Objects.requireNonNull(dtfmt.parse(parts[0] + " " + parts[1])).getTime() / 1000) - xstart);
                                String dev = parts[2];
                                boolean on = parts[3].equalsIgnoreCase("1");
                                history_state.computeIfAbsent(dev, k -> new HashMap<>());
                                Objects.requireNonNull(history_state.get(dev)).put(tm, on);
                            }
                            if (xend == 0) {
                                xend = xstart + 24 * 60 * 60;
                            }
                        }
                        drawChart();
                    } catch (ParseException e) {
                        Log.i("TerrariaBT","HistoryFragment: Parse error in parsing State trace file");
                        e.printStackTrace();
                        Utils.showMessage(requireContext(), getView(), e.getMessage());
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
            curTabNr = getArguments().getInt("tabnr");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        wait = new WaitSpinner(requireActivity());
        // Bind to BTService
        bound = false;
        Intent intent = new Intent(getContext(), BTService.class);
        if(!requireContext().bindService(intent, connection, 0)) {
            Log.e("TerrariaBT","HistoryFragment: Could not bind to BTService");
        }

        chart = view.findViewById(R.id.linechart);
        chart.setHardwareAccelerationEnabled(true);
        chart.measure(0,0);
        chart.setTouchEnabled(false);
        chart.setDragEnabled(false);
        chart.setScaleEnabled(false);
        chart.setPinchZoom(false);
        chart.setDoubleTapToZoomEnabled(false);
        chart.setDescription(null);
        chart.setDrawGridBackground(true);
        chart.setDrawMarkers(false);
        chart.getLegend().setEnabled(false);

        Spinner list = view.findViewById(R.id.his_list);
        fileList = new ArrayList<>();
        fileList.add("<select day>");
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getActivity(), R.layout.file_dropdown, fileList);
        adapter.setDropDownViewResource(R.layout.file_dropdown);
        list.setAdapter(adapter);
        list.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position > 0) {
                    wait = new WaitSpinner(requireContext());
                    wait.start();
                    if (chart.getLineData() != null) {
                        chart.clearValues();
                    }
                    readHistoryState(fileList.get(position));
                    readHistoryTemperture(fileList.get(position));
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });
        Button btnView = view.findViewById(R.id.his_OkButton);
        btnView.setOnClickListener(v -> getParentFragmentManager()
                .beginTransaction()
                .replace(R.id.layout, StateFragment.newInstance(curTabNr))
                .commit());
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
            bound = false;
            Log.i("TerrariaBT", "HistoryFragment: onDestroy() end");
        } else {
            Log.i("TerrariaBT", "HistoryFragment: why is onDestroy() called?");
        }
    }

    private void getHistoryFiles() {
        if (svc != null) {
            Log.i("TerrariaBT", "HistoryFragment: getHistoryFiles() from server");
            try {
                Message msg = Message.obtain(null, BTService.CMD_GET_STATE_FILES);
                msg.replyTo = mMessenger;
                svc.send(msg);
            } catch (RemoteException e) {
                // There is nothing special we need to do if the service has crashed.
            }
        } else {
            Log.i("TerrariaBT", "HistoryFragment: BTService is not ready yet");
        }
    }

    private void readHistoryState(String day) {
        if (svc != null) {
            Log.i("TerrariaBT", "HistoryFragment: readHistoryState() from server");
            try {
                Message msg = Message.obtain(null, BTService.CMD_GET_STATE_FILE);
                msg.replyTo = mMessenger;
                msg.obj = "state_" + day;
                svc.send(msg);
            } catch (RemoteException e) {
                // There is nothing special we need to do if the service has crashed.
            }
        } else {
            Log.i("TerrariaBT", "HistoryFragment: BTService is not ready yet");
        }
    }

    private void drawChart() {
        // X axis
        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(true);
        xAxis.setAxisMinimum(hmstart);
        xAxis.setAxisMaximum((24 * 60 * 60) + hmstart);
//        xAxis.setLabelRotationAngle(270f);
        xAxis.setLabelCount((((int)(xend - xstart)) / 900) + 1, true); // force 11 labels
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getAxisLabel(float value, AxisBase axis) {
                int hr = (int)value / 3600;
                int mn = ((int)value - (hr * 3600)) / 60;
                return String.format(Locale.US, "%02d:%02d", (hr >= 24 ? hr -24 : hr), mn);
            }
        });
        // Y axis
        chart.getAxisRight().setEnabled(false); // suppress right y-axis
        YAxis yAxis = chart.getAxisLeft();
        yAxis.setTextSize(14f); // set the text size
        yAxis.setAxisMinimum(0f); // start at zero
        yAxis.setAxisMaximum(devicesNl.length * 1.5f); // the axis maximum
        yAxis.setTextColor(Color.BLACK);
        yAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getAxisLabel(float value, AxisBase axis) {
                int v = (int) (value * 2f); // 0, 3, 6, 9, ....
                if (v % 3 == 0 ) {
                    if (v == 0) {
                        return "Temp";
                    } else {
                        return devicesNl[(v - 3) / 3];
                    }
                } else {
                    return "";
                }
            }
        });
        yAxis.setGranularity(1.5f); // interval 1.5
        yAxis.setLabelCount(13, true); // force 12 labels

        // Constructing the datasets
        dataSets.add(getDatasets("light1",  Color.BLACK));
        dataSets.add(getDatasets("light2",  Color.BLUE));
        dataSets.add(getDatasets("light3",  Color.GRAY));
        dataSets.add(getDatasets("light4",  Color.RED));
        dataSets.add(getDatasets("uvlight", Color.GREEN));
        dataSets.add(getDatasets("light6",  Color.MAGENTA));
        dataSets.add(getDatasets("pump",    Color.BLACK));
        dataSets.add(getDatasets("sprayer", Color.GRAY));
        dataSets.add(getDatasets("mist",    Color.BLUE));
        dataSets.add(getDatasets("fan_in",  Color.RED));
        dataSets.add(getDatasets("fan_out", Color.GREEN));
        chart.setData(lineData);

        chart.invalidate(); // refresh
    }

    private void readHistoryTemperture(String day) {
        if (svc != null) {
            Log.i("TerrariaBT", "HistoryFragment: readHistoryTemperture() from server");
            try {
                Message msg = Message.obtain(null, BTService.CMD_GET_TEMP_FILE);
                msg.replyTo = mMessenger;
                msg.obj = "temp_" + day;
                svc.send(msg);
            } catch (RemoteException e) {
                // There is nothing special we need to do if the service has crashed.
            }
        } else {
            Log.i("TerrariaBT", "HistoryFragment: BTService is not ready yet");
        }
    }

    public void drawTerrTempLine(int color) {
        int curTemp = 0;
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < (xend - xstart); i++) {
            if (history_temp.get(i) != null) {
                curTemp = history_temp.get(i);
            }
            entries.add(new Entry(i + hmstart, (curTemp - 15f) / 20f));
        }
        LineDataSet dataSet = new LineDataSet(entries, ""); // add entries to dataset
        dataSet.setColor(color);
        dataSet.setDrawValues(false);
        dataSet.setDrawCircles(false);
        dataSets.add(dataSet);
        chart.invalidate(); // refresh

    }

    private ILineDataSet getDatasets(String device, int color) {
        List<Entry> entries = getEntries(device);
        LineDataSet dataSet = new LineDataSet(entries, ""); // add entries to dataset
        dataSet.setColor(color);
        dataSet.setDrawValues(false);
        dataSet.setDrawCircles(false);
        return dataSet;
    }

    private List<Entry> getEntries(String device) {
        int ix = getIndex(device);
        Map<Integer, Boolean> dev_states = history_state.get(device);
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < (xend - xstart); i++) {
            if (Objects.requireNonNull(dev_states).get(i) != null) {
                devState[ix] = Boolean.TRUE.equals(dev_states.get(i));
            }
            entries.add(new Entry(i + hmstart, (devState[ix] ? 1f : 0f) + (ix + 1) * 1.5f));
        }
        return entries;
    }

    private int getIndex(String device) {
        for (int i = 0; i < devicesEn.length; i++) {
            if (devicesEn[i].equalsIgnoreCase(device)) {
                return i;
            }
        }
        return devicesEn.length;
    }
}