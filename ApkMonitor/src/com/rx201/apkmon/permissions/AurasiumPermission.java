package com.rx201.apkmon.permissions;
import java.io.Serializable;

import android.os.Parcel;
import android.os.Parcelable;

public abstract class AurasiumPermission implements Parcelable, Serializable {
	private String description;
	
	public void setDescription(String Description) {
		this.description = Description;
	}
	public String getDescription() {
		return description;
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeString(description);
	}

	protected AurasiumPermission(Parcel in)
	{
		description = in.readString();
	}
	protected AurasiumPermission() {}
	public AurasiumPermission(String Description)
	{
		setDescription(Description);
	}
	// Returns a generic permission identifier for current permission
	abstract public String getPermissionIdentifier();
	// Returns a UID that identifies an subset of classes of similar permission requests.
	abstract public String getGroupingIdentifier();
	// Returns a user-friendly description of the current permission class.
	abstract public String getGroupingDescription();
}