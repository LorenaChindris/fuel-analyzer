package com.ibericart.fuelanalyzer.activity;

import android.app.Dialog;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import com.ibericart.fuelanalyzer.R;
import com.ibericart.fuelanalyzer.trips.TripListAdapter;
import com.ibericart.fuelanalyzer.trips.TripLog;
import com.ibericart.fuelanalyzer.trips.TripRecord;

import java.util.List;

import roboguice.activity.RoboActivity;

import static com.ibericart.fuelanalyzer.activity.ConfirmDialog.createDialog;

/**
 * Activity which displays the list of trips.
 * Uses code from https://github.com/pires/android-obd-reader
 * and https://github.com/wdkapps/FillUp
 */
public class TripListActivity extends RoboActivity implements ConfirmDialog.Listener {

    private List<TripRecord> tripRecords;
    private TripLog tripLog = null;
    private TripListAdapter tripListAdapter = null;

    // the currently selected row from the list of tripRecords
    private int selectedRow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trips_list);

        ListView listView = (ListView) findViewById(R.id.tripList);

        tripLog = TripLog.getInstance(this.getApplicationContext());
        tripRecords = tripLog.readAllRecords();
        tripListAdapter = new TripListAdapter(this, tripRecords);
        listView.setAdapter(tripListAdapter);
        registerForContextMenu(listView);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // TODO refactor or remove this method
        // Inflate the menu; this adds items to the action bar if it is present.
        //getMenuInflater().inflate(R.menu.menu_trips_list, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
                                    ContextMenu.ContextMenuInfo menuInfo) {
        // TODO finish the implementation / refactor method
        // create the menu
        getMenuInflater().inflate(R.menu.context_trip_list, menu);

        // get index of currently selected row
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        selectedRow = (int) info.id;

        // get record that is currently selected
        TripRecord record = tripRecords.get(selectedRow);
    }

    // TODO this method is never used; finish the implementation
    private void deleteTrip() {
        // get the record to delete from our list of tripRecords
        TripRecord record = tripRecords.get(selectedRow);

        // attempt to remove the record from the log
        if (tripLog.deleteTrip(record.getID())) {

            // remove the record from our list of tripRecords
            tripRecords.remove(selectedRow);

            // update the list view
            tripListAdapter.notifyDataSetChanged();
        }
        else {
            // TODO use Toast properly
            //Utilities.toast(this,getString(R.string.toast_delete_failed));
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        // get index of currently selected row
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        selectedRow = (int) info.id;

        switch (item.getItemId()) {
            case R.id.itemDelete:
                // TODO refactor so that it doesn't use a deprecated method anymore
                showDialog(ConfirmDialog.DIALOG_CONFIRM_DELETE_ID);
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        // TODO remove deprecated method
        return createDialog(id, this, this);
    }

    /**
     * Called when the user has selected a gasoline record to delete
     * from the log and has confirmed deletion.
     */
    protected void deleteRow() {
        // get the record to delete from our list of tripRecords
        TripRecord record = tripRecords.get(selectedRow);
        // attempt to remove the record from the log
        if (tripLog.deleteTrip(record.getID())) {
            tripRecords.remove(selectedRow);
            tripListAdapter.notifyDataSetChanged();
        }
        else {
            // TODO use Toast properly
            //Utilities.toast(this,getString(R.string.toast_delete_failed));
        }
    }

    @Override
    public void onConfirmationDialogResponse(int id, boolean confirmed) {
        // TODO don't use the deprecated method anymore
        removeDialog(id);
        if (!confirmed) {
            return;
        }
        switch (id) {
            case ConfirmDialog.DIALOG_CONFIRM_DELETE_ID:
                deleteRow();
                break;
            default:
                // TODO use Toast properly
                //Utilities.toast(this,"Invalid dialog id.");
        }
    }
}
