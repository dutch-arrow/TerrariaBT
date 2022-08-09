package nl.das.terraria.dialogs;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Window;
import android.view.WindowManager;

import androidx.appcompat.app.AlertDialog;

import nl.das.terraria.R;

public class WaitSpinner {
    private final Context context;
    private AlertDialog waitSpinner;
    private boolean started = false;

    public WaitSpinner(Context context) {
        this.context = context;
    }

    public void start() {
        Log.i("TerrariaBT","start wait");
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        LayoutInflater inflater = LayoutInflater.from(context);
        builder.setView(inflater.inflate(R.layout.wait_dlg, null));
        builder.setCancelable(true);
        waitSpinner = builder.create();
        waitSpinner.show();
        resizeDialog();
        started = true;
    }

    public void dismiss() {
        Log.i("TerrariaBT","dismiss wait");
        if (started) {
            started = false;
            waitSpinner.dismiss();
        } else {
            Log.i("TerrariaBT","wait not started");
        }
    }

    /**
     * To resize the size of this dialog
     */
    private void resizeDialog() {
            Window window = waitSpinner.getWindow();
            if (context == null || window == null) return;
            DisplayMetrics displayMetrics = new DisplayMetrics();
            ((WindowManager)context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(displayMetrics);
            // Adjust width and height
            // Change background
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));// make tranparent around the popup
    }
}
