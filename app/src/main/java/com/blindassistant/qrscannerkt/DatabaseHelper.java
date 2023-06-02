package com.blindassistant.qrscannerkt;
import android.content.ContentValues;
import android.content.Context;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


public class DatabaseHelper extends SQLiteOpenHelper {

    private static String DB_NAME = "qr.db";
    private static String DB_PATH = "";
    private static final int DB_VERSION = 3;
    private SQLiteDatabase mDataBase;
    private final Context mContext;
    private boolean mNeedUpdate = false;
    public DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        DB_PATH = context.getApplicationInfo().dataDir + "/databases/";
        this.mContext = context;

        copyDataBase();

        this.getWritableDatabase();
    }
    public void updateDataBase() throws IOException {
        if (mNeedUpdate) {
            File dbFile = new File(DB_PATH + DB_NAME);
            if (dbFile.exists())
                dbFile.delete();

            copyDataBase();

            mNeedUpdate = false;
        }
    }

    public void mergeDataBase(String newDB, String dbPath) throws IOException {
//        InputStream mInput = mContext.getAssets().open(newDB + ".db");
//        OutputStream mOutput = new FileOutputStream(DB_PATH + newDB + ".db");
//        byte[] mBuffer = new byte[1024];
//        int mLength;
//        while ((mLength = mInput.read(mBuffer)) > 0)
//            mOutput.write(mBuffer, 0, mLength);
//        mOutput.flush();
//        mOutput.close();
//        mInput.close();

        this.getWritableDatabase().execSQL("attach '" + dbPath + "' as toMerge;");
        this.getWritableDatabase().execSQL("CREATE TABLE " + newDB + "(qr TEXT(32) NOT NULL, name TEXT(50) NOT NULL, nameRU TEXT(50) NOT NULL);");
        this.getWritableDatabase().execSQL("insert into " + newDB + " select * from toMerge." + newDB + "; ");
        this.getWritableDatabase().execSQL("detach toMerge;");

        String infosql = "INSERT INTO info (name,qr) VALUES (\"test\", \"098f6bcd4621d373cade4e832627b4f6\")";
        this.getWritableDatabase().execSQL(infosql);
    }
    private boolean checkDataBase() {
        File dbFile = new File(DB_PATH + DB_NAME);
        return dbFile.exists();
    }
    private void copyDataBase() {
        if (!checkDataBase()) {
            this.getReadableDatabase();
            this.close();
            try {
                copyDBFile();
            } catch (IOException mIOException) {
                throw new Error("ErrorCopyingDataBase");
            }
        }
    }
    private void copyDBFile() throws IOException {
        InputStream mInput = mContext.getAssets().open(DB_NAME);
        OutputStream mOutput = new FileOutputStream(DB_PATH + DB_NAME);
        byte[] mBuffer = new byte[1024];
        int mLength;
        while ((mLength = mInput.read(mBuffer)) > 0)
            mOutput.write(mBuffer, 0, mLength);
        mOutput.flush();
        mOutput.close();
        mInput.close();
    }

    public boolean openDataBase() throws SQLException {
        mDataBase = SQLiteDatabase.openDatabase(DB_PATH + DB_NAME, null, SQLiteDatabase.CREATE_IF_NECESSARY);
        return mDataBase != null;
    }

    @Override
    public synchronized void close() {
        if (mDataBase != null)
            mDataBase.close();
        super.close();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {

    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (newVersion > oldVersion)
            mNeedUpdate = true;
    }
}