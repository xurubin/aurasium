package com.rx201.apkmon.permissions;

import java.io.Serializable;

import android.os.Parcel;
import android.os.Parcelable;

public class ExecPermission extends AurasiumPermission implements Serializable {

	private String executablePath;
	@Override
	public void writeToParcel(Parcel dest, int flags) {
		super.writeToParcel(dest, flags);
		dest.writeString(executablePath);
	}
	private ExecPermission(Parcel in) {
		super(in);
		executablePath = in.readString();
	}

	// if DomainName is null, then use value of RemoteIP as DomainName
	public ExecPermission(String executable)
	{
		super();
		executablePath = executable;
		setDescription("This application may be trying to increase its control over your phone by executing " + executable +  ", allow or not?");
	}
	
	public static final Parcelable.Creator<ExecPermission> CREATOR = new Parcelable.Creator<ExecPermission>() {
		public ExecPermission createFromParcel(Parcel in) {
			return new ExecPermission(in);
		}

		public ExecPermission[] newArray(int size) {
			return new ExecPermission[size];
		}
	};

	@Override
	public String getPermissionIdentifier() {
		return "execvp";
	}
	@Override
	public String getGroupingIdentifier() {
		return executablePath;
	}
	@Override
	public String getGroupingDescription() {
		return executablePath;
	}
	@Override
	public boolean equals(Object o) {
		if (o != null && o instanceof ExecPermission)
			return executablePath.equals(((ExecPermission)o).executablePath);
		else
			return false;
	}
}
