package com.rx201.apkmon.permissions;

import java.io.Serializable;

import android.os.Parcel;
import android.os.Parcelable;

public class NativeLibraryPermission extends AurasiumPermission  implements Serializable  {

	private String libraryPath;
	@Override
	public void writeToParcel(Parcel dest, int flags) {
		super.writeToParcel(dest, flags);
		dest.writeString(libraryPath);
	}
	private NativeLibraryPermission(Parcel in) {
		super(in);
		libraryPath = in.readString();
	}

	// if DomainName is null, then use value of RemoteIP as DomainName
	public NativeLibraryPermission(String Library)
	{
		super();
		libraryPath = Library;
		setDescription("This application may be trying to increase its control over your phone by loading native library " + Library +  ", allow or not?");
	}
	
	public static final Parcelable.Creator<NativeLibraryPermission> CREATOR = new Parcelable.Creator<NativeLibraryPermission>() {
		public NativeLibraryPermission createFromParcel(Parcel in) {
			return new NativeLibraryPermission(in);
		}

		public NativeLibraryPermission[] newArray(int size) {
			return new NativeLibraryPermission[size];
		}
	};

	@Override
	public String getPermissionIdentifier() {
		return "dlopen";
	}
	@Override
	public String getGroupingIdentifier() {
		return libraryPath;
	}
	@Override
	public String getGroupingDescription() {
		return libraryPath;
	}

	@Override
	public boolean equals(Object o) {
		if (o != null && o instanceof NativeLibraryPermission)
			return libraryPath.equals(((NativeLibraryPermission)o).libraryPath);
		else
			return false;
	}
}
