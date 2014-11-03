package com.rx201.apkmon.permissions;

import android.content.Intent;
import android.os.Messenger;
import android.os.Parcel;
import android.os.Parcelable;


public class PermissionRequestIntent extends Intent implements Parcelable {
	public void writeToParcel(Parcel dest, int flags) {
		super.writeToParcel(dest, flags);
	}

	public PermissionRequestIntent(Parcel in) {
		readFromParcel(in);
	}
	
	public static final Parcelable.Creator<PermissionRequestIntent> CREATOR = new Parcelable.Creator<PermissionRequestIntent>() {
        public PermissionRequestIntent createFromParcel(Parcel in) {
            return new PermissionRequestIntent(in);
        }

		public PermissionRequestIntent[] newArray(int size) {
			return new PermissionRequestIntent[size];
		}
    };
	
    public boolean validate() {
    	return hasExtra("Application") && hasExtra("ResponseToken") && hasExtra("PermissionRequest") && hasExtra("ResultCallback");
    }
    public PermissionRequestIntent(Intent intent)
    {
    	super(intent);
    }
    
	public PermissionRequestIntent() {
		super();
	}

	public int getResponseToken() {
		return getIntExtra("ResponseToken", 0);
	}
	public void setResponseToken(int token) {
		putExtra("ResponseToken", token);
	}
	public String getApplicationPackage() {
		return getStringExtra("Application");
	}
	public void setApplicationPackage(String app) {
		putExtra("Application", app);
	}
	public void setPermissionRequest(AurasiumPermission permission) {
		putExtra("PermissionRequest", (Parcelable)permission);
	}
	public AurasiumPermission getPermissionRequest() {
		return (AurasiumPermission)getParcelableExtra("PermissionRequest");
	}
	public String getPromptText() {
		return getPermissionRequest().getDescription();
	}
	public String getGroupingDescription() {
		return getPermissionRequest().getGroupingDescription();
	}
	public Messenger getResultMessenger() {
		return (Messenger)getParcelableExtra("ResultCallback");
	}
	public void setResultMessenger(Messenger messenger) {
		putExtra("ResultCallback", messenger);
	}
	public int getDefaultDelay() {
		return getIntExtra("DefaultDelay", 0);
	}
	public void setDefaultDelay(int delay) {
		putExtra("DefaultDelay", delay);
	}
	public int getDefaultChoice() {
		return getIntExtra("DefaultChoice", 0);
	}
	public void setDefaultChoice(int choice) {
		putExtra("DefaultChoice", choice);
	}
	public String getRemoteIP() {
		AurasiumPermission p = getPermissionRequest();
		if (p instanceof SocketConnectPermission)
			return ((SocketConnectPermission)p).remoteIP;
		else
			return null;
	}
}