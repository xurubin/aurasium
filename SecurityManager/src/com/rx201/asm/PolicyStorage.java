package com.rx201.asm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.rx201.apkmon.permissions.AurasiumPermission;
import com.rx201.apkmon.permissions.PermissionRequestIntent;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class PolicyStorage extends SQLiteOpenHelper {
	 
    // All Static variables
    // Database Version
    private static final int DATABASE_VERSION = 2;
 
    // Database Name
    private static final String DATABASE_NAME = "policies";
 
    // Contacts table name
    private static final String TABLE_POLICY = "policy";
 
    // Contacts Table Columns names
    private static final String KEY_PACKAGE = "package";
    private static final String KEY_PERMISSION = "permission";
    private static final String KEY_VALUE = "handler";
 
    private static PolicyStorage instance = null;
    public static synchronized PolicyStorage get(Context context) {
    	if (instance == null)
    		instance = new PolicyStorage(context.getApplicationContext());
    	return instance;
    }
    protected PolicyStorage(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }
 
    // Creating Tables
    @Override
    public void onCreate(SQLiteDatabase db) {
        String CREATE_CONTACTS_TABLE = "CREATE TABLE " + TABLE_POLICY + "("
                + KEY_PACKAGE + " TEXT ," + KEY_PERMISSION + " TEXT ,"
                + KEY_VALUE + " TEXT ,"
                + " PRIMARY KEY (" + KEY_PACKAGE + ", " + KEY_PERMISSION + "))";
        db.execSQL(CREATE_CONTACTS_TABLE);
    }
 
    // Upgrading database
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Drop older table if existed
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_POLICY);
 
        // Create tables again
        onCreate(db);
    }
 
    private Cursor lookupPolicy(PermissionRequestIntent permission) {
        SQLiteDatabase db = this.getReadableDatabase();
    	Cursor cursor = db.query(TABLE_POLICY,
                new String[] {KEY_PACKAGE, KEY_PERMISSION, KEY_VALUE },
                KEY_PACKAGE + "=?",
                new String[]{permission.getApplicationPackage()},
                null, null, null);
    	
    	AurasiumPermission target = permission.getPermissionRequest();
    	if (cursor.moveToFirst())
    	{
    		do {
    			if(target.equals(PermissionTranslator.StringToPermission(cursor.getString(1))))
    			{
    				return cursor;
    			}
    		} while (cursor.moveToNext());
    	}
    	cursor.close();
    	return null;
    }
    public String getPolicy(PermissionRequestIntent permission) {
    	Cursor result = lookupPolicy(permission);
    	if (result == null)
    		return null;
    	else {
    		String r = result.getString(2);
    		result.close();
    		return r;
    	}
    }
    // Adding/updating a policy
    void setPolicy(PermissionRequestIntent permission, String decision) {
        ContentValues values = new ContentValues();
        values.put(KEY_VALUE, decision);

        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = lookupPolicy(permission);
        //updating 
        if (cursor != null)
        {
        	db.update(TABLE_POLICY, values, 
        			KEY_PACKAGE + "=? AND " + KEY_PERMISSION + "=?", 
        			new String[]{cursor.getString(0), cursor.getString(1)});
        }
        else //inserting
        {
            values.put(KEY_PACKAGE, permission.getApplicationPackage());
            values.put(KEY_PERMISSION, PermissionTranslator.PermissionToString(permission.getPermissionRequest())); 
            db.insert(TABLE_POLICY, null, values);
        }
        db.close(); // Closing database connection
    }
 
    public HashMap<PermissionRequestIntent, String> getAllPolicies()
    {
    	return getAllPolicies(null);
    }
    public HashMap<PermissionRequestIntent, String> getAllPolicies(String application) {
    	HashMap<PermissionRequestIntent, String> result = new HashMap<PermissionRequestIntent, String>();
        // Select All Query
 
        SQLiteDatabase db = this.getReadableDatabase();
    	Cursor cursor = db.query(TABLE_POLICY,
                new String[] {KEY_PACKAGE, KEY_PERMISSION, KEY_VALUE},
                null, null, null, null, null);
 
        // looping through all rows and adding to list
        if (cursor.moveToFirst()) {
            do {
            	if (application == null || application.equals(cursor.getString(0)))
            	{
            		AurasiumPermission p = PermissionTranslator.StringToPermission(cursor.getString(1));
            		if (p != null)
            		{
		                PermissionRequestIntent permission = new PermissionRequestIntent();
		                permission.setApplicationPackage(cursor.getString(0));
		                permission.setPermissionRequest(p);
		                result.put(permission, cursor.getString(2));
            		}
            	}
            } while (cursor.moveToNext());
        }
        cursor.close();
        return result;
    }
 
    public HashMap<String, List<PermissionRequestIntent>> getAllPoliciesDict() {
    	HashMap<String, List<PermissionRequestIntent>> result = new HashMap<String, List<PermissionRequestIntent>>();
    	for (Map.Entry<PermissionRequestIntent, String> entry : getAllPolicies().entrySet() ) {
    		String appName = entry.getKey().getApplicationPackage();
    		List<PermissionRequestIntent> intents;
    		if (result.containsKey(appName))
    		{
    			intents = result.get(appName);
    		}
    		else
    		{
    			intents = new ArrayList<PermissionRequestIntent>();
    			result.put(appName, intents);
    		}
			intents.add(entry.getKey());
    	}
    	return result;
    }
    public void deletePolicy(PermissionRequestIntent permission) throws Exception {
//        SQLiteDatabase db = this.getWritableDatabase();
//        db.delete(TABLE_POLICY, KEY_PACKAGE + " = ?",
//                new String[] { String.valueOf(contact.getID()) });
//        db.close();
    	throw new Exception("Not implemented");
    }
 
}