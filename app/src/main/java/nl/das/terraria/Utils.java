package nl.das.terraria;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

public class Utils {
    public static String cvthm2string(int hour, int minute) {
        if (hour == 0 && minute == 0) {
            return "";
        } else {
            return String.format("%02d.%02d", hour, minute);
        }
    }
    public static int getH(String tm) {
        if (tm.trim().length() == 0) {
            return 0;
        } else {
            return Integer.parseInt(tm.split("\\.")[0]);
        }
    }
    public static int getM(String tm) {
        if (tm.trim().length() == 0) {
            return 0;
        } else {
            return Integer.parseInt(tm.split("\\.")[1]);
        }
    }

    public static void showMessage(Context ctx, View view, String message) {

        // inflate the layout of the popup window
        LayoutInflater inflater = (LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View popupView = inflater.inflate(R.layout.popup_window, null);
        TextView pupText = popupView.findViewById(R.id.putxtMessage);
        pupText.setText(message);

        // create the popup window
        int width = LinearLayout.LayoutParams.WRAP_CONTENT;
        int height = LinearLayout.LayoutParams.WRAP_CONTENT;
        final PopupWindow popupWindow = new PopupWindow(popupView, width, height, true);

        // show the popup window
        // which view you pass in doesn't matter, it is only used for the window tolken
        popupWindow.showAtLocation(view, Gravity.CENTER, 0, 0);

        // dismiss the popup window when touched
        popupView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                popupWindow.dismiss();
                return true;
            }
        });

    }
}
