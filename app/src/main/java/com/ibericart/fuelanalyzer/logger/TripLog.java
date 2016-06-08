package com.ibericart.fuelanalyzer.logger;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.ibericart.fuelanalyzer.model.TripRecord;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Saves information about trips in an SQLite database.
 * <br />
 * Uses code from https://github.com/pires/android-obd-reader
 * and from https://github.com/wdkapps/FillUp
 */
public class TripLog {

    // a tag for debug logging (the name of this class)
    private static final String TAG = TripLog.class.getName();

    // the database version number
    public static final int DATABASE_VERSION = 1;

    // the name of the database
    public static final String DATABASE_NAME = "trips.db";

    // database table name
    private static final String RECORDS_TABLE = "Records";

    // TODO check if this is needed
    // SQL commands to delete the database tables and schema
    public static final String[] DATABASE_DELETE = new String[] {
            "drop table if exists " + RECORDS_TABLE + ";",
    };

    // column names for RECORDS_TABLE
    private static final String RECORD_ID = "id";
    private static final String RECORD_START_DATE = "startDate";
    private static final String RECORD_END_DATE = "endDate";
    private static final String RECORD_SPEED_MAX = "speedMax";
    private static final String RECORD_RPM_MAX = "rpmMax";
    private static final String RECORD_ENGINE_RUNTIME = "engineRuntime";

    // SQL commands to create the database
    public static final String[] DATABASE_CREATE = new String[]{
            "create table " + RECORDS_TABLE + " ( " +
                    RECORD_ID + " integer primary key autoincrement, " +
                    RECORD_START_DATE + " integer not null, " +
                    RECORD_END_DATE + " integer, " +
                    RECORD_SPEED_MAX + " integer, " +
                    RECORD_RPM_MAX + " integer, " +
                    RECORD_ENGINE_RUNTIME + " text" +
                    ");"
    };

    // array containing all the column names for RECORDS_TABLE
    private static final String[] RECORDS_TABLE_COLUMNS = new String[]{
            RECORD_ID,
            RECORD_START_DATE,
            RECORD_END_DATE,
            RECORD_SPEED_MAX,
            RECORD_RPM_MAX,
            RECORD_ENGINE_RUNTIME
    };

    // singleton instance
    private static TripLog instance;

    // context of the instance creator
    private final Context context;

    // a helper instance used to open and close the database
    private final TripLogOpenHelper helper;

    // the database
    private final SQLiteDatabase db;

    // private constructor to build the singleton
    private TripLog(Context context) {
        this.context = context;
        this.helper = new TripLogOpenHelper(this.context);
        this.db = helper.getWritableDatabase();
    }

    /**
     * Returns a single instance, creating it if necessary.
     *
     * @return A TripLog singleton instance.
     */
    public static TripLog getInstance(Context context) {
        if (instance == null) {
            instance = new TripLog(context);
        }
        return instance;
    }

    /**
     * Convenience method to make assertions.
     *
     * @param assertion an asserted boolean condition.
     * @param tag a String identifying the calling method.
     * @param message an error message to display / log.
     * @throws RuntimeException if the assertion fails.
     */
    private static void assertion(boolean assertion, String tag, String message) {
        if (!assertion) {
            String assertionMessage = "Assertion failed: " + message;
            Log.e(tag, assertionMessage);
            throw new RuntimeException(assertionMessage);
        }
    }

    public TripRecord startTrip() {
        final String tag = TAG + ".createRecord()";

        try {
            TripRecord record = new TripRecord();
            long rowId = db.insertOrThrow(RECORDS_TABLE, null, getContentValues(record));
            record.setId((int) rowId);
            return record;
        }
        catch (SQLiteConstraintException e) {
            Log.e(tag, "SQLiteConstraintException: " + e.getMessage());
        }
        catch (SQLException e) {
            Log.e(tag, "SQLException: " + e.getMessage());
        }
        return null;
    }

    /**
     * Updates a trip record in the log.
     *
     * @param record the TripRecord to update.
     * @return a boolean indicating the success / failure of the update (true == success)
     */
    public boolean updateRecord(TripRecord record) {
        final String tag = TAG + ".updateRecord()";
        assertion((record.getId() != null), tag, "record id cannot be null");
        boolean success = false;
        try {
            ContentValues values = getContentValues(record);
            values.remove(RECORD_ID);
            String whereClause = RECORD_ID + "=" + record.getId();
            int count = db.update(RECORDS_TABLE, values, whereClause, null);
            success = (count > 0);
        }
        catch (SQLiteConstraintException e) {
            Log.e(tag, "SQLiteConstraintException: " + e.getMessage());
        }
        catch (SQLException e) {
            Log.e(tag, "SQLException: " + e.getMessage());
        }
        return success;
    }

    /**
     * Convenience method to convert a TripRecord instance to a set of key/value
     * pairs in a ContentValues instance utilized by SQLite access methods.
     *
     * @param record the TripRecord to convert.
     * @return a ContentValues instance representing the specified TripRecord.
     */
    private ContentValues getContentValues(TripRecord record) {
        ContentValues values = new ContentValues();
        values.put(RECORD_ID, record.getId());
        values.put(RECORD_START_DATE, record.getStartDate().getTime());
        if (record.getEndDate() != null) {
            values.put(RECORD_END_DATE, record.getEndDate().getTime());
        }
        values.put(RECORD_SPEED_MAX, record.getSpeedMax());
        values.put(RECORD_RPM_MAX, record.getEngineRpmMax());
        if (record.getEngineRuntime() != null) {
            values.put(RECORD_ENGINE_RUNTIME, record.getEngineRuntime());
        }
        return values;
    }

    // TODO check the purpose of this method and decide if it's still needed
    private void update() {
        String sql = "ALTER TABLE " + RECORDS_TABLE + " ADD COLUMN " + RECORD_ENGINE_RUNTIME + " integer;";
        db.execSQL(sql);
    }

    public List<TripRecord> readAllRecords() {

        // TODO is this still needed?
        //update();

        final String tag = TAG + ".readAllRecords()";
        List<TripRecord> list = new ArrayList<>();
        Cursor cursor = null;

        try {
            String orderBy = RECORD_START_DATE;
            cursor = db.query(RECORDS_TABLE, RECORDS_TABLE_COLUMNS, null, null, null, null,
                    orderBy, null);
            // create a list of TripRecords from the data
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    do {
                        TripRecord record = getRecordFromCursor(cursor);
                        list.add(record);
                    }
                    while (cursor.moveToNext());
                }
            }

        }
        catch (SQLException e) {
            Log.e(tag, "SQLException: " + e.getMessage());
            list.clear();
        }
        finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return list;
    }

    /**
     * Deletes a specified trip record from the log.
     *
     * @param id the TripRecord to delete.
     * @return a boolean indicating the success / failure of the delete (true == success)
     */
    public boolean deleteTrip(long id) {
        final String tag = TAG + ".deleteRecord()";
        boolean success = false;
        try {
            String whereClause = RECORD_ID + "=" + id;
            String[] whereArgs = null;
            int count = db.delete(RECORDS_TABLE, whereClause, whereArgs);
            success = (count == 1);
        }
        catch (SQLException e) {
            Log.e(tag, "SQLException: " + e.getMessage());
        }
        return success;
    }

    /**
     * Convenience method to create a TripRecord instance from values read
     * from the database.
     *
     * @param cursor a Cursor containing results of a database query.
     * @return a TripRecord instance (null if no data).
     */
    private TripRecord getRecordFromCursor(Cursor cursor) {
        final String tag = TAG + ".getRecordFromCursor()";
        TripRecord record = null;
        if (cursor != null) {
            record = new TripRecord();
            int id = cursor.getInt(cursor.getColumnIndex(RECORD_ID));
            long startDate = cursor.getLong(cursor.getColumnIndex(RECORD_START_DATE));
            long endTime = cursor.getLong(cursor.getColumnIndex(RECORD_END_DATE));
            int speedMax = cursor.getInt(cursor.getColumnIndex(RECORD_SPEED_MAX));
            int engineRpmMax = cursor.getInt(cursor.getColumnIndex(RECORD_RPM_MAX));
            record.setId(id);
            record.setStartDate(new Date(startDate));
            record.setEndDate(new Date(endTime));
            record.setSpeedMax(speedMax);
            record.setEngineRpmMax(engineRpmMax);
            if (!cursor.isNull(cursor.getColumnIndex(RECORD_ENGINE_RUNTIME))) {
                record.setEngineRuntime(cursor.getString(cursor.getColumnIndex(RECORD_ENGINE_RUNTIME)));
            }
        }
        return record;
    }
}
