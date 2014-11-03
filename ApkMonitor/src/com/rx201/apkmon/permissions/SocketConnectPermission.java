package com.rx201.apkmon.permissions;

import java.io.Serializable;

import android.os.Parcel;
import android.os.Parcelable;

public class SocketConnectPermission extends AurasiumPermission  implements Serializable {
	// We distinguish between IP address and Domain names, as IP address is 
	// extracted from connect() syscall directly, while domain names are guessed
	// from previous DNS resolutions. IP address is used for IP reputation, and 
	// domain names are better served to the user and used as grouping identifier.
	public String remoteIP;
	private String remoteDomain;
	private int remotePort;
	
	@Override
	public void writeToParcel(Parcel dest, int flags) {
		super.writeToParcel(dest, flags);
		dest.writeString(remoteIP);
		dest.writeString(remoteDomain);
		dest.writeInt(remotePort);
	}
	private SocketConnectPermission(Parcel in) {
		super(in);
		remoteIP = in.readString();
		remoteDomain = in.readString();
		remotePort = in.readInt();
	}

	// if DomainName is null, then use value of RemoteIP as DomainName
	public SocketConnectPermission(String RemoteIP, String DomainName, int PortNum)
	{
		super();
		remoteIP = RemoteIP;
		if (DomainName != null)
			remoteDomain = DomainName;
		else
			remoteDomain = RemoteIP;
		remotePort = PortNum;

		setDescription(String.format("This application is connecting to remote server: %s:%d, allow or not?", remoteDomain, remotePort));
	}
	
	public static final Parcelable.Creator<SocketConnectPermission> CREATOR = new Parcelable.Creator<SocketConnectPermission>() {
		public SocketConnectPermission createFromParcel(Parcel in) {
			return new SocketConnectPermission(in);
		}

		public SocketConnectPermission[] newArray(int size) {
			return new SocketConnectPermission[size];
		}
	};

	@Override
	public String getPermissionIdentifier() {
		return "connect";
	}
	@Override
	public String getGroupingIdentifier() {
		return remoteDomain;
	}
	@Override
	public String getGroupingDescription() {
		return remoteDomain;
	}
	@Override
	public boolean equals(Object o) {
		if (o != null && o instanceof SocketConnectPermission)
		{
			SocketConnectPermission p = (SocketConnectPermission)o;
			return remoteIP.equals(p.remoteIP) && remoteDomain.equals(p.remoteDomain) && remotePort == p.remotePort;
		}
		else
			return false;
	}

}
