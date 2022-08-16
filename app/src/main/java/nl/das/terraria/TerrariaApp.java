package nl.das.terraria;

import static nl.das.terraria.R.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import nl.das.terraria.dialogs.WaitSpinner;
import nl.das.terraria.fragments.HelpFragment;
import nl.das.terraria.fragments.HistoryFragment;
import nl.das.terraria.fragments.RulesetsFragment;
import nl.das.terraria.fragments.StateFragment;
import nl.das.terraria.fragments.TimersFragment;
import nl.das.terraria.json.Properties;
import nl.das.terraria.json.Error;

public class TerrariaApp extends AppCompatActivity {

    public static final boolean[] MOCK = {false, false, false};

    public static int nrOfTerraria;
    public static Properties[] configs;
    public static int curTabNr;
    private static String curUuid;
    public TerrariaApp instance;
    private final ArrayList<Integer> supportedMessages = new ArrayList<>();
    private Messenger svc;

    public View appView;

    /** Flag indicating whether we have called bind on the BTService. */
    private boolean bound;
    private BluetoothDevice connectedDevice;
    private View mTabbar;
    private TextView[] mTabTitles;
    private WaitSpinner wait;
    public Menu menu;

    public TerrariaApp() {
        supportedMessages.add(BTService.MSG_DISCONNECTED);
        supportedMessages.add(BTService.CMD_GET_PROPERTIES);
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
                getProperties(curTabNr);
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
    @SuppressLint("HandlerLeak")
    class IncomingHandler extends Handler {
        private boolean connected;
        @SuppressLint("MissingPermission")
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            Log.i("TerrariaBT","TerrariaApp: handleMessage() for message " + msg.what );
            if (msg.what == BTService.MSG_DISCONNECTED) {
                connected = false;
                Utils.showMessage(getBaseContext(), appView, "Verbinding met met terrarium " + connectedDevice.getName() + " is verbroken");
            } else if (msg.what == BTService.CMD_GET_PROPERTIES) {
                Log.i("TerrariaBT", "TerrariaApp: " + msg.obj.toString());
                if (msg.obj.toString().startsWith("{\"error")) {
                    Error err = new Gson().fromJson(msg.obj.toString(), Error.class);
                    Utils.showMessage(getBaseContext(), appView, err.getError());
                } else {
                    Properties tmp = new Gson().fromJson(msg.obj.toString(), Properties.class);
                    configs[curTabNr - 1].setDevices(tmp.getDevices());
                    configs[curTabNr - 1].setTcu(tmp.getTcu());
                    configs[curTabNr - 1].setNrOfTimers(tmp.getNrOfTimers());
                    configs[curTabNr - 1].setNrOfPrograms(tmp.getNrOfPrograms());
                }
                connected = true;
            }
            mTabbar.setVisibility(View.VISIBLE);
            menu.findItem(id.menu_history_item).setVisible(true);
            for (int i = 0; i < nrOfTerraria; i++) {
                mTabTitles[i].setVisibility(View.VISIBLE);
                mTabTitles[i].setText(getString(string.tabName, configs[i].getTcuName(), (MOCK[i] ? " (Test)" : "")));
            }
            if (connected) { menu.performIdentifierAction(id.menu_state_item, 0); }// select state fragment
            wait.dismiss();
        }
    }

    @SuppressLint("MissingPermission")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(layout.activity_main);
        appView = findViewById(android.R.id.content).getRootView();
        Log.i("TerrariaBT", "TerrariaApp: onCreate()");
        instance = this;
        wait = new WaitSpinner(this);
        curTabNr = 1;

        // Get the configuration from the properties file
        java.util.Properties config = readConfig();
        mTabTitles = new TextView[nrOfTerraria];
        configs = new Properties[nrOfTerraria];
        mTabbar = findViewById(id.tabbar);
        mTabbar.setVisibility(View.GONE);
        // Get the properties from the TCU's
        for (int i = 0; i < nrOfTerraria; i++) {
            int tabnr = i + 1;
            int r = getResources().getIdentifier("tab" + tabnr, "id", "nl.das.terrariaBT");
            mTabTitles[i] = findViewById(r);
            configs[i] = new Properties();
            configs[i].setTcuName(config.getProperty("t" + tabnr +".title"));
            configs[i].setDeviceName(config.getProperty("t" + tabnr +".hostname"));
            configs[i].setUuid(config.getProperty("t" + tabnr +".uuid"));
            configs[i].setMockPostfix(config.getProperty("t" + tabnr +".mock_postfix"));
        }

        // Create the main toolbar
        Toolbar mTopToolbar = findViewById(id.main_toolbar);
        setSupportActionBar(mTopToolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayShowTitleEnabled(false);

        // Start the BTService and bind to it
        curUuid = configs[0].getUuid();
        connectedDevice = findBTDevice(configs[0].getDeviceName());
        Intent intent = new Intent(getApplicationContext(), BTService.class);
        intent.putExtra("UUID", curUuid);
        intent.putExtra("Device", connectedDevice);
        startService(intent);
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.i("TerrariaBT", "TerrariaApp: onStart()");
    }

    @Override
    public void onRestart() {
        super.onRestart();
        Log.i("TerrariaBT", "TerrariaApp: onRestart()");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i("TerrariaBT", "TerrariaApp: onResume()");
        mTabbar.setVisibility(View.VISIBLE);
        mTabTitles[curTabNr - 1].setTextColor(Color.WHITE);
        bound = false;
        Intent intent = new Intent(getApplicationContext(), BTService.class);
        if (!getApplicationContext().bindService(intent, connection, 0)) {
            Log.e("TerrariaBT", "TerrariaApp: Could not bind to BTService");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i("TerrariaBT", "TerrariaApp: onPause()");
        getApplicationContext().unbindService(connection);
        bound = false;
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.i("TerrariaBT", "TerrariaApp: onStop()");
    }

    @Override
    protected void onDestroy() {
        Log.i("TerrariaBT", "TerrariaApp: onDestroy()");
        // Unbind from the service
        if (bound) {
            // If we have received the service, and hence registered with
            // it, then now is the time to unregister.
            if (svc != null) {
                try {
                    Message msg = Message.obtain(null, BTService.MSG_UNREGISTER_CLIENT);
                    msg.replyTo = mMessenger;
                    svc.send(msg);
                } catch (RemoteException e) {
                    // There is nothing special we need to do if the service
                    // has crashed.
                }
            }
            unbindService(connection);
            bound = false;
        }
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        this.menu = menu;
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_state_item) {
            mTabbar.setVisibility(View.VISIBLE);
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.layout, StateFragment.newInstance(curTabNr), "state")
                    .commit();
            return true;
        }
        if (id == R.id.menu_timers_item) {
            mTabbar.setVisibility(View.VISIBLE);
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.layout, TimersFragment.newInstance(curTabNr), "timers")
                    .commit();
            return true;
        }
        if (id == R.id.menu_program_item) {
            mTabbar.setVisibility(View.VISIBLE);
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.layout, RulesetsFragment.newInstance(curTabNr), "rulesets")
                    .commit();
            return true;
        }
        if (id == R.id.menu_history_item) {
            mTabbar.setVisibility(View.VISIBLE);
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.layout, HistoryFragment.newInstance(curTabNr),"history")
                    .commit();
            return true;
        }
        if (id == R.id.menu_help_item) {
            mTabbar.setVisibility(View.GONE);
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.layout, new HelpFragment(), "help")
                    .commit();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void onTabSelect(View v){
        Log.i("TerrariaBT","TerrariaApp: onTabSelect()");
        getApplicationContext().unbindService(connection);
        List<Fragment> frags = getSupportFragmentManager().getFragments();
        for (Fragment f : frags) {
            Log.i("TerrariaBT","TerrariaApp: Active fragment '" + f.getTag() + "' removed");
            getSupportFragmentManager().beginTransaction().remove(f).commit();
        }
        Intent intent = new Intent(getApplicationContext(), BTService.class);
        if (stopService(intent)) {
            mTabTitles[curTabNr - 1].setTextColor(Color.BLACK);
            curTabNr = Integer.parseInt((String)v.getTag());
            mTabbar.setVisibility(View.VISIBLE);
            mTabTitles[curTabNr - 1].setTextColor(Color.WHITE);
            curUuid = configs[curTabNr - 1].getUuid();
            connectedDevice = findBTDevice(configs[curTabNr - 1].getDeviceName());
            intent = new Intent(getApplicationContext(), BTService.class);
            intent.putExtra("UUID", curUuid);
            intent.putExtra("Device", connectedDevice);
            startService(intent);
            if(!getApplicationContext().bindService(intent, connection, 0)) {
                Log.e("TerrariaBT","TerrariaApp: Could not bind to BTService");
            }
        }
    }

    private java.util.Properties readConfig() {
        java.util.Properties config = new java.util.Properties();
        AssetManager assetManager = getAssets();
        try {
            config.load(assetManager.open("config.properties"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        nrOfTerraria = Integer.parseInt(config.getProperty("nrOfTerraria"));
        return config;
    }
    public void getProperties(int tabnr) {
        int tcunr = tabnr - 1;
        String pfx = configs[tcunr].getMockPostfix();
        if (MOCK[tcunr]) {
            Log.i("TerrariaBT","TerrariaApp: getProperties() from file (mock)");
            try {
                String response = new BufferedReader(
                        new InputStreamReader(getResources().getAssets().open("properties_" + pfx + ".json")))
                        .lines().collect(Collectors.joining("\n"));
                Properties tmp = new Gson().fromJson(response, Properties.class);
                configs[tcunr].setDevices(tmp.getDevices());
                configs[tcunr].setTcu(tmp.getTcu());
                configs[tcunr].setNrOfTimers(tmp.getNrOfTimers());
                configs[tcunr].setNrOfPrograms(tmp.getNrOfPrograms());
            } catch (IOException ignored) {
            }
        } else {
            if (svc != null) {
                Log.i("TerrariaBT", "TerrariaApp: getProperties() from server");
                wait.start();
                try {
                    Message msg = Message.obtain(null, BTService.CMD_GET_PROPERTIES);
                    msg.replyTo = mMessenger;
                    svc.send(msg);
                } catch (RemoteException e) {
                    // There is nothing special we need to do if the service has crashed.
                    Log.i("TerrariaBT", "TerrariaApp: BTService is crashed");                }
            } else {
                Log.i("TerrariaBT", "TerrariaApp: BTService is not ready yet");
            }
        }
    }
/*
===================================== Bluetooth methods ==========================================================================
 */
    ActivityResultLauncher<Intent> enableBt = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
            result -> Log.i("TerrariaBT", "TerrariaApp: " + result.getResultCode())
    );

    ActivityResultLauncher<String> reqPermission = registerForActivityResult(new ActivityResultContracts.RequestPermission(),
            result -> Log.i("TerrariaBT", "TerrariaApp: " + result)
    );
    
    private BluetoothDevice findBTDevice(String hostName) {
        BluetoothDevice curBTDevice = null;
        // Get the list of paired devices from the local Bluetooth adapter
        BluetoothManager bluetoothManager = getSystemService(BluetoothManager.class);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter == null) {
            Log.i("TerrariaBT", "TerrariaApp: No bluetooth adapter found.");
        } else {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                Log.e("TerrariaBT", "TerrariaApp: : android.permission.BLUETOOTH_SCAN not given. ");
                reqPermission.launch(Manifest.permission.BLUETOOTH_SCAN);
            }
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Log.e("TerrariaBT", "TerrariaApp: : android.permission.BLUETOOTH_CONNECT not given.");
                reqPermission.launch(Manifest.permission.BLUETOOTH_CONNECT);
            }
            if (!bluetoothAdapter.isEnabled()) {
                Log.i("TerrariaBT", "TerrariaApp: Bluetooth is not enabled.");
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                try {
                    enableBt.launch(enableBtIntent);
                } catch (ActivityNotFoundException e) {
                    Log.e("TerrariaBT", "TerrariaApp: " + e.getMessage());
                }
            }
            Log.i("TerrariaBT", "TerrariaApp: Bluetooth adapter ready.");
            Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
            Log.i("TerrariaBT", "TerrariaApp: found " + pairedDevices.size() + " paired devices.");
            if (pairedDevices.size() > 0) {
                // There are paired devices. Get the name and address of each paired device.
                for (BluetoothDevice device : pairedDevices) {
                    String deviceName = device.getName();
                    if (deviceName.equalsIgnoreCase(hostName)) {
                        curBTDevice = device;
                    }
                }
            }
            if (curBTDevice == null) {
                runOnUiThread(() -> {
                    wait.dismiss();
                    Utils.showMessage(getBaseContext(), appView, "TCU '" + hostName + "' is niet gekoppeld.");
                });
            }
        }
        assert curBTDevice != null;
        Log.i("TerrariaBT", "TerrariaApp: found the device '" + curBTDevice.getName() + "'.");
        return curBTDevice;
    }

}